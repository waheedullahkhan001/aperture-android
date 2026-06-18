package com.fuuastisb.aperture.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** Backs the configuration screens — exposes the persisted settings, the live server health, setters. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serverHealthMonitor: ServerHealthMonitor,
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

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

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
     * Save the backend (URL + token) and media (stream URL) configuration, then check both servers —
     * so the user learns whether each is reachable before turning streaming on.
     */
    fun saveAndTest(baseUrl: String, token: String, stream: StreamSettings) {
        viewModelScope.launch {
            _testing.value = true
            settingsRepository.setServerConfig(ServerConfig(baseUrl, token))
            settingsRepository.setStreamSettings(stream)
            serverHealthMonitor.check()
            _testing.value = false
        }
    }
}
