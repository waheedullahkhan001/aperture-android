package com.fuuastisb.aperture.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Radio
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Volume2
import com.fuuastisb.aperture.ui.navigation.Routes

/** The settings hub — a list of categories. Rows are added as each screen lands. */
@Composable
fun SettingsHubScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    SettingsScaffold("Settings", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsRow(
                icon = Lucide.Volume2,
                title = "Activation",
                subtitle = "Which volume buttons start recording, and how",
                onClick = { onOpen(Routes.ACTIVATION) },
            )
            SettingsRow(
                icon = Lucide.Video,
                title = "Recording",
                subtitle = "Camera, resolution and frame rate",
                onClick = { onOpen(Routes.RECORDING) },
            )
            SettingsRow(
                icon = Lucide.Radio,
                title = "Streaming",
                subtitle = "Server connection, live-stream quality and target",
                onClick = { onOpen(Routes.STREAMING) },
            )
            SettingsRow(
                icon = Lucide.Bell,
                title = "Notification",
                subtitle = "How the recording notification reads",
                onClick = { onOpen(Routes.NOTIFICATION) },
            )
            SettingsRow(
                icon = Lucide.MapPin,
                title = "Metadata",
                subtitle = "Optional timestamp, device info and location",
                onClick = { onOpen(Routes.METADATA) },
            )
            SettingsRow(
                icon = Lucide.HardDrive,
                title = "Storage",
                subtitle = "Size limit and auto-delete of old recordings",
                onClick = { onOpen(Routes.STORAGE) },
            )
        }
    }
}
