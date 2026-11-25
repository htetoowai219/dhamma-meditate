package com.dhammameditate.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerValidationTest {

    @Test
    fun `defaultDurationMs uses 30 minutes or track length when shorter`() {
        assertEquals(30 * 60_000L, TimerValidation.defaultDurationMs(45 * 60_000L))
        assertEquals(12 * 60_000L, TimerValidation.defaultDurationMs(12 * 60_000L))
        assertEquals(5_000L, TimerValidation.defaultDurationMs(5_000L))
    }

    @Test
    fun `isPresetEnabled disables presets exceeding track duration`() {
        val trackMs = 25 * 60_000L
        assertTrue(TimerValidation.isPresetEnabled(10, trackMs))
        assertTrue(TimerValidation.isPresetEnabled(15, trackMs))
        assertFalse(TimerValidation.isPresetEnabled(30, trackMs))
        assertFalse(TimerValidation.isPresetEnabled(45, trackMs))
        assertFalse(TimerValidation.isPresetEnabled(60, trackMs))
    }

    @Test
    fun `validateCustomMinutes caps values above track duration with notice`() {
        val trackMs = 18 * 60_000L + 30_000L // 18:30
        val within = TimerValidation.validateCustomMinutes(15, trackMs)
        assertEquals(15 * 60_000L, within.durationMs)
        assertFalse(within.wasCapped)
        assertNull(within.noticeMessage)

        val exceeded = TimerValidation.validateCustomMinutes(25, trackMs)
        assertEquals(trackMs, exceeded.durationMs)
        assertTrue(exceeded.wasCapped)
        assertEquals("Adjusted to track length (18:30)", exceeded.noticeMessage)
    }

    @Test
    fun `formatDuration formats MM SS and H MM SS accurately`() {
        assertEquals("00:00", TimerValidation.formatDuration(0L))
        assertEquals("00:01", TimerValidation.formatDuration(400L))
        assertEquals("00:01", TimerValidation.formatDuration(1_000L))
        assertEquals("00:59", TimerValidation.formatDuration(59_999L))
        assertEquals("01:00", TimerValidation.formatDuration(60_000L))
        assertEquals("30:00", TimerValidation.formatDuration(30 * 60_000L))
        assertEquals("1:00:00", TimerValidation.formatDuration(3_600_000L))
        assertEquals("1:05:09", TimerValidation.formatDuration(3_909_000L))
    }
}
