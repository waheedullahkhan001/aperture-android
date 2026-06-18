package com.fuuastisb.aperture.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    @Test
    fun `configured requires both a URL and a token`() {
        assertTrue(ServerConfig(baseUrl = "https://aperture.example.com", token = "tok").isConfigured)
        assertFalse(ServerConfig(baseUrl = "", token = "tok").isConfigured)
        assertFalse(ServerConfig(baseUrl = "https://aperture.example.com", token = "").isConfigured)
    }
}
