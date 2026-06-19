package com.fuuastisb.aperture.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fuuastisb.aperture.MainActivity
import com.fuuastisb.aperture.R
import com.fuuastisb.aperture.core.di.ApplicationScope
import com.fuuastisb.aperture.data.metadata.MetadataReporter
import com.fuuastisb.aperture.data.recordings.RecordingsRepository
import com.fuuastisb.aperture.data.server.AlertCanceller
import com.fuuastisb.aperture.data.server.DeviceApi
import com.fuuastisb.aperture.data.server.StreamTarget
import com.fuuastisb.aperture.data.settings.SettingsRepository
import com.fuuastisb.aperture.data.upload.PendingUploadStore
import com.fuuastisb.aperture.data.upload.UploadManager
import com.fuuastisb.aperture.domain.model.EmergencyState
import com.fuuastisb.aperture.domain.model.MetadataConfig
import com.fuuastisb.aperture.domain.model.NotificationStyle
import com.fuuastisb.aperture.domain.model.PendingUpload
import com.fuuastisb.aperture.domain.model.RecordingState
import com.fuuastisb.aperture.domain.model.ServerConfig
import com.fuuastisb.aperture.domain.model.StreamingState
import com.fuuastisb.aperture.domain.model.label
import com.fuuastisb.aperture.domain.model.minQuality
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Foreground service that owns a single recording session. It *orchestrates only* — foreground
 * notification, wake lock, choosing a [Recorder] from a settings snapshot, the emergency countdown,
 * and publishing [RecordingState] — while the actual capture lives in the chosen recorder. This is
 * the decomposition of the POC's 558-line god service.
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var stateHolder: RecordingStateHolder
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var recordingsRepository: RecordingsRepository
    @Inject lateinit var metadataReporter: MetadataReporter
    @Inject lateinit var deviceApi: DeviceApi
    @Inject lateinit var alertCanceller: AlertCanceller
    @Inject lateinit var pendingUploadStore: PendingUploadStore
    @Inject lateinit var uploadManager: UploadManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private lateinit var wakeLock: PowerManager.WakeLock
    private var recorder: Recorder? = null
    private var countdownJob: Job? = null
    private var startupJob: Job? = null

    // Backend session (best-effort; never blocks local recording). Id is generated at trigger time.
    private var sessionId: String? = null
    private var sessionServer: ServerConfig? = null

    // Set synchronously in onStartCommand so a rapid re-trigger can't start a second pipeline
    // before the async recorder selection assigns [recorder].
    @Volatile private var active = false

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Aperture::RecordingWakeLock",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (active) return // already recording; ignore a re-trigger
        active = true

        startForegroundCompat(NotificationStyle.DISCREET)
        if (!wakeLock.isHeld) wakeLock.acquire(MAX_RECORDING_MS)
        val id = UUID.randomUUID().toString() // the recording's id, shared with the backend + stream path
        sessionId = id
        stateHolder.setRecordingId(id)
        stateHolder.set(RecordingState.Active())

        startupJob = lifecycleScope.launch {
            val streamSettings = settingsRepository.streamSettingsSnapshot()
            val config = settingsRepository.recordingConfigSnapshot()
            val style = settingsRepository.notificationStyleSnapshot()
            val server = settingsRepository.serverConfigSnapshot()
            if (server.isConfigured) sessionServer = server // set early so a fast Stop still fires /end
            notificationManager().notify(NOTIFICATION_ID, buildNotification(style))

            val storage = settingsRepository.storagePolicySnapshot()
            if (storage.autoDelete) {
                // Reclaim space oldest-first: first against our own cap, then — if the device itself
                // is critically low — against system free space. Reliability over keeping old history.
                recordingsRepository.enforceLimit(storage.maxBytes)
                recordingsRepository.reclaimUntilFree(MIN_FREE_BYTES) {
                    getExternalFilesDir(null)?.usableSpace ?: Long.MAX_VALUE
                }
            }

            val freeBytes = getExternalFilesDir(null)?.usableSpace ?: Long.MAX_VALUE
            if (freeBytes < MIN_FREE_BYTES) {
                stateHolder.setError("Not enough free storage to record. Free up space, or turn on auto-delete.")
                teardown()
                return@launch
            }

            // A stop() may have raced in during the suspending reads above (the home screen shows
            // "recording" the instant we set Active, so a fast Stop tap can land here). If we were
            // already torn down, don't bring a pipeline up — it would orphan the camera/stream.
            if (!active) return@launch

            // Resolve the stream target: a manual RTSP URL, or one derived from the backend connection
            // (rtsp://host:8554/aperture/{id}?token=…). Need at least one sink — stream or local copy.
            val streamUrl = if (StreamTarget.canStream(streamSettings, server)) {
                StreamTarget.publishUrl(streamSettings, server, id)
            } else {
                null
            }
            if (streamUrl == null && !config.saveLocally) {
                stateHolder.setError("Nothing to record — turn on local saving or streaming first.")
                teardown()
                return@launch
            }

            val chosen: Recorder = if (streamUrl != null) {
                stateHolder.updateActive { it.copy(streaming = StreamingState.Connecting) }
                // One encoder feeds both the stream and the local copy. If we're keeping a local copy,
                // run at the LOCAL quality so the saved file isn't degraded (the stream rides it); only
                // when streaming WITHOUT a local copy do we drop to the lower stream quality. Either way
                // the stream never exceeds local quality. Lens is always shared.
                val streamCapture = if (config.saveLocally) {
                    config
                } else {
                    config.copy(
                        quality = minQuality(streamSettings.quality, config.quality),
                        fps = minOf(streamSettings.fps, config.fps),
                    )
                }
                val qualityLabel = streamCapture.quality.label
                StreamingRecorder(
                    context = this@RecordingService,
                    url = streamUrl,
                    scope = appScope,
                    config = streamCapture,
                    audioEnabled = streamSettings.streamAudio,
                    onStreamingState = ::updateStreamingState,
                    onChunk = { uri, startWall, endWall, segment, fullyLive ->
                        handleChunk(id, server, qualityLabel, uri, startWall, endWall, segment, fullyLive)
                    },
                    onFinalized = { onFinalized(null) },
                )
            } else {
                CameraXRecorder(this@RecordingService, this@RecordingService, config, ::onFinalized)
            }
            recorder = chosen
            try {
                chosen.start()
                // Stopped while the pipeline was starting: stopRecording() already stopped the
                // recorder, so don't kick off the backend session on a session that's ending.
                if (!active) return@launch
                // Announce to the backend off the recording path — returns the server-driven alert
                // countdown + watch URL and queues the captured metadata. Never blocks capture.
                if (server.isConfigured) {
                    launchBackendSession(id, server, settingsRepository.metadataConfigSnapshot())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                stateHolder.setError("Couldn't start recording — the camera may be in use or unavailable.")
                try { chosen.stop() } catch (_: Exception) {} // release any partially-acquired pipeline
                teardown() // guarantee teardown — chosen.stop() may not finalize on a failed start
            }
        }
    }

    private fun stopRecording() {
        // If we stop while the alert countdown is still running, that's the grace-period cancel: tell
        // the backend to cancel the alert (it retries until confirmed), then stop.
        val duringGrace = countdownJob?.isActive == true
        countdownJob?.cancel()
        countdownJob = null
        if (duringGrace) sessionId?.let { alertCanceller.requestCancel(it) }

        // Best-effort "ended" hint to the backend (the media-server hook is authoritative).
        val id = sessionId
        val server = sessionServer
        if (id != null && server != null) {
            appScope.launch { runCatching { deviceApi.endRecording(server, id) } }
        }

        val current = recorder
        if (current != null) {
            current.stop() // the recorder's finalize callback → onFinalized() tears everything down
        } else {
            // No recorder yet — a Stop landed during the async startup window. Cancel the in-flight
            // startup (it also guards on `active`) and tear down now.
            startupJob?.cancel()
            startupJob = null
            teardown()
        }
    }

    /**
     * Tell the backend a recording started (returns the server-driven alert countdown + watch URL) and
     * queue the captured metadata. Fully guarded — a backend failure must never disturb the recording.
     */
    private fun launchBackendSession(id: String, server: ServerConfig, metadataConfig: MetadataConfig) {
        // On the process scope so a slow PUT still completes if the user stops quickly. The countdown
        // it may start runs on lifecycleScope and self-terminates when `active` clears.
        appScope.launch {
            runCatching {
                val created = deviceApi.createRecording(server, id, Instant.now().toString())
                if (active && created?.countdownEndsAt != null) startServerCountdown(created.countdownEndsAt)
            }.onFailure { Log.w(TAG, "backend session failed (non-fatal)", it) }
        }
        // Stream live metadata (active location + battery) to the server for the duration of the
        // recording — the emergency live view's moving location. Stopped in teardown()/onDestroy().
        metadataReporter.start(id, server, metadataConfig)
    }

    /**
     * Mirror the backend's alert countdown locally (the server owns it and dispatches on expiry). The
     * user can cancel during this window via the Cancel-alerts action, or by stopping the recording.
     */
    private fun startServerCountdown(endsAtIso: String) {
        val endMs = runCatching { Instant.parse(endsAtIso).toEpochMilli() }.getOrNull() ?: return
        countdownJob = lifecycleScope.launch {
            while (active) {
                val remaining = endMs - System.currentTimeMillis()
                if (remaining <= 0) break
                stateHolder.updateActive { it.copy(emergency = EmergencyState.CountingDown(remaining)) }
                delay(1_000)
            }
            stateHolder.updateActive { it.copy(emergency = EmergencyState.AlertsDispatched) }
        }
    }

    private fun updateStreamingState(streaming: StreamingState) {
        if (recorder == null) return // ignore late streaming callbacks after teardown
        stateHolder.updateActive { it.copy(streaming = streaming) }
    }

    private fun onFinalized(uri: Uri?) {
        if (uri != null) Log.d(TAG, "Recording saved: $uri")
        teardown()
    }

    /**
     * A local recording chunk finalised: it's already published to the gallery; if the stream wasn't
     * live for its whole span the server is missing it, so enqueue it for retro-upload. Best-effort and
     * off the recording path; [UploadManager] uploads it when online (mid-recording, throttled).
     */
    private fun handleChunk(
        id: String,
        server: ServerConfig,
        quality: String,
        uri: Uri?,
        startWallMs: Long,
        endWallMs: Long,
        segment: Int,
        fullyLive: Boolean,
    ) {
        if (uri == null || fullyLive || !server.isConfigured) return // server already has live chunks
        appScope.launch {
            runCatching {
                pendingUploadStore.add(
                    PendingUpload(id, segment, uri.toString(), startWallMs, endWallMs, quality),
                )
                uploadManager.kick()
            }.onFailure { Log.w(TAG, "enqueue chunk upload failed (non-fatal)", it) }
        }
    }

    private fun teardown() {
        active = false
        metadataReporter.stop()
        countdownJob?.cancel()
        countdownJob = null
        startupJob = null
        recorder = null
        sessionId = null
        sessionServer = null
        stateHolder.setRecordingId(null)
        stateHolder.set(RecordingState.Idle)
        if (wakeLock.isHeld) wakeLock.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Defensive cleanup for an abnormal end (low-memory kill, task removed) that bypasses
        // teardown(): never leave the wake lock held or a pipeline running.
        startupJob?.cancel()
        countdownJob?.cancel()
        metadataReporter.stop()
        try { recorder?.stop() } catch (e: Exception) { Log.w(TAG, "recorder stop on destroy", e) }
        recorder = null
        sessionId = null
        sessionServer = null
        stateHolder.setRecordingId(null)
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    @SuppressLint("InlinedApi") // the FGS-type constants are only used under the Build.VERSION guards below
    private fun startForegroundCompat(style: NotificationStyle) {
        createChannel()
        val notification = buildNotification(style)
        // Claim the location FGS type only when location is granted, so live location works even with
        // the screen off (Android 14+ requires it for background location during the service).
        val location = if (metadataReporter.canUseLocationForegroundType()) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or location,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or location,
            )
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW, // low = no sound, regardless of wording
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(style: NotificationStyle): Notification {
        val (title, text) = when (style) {
            NotificationStyle.DISCREET ->
                getString(R.string.recording_notification_title) to getString(R.string.recording_notification_text)
            NotificationStyle.CLEAR ->
                "Recording" to "Aperture is recording video"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "recording"
        private const val MAX_RECORDING_MS = 60 * 60 * 1000L // wake-lock safety cap
        private const val MIN_FREE_BYTES = 200L * 1024 * 1024 // refuse to start under ~200 MB free

        const val ACTION_START = "com.fuuastisb.aperture.action.START_RECORDING"
        const val ACTION_STOP = "com.fuuastisb.aperture.action.STOP_RECORDING"

        fun startIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }
    }
}
