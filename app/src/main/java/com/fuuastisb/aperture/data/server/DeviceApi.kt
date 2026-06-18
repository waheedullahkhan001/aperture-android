package com.fuuastisb.aperture.data.server

import com.fuuastisb.aperture.domain.model.MetadataSnapshot
import com.fuuastisb.aperture.domain.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Result of the device identity/token check (`GET /api/v1/device/me`). */
sealed interface MeResult {
    data class Ok(val userFullname: String, val deviceName: String) : MeResult
    /** 401 — [revoked] true means the user disconnected this device on the web (stop, reconnect needed). */
    data class Unauthorized(val revoked: Boolean) : MeResult
    data object Unreachable : MeResult
}

/** What the server returns when a recording is created (`PUT …/recordings/{id}`). */
data class CreatedRecording(
    val recordingId: String,
    val countdownEndsAt: String?, // ISO-8601; null = no alert armed (no contacts on the web)
    val watchUrl: String?,
)

/** Outcome of a cancel-alerts attempt. */
sealed interface CancelOutcome {
    data class Done(val alertsAlreadyDispatched: Boolean) : CancelOutcome
    /** 404 — the recording hasn't reached the server yet (offline start); keep retrying. */
    data object NotFoundRetry : CancelOutcome
    data object Failed : CancelOutcome
}

/**
 * The Aperture backend's **device** API (token = `apd_…`, `Authorization: Bearer`). All calls are
 * suspend + best-effort: callers treat failures as "try later", never letting them block recording.
 * Endpoints and the `/device/me`-not-`/actuator/health` correction come from the backend contract.
 */
@Singleton
class DeviceApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Validates server identity AND token in one call (unlike the public, token-ignoring health route). */
    suspend fun me(config: ServerConfig): MeResult = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(builder(config, "/api/v1/device/me").get().build()).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        val j = json(resp)
                        MeResult.Ok(
                            userFullname = j?.optString("userFullname").orEmpty(),
                            deviceName = j?.optString("deviceName").orEmpty(),
                        )
                    }
                    resp.code == 401 -> MeResult.Unauthorized(revoked = json(resp)?.optString("code") == "DEVICE_REVOKED")
                    else -> MeResult.Unreachable
                }
            }
        }.getOrDefault(MeResult.Unreachable)
    }

    /** Create/announce the recording. Fire-and-forget at trigger time — don't block streaming on it. */
    suspend fun createRecording(config: ServerConfig, id: String, startedAtIso: String): CreatedRecording? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("startedAt", startedAtIso).toString().toRequestBody(JSON)
                client.newCall(builder(config, "$RECORDINGS/$id").put(body).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val j = json(resp) ?: return@use null
                    CreatedRecording(
                        recordingId = j.optString("recordingId", id),
                        countdownEndsAt = j.optStringOrNull("countdownEndsAt"),
                        watchUrl = j.optStringOrNull("watchUrl"),
                    )
                }
            }.getOrNull()
        }

    /** Best-effort "stopped" hint (the media-server hook is authoritative). */
    suspend fun endRecording(config: ServerConfig, id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(builder(config, "$RECORDINGS/$id/end").post(EMPTY).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Queue a captured metadata sample for the recording. */
    suspend fun postMetadataSample(config: ServerConfig, id: String, m: MetadataSnapshot): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                // The server expects a batch: { "samples": [ { … } ] }.
                val sample = JSONObject().apply {
                    put("clientTimestamp", Instant.ofEpochMilli(m.timestampMs).toString())
                    m.latitude?.let { put("latitude", it) }
                    m.longitude?.let { put("longitude", it) }
                    m.deviceModel?.let { put("deviceInfo", it) }
                }
                val body = JSONObject().put("samples", JSONArray().put(sample)).toString().toRequestBody(JSON)
                client.newCall(builder(config, "$RECORDINGS/$id/metadata-samples").post(body).build())
                    .execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /** Cancel the emergency alert for this recording. Both "disarmed" and "too late" return 200. */
    suspend fun cancelAlerts(config: ServerConfig, id: String): CancelOutcome = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(builder(config, "$RECORDINGS/$id/cancel-alerts").post(EMPTY).build()).execute().use { resp ->
                when {
                    resp.isSuccessful ->
                        CancelOutcome.Done(alertsAlreadyDispatched = json(resp)?.optBoolean("alertsAlreadyDispatched", false) == true)
                    resp.code == 404 -> CancelOutcome.NotFoundRetry
                    else -> CancelOutcome.Failed
                }
            }
        }.getOrDefault(CancelOutcome.Failed)
    }

    private fun builder(config: ServerConfig, path: String): Request.Builder = Request.Builder()
        .url(config.baseUrl.trim().trimEnd('/') + path)
        .header("Authorization", "Bearer ${config.token.trim()}")

    private fun json(resp: Response): JSONObject? =
        runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private companion object {
        const val RECORDINGS = "/api/v1/device/recordings"
        val JSON = "application/json; charset=utf-8".toMediaType()
        val EMPTY = ByteArray(0).toRequestBody()
    }
}
