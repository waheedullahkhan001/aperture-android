package com.fuuastisb.aperture.domain.model

import android.net.Uri

/** A recording in the on-device library, as seen in MediaStore (`Movies/Aperture`). */
data class RecordingItem(
    val uri: Uri,
    val name: String,
    val dateAddedSec: Long,
    val durationMs: Long,
    val sizeBytes: Long,
)
