package com.fuuastisb.aperture.domain.trigger

/** Which hardware volume key(s) arm the recording trigger. */
enum class TriggerButton { VOLUME_UP, VOLUME_DOWN, EITHER }

/**
 * User-configurable activation pattern (SRS-030..032): which button arms the trigger,
 * how many presses are required, the window the whole sequence must complete within,
 * and a debounce floor that ignores unrealistically fast repeats.
 */
data class TriggerPattern(
    val button: TriggerButton,
    val pressCount: Int,
    val windowMs: Long,
    val debounceMs: Long,
) {
    /** Whether a physical [button] press counts toward this pattern. */
    fun watches(button: TriggerButton): Boolean = when (this.button) {
        TriggerButton.EITHER -> true
        else -> button == this.button
    }

    companion object {
        /** The POC's effective default — three rapid volume-up presses. */
        val DEFAULT = TriggerPattern(
            button = TriggerButton.VOLUME_UP,
            pressCount = 3,
            windowMs = 1_000,
            debounceMs = 100,
        )
    }
}
