package com.fuuastisb.aperture.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuuastisb.aperture.data.server.AlertCancelState
import com.fuuastisb.aperture.data.server.AlertCanceller
import com.fuuastisb.aperture.data.server.ServerHealthMonitor
import com.fuuastisb.aperture.data.server.ServerHealthStatus
import com.fuuastisb.aperture.data.settings.SettingsRepository
import com.fuuastisb.aperture.domain.model.RecordingState
import com.fuuastisb.aperture.domain.model.StreamSettings
import com.fuuastisb.aperture.domain.trigger.TriggerPattern
import com.fuuastisb.aperture.launcher.TransparentLauncherActivity
import com.fuuastisb.aperture.recording.RecordingService
import com.fuuastisb.aperture.recording.RecordingStateHolder
import com.fuuastisb.aperture.trigger.VolumeAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the home screen: exposes the live [RecordingState], the configured trigger and mode (so the
 * UI describes what the next recording will actually do), polls whether the accessibility service is
 * enabled (the user can only toggle it from system settings), and routes manual start/stop.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val stateHolder: RecordingStateHolder,
    private val alertCanceller: AlertCanceller,
    serverHealthMonitor: ServerHealthMonitor,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val recordingState: StateFlow<RecordingState> = stateHolder.state
    val lastError: StateFlow<String?> = stateHolder.lastError

    val triggerPattern: StateFlow<TriggerPattern> = settingsRepository.triggerPattern
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TriggerPattern.DEFAULT)

    val streamSettings: StateFlow<StreamSettings> = settingsRepository.streamSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamSettings())

    /** Whether a backend is configured — gates the "Cancel alerts" affordance while recording. */
    val serverConfigured: StateFlow<Boolean> = settingsRepository.serverConfig
        .map { it.isConfigured }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val alertCancelState: StateFlow<AlertCancelState> = alertCanceller.state

    /** Live server reachability — Home warns when streaming is on but the media server is down. */
    val serverStatus: StateFlow<ServerHealthStatus> = serverHealthMonitor.status

    private val _accessibilityEnabled = MutableStateFlow(isAccessibilityServiceEnabled())
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _accessibilityEnabled.value = isAccessibilityServiceEnabled()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun startRecording() {
        alertCanceller.reset() // a fresh recording clears any prior alert-cancel state
        appContext.startActivity(TransparentLauncherActivity.launchIntent(appContext))
    }

    fun stopRecording() {
        appContext.startService(RecordingService.stopIntent(appContext))
    }

    /** Cancel the emergency alert for the active recording — keeps retrying until the server confirms. */
    fun cancelAlerts() {
        val id = stateHolder.recordingId.value ?: return
        alertCanceller.requestCancel(id)
    }

    fun openAccessibilitySettings() {
        appContext.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun dismissError() = stateHolder.clearError()

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "${appContext.packageName}/${VolumeAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return enabled?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
    }
}
