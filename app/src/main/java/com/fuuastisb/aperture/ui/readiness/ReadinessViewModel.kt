package com.fuuastisb.aperture.ui.readiness

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuuastisb.aperture.data.server.ServerHealthMonitor
import com.fuuastisb.aperture.data.server.ServerHealthStatus
import com.fuuastisb.aperture.trigger.VolumeAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A snapshot of whether the app is fully armed and ready to record on demand. */
data class Readiness(
    val cameraAndMic: Boolean = false,
    val notifications: Boolean = false,
    val accessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
)

@HiltViewModel
class ReadinessViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val serverHealthMonitor: ServerHealthMonitor,
) : ViewModel() {

    private val _readiness = MutableStateFlow(Readiness())
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    /** Live reachability of the Spring API and the MediaMTX media server. */
    val serverStatus: StateFlow<ServerHealthStatus> = serverHealthMonitor.status

    fun refresh() {
        viewModelScope.launch {
            _readiness.value = Readiness(
                cameraAndMic = granted(Manifest.permission.CAMERA) && granted(Manifest.permission.RECORD_AUDIO),
                notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    granted(Manifest.permission.POST_NOTIFICATIONS),
                accessibility = isAccessibilityEnabled(),
                batteryUnrestricted = isBatteryUnrestricted(),
            )
            serverHealthMonitor.check()
        }
    }

    fun openAppSettings() = startSettings(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${appContext.packageName}".toUri()),
    )

    fun openAccessibilitySettings() = startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openBatterySettings() = startSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun startSettings(intent: Intent) {
        runCatching { appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "${appContext.packageName}/${VolumeAccessibilityService::class.java.name}"
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
    }

    private fun isBatteryUnrestricted(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }
}
