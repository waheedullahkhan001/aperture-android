package com.fuuastisb.aperture.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fuuastisb.aperture.domain.model.CameraLens
import com.fuuastisb.aperture.domain.model.MetadataConfig
import com.fuuastisb.aperture.domain.model.NotificationStyle
import com.fuuastisb.aperture.domain.model.RecordingConfig
import com.fuuastisb.aperture.domain.model.ServerConfig
import com.fuuastisb.aperture.domain.model.StoragePolicy
import com.fuuastisb.aperture.domain.model.StreamSettings
import com.fuuastisb.aperture.domain.model.VideoQuality
import com.fuuastisb.aperture.domain.trigger.TriggerButton
import com.fuuastisb.aperture.domain.trigger.TriggerPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the native client's settings in Preferences DataStore. Single source of truth shared by
 * the UI (which observes the flows) and the services (which take snapshots at the instant a
 * recording starts / a key is read, so behaviour can't change mid-capture).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    // Reads fall back to defaults if the on-disk preferences are unreadable/corrupt, rather than
    // throwing into every collector and the services' settings snapshots (the recommended
    // DataStore resilience pattern).
    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    // region Streaming
    val streamSettings: Flow<StreamSettings> = data.map { prefs ->
        StreamSettings(
            enabled = prefs[KEY_STREAM_ENABLED] ?: false,
            url = prefs[KEY_STREAM_URL] ?: "",
            quality = prefs[KEY_STREAM_QUALITY]
                ?.let { name -> runCatching { VideoQuality.valueOf(name) }.getOrNull() }
                ?: StreamSettings().quality,
            fps = prefs[KEY_STREAM_FPS] ?: StreamSettings().fps,
            streamAudio = prefs[KEY_STREAM_AUDIO] ?: StreamSettings().streamAudio,
        )
    }

    suspend fun streamSettingsSnapshot(): StreamSettings = streamSettings.first()

    suspend fun setStreamSettings(settings: StreamSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_STREAM_ENABLED] = settings.enabled
            prefs[KEY_STREAM_URL] = settings.url.trim()
            prefs[KEY_STREAM_QUALITY] = settings.quality.name
            prefs[KEY_STREAM_FPS] = settings.fps
            prefs[KEY_STREAM_AUDIO] = settings.streamAudio
        }
    }
    // endregion

    // region Trigger / activation pattern (SRS-030..032)
    val triggerPattern: Flow<TriggerPattern> = data.map { prefs ->
        TriggerPattern(
            button = prefs[KEY_TRIGGER_BUTTON]
                ?.let { name -> runCatching { TriggerButton.valueOf(name) }.getOrNull() }
                ?: TriggerPattern.DEFAULT.button,
            pressCount = prefs[KEY_TRIGGER_PRESSES] ?: TriggerPattern.DEFAULT.pressCount,
            windowMs = prefs[KEY_TRIGGER_WINDOW] ?: TriggerPattern.DEFAULT.windowMs,
            debounceMs = prefs[KEY_TRIGGER_DEBOUNCE] ?: TriggerPattern.DEFAULT.debounceMs,
        )
    }

    suspend fun triggerPatternSnapshot(): TriggerPattern = triggerPattern.first()

    suspend fun setTriggerPattern(pattern: TriggerPattern) {
        dataStore.edit { prefs ->
            prefs[KEY_TRIGGER_BUTTON] = pattern.button.name
            prefs[KEY_TRIGGER_PRESSES] = pattern.pressCount
            prefs[KEY_TRIGGER_WINDOW] = pattern.windowMs
            prefs[KEY_TRIGGER_DEBOUNCE] = pattern.debounceMs
        }
    }
    // endregion

    // region Recording capture config (SRS-020..022)
    val recordingConfig: Flow<RecordingConfig> = data.map { prefs ->
        RecordingConfig(
            lens = prefs[KEY_CAMERA_LENS]
                ?.let { name -> runCatching { CameraLens.valueOf(name) }.getOrNull() }
                ?: RecordingConfig().lens,
            quality = prefs[KEY_QUALITY]
                ?.let { name -> runCatching { VideoQuality.valueOf(name) }.getOrNull() }
                ?: RecordingConfig().quality,
            fps = prefs[KEY_FPS] ?: RecordingConfig().fps,
            saveLocally = prefs[KEY_SAVE_LOCALLY] ?: RecordingConfig().saveLocally,
            recordAudio = prefs[KEY_RECORD_AUDIO] ?: RecordingConfig().recordAudio,
        )
    }

    suspend fun recordingConfigSnapshot(): RecordingConfig = recordingConfig.first()

    suspend fun setRecordingConfig(config: RecordingConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_CAMERA_LENS] = config.lens.name
            prefs[KEY_QUALITY] = config.quality.name
            prefs[KEY_FPS] = config.fps
            prefs[KEY_SAVE_LOCALLY] = config.saveLocally
            prefs[KEY_RECORD_AUDIO] = config.recordAudio
        }
    }
    // endregion

    // region Storage policy (SRS-038..041)
    val storagePolicy: Flow<StoragePolicy> = data.map { prefs ->
        StoragePolicy(
            maxBytes = prefs[KEY_STORAGE_MAX_BYTES] ?: StoragePolicy().maxBytes,
            autoDelete = prefs[KEY_STORAGE_AUTODELETE] ?: StoragePolicy().autoDelete,
        )
    }

    suspend fun storagePolicySnapshot(): StoragePolicy = storagePolicy.first()

    suspend fun setStoragePolicy(policy: StoragePolicy) {
        dataStore.edit { prefs ->
            prefs[KEY_STORAGE_MAX_BYTES] = policy.maxBytes
            prefs[KEY_STORAGE_AUTODELETE] = policy.autoDelete
        }
    }
    // endregion

    // region Notification style
    val notificationStyle: Flow<NotificationStyle> = data.map { prefs ->
        prefs[KEY_NOTIFICATION_STYLE]
            ?.let { name -> runCatching { NotificationStyle.valueOf(name) }.getOrNull() }
            ?: NotificationStyle.DISCREET
    }

    suspend fun notificationStyleSnapshot(): NotificationStyle = notificationStyle.first()

    suspend fun setNotificationStyle(style: NotificationStyle) {
        dataStore.edit { prefs -> prefs[KEY_NOTIFICATION_STYLE] = style.name }
    }
    // endregion

    // region Metadata collection (SRS-021, SRS-026)
    val metadataConfig: Flow<MetadataConfig> = data.map { prefs ->
        MetadataConfig(
            location = prefs[KEY_META_LOCATION] ?: MetadataConfig().location,
            timestamp = prefs[KEY_META_TIMESTAMP] ?: MetadataConfig().timestamp,
            deviceInfo = prefs[KEY_META_DEVICE] ?: MetadataConfig().deviceInfo,
        )
    }

    suspend fun metadataConfigSnapshot(): MetadataConfig = metadataConfig.first()

    suspend fun setMetadataConfig(config: MetadataConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_META_LOCATION] = config.location
            prefs[KEY_META_TIMESTAMP] = config.timestamp
            prefs[KEY_META_DEVICE] = config.deviceInfo
        }
    }
    // endregion

    // region Server connection (UC-09)
    val serverConfig: Flow<ServerConfig> = data.map { prefs ->
        ServerConfig(
            baseUrl = prefs[KEY_SERVER_URL] ?: "",
            token = prefs[KEY_SERVER_TOKEN] ?: "",
        )
    }

    suspend fun serverConfigSnapshot(): ServerConfig = serverConfig.first()

    suspend fun setServerConfig(config: ServerConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = config.baseUrl.trim()
            prefs[KEY_SERVER_TOKEN] = config.token.trim()
        }
    }
    // endregion

    private companion object {
        val KEY_STREAM_ENABLED = booleanPreferencesKey("stream_enabled")
        val KEY_STREAM_URL = stringPreferencesKey("stream_url")
        val KEY_STREAM_QUALITY = stringPreferencesKey("stream_quality")
        val KEY_STREAM_FPS = intPreferencesKey("stream_fps")
        val KEY_STREAM_AUDIO = booleanPreferencesKey("stream_audio")
        val KEY_TRIGGER_BUTTON = stringPreferencesKey("trigger_button")
        val KEY_TRIGGER_PRESSES = intPreferencesKey("trigger_presses")
        val KEY_TRIGGER_WINDOW = longPreferencesKey("trigger_window_ms")
        val KEY_TRIGGER_DEBOUNCE = longPreferencesKey("trigger_debounce_ms")
        val KEY_CAMERA_LENS = stringPreferencesKey("camera_lens")
        val KEY_QUALITY = stringPreferencesKey("video_quality")
        val KEY_FPS = intPreferencesKey("video_fps")
        val KEY_SAVE_LOCALLY = booleanPreferencesKey("save_locally")
        val KEY_RECORD_AUDIO = booleanPreferencesKey("record_audio")
        val KEY_STORAGE_MAX_BYTES = longPreferencesKey("storage_max_bytes")
        val KEY_STORAGE_AUTODELETE = booleanPreferencesKey("storage_auto_delete")
        val KEY_NOTIFICATION_STYLE = stringPreferencesKey("notification_style")
        val KEY_META_LOCATION = booleanPreferencesKey("meta_location")
        val KEY_META_TIMESTAMP = booleanPreferencesKey("meta_timestamp")
        val KEY_META_DEVICE = booleanPreferencesKey("meta_device_info")
        val KEY_SERVER_URL = stringPreferencesKey("server_base_url")
        val KEY_SERVER_TOKEN = stringPreferencesKey("server_token")
    }
}
