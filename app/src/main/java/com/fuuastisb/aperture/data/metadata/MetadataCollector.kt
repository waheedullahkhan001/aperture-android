package com.fuuastisb.aperture.data.metadata

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.fuuastisb.aperture.domain.model.MetadataConfig
import com.fuuastisb.aperture.domain.model.MetadataSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers the optional metadata for a recording (SRS-026), honouring the user's [MetadataConfig]
 * and the location permission. Location uses the cached last-known fix (cheap, no live GPS wait).
 * The captured snapshot is what the backend would embed into the stream page.
 */
@Singleton
class MetadataCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun collect(config: MetadataConfig): MetadataSnapshot {
        val location = if (config.location && hasLocationPermission()) lastKnownLocation() else null
        return MetadataSnapshot(
            timestampMs = System.currentTimeMillis(),
            deviceModel = if (config.deviceInfo) "${Build.MANUFACTURER} ${Build.MODEL}" else null,
            latitude = location?.latitude,
            longitude = location?.longitude,
        )
    }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() at the call site
    private fun lastKnownLocation(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
