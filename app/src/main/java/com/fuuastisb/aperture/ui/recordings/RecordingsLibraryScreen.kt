package com.fuuastisb.aperture.ui.recordings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuuastisb.aperture.domain.model.RecordingItem
import com.fuuastisb.aperture.ui.settings.SettingsScaffold

/** The on-device recordings library (UC-12): list, play, delete, delete-all — deletes are confirmed. */
@Composable
fun RecordingsLibraryScreen(
    onBack: () -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    var confirmDeleteAll by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecordingItem?>(null) }

    SettingsScaffold("Recordings", onBack) { padding ->
        if (recordings.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No recordings yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val totalBytes = recordings.sumOf { it.sizeBytes }
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${recordings.size} recordings · ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { confirmDeleteAll = true }) { Text("Delete all") }
                }
                HorizontalDivider()
                recordings.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.play(item.uri) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${formatDate(item.dateAddedSec)} · ${formatDuration(item.durationMs)} · ${formatBytes(item.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { pendingDelete = item }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "Delete all recordings?",
            body = "This permanently deletes all ${recordings.size} recordings on this device. " +
                "This can't be undone.",
            confirmLabel = "Delete all",
            onConfirm = { confirmDeleteAll = false; viewModel.deleteAll() },
            onDismiss = { confirmDeleteAll = false },
        )
    }
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete recording?",
            body = "Permanently delete \"${item.name}\"? This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { pendingDelete = null; viewModel.delete(item.uri) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
