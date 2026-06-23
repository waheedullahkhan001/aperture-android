package com.fuuastisb.aperture.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.QrCode
import com.fuuastisb.aperture.data.server.ConnectString
import com.fuuastisb.aperture.data.server.ServerHealth
import com.fuuastisb.aperture.domain.model.VideoQuality
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.fuuastisb.aperture.domain.model.minQuality
import com.fuuastisb.aperture.domain.model.rank

/**
 * Streaming + both backing servers on one page (UC-09/10): the Spring backend (URL + token) and the
 * MediaMTX stream target (stream URL), each health-checked separately. The "Stream to a server" switch
 * stays off until the media server is actually reachable, so the user learns early.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val stream by viewModel.streamSettings.collectAsStateWithLifecycle()
    val server by viewModel.serverConfig.collectAsStateWithLifecycle()
    val recording by viewModel.recordingConfig.collectAsStateWithLifecycle()
    val status by viewModel.serverStatus.collectAsStateWithLifecycle()
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()

    // Prefill the box with the current connection (re-encoded) so a returning user sees what's paired.
    var connectCode by remember(server.baseUrl, server.token, stream.url) {
        mutableStateOf(
            if (server.isConfigured) {
                ConnectString.encode(server.baseUrl, server.token, stream.url.ifBlank { null })
            } else {
                ""
            },
        )
    }
    var streamUrl by remember(stream.url) { mutableStateOf(stream.url) }

    // GMS-free QR scan via ZXing's embedded scanner; a successful scan fills the connect-code box.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { connectCode = it }
    }

    val mediaReachable = status.media == ServerHealth.Reachable
    val streamUrlValid = streamUrl.isBlank() || looksLikeStreamUrl(streamUrl.trim())

    SettingsScaffold("Streaming", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // --- Pair this device: one connect code (paste or scan) carries the server + token ---
            Text("Pair this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "On the web, create a connect code for this device, then paste it below — or scan its QR. " +
                    "It carries your server and access token; the app never handles your password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = connectCode,
                onValueChange = { connectCode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste connect code") },
                trailingIcon = {
                    IconButton(onClick = {
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan the pairing QR")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    }) {
                        Icon(Lucide.QrCode, contentDescription = "Scan QR code")
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.connect(connectCode) },
                enabled = connectCode.isNotBlank() && connectState !is ConnectState.Connecting,
            ) { Text("Connect") }

            Spacer(Modifier.height(12.dp))
            when (val s = connectState) {
                ConnectState.Idle -> {
                    // Show the standing health once paired, so a returning user sees state.
                    if (server.isConfigured) {
                        HealthLine("Backend (API)", status.backend, status.backendDetail)
                        Spacer(Modifier.height(4.dp))
                        HealthLine("Media server", status.media)
                    }
                }
                ConnectState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting…")
                }
                is ConnectState.Connected -> Text(
                    "✓ Connected as ${s.name}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ConnectState.Error -> Text(
                    "✗ ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(20.dp))
            // --- Media server (MediaMTX): the stream target ---
            Text("Media server (stream target)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Leave blank to publish to your configured server automatically. Or set a specific RTSP " +
                    "URL (advanced / LAN testing) — RTSP only, full path/stream name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = streamUrl,
                onValueChange = { streamUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Stream URL") },
                placeholder = { Text("rtsp://192.168.1.10:8554/aperture") },
                isError = !streamUrlValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            if (streamUrl.trim() != stream.url && streamUrlValid) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.setStreamSettings(stream.copy(url = streamUrl.trim())) }) {
                    Text("Save stream URL")
                }
            }

            SectionDivider()

            // --- Enable toggle, gated on a reachable media server ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Stream to a server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Push a live feed while recording. Local recording continues even if it drops.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = stream.enabled,
                    // Can only turn ON once the media server is reachable; can always turn OFF.
                    enabled = stream.enabled || mediaReachable,
                    onCheckedChange = { viewModel.setStreamSettings(stream.copy(enabled = it)) },
                )
            }
            if (!stream.enabled && !mediaReachable) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Turns on once the media server is reachable — run Save & test above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stream.enabled && status.media == ServerHealth.Unreachable) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ Streaming is on but the media server is unreachable right now — it will keep retrying.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SectionDivider()

            // --- Stream quality / frame rate (never above local) ---
            Text("Stream quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Used when streaming without a local copy — and never above your local recording " +
                    "(${recording.quality.displayName()} / ${recording.fps} fps).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = minQuality(stream.quality, recording.quality) == quality,
                        enabled = quality.rank <= recording.quality.rank,
                        onClick = {
                            viewModel.setStreamSettings(stream.copy(quality = minQuality(quality, recording.quality)))
                        },
                        label = { Text(quality.displayName()) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val effStreamFps = minOf(stream.fps, recording.fps)
            Stepper(
                label = "Frame rate",
                value = "$effStreamFps fps",
                onDecrement = { viewModel.setStreamSettings(stream.copy(fps = effStreamFps - STREAM_FPS_STEP)) },
                onIncrement = { viewModel.setStreamSettings(stream.copy(fps = effStreamFps + STREAM_FPS_STEP)) },
                canDecrement = effStreamFps > STREAM_MIN_FPS,
                canIncrement = effStreamFps < minOf(STREAM_MAX_FPS, recording.fps),
            )

            Spacer(Modifier.height(12.dp))
            BitrateDropdown(
                selectedKbps = stream.bitrateKbps,
                onSelect = { viewModel.setStreamSettings(stream.copy(bitrateKbps = it)) },
            )

            Spacer(Modifier.height(4.dp))
            ToggleRow(
                title = "Stream audio",
                subtitle = "Include the microphone in the stream (and its saved copy).",
                checked = stream.streamAudio,
                onCheckedChange = { viewModel.setStreamSettings(stream.copy(streamAudio = it)) },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "One encoder feeds both, so while you keep a local copy the stream matches your local " +
                    "quality (the saved file is never degraded). The lower stream quality above is used " +
                    "only when streaming without a local copy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthLine(label: String, health: ServerHealth, detail: String? = null) {
    val (text, color) = when (health) {
        ServerHealth.Reachable -> "✓ reachable" to MaterialTheme.colorScheme.primary
        ServerHealth.Unreachable -> (detail ?: "✗ unreachable") to MaterialTheme.colorScheme.error
        ServerHealth.Disabled -> "not set" to MaterialTheme.colorScheme.onSurfaceVariant
        ServerHealth.Unknown -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Stream video bitrate as a dropdown; "Auto" derives the bitrate from the selected quality. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BitrateDropdown(selectedKbps: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = bitrateLabel(selectedKbps),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Bitrate") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BITRATE_OPTIONS.forEach { kbps ->
                DropdownMenuItem(
                    text = { Text(bitrateLabel(kbps)) },
                    onClick = { onSelect(kbps); expanded = false },
                )
            }
        }
    }
}

private val BITRATE_OPTIONS = listOf(0, 1000, 2500, 4000, 8000)

private fun bitrateLabel(kbps: Int): String = when {
    kbps <= 0 -> "Auto (from quality)"
    kbps % 1000 == 0 -> "${kbps / 1000} Mbps"
    else -> "${kbps / 1000.0} Mbps"
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(20.dp))
}

private fun VideoQuality.displayName() = when (this) {
    VideoQuality.FHD -> "1080p"
    VideoQuality.HD -> "720p"
    VideoQuality.SD -> "480p"
}

private fun looksLikeStreamUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.startsWith("rtsp://") || lower.startsWith("rtsps://")
}

private const val STREAM_MIN_FPS = 15
private const val STREAM_MAX_FPS = 60
private const val STREAM_FPS_STEP = 5
