package com.fuuastisb.aperture.data.server

import org.json.JSONObject
import java.util.Base64

/**
 * The device pairing "connect code" (per the spec): base64url(RFC 4648 url-safe, padding optional) of
 * a UTF-8 JSON `{ "v":1, "api":"<origin>", "token":"<apd_…>", "stream"?:"<rtsp override>" }`.
 *
 * One paste/scan carries both the API base URL and the device token (replacing the old two fields).
 */
object ConnectString {

    data class Parsed(val api: String, val token: String, val streamOverride: String?)

    /** Decode + validate a connect code. Failure carries a user-facing message. */
    fun parse(input: String): Result<Parsed> = runCatching {
        val json = decodeToJson(input)
        val o = JSONObject(json)
        require(o.optInt("v", -1) == 1) { "Unsupported connect code (version)." }
        val api = o.optString("api").trim()
        val token = o.optString("token").trim()
        require(api.isNotEmpty() && token.isNotEmpty()) { "Connect code is missing the server or token." }
        val stream = o.optString("stream").trim().ifEmpty { null }
        Parsed(api, token, stream)
    }.recoverCatching { e ->
        // Re-wrap low-level decode/JSON errors as a single clear message.
        if (e is IllegalArgumentException) throw e
        throw IllegalArgumentException("That doesn't look like a valid connect code.")
    }

    /** base64url → UTF-8 string. Pure + unit-tested; tolerates input with or without `=` padding. */
    fun decodeToJson(input: String): String {
        val cleaned = input.trim().filterNot { it.isWhitespace() }
        val padded = when (cleaned.length % 4) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned // 0 = already padded/exact; 1 is invalid and the decoder will reject it
        }
        val bytes = Base64.getUrlDecoder().decode(padded)
        return String(bytes, Charsets.UTF_8)
    }
}
