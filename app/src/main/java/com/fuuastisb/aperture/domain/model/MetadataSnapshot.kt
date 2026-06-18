package com.fuuastisb.aperture.domain.model

/** The metadata captured for a recording session, per the user's [MetadataConfig]. */
data class MetadataSnapshot(
    val timestampMs: Long,
    val deviceModel: String?,
    val latitude: Double?,
    val longitude: Double?,
)
