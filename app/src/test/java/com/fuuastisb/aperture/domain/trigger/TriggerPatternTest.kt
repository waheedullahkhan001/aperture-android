package com.fuuastisb.aperture.domain.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerPatternTest {

    @Test
    fun `a volume-up pattern watches only volume up`() {
        val pattern = TriggerPattern(TriggerButton.VOLUME_UP, pressCount = 3, windowMs = 1_000, debounceMs = 100)
        assertTrue(pattern.watches(TriggerButton.VOLUME_UP))
        assertFalse(pattern.watches(TriggerButton.VOLUME_DOWN))
    }

    @Test
    fun `an either pattern watches both volume buttons`() {
        val pattern = TriggerPattern(TriggerButton.EITHER, pressCount = 3, windowMs = 1_000, debounceMs = 100)
        assertTrue(pattern.watches(TriggerButton.VOLUME_UP))
        assertTrue(pattern.watches(TriggerButton.VOLUME_DOWN))
    }

    @Test
    fun `the default is three rapid volume-up presses`() {
        assertEquals(TriggerButton.VOLUME_UP, TriggerPattern.DEFAULT.button)
        assertEquals(3, TriggerPattern.DEFAULT.pressCount)
    }
}
