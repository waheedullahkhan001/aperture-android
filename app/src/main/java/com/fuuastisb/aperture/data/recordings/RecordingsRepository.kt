package com.fuuastisb.aperture.data.recordings

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.fuuastisb.aperture.domain.model.RecordingItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device recordings library, backed by MediaStore (every pipeline saves into
 * `Movies/Aperture`). We query our own entries — no read permission needed on API 29+ — which gives
 * the in-app list, deletion, and the storage-policy enforcement the SRS asks for, without a
 * separate database.
 */
@Singleton
class RecordingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun list(): List<RecordingItem> = withContext(Dispatchers.IO) { query() }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        query().forEach { runCatching { context.contentResolver.delete(it.uri, null, null) } }
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) { query().sumOf { it.sizeBytes } }

    /** Delete the oldest recordings until the total fits within [maxBytes]. */
    suspend fun enforceLimit(maxBytes: Long) = withContext(Dispatchers.IO) {
        val oldestFirst = query().sortedBy { it.dateAddedSec }
        var total = oldestFirst.sumOf { it.sizeBytes }
        for (item in oldestFirst) {
            if (total <= maxBytes) break
            val deleted = runCatching { context.contentResolver.delete(item.uri, null, null) > 0 }
                .getOrDefault(false)
            if (deleted) total -= item.sizeBytes
        }
    }

    /**
     * Delete our oldest recordings until the device reports at least [targetFreeBytes] free (or none
     * of ours remain). Keeps recording possible on a critically full device — oldest sacrificed first,
     * re-reading free space after each delete so we stop as soon as there's room.
     */
    suspend fun reclaimUntilFree(targetFreeBytes: Long, freeBytes: () -> Long) = withContext(Dispatchers.IO) {
        if (freeBytes() >= targetFreeBytes) return@withContext
        for (item in query().sortedBy { it.dateAddedSec }) {
            if (freeBytes() >= targetFreeBytes) break
            runCatching { context.contentResolver.delete(item.uri, null, null) }
        }
    }

    private fun query(): List<RecordingItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
        )
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" to arrayOf("Movies/Aperture%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Video.Media.DATA} LIKE ?" to arrayOf("%/Movies/Aperture/%")
        }
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val items = mutableListOf<RecordingItem>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                items += RecordingItem(
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(nameCol) ?: "recording",
                    dateAddedSec = c.getLong(dateCol),
                    durationMs = c.getLong(durCol),
                    sizeBytes = c.getLong(sizeCol),
                )
            }
        }
        return items
    }
}
