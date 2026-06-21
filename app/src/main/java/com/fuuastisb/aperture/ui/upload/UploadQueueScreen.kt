package com.fuuastisb.aperture.ui.upload

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuuastisb.aperture.data.upload.UploadClipStatus
import com.fuuastisb.aperture.data.upload.UploadState
import com.fuuastisb.aperture.ui.settings.SettingsScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug/visibility screen for the retro-upload queue: the clips recorded while the live stream was down,
 * waiting to be uploaded to the server. Shows each clip's wall-clock range, state and retry count.
 */
@Composable
fun UploadQueueScreen(
    onBack: () -> Unit,
    viewModel: UploadQueueViewModel = hiltViewModel(),
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    SettingsScaffold("Upload queue", onBack) { padding ->
        if (statuses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No clips waiting to upload.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                Text(
                    "Clips recorded while the live stream was down, uploaded separately when online.",
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::retryNow, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Retry now")
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                statuses.forEach { clip -> ClipRow(clip) }
            }
        }
    }
}

@Composable
private fun ClipRow(clip: UploadClipStatus) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(timeRange(clip.startMs, clip.endMs), style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append("seg ${clip.segmentNumber}")
                    clip.quality?.let { append(" · $it") }
                    if (clip.attempts > 0) append(" · ${clip.attempts} ${if (clip.attempts == 1) "retry" else "retries"}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val (label, color) = when (clip.state) {
            UploadState.QUEUED -> "Queued" to MaterialTheme.colorScheme.onSurfaceVariant
            UploadState.UPLOADING -> "Uploading…" to MaterialTheme.colorScheme.tertiary
            UploadState.FAILED -> "Failed" to MaterialTheme.colorScheme.error
            UploadState.DONE -> "Done" to MaterialTheme.colorScheme.primary
            UploadState.GAVE_UP -> "Gave up" to MaterialTheme.colorScheme.error
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = color)
    }
    HorizontalDivider()
}

private val TIME_FMT = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

private fun timeRange(startMs: Long, endMs: Long): String {
    val durationS = ((endMs - startMs).coerceAtLeast(0)) / 1000
    return "${TIME_FMT.format(Date(startMs))} · ${durationS}s"
}
