package com.fuuastisb.aperture.recording

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.Display
import android.view.Surface
import com.fuuastisb.aperture.domain.model.CameraLens
import com.fuuastisb.aperture.domain.model.RecordingConfig
import com.fuuastisb.aperture.domain.model.StreamingState
import com.fuuastisb.aperture.domain.model.VideoQuality
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.BitrateAdapter
import com.pedro.rtsp.rtsp.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stream + local pipeline: RootEncoder's [GenericStream] drives one H.264/AAC encoder that feeds
 * BOTH an RTSP/RTMP/SRT push and a parallel local MP4 (written to app storage, then published to
 * the gallery on stop). Local recording continues even if the network push fails. Chosen when a
 * stream URL is configured — mirrors the POC's RootEncoder path, extracted into a [Recorder].
 *
 * Network resilience: a [BitrateAdapter] lowers/raises the live video bitrate as the link changes,
 * and a dropped connection auto-reconnects (bounded retries) without disturbing local recording.
 * Honours the shared [RecordingConfig.lens]; quality and fps come from the chosen config.
 *
 * Retro-upload: the local copy is recorded in rotating ~30s **chunks** (so a chunk recorded while the
 * stream was down can be uploaded mid-recording — an emergency recording may never cleanly stop). As
 * each chunk finalises it's published to the gallery and reported via [onChunk]; a chunk that wasn't
 * live for its whole span is flagged for retro-upload (the server only lacks those).
 */
