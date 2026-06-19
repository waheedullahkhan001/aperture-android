package com.fuuastisb.aperture.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuuastisb.aperture.data.server.ConnectString
import com.fuuastisb.aperture.data.server.DeviceApi
import com.fuuastisb.aperture.data.server.MeResult
import com.fuuastisb.aperture.data.server.ServerHealthMonitor
import com.fuuastisb.aperture.data.server.ServerHealthStatus
import com.fuuastisb.aperture.data.settings.SettingsRepository
import com.fuuastisb.aperture.domain.model.MetadataConfig
import com.fuuastisb.aperture.domain.model.NotificationStyle
import com.fuuastisb.aperture.domain.model.RecordingConfig
import com.fuuastisb.aperture.domain.model.ServerConfig
import com.fuuastisb.aperture.domain.model.StreamSettings
import com.fuuastisb.aperture.domain.trigger.TriggerPattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of pairing the device from a connect code. */
sealed interface ConnectState {
    data object Idle : ConnectState
    data object Connecting : ConnectState
    data class Connected(val name: String) : ConnectState
    data class Error(val message: String) : ConnectState
}

/** Backs the configuration screens — exposes the persisted settings, the live server health, setters. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serverHealthMonitor: ServerHealthMonitor,
    private val deviceApi: DeviceApi,
) : ViewModel() {

    val triggerPattern: StateFlow<TriggerPattern> = settingsRepository.triggerPattern
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TriggerPattern.DEFAULT)

    val streamSettings: StateFlow<StreamSettings> = settingsRepository.streamSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamSettings())

    val recordingConfig: StateFlow<RecordingConfig> = settingsRepository.recordingConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingConfig())

    val notificationStyle: StateFlow<NotificationStyle> = settingsRepository.notificationStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationStyle.DISCREET)

    val metadataConfig: StateFlow<MetadataConfig> = settingsRepository.metadataConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetadataConfig())

    val serverConfig: StateFlow<ServerConfig> = settingsRepository.serverConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerConfig())

    /** Live reachability of both servers (Spring API + MediaMTX), refreshed periodically. */
    val serverStatus: StateFlow<ServerHealthStatus> = serverHealthMonitor.status

    private val _connectState = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val connectState: StateFlow<ConnectState> = _connectState.asStateFlow()

    fun setTriggerPattern(pattern: TriggerPattern) {
        viewModelScope.launch { settingsRepository.setTriggerPattern(pattern) }
    }

    fun setStreamSettings(settings: StreamSettings) {
        viewModelScope.launch { settingsRepository.setStreamSettings(settings) }
    }

    fun setRecordingConfig(config: RecordingConfig) {
        viewModelScope.launch { settingsRepository.setRecordingConfig(config) }
    }

    fun setNotificationStyle(style: NotificationStyle) {
        viewModelScope.launch { settingsRepository.setNotificationStyle(style) }
    }

    fun setMetadataConfig(config: MetadataConfig) {
        viewModelScope.launch { settingsRepository.setMetadataConfig(config) }
    }

    /**
     * Pair the device from a connect code (base64url JSON carrying api + token + optional stream
     * override). Backward-tolerant: a bare `apd_…` token re-pairs against the already-configured server.
     * Persists the config, then validates with `GET /device/me` and surfaces "Connected as <name>".
     */
    fun connect(code: String) {
        viewModelScope.launch {
            _connectState.value = ConnectState.Connecting
            val trimmed = code.trim()
            val parsed = ConnectString.parse(trimmed)
            val target: ServerConfig
            val streamOverride: String?
            when {
                parsed.isSuccess -> parsed.getOrThrow().let {
                    target = ServerConfig(it.api, it.token); streamOverride = it.streamOverride
                }
                trimmed.startsWith("apd_") && serverConfig.value.baseUrl.isNotBlank() -> {
                    target = serverConfig.value.copy(token = trimmed); streamOverride = null
                }
                else -> {
                    _connectState.value = ConnectState.Error(
                        parsed.exceptionOrNull()?.message ?: "That doesn't look like a valid connect code.",
                    )
                    return@launch
                }
            }
            settingsRepository.setServerConfig(target)
            if (streamOverride != null) {
                settingsRepository.setStreamSettings(streamSettings.value.copy(url = streamOverride))
            }
            _connectState.value = when (val me = deviceApi.me(target)) {
                is MeResult.Ok -> ConnectState.Connected(me.userFullname.ifBlank { me.deviceName }.ifBlank { "your account" })
                is MeResult.Unauthorized ->
                    ConnectState.Error(if (me.revoked) "This device was disconnected — get a fresh connect code." else "Token rejected — check the connect code.")
                MeResult.Unreachable -> ConnectState.Error("Saved, but the server isn't reachable right now.")
            }
            serverHealthMonitor.check()
        }
    }
}
