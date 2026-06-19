package com.fuuastisb.aperture.data.metadata

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.fuuastisb.aperture.core.di.ApplicationScope
import com.fuuastisb.aperture.data.server.DeviceApi
import com.fuuastisb.aperture.domain.model.MetadataConfig
import com.fuuastisb.aperture.domain.model.MetadataSnapshot
import com.fuuastisb.aperture.domain.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams recording metadata to the server during an active emergency (SRS-026): instead of one stale
 * fix at start, it requests **active** location updates and posts a sample every [POST_INTERVAL_MS] —
 * so a contact watching the live view sees a moving location with speed/heading/accuracy, plus battery
 * ("phone about to die?"). Best-effort and off the recording path; failures are skipped (the next tick
 * re-sends a fresh sample).
 *
 * Uses the framework [LocationManager] rather than fused/Play-Services location — the app is
 * offline-first and must work on devices without Google Play Services.
 */
@Singleton
class MetadataReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val deviceApi: DeviceApi,
) {
    @Volatile private var latestLocation: Location? = null
    private var listener: LocationListener? = null
    private var job: Job? = null

    private val locationManager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /** Begin sampling for [recordingId], posting to [server], honouring [config]. Idempotent-ish: a new
     *  start replaces any prior session. */
    fun start(recordingId: String, server: ServerConfig, config: MetadataConfig) {
        stop() // never run two samplers at once
        latestLocation = null
        if (config.location && hasLocationPermission()) startLocationUpdates()

        job = appScope.launch {
            while (isActive) {
                runCatching { deviceApi.postMetadataSample(server, recordingId, snapshot(config)) }
                    .onFailure { Log.d(TAG, "metadata post skipped: ${it.message}") }
                delay(POST_INTERVAL_MS)
            }
        }
    }

    /** Stop sampling and release location updates. */
    fun stop() {
        job?.cancel()
        job = null
        listener?.let { l -> runCatching { locationManager?.removeUpdates(l) } }
        listener = null
        latestLocation = null
    }

    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() in start()
    private fun startLocationUpdates() {
        val manager = locationManager ?: return
        val l = LocationListener { location -> latestLocation = location }
        listener = l
        // Seed with the last-known fix so the first sample isn't empty while GPS warms up.
        runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { p -> runCatching { manager.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { latestLocation = it }
        }
        // Request active updates from every enabled provider (GPS gives speed/bearing/altitude).
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, MIN_TIME_MS, MIN_DISTANCE_M, l, Looper.getMainLooper())
                }
            }.onFailure { Log.w(TAG, "requestLocationUpdates($provider) failed", it) }
        }
    }

    private fun snapshot(config: MetadataConfig): MetadataSnapshot {
        val loc = if (config.location) latestLocation else null
        return MetadataSnapshot(
            timestampMs = System.currentTimeMillis(),
            deviceModel = if (config.deviceInfo) "${Build.MANUFACTURER} ${Build.MODEL}" else null,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            horizontalAccuracyM = loc?.takeIf { it.hasAccuracy() }?.accuracy,
            speedMps = loc?.takeIf { it.hasSpeed() }?.speed,
            bearingDeg = loc?.takeIf { it.hasBearing() }?.bearing,
            altitudeM = loc?.takeIf { it.hasAltitude() }?.altitude,
            batteryPercent = batteryPercent(),
        )
    }

    private fun batteryPercent(): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Whether the location FGS type can be claimed (location granted) — for the service's startForeground. */
    fun canUseLocationForegroundType(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocationPermission()

    private companion object {
        const val TAG = "MetadataReporter"
        const val POST_INTERVAL_MS = 8_000L // ~8s live cadence (within the 5–10s the backend suggested)
        const val MIN_TIME_MS = 5_000L
        const val MIN_DISTANCE_M = 0f
    }
}
