package com.fuuastisb.aperture.domain.trigger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerPatternDetectorTest {

    private val threeVolumeUp = TriggerPattern(
        button = TriggerButton.VOLUME_UP,
        pressCount = 3,
        windowMs = 1_000,
        debounceMs = 100,
    )

    @Test
    fun `three presses within the window complete the pattern`() {
        val detector = TriggerPatternDetector(threeVolumeUp)

        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 0))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 200))
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 400))
    }

    @Test
    fun `presses of a button other than the configured one are ignored`() {
        val detector = TriggerPatternDetector(threeVolumeUp)

        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_DOWN, timestampMs = 0))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_DOWN, timestampMs = 200))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_DOWN, timestampMs = 400))
    }

    @Test
    fun `a gap longer than the window restarts the sequence`() {
        val detector = TriggerPatternDetector(threeVolumeUp)

        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 0))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 200))
        // 1500 ms since the previous press exceeds the 1000 ms window -> fresh press #1
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 1_700))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 1_900))
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 2_100))
    }

    @Test
    fun `presses faster than the debounce floor are ignored`() {
        val detector = TriggerPatternDetector(threeVolumeUp)

        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 0))
        // 50 ms and 40 ms gaps are below the 100 ms debounce floor -> ignored
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 50))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 90))
        // the genuine 2nd and 3rd presses
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 300))
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 600))
    }

    @Test
    fun `an EITHER pattern accepts both volume buttons`() {
        val detector = TriggerPatternDetector(threeVolumeUp.copy(button = TriggerButton.EITHER))

        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 0))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_DOWN, timestampMs = 200))
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 400))
    }

    @Test
    fun `the detector rearms after firing`() {
        val detector = TriggerPatternDetector(threeVolumeUp)

        detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 0)
        detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 200)
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 400))

        // A fresh sequence fires again from a clean state.
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 600))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 800))
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 1_000))
    }

    @Test
    fun `presses of an unwatched button between valid presses do not disturb the sequence`() {
        val detector = TriggerPatternDetector(threeVolumeUp)

        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 0))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_DOWN, timestampMs = 100)) // ignored
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 200))
        assertFalse(detector.onKeyDown(TriggerButton.VOLUME_DOWN, timestampMs = 300)) // ignored
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 400))
    }

    @Test
    fun `a single-press pattern fires on the first press`() {
        val detector = TriggerPatternDetector(threeVolumeUp.copy(pressCount = 1))
        assertTrue(detector.onKeyDown(TriggerButton.VOLUME_UP, timestampMs = 0))
    }
}
