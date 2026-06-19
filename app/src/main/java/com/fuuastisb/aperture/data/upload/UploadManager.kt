package com.fuuastisb.aperture.data.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.fuuastisb.aperture.core.di.ApplicationScope
import com.fuuastisb.aperture.data.server.DeviceApi
import com.fuuastisb.aperture.data.settings.SettingsRepository
import com.fuuastisb.aperture.domain.model.PendingUpload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the [PendingUploadStore] — retro-uploading recording chunks the server doesn't have yet
 * (recorded while the live stream was down). Each queued item is a whole finalized chunk file.
 *  - Uploads **during recording too** (the emergency case: get footage off the phone ASAP at
 *    reconnect), throttled by serial single-flight so it doesn't starve the recovering stream;
 *  - only when online and a server is configured;
 *  - bounded retries; a clip that keeps failing (or exceeds the 200 MB cap) is dropped.
 *
 * Triggered on app start, when the network becomes available, and as each chunk finalises.
 */
@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val store: PendingUploadStore,
    private val deviceApi: DeviceApi,
    private val settings: SettingsRepository,
) {
    private val drainLock = Mutex()

    init {
        // Re-attempt the queue whenever connectivity returns.
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = kick()
            })
        }.onFailure { Log.w(TAG, "could not register network callback", it) }
    }

    /** Attempt to drain the queue (no-op if already draining). Safe to call from anywhere. */
    fun kick() {
        appScope.launch {
            if (!drainLock.tryLock()) return@launch
            try {
                drain()
            } finally {
                drainLock.unlock()
            }
        }
    }

    private suspend fun drain() {
        if (!isOnline()) return
        val server = settings.serverConfigSnapshot()
        if (!server.isConfigured) return

        for (item in store.all().sortedBy { it.startMs }) {
            if (item.attempts >= MAX_ATTEMPTS) {
                Log.w(TAG, "dropping ${item.key} after ${item.attempts} failed attempts")
                store.remove(item.key)
                continue
            }
            when (upload(server, item)) {
                UploadResult.SUCCESS, UploadResult.PERMANENT_SKIP -> store.remove(item.key)
                UploadResult.RETRY -> store.incrementAttempts(item.key)
            }
        }
    }

    private suspend fun upload(server: com.fuuastisb.aperture.domain.model.ServerConfig, item: PendingUpload): UploadResult {
        val uri = runCatching { Uri.parse(item.uri) }.getOrNull() ?: return UploadResult.RETRY
        val name = displayName(uri) ?: "clip_${item.recordingId}_${item.segmentNumber}.mp4"
        val size = fileSize(uri) ?: return UploadResult.RETRY
        // The server rejects clips over 200 MB — don't waste retries on one that can never succeed.
        if (size > MAX_UPLOAD_BYTES) {
            Log.w(TAG, "skipping ${item.key}: ${size / (1024 * 1024)} MB exceeds the 200 MB cap")
            return UploadResult.PERMANENT_SKIP
        }
        val ok = deviceApi.uploadClip(
            config = server,
            recordingId = item.recordingId,
            // Stable per-clip idempotency key (the chunk's identity, unchanged across retries).
            clipId = item.key,
            fileName = name,
            sizeBytes = size,
            startIso = Instant.ofEpochMilli(item.startMs).toString(),
            endIso = Instant.ofEpochMilli(item.endMs).toString(),
            quality = item.quality,
            openStream = { context.contentResolver.openInputStream(uri) ?: error("cannot open $uri") },
        )
        return if (ok) UploadResult.SUCCESS else UploadResult.RETRY
    }

    private enum class UploadResult { SUCCESS, RETRY, PERMANENT_SKIP }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun displayName(uri: Uri): String? = queryColumn(uri, OpenableColumns.DISPLAY_NAME)?.takeIf { it.isNotBlank() }

    private fun fileSize(uri: Uri): Long? = queryColumn(uri, OpenableColumns.SIZE)?.toLongOrNull()

    private fun queryColumn(uri: Uri, column: String): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private companion object {
        const val TAG = "UploadManager"
        const val MAX_ATTEMPTS = 5
        const val MAX_UPLOAD_BYTES = 200L * 1024 * 1024 // server caps clips at 200 MB
    }
}
