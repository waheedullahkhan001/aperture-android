package com.fuuastisb.aperture.data.server

import com.fuuastisb.aperture.data.settings.SettingsRepository
import com.fuuastisb.aperture.domain.model.ServerConfig
import com.fuuastisb.aperture.domain.model.StreamSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/** Reachability of one server, from a real check (not just "the field is filled in"). */
enum class ServerHealth {
    Unknown,      // not checked yet this run
    Disabled,     // nothing configured for this target
    Reachable,    // answered / accepted a connection
    Unreachable,  // configured, but the check is currently failing
}

/**
 * The client talks to TWO distinct servers, checked separately:
 *  - [backend]: the Spring API (`GET {baseUrl}/api/v1/device/me` — validates the token, not just reach).
 *  - [media]: the MediaMTX stream target — it speaks RTSP (not HTTP), so we verify the host:port
 *    accepts a TCP connection.
 */
data class ServerHealthStatus(
    val backend: ServerHealth = ServerHealth.Unknown,
    val media: ServerHealth = ServerHealth.Unknown,
    /** Extra detail when the backend is unreachable (e.g. token rejected / device revoked). */
    val backendDetail: String? = null,
)

/**
 * Periodically verifies both servers are actually reachable so the UI can distinguish "configured"
 * from "working", gate streaming on a live target, and warn when something's enabled but down.
 * Re-checks on its own process-lifetime scope every [POLL_MS], and on demand via [check].
 */
@Singleton
class ServerHealthMonitor @Inject constructor(
    private val settings: SettingsRepository,
    private val deviceApi: DeviceApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ServerHealthStatus())
    val status: StateFlow<ServerHealthStatus> = _status.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                check()
                delay(POLL_MS)
            }
        }
    }

    /** Re-evaluate both servers now. */
    suspend fun check() {
        val config = settings.serverConfigSnapshot()
        val stream = settings.streamSettingsSnapshot()
        var detail: String? = null
        val backend = when {
            !config.isConfigured -> ServerHealth.Disabled
            else -> when (val r = deviceApi.me(config)) {
                is MeResult.Ok -> ServerHealth.Reachable
                is MeResult.Unauthorized -> {
                    detail = if (r.revoked) "Device disconnected on the web — reconnect it." else "Token rejected — check it."
                    ServerHealth.Unreachable
                }
                MeResult.Unreachable -> ServerHealth.Unreachable
            }
        }
        _status.value = ServerHealthStatus(backend = backend, media = checkMedia(stream, config), backendDetail = detail)
    }

    /**
     * MediaMTX has no remote-friendly HTTP health route (its control API on :9997 and Prometheus
     * metrics on :9998 are off by default and localhost-only), so we probe the actual RTSP ingest port
     * with a TCP connect (the stack is RTSP-only, default port 8554).
     */
    private suspend fun checkMedia(stream: StreamSettings, server: ServerConfig): ServerHealth = withContext(Dispatchers.IO) {
        val target = StreamTarget.mediaHostPort(stream, server) ?: return@withContext ServerHealth.Disabled
        runCatching {
            Socket().use { it.connect(InetSocketAddress(target.first, target.second), TCP_TIMEOUT_MS) }
            ServerHealth.Reachable
        }.getOrDefault(ServerHealth.Unreachable)
    }

    private companion object {
        const val POLL_MS = 45_000L
        const val TCP_TIMEOUT_MS = 4_000
    }
}
