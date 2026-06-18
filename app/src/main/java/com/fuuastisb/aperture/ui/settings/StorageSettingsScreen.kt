package com.fuuastisb.aperture.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.fuuastisb.aperture.ui.recordings.RecordingsViewModel
import com.fuuastisb.aperture.ui.recordings.formatBytes
import com.fuuastisb.aperture.ui.recordings.formatGb

/** Storage preferences (SRS-038..041): the size cap and auto-delete-oldest toggle. */
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    onOpenRecordings: () -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val policy by viewModel.storagePolicy.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val used = recordings.sumOf { it.sizeBytes }

    SettingsScaffold("Storage", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Using ${formatBytes(used)} of ${formatGb(policy.maxBytes)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))
            Stepper(
                label = "Storage limit",
                value = formatGb(policy.maxBytes),
                onDecrement = { viewModel.setStoragePolicy(policy.copy(maxBytes = policy.maxBytes - GB)) },
                onIncrement = { viewModel.setStoragePolicy(policy.copy(maxBytes = policy.maxBytes + GB)) },
                canDecrement = policy.maxBytes > GB,
                canIncrement = policy.maxBytes < MAX_GB * GB,
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-delete oldest", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "When space runs low, remove the oldest recordings so a new one can always start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = policy.autoDelete,
                    onCheckedChange = { viewModel.setStoragePolicy(policy.copy(autoDelete = it)) },
                )
            }
            if (!policy.autoDelete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ With this off, recording won't start once storage is full — including in an " +
                        "emergency. Leave it on for reliability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) {
                Text("Manage recordings")
            }
        }
    }
}

private const val GB = 1024L * 1024L * 1024L
private const val MAX_GB = 64L
