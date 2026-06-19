package com.fuuastisb.aperture.data.upload

import android.content.Context
import com.fuuastisb.aperture.domain.model.PendingUpload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent queue of clips awaiting retro-upload, stored as a small JSON file in app storage (no DB
 * dependency for a handful of entries). Keyed by ([PendingUpload.key] = recordingId#segmentNumber) —
 * one recording may have several gap segments. All mutations are serialised through a [Mutex] so the
 * uploader and the recording-finalize path can't race.
 */
@Singleton
class PendingUploadStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    suspend fun add(item: PendingUpload) = mutex.withLock {
        write(read().filterNot { it.key == item.key } + item)
    }

    suspend fun all(): List<PendingUpload> = mutex.withLock { read() }

    suspend fun remove(key: String) = mutex.withLock {
        write(read().filterNot { it.key == key })
    }

    suspend fun incrementAttempts(key: String) = mutex.withLock {
        write(read().map { if (it.key == key) it.copy(attempts = it.attempts + 1) else it })
    }

    private fun read(): List<PendingUpload> =
        runCatching { if (file.exists()) parsePendingUploads(file.readText()) else emptyList() }.getOrDefault(emptyList())

    private fun write(list: List<PendingUpload>) {
        runCatching { file.writeText(pendingUploadsToJson(list)) }
    }

    private companion object {
        const val FILE_NAME = "pending_uploads.json"
    }
}

/** Pure (testable) serialisation of the pending-upload queue. */
fun pendingUploadsToJson(list: List<PendingUpload>): String {
    val arr = JSONArray()
    list.forEach { item ->
        arr.put(
            JSONObject().apply {
                put("recordingId", item.recordingId)
                put("segmentNumber", item.segmentNumber)
                put("uri", item.uri)
                put("startMs", item.startMs)
                put("endMs", item.endMs)
                item.quality?.let { put("quality", it) }
                put("attempts", item.attempts)
            },
        )
    }
    return arr.toString()
}

fun parsePendingUploads(json: String): List<PendingUpload> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        PendingUpload(
            recordingId = o.getString("recordingId"),
            segmentNumber = o.optInt("segmentNumber", 1),
            uri = o.getString("uri"),
            startMs = o.getLong("startMs"),
            endMs = o.getLong("endMs"),
            quality = if (o.has("quality") && !o.isNull("quality")) o.getString("quality") else null,
            attempts = o.optInt("attempts", 0),
        )
    }
}
