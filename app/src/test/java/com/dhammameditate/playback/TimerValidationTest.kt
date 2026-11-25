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
        assertEquals(0L, TimerValidation.defaultDurationMs(0L))
    }

    @Test
    fun `isPresetEnabled disables presets exceeding max duration`() {
        val trackMs = 25 * 60_000L
        assertTrue(TimerValidation.isPresetEnabled(10, trackMs))
        assertTrue(TimerValidation.isPresetEnabled(15, trackMs))
        assertFalse(TimerValidation.isPresetEnabled(30, trackMs))
        assertFalse(TimerValidation.isPresetEnabled(45, trackMs))
        assertFalse(TimerValidation.isPresetEnabled(60, trackMs))
        assertFalse(TimerValidation.isPresetEnabled(10, 0L))
    }

    @Test
    fun `validateCustomMinutes caps values above track or Main Queue duration with matching notice`() {
        val trackMs = 18 * 60_000L + 30_000L // 18:30
        val within = TimerValidation.validateCustomMinutes(15, trackMs)
        assertEquals(15 * 60_000L, within.durationMs)
        assertFalse(within.wasCapped)
        assertNull(within.noticeMessage)

        val exceededTrack = TimerValidation.validateCustomMinutes(25, trackMs)
        assertEquals(trackMs, exceededTrack.durationMs)
        assertTrue(exceededTrack.wasCapped)
        assertEquals("Adjusted to track length (18:30)", exceededTrack.noticeMessage)

        val exceededQueue = TimerValidation.validateCustomMinutes(
            minutes = 25,
            maxDurationMs = trackMs,
            scopeLabel = TimerValidation.SCOPE_MAIN_QUEUE
        )
        assertEquals(trackMs, exceededQueue.durationMs)
        assertTrue(exceededQueue.wasCapped)
        assertEquals("Adjusted to Main Queue length (18:30)", exceededQueue.noticeMessage)
    }

    @Test
    fun `reconcileTimerAfterMainChange initializes on first track, snaps down on shrinkage, and zeroes on empty`() {
        // 0 -> 1 track (45m) initializes to default 30m
        val initialized = TimerValidation.reconcileTimerAfterMainChange(
            currentDurationMs = 0L,
            previousMainTotalMs = 0L,
            newMainTotalMs = 45 * 60_000L
        )
        assertEquals(30 * 60_000L, initialized.durationMs)
        assertFalse(initialized.wasCapped)
        assertNull(initialized.noticeMessage)

        // 0 -> 1 short track (12m) initializes to full 12m
        val shortInit = TimerValidation.reconcileTimerAfterMainChange(
            currentDurationMs = 0L,
            previousMainTotalMs = 0L,
            newMainTotalMs = 12 * 60_000L
        )
        assertEquals(12 * 60_000L, shortInit.durationMs)
        assertFalse(shortInit.wasCapped)

        // Removing a Main track drops total from 45m to 20m while timer was 30m -> snaps to 20m with notice
        val snapped = TimerValidation.reconcileTimerAfterMainChange(
            currentDurationMs = 30 * 60_000L,
            previousMainTotalMs = 45 * 60_000L,
            newMainTotalMs = 20 * 60_000L
        )
        assertEquals(20 * 60_000L, snapped.durationMs)
        assertTrue(snapped.wasCapped)
        assertEquals("Adjusted to Main Queue length (20:00)", snapped.noticeMessage)

        // Removing a Main track keeps total above current timer (45m -> 35m, timer 30m) -> unchanged
        val unchanged = TimerValidation.reconcileTimerAfterMainChange(
            currentDurationMs = 30 * 60_000L,
            previousMainTotalMs = 45 * 60_000L,
            newMainTotalMs = 35 * 60_000L
        )
        assertEquals(30 * 60_000L, unchanged.durationMs)
        assertFalse(unchanged.wasCapped)
        assertNull(unchanged.noticeMessage)

        // Removing last Main track -> 0ms, no cap notice
        val emptied = TimerValidation.reconcileTimerAfterMainChange(
            currentDurationMs = 20 * 60_000L,
            previousMainTotalMs = 20 * 60_000L,
            newMainTotalMs = 0L
        )
        assertEquals(0L, emptied.durationMs)
        assertFalse(emptied.wasCapped)
        assertNull(emptied.noticeMessage)
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
