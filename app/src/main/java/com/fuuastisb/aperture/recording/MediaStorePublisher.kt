package com.fuuastisb.aperture.recording

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Moves an app-private recorded file into MediaStore (`Movies/Aperture`) so it shows up in the
 * gallery, then deletes the source. Used by the streaming pipeline, which writes its local MP4 to
 * app storage first; the CameraX pipeline writes to MediaStore directly and doesn't need this.
 */
object MediaStorePublisher {

    private const val TAG = "MediaStorePublisher"

    suspend fun publish(context: Context, file: File): Uri? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Source missing or empty, skipping publish: $file")
            return@withContext null
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Aperture")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            file.delete()
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Publish failed", e)
            resolver.delete(uri, null, null)
            null
        }
    }
}
