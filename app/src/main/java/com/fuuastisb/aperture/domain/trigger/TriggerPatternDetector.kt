package com.fuuastisb.aperture.domain.trigger

/**
 * Pure, deterministic detector for the volume-button activation pattern. Timestamps are
 * passed in rather than read from a system clock, so behaviour is fully unit-testable.
 *
 * Extracted from the POC's VolumeDetectionService, where this timing logic was entangled
 * with the Android AccessibilityService (and where the constants disagreed with their own
 * comments).
 */
class TriggerPatternDetector(private val pattern: TriggerPattern) {

    private var count = 0
    private var lastTimestampMs = 0L

    /** Presses counted toward the current (incomplete) sequence; 0 when idle or just after firing. */
    val pressesSoFar: Int get() = count

    /**
     * Feed a key-down event. Returns `true` exactly on the event that completes the
     * configured pattern; the caller should then fire the trigger and the detector rearms.
     */
    fun onKeyDown(button: TriggerButton, timestampMs: Long): Boolean {
        if (!matches(button)) return false

        if (count > 0) {
            val sinceLast = timestampMs - lastTimestampMs
            // Debounce: ignore repeats faster than the floor (e.g. key auto-repeat / bounce).
            if (sinceLast < pattern.debounceMs) return false
            // Too slow since the previous press: abandon the old sequence and start fresh.
            if (sinceLast > pattern.windowMs) count = 0
        }

        lastTimestampMs = timestampMs
        count++
        if (count >= pattern.pressCount) {
            count = 0
            return true
        }
        return false
    }

    private fun matches(button: TriggerButton): Boolean = pattern.watches(button)
}
