package com.fuuastisb.aperture.domain.model

/**
 * The native client's streaming configuration: whether to stream, the streaming-specific
 * [quality]/[fps] (independent of local capture — a lower resolution/frame rate is usually wanted
 * for the network), and an optional direct media-server [url]. Offline-first: disabled = local-only.
 *
 * The backend connection (base URL + token) lives in [ServerConfig]; once a backend exists the ingest
 * URL is derived from it, and [url] is the manual/LAN override that drives streaming today.
 */
data class StreamSettings(
    val enabled: Boolean = false,
    val url: String = "",
    val quality: VideoQuality = VideoQuality.HD,
    val fps: Int = 30,
    val streamAudio: Boolean = true,
) {
    /** Whether a recording should stream: enabled, with a non-blank, supported-scheme URL. */
    val isReadyToStream: Boolean
        get() = enabled && url.isNotBlank() && SUPPORTED_SCHEMES.any { url.startsWith(it, ignoreCase = true) }

    private companion object {
        // RTSP only — the backend disables RTMP / SRT / WHIP server-side; there is no fallback.
        val SUPPORTED_SCHEMES = listOf("rtsp://", "rtsps://")
    }
}
