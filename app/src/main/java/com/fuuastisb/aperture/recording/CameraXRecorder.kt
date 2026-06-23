package com.fuuastisb.aperture.recording

import android.annotation.SuppressLint
import android.content.ContentValues
import android.util.Range
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.Recorder as CameraXVideoRecorder
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fuuastisb.aperture.domain.model.CameraLens
import com.fuuastisb.aperture.domain.model.RecordingConfig
import com.fuuastisb.aperture.domain.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only recording pipeline: CameraX writes the configured quality (falling back to lower)
 * MP4 straight into MediaStore at `Movies/Aperture` — the POC's CameraX path, lifted out of the
 * foreground service into a focused [Recorder]. Chosen when no media-server stream is configured.
 *
 * Lens, quality and frame rate all apply ([RecordingConfig.fps] is set as a target frame-rate range;
 * the device rounds to the nearest supported range, so it's a strong hint rather than a guarantee).
 */
class CameraXRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val config: RecordingConfig,
    private val relativePath: String,
    private val onFinalized: (Uri?) -> Unit,
) : Recorder {

    private var cameraProvider: ProcessCameraProvider? = null
    private var recording: Recording? = null

    // Camera/microphone permissions are granted during onboarding before recording is reachable; if
    // they are later revoked, the resulting SecurityException is caught by RecordingService.start()'s
    // try/catch and surfaced to the user, so a missing permission can't crash here.
    @SuppressLint("MissingPermission")
    override suspend fun start() {
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        cameraProvider = provider

        withContext(Dispatchers.Main) {
            provider.unbindAll()

            val videoCapture = VideoCapture.Builder(
                CameraXVideoRecorder.Builder().setQualitySelector(qualitySelector()).build(),
            ).setTargetFrameRate(Range(config.fps, config.fps)).build()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector(), videoCapture)

            val name = "emergency_${TIMESTAMP_FORMAT.format(Date())}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            }
            val output = MediaStoreOutputOptions
                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(values)
                .build()

            val pending = videoCapture.output.prepareRecording(context, output)
            if (config.recordAudio) pending.withAudioEnabled()
            recording = pending
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        val uri = if (event.hasError()) {
                            Log.e(TAG, "Recording finalised with error: ${event.error}")
                            null
                        } else {
                            event.outputResults.outputUri
                        }
                        onFinalized(uri)
                    }
                }
        }
    }

    override fun stop() {
        recording?.stop()
        recording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun cameraSelector(): CameraSelector = when (config.lens) {
        CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
    }

    private fun qualitySelector(): QualitySelector {
        val ordered = when (config.quality) {
            VideoQuality.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
            VideoQuality.HD -> listOf(Quality.HD, Quality.SD)
            VideoQuality.SD -> listOf(Quality.SD)
        }
        return QualitySelector.fromOrderedList(ordered, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
    }

    private companion object {
        const val TAG = "CameraXRecorder"
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}
