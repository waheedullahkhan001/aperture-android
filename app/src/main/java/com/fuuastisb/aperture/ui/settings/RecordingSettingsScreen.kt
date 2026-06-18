package com.fuuastisb.aperture.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuuastisb.aperture.data.server.StreamTarget
import com.fuuastisb.aperture.domain.model.CameraLens
import com.fuuastisb.aperture.domain.model.VideoQuality

/** Configure capture quality (SRS-020..022): camera, resolution, frame rate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.recordingConfig.collectAsStateWithLifecycle()
    val stream by viewModel.streamSettings.collectAsStateWithLifecycle()
    val server by viewModel.serverConfig.collectAsStateWithLifecycle()

    SettingsScaffold("Recording", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ToggleRow(
                title = "Save to this device",
                subtitle = "Keep a local copy of every recording.",
                checked = config.saveLocally,
                onCheckedChange = { viewModel.setRecordingConfig(config.copy(saveLocally = it)) },
            )
            if (!config.saveLocally) {
                Text(
                    if (StreamTarget.canStream(stream, server)) {
                        "Footage will only live on the server while streaming — nothing is kept here, so " +
                            "if the stream drops, that footage is lost."
                    } else {
                        "⚠ Local saving and streaming are both off, so a trigger would record nothing. " +
                            "Turn on streaming, or keep this on."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ToggleRow(
                title = "Record audio",
                subtitle = "Capture the microphone with local recordings.",
                checked = config.recordAudio,
                onCheckedChange = { viewModel.setRecordingConfig(config.copy(recordAudio = it)) },
            )
            Spacer(Modifier.height(20.dp))

            Text("Camera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CameraLens.entries.forEach { lens ->
                    FilterChip(
                        selected = config.lens == lens,
                        onClick = { viewModel.setRecordingConfig(config.copy(lens = lens)) },
                        label = { Text(lens.displayName()) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = config.quality == quality,
                        onClick = { viewModel.setRecordingConfig(config.copy(quality = quality)) },
                        label = { Text(quality.displayName()) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Stepper(
                label = "Frame rate",
                value = "${config.fps} fps",
                onDecrement = { viewModel.setRecordingConfig(config.copy(fps = config.fps - FPS_STEP)) },
                onIncrement = { viewModel.setRecordingConfig(config.copy(fps = config.fps + FPS_STEP)) },
                canDecrement = config.fps > MIN_FPS,
                canIncrement = config.fps < MAX_FPS,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "These apply to recordings saved on this device. Quality falls back automatically if the " +
                    "camera can't reach the chosen resolution; frame rate is a target the device rounds " +
                    "to a supported value. The live stream has its own quality and frame rate, set in " +
                    "Streaming settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun CameraLens.displayName() = when (this) {
    CameraLens.BACK -> "Back"
    CameraLens.FRONT -> "Front"
}

private fun VideoQuality.displayName() = when (this) {
    VideoQuality.FHD -> "1080p"
    VideoQuality.HD -> "720p"
    VideoQuality.SD -> "480p"
}

private const val MIN_FPS = 24
private const val MAX_FPS = 60
private const val FPS_STEP = 6
