package com.fuuastisb.aperture.domain.model

/** Local-storage policy (SRS-038..041): a size cap and whether to auto-delete the oldest over it. */
data class StoragePolicy(
    val maxBytes: Long = DEFAULT_MAX_BYTES,
    // Reliability-first default: keep recording by reclaiming space from the oldest clips. The user
    // can turn this off, but then a full disk stops recording (the Storage screen warns about that).
    val autoDelete: Boolean = true,
    // MediaStore relative path recordings are saved to. Scoped storage restricts video to the Movies/
    // and DCIM/ collections, so the location is a choice of those + a folder name (not an arbitrary
    // filesystem path) — which keeps it reliable. The in-app library queries this same path.
    val relativePath: String = DEFAULT_RELATIVE_PATH,
) {
    companion object {
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024 * 1024 // 8 GB
        const val DEFAULT_RELATIVE_PATH = "Movies/Aperture"
    }
}
