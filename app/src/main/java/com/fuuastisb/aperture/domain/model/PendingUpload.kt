package com.fuuastisb.aperture.domain.model

/**
 * A finalized local recording **chunk** awaiting retro-upload to the server — a ~30s segment that was
 * recorded while the live stream was down (offline, or a dropped span), so the server doesn't have it
 * yet. [uri] is the chunk's MediaStore content URI; [startMs]/[endMs] are its real wall-clock bounds
 * (so the server can stitch chunks and streamed segments onto one timeline).
 *
 * The queue key is ([recordingId], [segmentNumber]) — a recording has many chunks, and that pair is
 * also the server's idempotency key, so a retry never duplicates. Whole chunk files are uploaded as-is
 * (no trimming).
 */
data class PendingUpload(
    val recordingId: String,
    val segmentNumber: Int,
    val uri: String,
    val startMs: Long,
    val endMs: Long,
    val quality: String?,
    val attempts: Int = 0,
) {
    val key: String get() = "$recordingId#$segmentNumber"
}
