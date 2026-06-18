package com.fuuastisb.aperture.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuuastisb.aperture.domain.model.NotificationStyle

/** Choose the recording-notification wording (the camera FGS notification is mandatory). */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val style by viewModel.notificationStyle.collectAsStateWithLifecycle()

    SettingsScaffold("Notification", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Android requires a visible notification while recording — it can't be hidden. " +
                    "Choose how it reads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            NotificationStyle.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setNotificationStyle(option) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = style == option, onClick = { viewModel.setNotificationStyle(option) })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(option.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            option.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun NotificationStyle.title() = when (this) {
    NotificationStyle.DISCREET -> "Discreet"
    NotificationStyle.CLEAR -> "Clear"
}

private fun NotificationStyle.description() = when (this) {
    NotificationStyle.DISCREET -> "Neutral wording (\"Aperture · Running\") — less likely to be noticed."
    NotificationStyle.CLEAR -> "Plainly states that recording is active."
}
