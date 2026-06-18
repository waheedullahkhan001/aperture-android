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
import com.fuuastisb.aperture.domain.trigger.TriggerButton

/** Configure the activation pattern (SRS-030..032): which button, how many presses, how fast. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val pattern by viewModel.triggerPattern.collectAsStateWithLifecycle()

    SettingsScaffold("Activation", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Trigger button", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerButton.entries.forEach { button ->
                    FilterChip(
                        selected = pattern.button == button,
                        onClick = { viewModel.setTriggerPattern(pattern.copy(button = button)) },
                        label = { Text(button.displayName()) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Stepper(
                label = "Number of presses",
                value = pattern.pressCount.toString(),
                onDecrement = { viewModel.setTriggerPattern(pattern.copy(pressCount = pattern.pressCount - 1)) },
                onIncrement = { viewModel.setTriggerPattern(pattern.copy(pressCount = pattern.pressCount + 1)) },
                canDecrement = pattern.pressCount > MIN_PRESSES,
                canIncrement = pattern.pressCount < MAX_PRESSES,
            )
            Stepper(
                label = "Max gap between presses",
                value = "${pattern.windowMs} ms",
                onDecrement = { viewModel.setTriggerPattern(pattern.copy(windowMs = pattern.windowMs - WINDOW_STEP)) },
                onIncrement = { viewModel.setTriggerPattern(pattern.copy(windowMs = pattern.windowMs + WINDOW_STEP)) },
                canDecrement = pattern.windowMs > MIN_WINDOW,
                canIncrement = pattern.windowMs < MAX_WINDOW,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                "Press ${button(pattern.button)} ${pattern.pressCount} times, with no more than " +
                    "${pattern.windowMs} ms between presses, to start recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun TriggerButton.displayName() = when (this) {
    TriggerButton.VOLUME_UP -> "Volume Up"
    TriggerButton.VOLUME_DOWN -> "Volume Down"
    TriggerButton.EITHER -> "Either"
}

private fun button(b: TriggerButton) = when (b) {
    TriggerButton.VOLUME_UP -> "volume up"
    TriggerButton.VOLUME_DOWN -> "volume down"
    TriggerButton.EITHER -> "either volume button"
}

private const val MIN_PRESSES = 2
private const val MAX_PRESSES = 6
private const val WINDOW_STEP = 100L
private const val MIN_WINDOW = 400L
private const val MAX_WINDOW = 2_500L
