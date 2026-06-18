package com.fuuastisb.aperture.domain.model

/**
 * The offline-first server connection: just a base URL and an access token the user obtains by
 * logging in on the web. The native client never handles passwords (UC-09).
 */
data class ServerConfig(
    val baseUrl: String = "",
    val token: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}
