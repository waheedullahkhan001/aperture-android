package com.fuuastisb.aperture.data.server

import android.net.Uri
import com.fuuastisb.aperture.domain.model.ServerConfig
import com.fuuastisb.aperture.domain.model.StreamSettings

/**
 * Resolves the RTSP stream target. By default the publish URL is **derived from the backend
 * connection** — `rtsp://<host>:8554/aperture/<recordingId>?token=<apd_…>`, per the backend contract —
 * but a manual RTSP URL (advanced / LAN testing) overrides it when set.
 */
object StreamTarget {
    private const val RTSP_PORT = 8554

    /** Streaming is possible when enabled, with either a valid manual RTSP URL or a configured server. */
    fun canStream(stream: StreamSettings, server: ServerConfig): Boolean =
        stream.enabled && (stream.isReadyToStream || (stream.url.isBlank() && server.isConfigured))

    /** Full RTSP publish URL for [recordingId] — the manual override if set, else derived from the server. */
    fun publishUrl(stream: StreamSettings, server: ServerConfig, recordingId: String): String? {
        if (stream.url.isNotBlank()) return stream.url.trim()
        val host = host(server) ?: return null
        return "rtsp://$host:$RTSP_PORT/aperture/$recordingId?token=${server.token.trim()}"
    }

    /** The RTSP host:port to health-check — the manual override's, else the derived server host:8554. */
    fun mediaHostPort(stream: StreamSettings, server: ServerConfig): Pair<String, Int>? {
        if (stream.url.isNotBlank()) {
            val uri = runCatching { Uri.parse(stream.url.trim()) }.getOrNull() ?: return null
            val h = uri.host ?: return null
            return h to (uri.port.takeIf { it != -1 } ?: RTSP_PORT)
        }
        val h = host(server) ?: return null
        return h to RTSP_PORT
    }

    private fun host(server: ServerConfig): String? =
        if (!server.isConfigured) null else runCatching { Uri.parse(server.baseUrl.trim()).host }.getOrNull()
}
