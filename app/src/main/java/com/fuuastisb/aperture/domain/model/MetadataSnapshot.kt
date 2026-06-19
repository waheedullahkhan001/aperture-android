package com.fuuastisb.aperture.domain.model

/**
 * One metadata sample for a recording (sent periodically during an emergency so the live-view sees a
 * moving location, not a stale dot). All but the timestamp are optional — send what's available.
 */
data class MetadataSnapshot(
    val timestampMs: Long,
    val deviceModel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val horizontalAccuracyM: Float? = null, // Location.accuracy
    val speedMps: Float? = null,            // Location.speed
    val bearingDeg: Float? = null,          // Location.bearing (0–360)
    val altitudeM: Double? = null,          // Location.altitude
    val batteryPercent: Int? = null,        // 0–100 ("phone about to die?")
)