class StreamingRecorder(
    private val context: Context,
    private val url: String,
    private val scope: CoroutineScope,
    private val config: RecordingConfig,
    private val audioEnabled: Boolean,
    // Video bitrate in bits/s; 0 = derive from quality. The local chunks are published here.
    private val overrideBitrateBps: Int,
    private val relativePath: String,
    private val onStreamingState: (StreamingState) -> Unit,
    // Per finalized chunk: published Uri, wall-clock start/end, 1-based segment number, and whether the
    // stream was live for the chunk's whole span (if not, the server is missing it → retro-upload).
    private val onChunk: (Uri?, Long, Long, Int, Boolean) -> Unit,
    // Called once after the final chunk, to tear the service down.
    private val onFinalized: () -> Unit,
) : Recorder, ConnectChecker {

    private var stream: GenericStream? = null
    private var bitrateAdapter: BitrateAdapter? = null

    // Chunked local recording. Guarded by [recLock] because the rotation timer and stop() both touch it.
    private val recLock = Any()
    private var recStarted = false
    private var rotationJob: Job? = null
    private var chunkDir: File? = null
    private var currentChunkFile: File? = null
    private var chunkIndex = 0
    private var chunkStartWallMs = 0L
    // Written from RootEncoder's callback thread (markStreamDown) and read under recLock on the rotation
    // thread, so it must be @Volatile — otherwise a dropped-stream chunk could be misread as fully-live
    // and skipped for retro-upload, leaving a permanent gap server-side.
    @Volatile private var chunkFullyLive = false
    @Volatile private var streamLive = false // current live state, sampled when a chunk starts

    // Set when we initiate stop(), so the stopStream-triggered callbacks don't try to reconnect.
    @Volatile private var stopping = false

    override suspend fun start() {
        val cameraSource = Camera2Source(context.applicationContext)
        // Honour the shared lens. Before the camera opens, switchCamera() just flips the target
        // facing (no hardware work); RootEncoder's Camera2Source defaults to BACK.
        if (config.lens == CameraLens.FRONT) {
            runCatching { cameraSource.switchCamera() }
                .onFailure { Log.w(TAG, "switchCamera(front) failed; using default lens", it) }
        }
        // Audio off = a muted mic (the pipeline/mux is unchanged, just silent) — safer than dropping
        // the audio track entirely. Mic permission is granted at onboarding regardless.
        val micSource = MicrophoneSource().apply { if (!audioEnabled) mute() }
        val genericStream = GenericStream(
            context.applicationContext,
            this,
            cameraSource,
            micSource,
        )
        stream = genericStream

        val (width, height) = resolution()
        val prepared = try {
            genericStream.prepareVideo(width, height, videoBitrate(), fps = config.fps, iFrameInterval = KEYFRAME_INTERVAL_S, rotation = encoderRotation()) &&
                genericStream.prepareAudio(AUDIO_SAMPLE_RATE, AUDIO_STEREO, AUDIO_BITRATE)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "prepareVideo/prepareAudio threw", e)
            false
        }
        if (!prepared) {
            onStreamingState(StreamingState.Failed("Encoder prepare failed"))
            releaseQuietly()
            throw IllegalStateException("Failed to prepare stream encoders")
        }

        // Local copy is optional (the user can stream without keeping one). When kept, it's recorded in
        // rotating ~30s chunks so a chunk can be uploaded mid-recording (see class doc). When off, only
        // the network push runs.
        if (config.saveLocally) {
            chunkDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Aperture")
                .apply { mkdirs() }
            synchronized(recLock) {
                recStarted = true
                startChunk()
            }
            rotationJob = scope.launch {
                while (recStarted) {
                    delay(CHUNK_MS)
                    synchronized(recLock) { if (recStarted) rotateChunk() }
                }
            }
        }

        // Adaptive bitrate: nudge the live video bitrate down/up as the network changes, capped at
        // the target for this quality. Bounded auto-reconnect on a dropped connection.
        bitrateAdapter = BitrateAdapter { newBitrate -> stream?.setVideoBitrateOnFly(newBitrate) }
            .apply { setMaxBitrate(videoBitrate()) }
        // The backend's MediaMTX exposes only 8554/TCP — RTSP over UDP times out. Force interleaved TCP.
        runCatching { genericStream.getStreamClient().setProtocol(Protocol.TCP) }
            .onFailure { Log.w(TAG, "setProtocol(TCP) failed; the stream may fall back to UDP and time out", it) }
        runCatching { genericStream.getStreamClient().setReTries(STREAM_RETRIES) }

        onStreamingState(StreamingState.Connecting)
        genericStream.startStream(url)

        // Torch on for the session if requested (camera is open by now). No-op/ignored on a lens with no
        // flash; turns off when the camera is released in stop(). Device-validated behaviour.
        if (config.torch) {
            runCatching { cameraSource.enableLantern() }
                .onFailure { Log.w(TAG, "enableLantern failed (no flash on this lens?)", it) }
        }
    }

    override fun stop() {
        stopping = true // suppress reconnect attempts from the stopStream-triggered callbacks
        streamLive = false
        val active = stream
        try { if (active?.isStreaming == true) active.stopStream() } catch (e: Exception) { Log.w(TAG, "stopStream", e) }
        synchronized(recLock) {
            recStarted = false
            rotationJob?.cancel()
            rotationJob = null
            finalizeChunk() // stop + publish + report the final chunk
        }
        bitrateAdapter = null
        releaseQuietly()
        onStreamingState(StreamingState.Off)
        onFinalized()
    }

    /** Begin a new local chunk. Must hold [recLock]. */
    private fun startChunk() {
        val dir = chunkDir ?: return
        val file = File(dir, "rec_${TIMESTAMP_FORMAT.format(Date())}_${chunkIndex + 1}.mp4")
        try {
            stream?.startRecord(file.absolutePath) { status -> Log.d(TAG, "record status: $status") }
            currentChunkFile = file
            chunkIndex += 1
            chunkStartWallMs = System.currentTimeMillis()
            chunkFullyLive = streamLive // only "fully live" if the stream is up for the whole chunk
        } catch (e: Exception) {
            Log.e(TAG, "startRecord (chunk $chunkIndex) failed", e)
            currentChunkFile = null
        }
    }

    /** Finalise the current chunk (stop record, publish, report). Must hold [recLock]. */
    private fun finalizeChunk() {
        val file = currentChunkFile ?: return
        val startWall = chunkStartWallMs
        val endWall = System.currentTimeMillis()
        val segment = chunkIndex
        val fullyLive = chunkFullyLive
        currentChunkFile = null
        try { if (stream?.isRecording == true) stream?.stopRecord() } catch (e: Exception) { Log.w(TAG, "stopRecord (chunk)", e) }
        // Publish to the gallery + report off the lock (publish is IO and survives teardown).
        scope.launch { onChunk(MediaStorePublisher.publish(context, file, relativePath), startWall, endWall, segment, fullyLive) }
    }

    /** Rotation tick: close the current chunk and open the next. Must hold [recLock]. */
    private fun rotateChunk() {
        finalizeChunk()
        startChunk()
    }

    /** The stream is no longer delivering — the current chunk is no longer fully-live. */
    private fun markStreamDown() {
        streamLive = false
        chunkFullyLive = false
    }

    private fun releaseQuietly() {
        try { stream?.release() } catch (e: Exception) { Log.w(TAG, "release", e) }
        stream = null
    }

    // --- ConnectChecker → streaming state ---
    override fun onConnectionStarted(url: String) = onStreamingState(StreamingState.Connecting)

    override fun onConnectionSuccess() {
        // Mark live, but DON'T flip the current chunk back to "fully live" — if it was down for any of
        // this chunk, the chunk still needs uploading; only the NEXT chunk starts fresh as fully-live.
        streamLive = true
        bitrateAdapter?.setMaxBitrate(videoBitrate()) // reset the ceiling after a (re)connect
        onStreamingState(StreamingState.Live)
    }

    override fun onConnectionFailed(reason: String) {
        markStreamDown() // stream is down until the next success — this chunk now needs uploading
        // Try to reconnect (unless we're the ones stopping). Local recording is unaffected either way;
        // only when the retry budget is exhausted do we surface a hard failure.
        if (!stopping) {
            val retrying = runCatching {
                stream?.getStreamClient()?.reTry(RETRY_DELAY_MS, reason) ?: false
            }.getOrDefault(false)
            if (retrying) {
                onStreamingState(StreamingState.Connecting)
                return
            }
        }
        // Redact the apd_ token if RootEncoder echoed the publish URL into the failure reason.
        onStreamingState(StreamingState.Failed(reason.replace(Regex("token=[^&\\s]+"), "token=***")))
    }

    override fun onDisconnect() {
        markStreamDown()
        onStreamingState(StreamingState.Off)
    }
    override fun onAuthError() {
        markStreamDown()
        onStreamingState(StreamingState.Failed("Auth error"))
    }
    override fun onAuthSuccess() = Unit

    override fun onNewBitrate(bitrate: Long) {
        val congested = runCatching { stream?.getStreamClient()?.hasCongestion() ?: false }.getOrDefault(false)
        bitrateAdapter?.adaptBitrate(bitrate, congested)
    }

    private fun resolution(): Pair<Int, Int> = when (config.quality) {
        VideoQuality.FHD -> 1920 to 1080
        VideoQuality.HD -> 1280 to 720
        VideoQuality.SD -> 848 to 480
    }

    private fun videoBitrate(): Int = if (overrideBitrateBps > 0) {
        overrideBitrateBps
    } else when (config.quality) {
        VideoQuality.FHD -> 4_000_000
        VideoQuality.HD -> 2_500_000
        VideoQuality.SD -> 1_200_000
    }

    /**
     * Rotate the encoder so portrait video isn't pillarboxed. The Camera2Source feeds frames in
     * the sensor's native (landscape) orientation, so we rotate to match the current display.
     */
    private fun encoderRotation(): Int {
        val surfaceRotation = try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        } catch (e: Exception) {
            Log.w(TAG, "Could not read display rotation, defaulting to portrait", e)
            Surface.ROTATION_0
        }
        return when (surfaceRotation) {
            Surface.ROTATION_0 -> 90
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_180 -> 270
            Surface.ROTATION_270 -> 180
            else -> 90
        }
    }

    private companion object {
        const val TAG = "StreamingRecorder"
        const val AUDIO_SAMPLE_RATE = 44_100
        const val AUDIO_BITRATE = 128_000
        const val AUDIO_STEREO = true
        const val STREAM_RETRIES = 8         // reconnect attempts before giving up (local keeps going)
        const val RETRY_DELAY_MS = 5_000L    // wait between reconnect attempts
        const val KEYFRAME_INTERVAL_S = 2    // ≤2s so the server can split its 30s segments at keyframes
        const val CHUNK_MS = 30_000L         // rotate the local recording every ~30s (matches server segments)
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}
