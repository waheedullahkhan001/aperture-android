package com.fuuastisb.aperture.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuuastisb.aperture.ui.recordings.RecordingsViewModel
import com.fuuastisb.aperture.ui.recordings.formatBytes
import com.fuuastisb.aperture.ui.recordings.formatGb

/** Storage preferences (SRS-038..041): save location, size cap and auto-delete-oldest toggle. */
@OptIn(ExperimentalMaterial3Api::class)
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

            Spacer(Modifier.height(24.dp))
            Text("Save location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Which gallery folder recordings are saved to. Only affects new recordings; existing ones " +
                    "stay where they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val parent = policy.relativePath.substringBefore('/', "Movies")
            var folder by remember(policy.relativePath) {
                mutableStateOf(policy.relativePath.substringAfter('/', "Aperture"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STORAGE_PARENTS.forEach { p ->
                    FilterChip(
                        selected = parent == p,
                        onClick = { viewModel.setStoragePolicy(policy.copy(relativePath = "$p/${sanitizeFolder(folder)}")) },
                        label = { Text(p) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = folder,
                onValueChange = { folder = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Folder name") },
            )
            val composed = "$parent/${sanitizeFolder(folder)}"
            if (composed != policy.relativePath) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.setStoragePolicy(policy.copy(relativePath = composed)) }) {
                    Text("Save location")
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) {
                Text("Manage recordings")
            }
        }
    }
}

/** Video is restricted by scoped storage to these top-level MediaStore collections. */
private val STORAGE_PARENTS = listOf("Movies", "DCIM")

/** Keep the folder name to safe path-segment characters; fall back to "Aperture" if empty. */
private fun sanitizeFolder(input: String): String =
    input.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Aperture" }

private const val GB = 1024L * 1024L * 1024L
private const val MAX_GB = 64L
