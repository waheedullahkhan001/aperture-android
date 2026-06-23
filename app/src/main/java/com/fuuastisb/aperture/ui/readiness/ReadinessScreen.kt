package com.fuuastisb.aperture.ui.readiness

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.fuuastisb.aperture.data.server.ServerHealth
import com.fuuastisb.aperture.ui.settings.SettingsScaffold

/** "Are you protected?" dashboard — surfaces the things that quietly break reliability. */
@Composable
fun ReadinessScreen(
    onBack: () -> Unit,
    viewModel: ReadinessViewModel = hiltViewModel(),
) {
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()

    // Re-check on every resume — the "Fix" buttons leave to system settings, and the dashboard must
    // reflect a just-granted permission when the user returns (a one-shot LaunchedEffect wouldn't).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScaffold("System status", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            StatusRow("Camera & microphone", readiness.cameraAndMic, "Grant", viewModel::openAppSettings)
            StatusRow("Notifications", readiness.notifications, "Grant", viewModel::openAppSettings)
            StatusRow("Volume trigger (accessibility)", readiness.accessibility, "Enable", viewModel::openAccessibilitySettings)
            StatusRow("Battery unrestricted", readiness.batteryUnrestricted, "Fix", viewModel::openBatterySettings)
            ServerRow("Backend (API)", serverStatus.backend)
            ServerRow("Media server", serverStatus.media)
        }
    }
}

/** A server reachability row: green when reachable, error when unreachable, neutral when not set. */
@Composable
private fun ServerRow(label: String, health: ServerHealth) {
    val ok = health == ServerHealth.Reachable
    val problem = health == ServerHealth.Unreachable
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                ok -> Lucide.CircleCheck
                problem -> Lucide.TriangleAlert
                else -> Lucide.Info
            },
            contentDescription = null,
            tint = when {
                ok -> MaterialTheme.colorScheme.primary
                problem -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            when (health) {
                ServerHealth.Reachable -> "Reachable"
                ServerHealth.Unreachable -> "Unreachable"
                ServerHealth.Disabled -> "Not set"
                ServerHealth.Unknown -> "—"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    fixLabel: String?,
    onFix: (() -> Unit)?,
    optional: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                ok -> Lucide.CircleCheck
                optional -> Lucide.Info
                else -> Lucide.TriangleAlert
            },
            contentDescription = null,
            tint = when {
                ok -> MaterialTheme.colorScheme.primary
                optional -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        when {
            !ok && fixLabel != null && onFix != null -> TextButton(onClick = onFix) { Text(fixLabel) }
            !ok && optional -> Text(
                "Optional",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}
