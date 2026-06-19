package com.fuuastisb.aperture.domain.model

/** Which camera the recording uses. */
enum class CameraLens { BACK, FRONT }

/** Target capture quality. Maps to a CameraX [androidx.camera.video.Quality] and a stream resolution. */
enum class VideoQuality { FHD, HD, SD }

/** Higher [rank] = better quality. Used to keep a stream from exceeding the local capture. */
val VideoQuality.rank: Int
    get() = when (this) {
        VideoQuality.FHD -> 2
        VideoQuality.HD -> 1
        VideoQuality.SD -> 0
    }

/** The lower of two qualities — so a stream is never asked to exceed local quality. */
fun minQuality(a: VideoQuality, b: VideoQuality): VideoQuality = if (a.rank <= b.rank) a else b

/** Human label used for the server's per-clip quality field and the UI. */
val VideoQuality.label: String
    get() = when (this) {
        VideoQuality.FHD -> "1080p"
        VideoQuality.HD -> "720p"
        VideoQuality.SD -> "480p"
    }

/** User-configurable capture settings applied to each recording session (SRS-020..022). */
data class RecordingConfig(
    val lens: CameraLens = CameraLens.BACK,
    val quality: VideoQuality = VideoQuality.FHD,
    val fps: Int = 30,
    // Whether to keep a local copy. Off = stream-only — but local and streaming can't BOTH be off,
    // or a trigger would capture nothing. [quality]/[fps] above apply to the local copy.
    val saveLocally: Boolean = true,
    val recordAudio: Boolean = true,
)
