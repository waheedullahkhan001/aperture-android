package com.fuuastisb.aperture.domain.model

/**
 * Optional metadata to attach to recordings / stream pages (SRS-021, SRS-026). All off-by-default
 * except the timestamp, and never required for recording — location additionally needs the
 * (optional) location permission, requested from the meta-info settings page.
 */
data class MetadataConfig(
    val location: Boolean = false,
    val timestamp: Boolean = true,
    val deviceInfo: Boolean = false,
)
