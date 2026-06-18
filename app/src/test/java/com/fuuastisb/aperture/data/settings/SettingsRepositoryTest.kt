package com.fuuastisb.aperture.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.fuuastisb.aperture.domain.model.CameraLens
import com.fuuastisb.aperture.domain.model.RecordingConfig
import com.fuuastisb.aperture.domain.model.ServerConfig
import com.fuuastisb.aperture.domain.model.StreamSettings
import com.fuuastisb.aperture.domain.model.VideoQuality
import com.fuuastisb.aperture.domain.trigger.TriggerButton
import com.fuuastisb.aperture.domain.trigger.TriggerPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Verifies every setting persists and reloads through DataStore (the architecture's constructor
 * injection makes this a plain JVM test — no device needed). */
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): SettingsRepository {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("settings.preferences_pb")
        }
        return SettingsRepository(dataStore)
    }

    @Test
    fun `defaults apply on an empty store`() = runBlocking {
        val repo = newRepository()
        assertEquals(TriggerPattern.DEFAULT, repo.triggerPatternSnapshot())
        assertFalse(repo.streamSettingsSnapshot().enabled)
        assertFalse(repo.serverConfigSnapshot().isConfigured)
        assertEquals(RecordingConfig(), repo.recordingConfigSnapshot())
    }

    @Test
    fun `trigger pattern round-trips`() = runBlocking {
        val repo = newRepository()
        val pattern = TriggerPattern(TriggerButton.EITHER, pressCount = 5, windowMs = 800, debounceMs = 50)
        repo.setTriggerPattern(pattern)
        assertEquals(pattern, repo.triggerPatternSnapshot())
    }

    @Test
    fun `recording config round-trips`() = runBlocking {
        val repo = newRepository()
        val config = RecordingConfig(lens = CameraLens.FRONT, quality = VideoQuality.HD, fps = 60)
        repo.setRecordingConfig(config)
        assertEquals(config, repo.recordingConfigSnapshot())
    }

    @Test
    fun `server config round-trips and trims`() = runBlocking {
        val repo = newRepository()
        repo.setServerConfig(ServerConfig("  https://x.example.com  ", "  token  "))
        val loaded = repo.serverConfigSnapshot()
        assertEquals("https://x.example.com", loaded.baseUrl)
        assertEquals("token", loaded.token)
    }

    @Test
    fun `stream config round-trips`() = runBlocking {
        val repo = newRepository()
        repo.setStreamSettings(StreamSettings(enabled = true, url = "rtsp://host/path"))
        assertEquals(StreamSettings(true, "rtsp://host/path"), repo.streamSettingsSnapshot())
    }
}
