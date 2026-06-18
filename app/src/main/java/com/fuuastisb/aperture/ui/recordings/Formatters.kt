package com.fuuastisb.aperture.ui.recordings

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val GB = 1024.0 * 1024.0 * 1024.0
private const val MB = 1024.0 * 1024.0

fun formatBytes(bytes: Long): String = when {
    bytes >= GB -> String.format(Locale.US, "%.1f GB", bytes / GB)
    else -> String.format(Locale.US, "%.0f MB", bytes / MB)
}

fun formatGb(bytes: Long): String = "${(bytes / GB).toInt()} GB"

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

fun formatDate(epochSeconds: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
