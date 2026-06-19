package com.fuuastisb.aperture.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

/**
 * Covers the base64url decode (the fiddly padding-tolerance part). The full [ConnectString.parse] uses
 * org.json, which isn't available in plain JVM unit tests, so it's exercised on-device instead.
 */
class ConnectStringTest {

    private val json = """{"v":1,"api":"https://aperture.example.com","token":"apd_abc123"}"""

    @Test
    fun `decodes base64url without padding`() {
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        assertEquals(json, ConnectString.decodeToJson(code))
    }

    @Test
    fun `decodes base64url with padding`() {
        val code = Base64.getUrlEncoder().encodeToString(json.toByteArray()) // includes '=' padding
        assertEquals(json, ConnectString.decodeToJson(code))
    }

    @Test
    fun `tolerates surrounding whitespace and newlines from a scan`() {
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        assertEquals(json, ConnectString.decodeToJson("  $code\n"))
    }

    @Test
    fun `rejects non-base64 garbage`() {
        assertThrows(IllegalArgumentException::class.java) { ConnectString.decodeToJson("!!!not base64!!!") }
    }
}
