package com.fuuastisb.aperture.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSettingsTest {

    @Test
    fun `ready when enabled with an RTSP URL`() {
        assertTrue(StreamSettings(enabled = true, url = "rtsp://192.168.1.10:8554/aperture").isReadyToStream)
        assertTrue(StreamSettings(enabled = true, url = "rtsps://host:8554/aperture").isReadyToStream)
    }

    @Test
    fun `not ready when streaming is disabled`() {
        assertFalse(StreamSettings(enabled = false, url = "rtsp://host/path").isReadyToStream)
    }

    @Test
    fun `not ready when the URL is blank`() {
        assertFalse(StreamSettings(enabled = true, url = "   ").isReadyToStream)
    }

    @Test
    fun `not ready for a non-RTSP scheme (RTSP-only stack)`() {
        assertFalse(StreamSettings(enabled = true, url = "http://host/path").isReadyToStream)
        assertFalse(StreamSettings(enabled = true, url = "srt://host:8890").isReadyToStream)
        assertFalse(StreamSettings(enabled = true, url = "rtmp://host/app").isReadyToStream)
    }
}
