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

    // start() suspends twice (provider acquisition, then the Main-thread bind). If stop() lands in that
    // window, we must NOT go on to bind + start a recording nothing can stop (a zombie). Set by stop().
    @Volatile private var stopped = false

    // Camera/microphone permissions are granted during onboarding before recording is reachable; if
    // they are later revoked, the resulting SecurityException is caught by RecordingService.start()'s
    // try/catch and surfaced to the user, so a missing permission can't crash here.
    @SuppressLint("MissingPermission")
    override suspend fun start() {
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        if (stopped) return // stopped during provider acquisition — don't bind anything
        cameraProvider = provider

        withContext(Dispatchers.Main) {
            if (stopped) return@withContext // stopped before binding — don't start a recording nothing stops
            provider.unbindAll()

            val videoCapture = VideoCapture.Builder(
                CameraXVideoRecorder.Builder().setQualitySelector(qualitySelector()).build(),
            ).setTargetFrameRate(Range(config.fps, config.fps)).build()
            val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector(), videoCapture)
            // Torch on for the whole session if requested and the lens has a flash. Released automatically
            // by unbindAll() in stop().
            if (config.torch && camera.cameraInfo.hasFlashUnit()) {
                runCatching { camera.cameraControl.enableTorch(true) }
                    .onFailure { Log.w(TAG, "enableTorch failed", it) }
            }

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
        stopped = true
        val rec = recording
        recording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        // A running recording's Finalize event drives onFinalized → teardown. If none was started yet
        // (stop arrived during start()), trigger teardown directly so the service doesn't hang Active.
        if (rec != null) rec.stop() else onFinalized(null)
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
