package com.fuuastisb.aperture.domain.model

/** Local-storage policy (SRS-038..041): a size cap and whether to auto-delete the oldest over it. */
data class StoragePolicy(
    val maxBytes: Long = DEFAULT_MAX_BYTES,
    // Reliability-first default: keep recording by reclaiming space from the oldest clips. The user
    // can turn this off, but then a full disk stops recording (the Storage screen warns about that).
    val autoDelete: Boolean = true,
) {
    companion object {
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024 * 1024 // 8 GB
    }
}
