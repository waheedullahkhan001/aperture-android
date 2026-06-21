package com.fuuastisb.aperture.data.upload

/** Lifecycle of a pending retro-upload clip, for the debug/visibility screen. */
enum class UploadState { QUEUED, UPLOADING, FAILED, DONE, GAVE_UP }

/** A snapshot of one clip's retro-upload status (wall-clock range, retry count, state). */
data class UploadClipStatus(
    val recordingId: String,
    val segmentNumber: Int,
    val startMs: Long,
    val endMs: Long,
    val quality: String?,
    val attempts: Int,
    val state: UploadState,
) {
    val key: String get() = "$recordingId#$segmentNumber"
}
