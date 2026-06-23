package com.fuuastisb.aperture.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The dedicated "meta info" page — optional metadata toggles kept out of the mandatory onboarding.
 * Location is requested here (and only here), since it's never required for basic recording.
 */
@Composable
fun MetaInfoScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.metadataConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationGranted = granted
        viewModel.setMetadataConfig(config.copy(location = granted))
    }

    SettingsScaffold("Metadata", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Optionally attach context to your recordings and stream pages. The timestamp is always " +
                    "recorded; everything else is optional and never required to record.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            ToggleRow(
                title = "Device info",
                subtitle = "Record the device model.",
                checked = config.deviceInfo,
                onCheckedChange = { viewModel.setMetadataConfig(config.copy(deviceInfo = it)) },
            )
            ToggleRow(
                title = "Location",
                subtitle = if (locationGranted) {
                    "Stream live GPS location during a recording."
                } else {
                    "Needs location permission (optional)."
                },
                checked = config.location && locationGranted,
                onCheckedChange = { wantOn ->
                    if (wantOn && !locationGranted) {
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        viewModel.setMetadataConfig(config.copy(location = wantOn))
                    }
                },
            )
            ToggleRow(
                title = "Motion & accuracy",
                subtitle = "Include speed, heading, altitude and accuracy with the location.",
                checked = config.motion && config.location && locationGranted,
                onCheckedChange = { viewModel.setMetadataConfig(config.copy(motion = it)) },
                // Only meaningful with location on — disable so it can't silently persist while hidden.
                enabled = config.location && locationGranted,
            )
            ToggleRow(
                title = "Battery level",
                subtitle = "Share the phone's battery percentage (\"about to die?\").",
                checked = config.battery,
                onCheckedChange = { viewModel.setMetadataConfig(config.copy(battery = it)) },
            )
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
