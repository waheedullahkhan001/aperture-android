package com.fuuastisb.aperture.trigger

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.fuuastisb.aperture.data.settings.SettingsRepository
import com.fuuastisb.aperture.domain.trigger.TriggerButton
import com.fuuastisb.aperture.domain.trigger.TriggerPattern
import com.fuuastisb.aperture.domain.trigger.TriggerPatternDetector
import com.fuuastisb.aperture.launcher.TransparentLauncherActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detects the configured volume-button activation pattern — even with the screen off — and
 * launches recording when it fires. The timing logic lives in the pure [TriggerPatternDetector];
 * this class only adapts Android key events into it and reacts when the pattern completes.
 *
 * Runs in the app's main process (unlike the POC's separate `:accessibility` process) so it can
 * inject [SettingsRepository] via Hilt and react to the user's configured [TriggerPattern].
 */
@AndroidEntryPoint
class VolumeAccessibilityService : AccessibilityService() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var pattern: TriggerPattern = TriggerPattern.DEFAULT
    @Volatile private var detector: TriggerPatternDetector = TriggerPatternDetector(pattern)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        // Keep the active pattern in sync with settings; a change restarts the in-progress sequence.
        scope.launch {
            settingsRepository.triggerPattern.collectLatest { configured ->
                pattern = configured
                detector = TriggerPatternDetector(configured)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TriggerButton.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> TriggerButton.VOLUME_DOWN
            else -> return super.onKeyEvent(event)
        }

        // Keys the pattern doesn't use pass through, so normal volume control still works.
        if (!pattern.watches(button)) return super.onKeyEvent(event)

        // Let UP / auto-repeat through so the key behaves normally outside of triggering.
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)

        if (detector.onKeyDown(button, SystemClock.elapsedRealtime())) {
            startActivity(TransparentLauncherActivity.launchIntent(this))
            return true
        }
        // Consume only once a multi-press sequence is genuinely underway, so a single press still
        // adjusts the volume normally; the first press of a potential sequence passes through.
        return detector.pressesSoFar >= 2
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
