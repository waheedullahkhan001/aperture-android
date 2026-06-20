package com.fuuastisb.aperture.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.Accessibility
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.FileVideo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Video
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuuastisb.aperture.data.server.AlertCancelState
import com.fuuastisb.aperture.data.server.ServerHealth
import com.fuuastisb.aperture.domain.model.EmergencyState
import com.fuuastisb.aperture.domain.model.RecordingState
import com.fuuastisb.aperture.domain.model.StreamingState
import com.fuuastisb.aperture.domain.trigger.TriggerButton
import com.fuuastisb.aperture.domain.trigger.TriggerPattern

/**
 * Home: walks the user through the gates (runtime permissions, then the accessibility service) and,
 * once both are satisfied, shows the ready / recording controls. The gear opens the settings hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenRecordings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Pre-scoped-storage (API 28): writing to MediaStore needs this at runtime.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions.all { p ->
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> permissionsGranted = result.values.all { it } }

    // Re-check on resume so granting permissions in system Settings updates the gate immediately.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = requiredPermissions.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val triggerPattern by viewModel.triggerPattern.collectAsStateWithLifecycle()
    val streamSettings by viewModel.streamSettings.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val serverConfigured by viewModel.serverConfigured.collectAsStateWithLifecycle()
    val alertCancelState by viewModel.alertCancelState.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aperture") },
                actions = {
                    // The status icon tells the truth: a warning until the core gates (permissions +
                    // the accessibility trigger) are satisfied — and also when streaming is enabled but
                    // its media server is unreachable, so a broken stream can't hide behind a green tick.
                    val streamingBroken = streamSettings.enabled &&
                        (serverStatus.media == ServerHealth.Unreachable || serverStatus.backend == ServerHealth.Unreachable)
                    val ready = permissionsGranted && accessibilityEnabled && !streamingBroken
                    IconButton(onClick = onOpenReadiness) {
                        Icon(
                            imageVector = if (ready) Lucide.CircleCheck else Lucide.TriangleAlert,
                            contentDescription = if (ready) "Ready" else "Setup incomplete",
                            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Lucide.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !permissionsGranted -> PermissionsStep(
                    onGrant = { permissionLauncher.launch(requiredPermissions) },
                )
                !accessibilityEnabled -> AccessibilityStep(
                    onOpenSettings = viewModel::openAccessibilitySettings,
                )
                else -> ReadyStep(
                    recordingState = recordingState,
                    triggerHint = triggerHint(triggerPattern),
                    modeLine = modeLine(streamSettings.enabled && (serverConfigured || streamSettings.isReadyToStream)),
                    serverConfigured = serverConfigured,
                    alertCancelState = alertCancelState,
                    onStart = viewModel::startRecording,
                    onStop = viewModel::stopRecording,
                    onCancelAlerts = viewModel::cancelAlerts,
                    onOpenRecordings = onOpenRecordings,
                )
            }
            lastError?.let { message ->
                ErrorBanner(
                    message = message,
                    onDismiss = viewModel::dismissError,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun PermissionsStep(onGrant: () -> Unit) = CenteredColumn {
    Icon(
        Lucide.Camera,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Text("Permissions needed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(
        "Aperture needs camera and microphone access to record, and notification access to keep " +
            "recording in the background.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Grant permissions") }
}

@Composable
private fun AccessibilityStep(onOpenSettings: () -> Unit) = CenteredColumn {
    Icon(
        Lucide.Accessibility,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Text("Enable the volume trigger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(
        "Turn on Aperture's accessibility service so a volume-button shortcut can start recording " +
            "without unlocking.\n\nSettings ▸ Accessibility ▸ Aperture.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text("Open accessibility settings")
    }
}

@Composable
private fun ReadyStep(
    recordingState: RecordingState,
    triggerHint: String,
    modeLine: String,
    serverConfigured: Boolean,
    alertCancelState: AlertCancelState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancelAlerts: () -> Unit,
    onOpenRecordings: () -> Unit,
) = CenteredColumn {
    val active = recordingState as? RecordingState.Active
    val isRecording = active != null
    val emergency = active?.emergency ?: EmergencyState.Off
    val counting = emergency as? EmergencyState.CountingDown

    // The cancel outcome is authoritative over the local countdown timer: if the server confirmed the
    // cancel, alerts did NOT go out even though the local timer may have elapsed. Precedence below
    // keeps the emergency status free of contradictions (no "Contacts alerted" + "Alerts cancelled").
    val cancelDone = alertCancelState as? AlertCancelState.Done
    val alertsCancelled = cancelDone != null && !cancelDone.alertsAlreadyDispatched
    val cancelAttempt = (alertCancelState as? AlertCancelState.InProgress)?.attempt
    val alertsDispatched = !alertsCancelled &&
        (emergency == EmergencyState.AlertsDispatched || cancelDone?.alertsAlreadyDispatched == true)

    Text(
        text = if (isRecording) "Recording…" else "Ready",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (isRecording) "Recording is active." else triggerHint,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!isRecording) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = modeLine,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    // Single emergency-status headline, precedence-ordered so it can't contradict itself.
    when {
        alertsCancelled -> {
            Spacer(Modifier.height(12.dp))
            Text("✓ Alerts cancelled", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        cancelAttempt != null -> {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Cancelling the alert… (attempt $cancelAttempt)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        counting != null -> {
            Spacer(Modifier.height(12.dp))
            Text(
                "Alerting your contacts in ${counting.remainingMs / 1000}s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
        alertsDispatched -> {
            Spacer(Modifier.height(12.dp))
            Text("Contacts alerted", color = MaterialTheme.colorScheme.error)
        }
    }

    if (active != null && active.streaming != StreamingState.Off) {
        Spacer(Modifier.height(8.dp))
        StreamingBadge(active.streaming)
    }

    Spacer(Modifier.height(28.dp))
    if (isRecording) {
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Lucide.Square, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            // Always "Stop recording" — cancelling the alert is the dedicated button's job now.
            Text("Stop recording")
        }
    } else {
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(Lucide.Video, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Start recording")
        }
    }

    // The cancel action: only while the countdown is live and no cancel is in flight/finished — the
    // in-progress and resolved states are shown by the headline above, so this never contradicts it.
    if (serverConfigured && counting != null && alertCancelState is AlertCancelState.Idle) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancelAlerts, modifier = Modifier.fillMaxWidth()) {
            Icon(Lucide.BellOff, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Cancel alerts")
        }
    }

    // Low-emphasis access to the saved-recordings library — present on the main screen, but not
    // competing with the primary record action.
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onOpenRecordings) {
        Icon(Lucide.FileVideo, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Recordings")
    }
}

@Composable
private fun StreamingBadge(streaming: StreamingState) {
    val (label, color) = when (streaming) {
        StreamingState.Off -> "Stream idle" to MaterialTheme.colorScheme.onSurfaceVariant
        StreamingState.Connecting -> "Connecting to server…" to MaterialTheme.colorScheme.tertiary
        StreamingState.Live -> "● Live — streaming to server" to MaterialTheme.colorScheme.primary
        is StreamingState.Failed -> "Stream failed: ${streaming.reason}" to MaterialTheme.colorScheme.error
    }
    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
}

@Composable
private fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) = Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(24.dp)
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    content = content,
)

private fun triggerHint(pattern: TriggerPattern): String {
    val button = when (pattern.button) {
        TriggerButton.VOLUME_UP -> "volume up"
        TriggerButton.VOLUME_DOWN -> "volume down"
        TriggerButton.EITHER -> "either volume button"
    }
    return "Press $button ${pattern.pressCount}× quickly to start recording — no need to unlock."
}

private fun modeLine(streaming: Boolean): String =
    if (streaming) "Streaming to your server + a local copy" else "Saving locally to this device"

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
