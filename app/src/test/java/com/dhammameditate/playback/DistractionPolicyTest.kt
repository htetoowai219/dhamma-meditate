package com.dhammameditate.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractionPolicyTest {

    @Test
    fun `airplane mode on is always ready regardless of dnd or ringer state`() {
        for (dnd in listOf(false, true)) {
            for (ringer in RingerState.entries) {
                val snapshot = DistractionSnapshot(
                    isAirplaneModeOn = true,
                    isDndActive = dnd,
                    ringerState = ringer
                )
                val eval = DistractionPolicy.evaluate(snapshot)
                assertTrue("Expected ready for $snapshot", eval.isReady)
                assertEquals("Airplane Mode is on. Ready for quiet meditation.", eval.statusSummary)
                assertEquals(DistractionPolicy.GUIDANCE_READY, eval.guidanceText)
                assertEquals(snapshot, eval.snapshot)
            }
        }
    }

    @Test
    fun `dnd active with airplane mode off is ready regardless of ringer state`() {
        for (ringer in RingerState.entries) {
            val snapshot = DistractionSnapshot(
                isAirplaneModeOn = false,
                isDndActive = true,
                ringerState = ringer
            )
            val eval = DistractionPolicy.evaluate(snapshot)
            assertTrue("Expected ready for $snapshot", eval.isReady)
            assertEquals("Do Not Disturb is active. Ready for quiet meditation.", eval.statusSummary)
            assertEquals(DistractionPolicy.GUIDANCE_READY, eval.guidanceText)
        }
    }

    @Test
    fun `silent ringer with airplane mode and dnd off is ready`() {
        val snapshot = DistractionSnapshot(
            isAirplaneModeOn = false,
            isDndActive = false,
            ringerState = RingerState.SILENT
        )
        val eval = DistractionPolicy.evaluate(snapshot)
        assertTrue(eval.isReady)
        assertEquals("Phone is silenced. Ready for quiet meditation.", eval.statusSummary)
        assertEquals(DistractionPolicy.GUIDANCE_READY, eval.guidanceText)
    }

    @Test
    fun `normal ringer with airplane mode and dnd off is not ready`() {
        val snapshot = DistractionSnapshot(
            isAirplaneModeOn = false,
            isDndActive = false,
            ringerState = RingerState.NORMAL
        )
        val eval = DistractionPolicy.evaluate(snapshot)
        assertFalse(eval.isReady)
        assertEquals("Airplane Mode is off and your ringer is on.", eval.statusSummary)
        assertEquals(DistractionPolicy.GUIDANCE_NOT_READY, eval.guidanceText)
    }

    @Test
    fun `vibrate ringer with airplane mode and dnd off is not ready`() {
        val snapshot = DistractionSnapshot(
            isAirplaneModeOn = false,
            isDndActive = false,
            ringerState = RingerState.VIBRATE
        )
        val eval = DistractionPolicy.evaluate(snapshot)
        assertFalse(eval.isReady)
        assertEquals("Airplane Mode is off and vibration is on.", eval.statusSummary)
        assertEquals(DistractionPolicy.GUIDANCE_NOT_READY, eval.guidanceText)
    }

    @Test
    fun `unknown ringer with airplane mode and dnd off is not ready`() {
        val snapshot = DistractionSnapshot(
            isAirplaneModeOn = false,
            isDndActive = false,
            ringerState = RingerState.UNKNOWN
        )
        val eval = DistractionPolicy.evaluate(snapshot)
        assertFalse(eval.isReady)
        assertEquals(
            "Airplane Mode is off and alerts may interrupt your session.",
            eval.statusSummary
        )
        assertEquals(DistractionPolicy.GUIDANCE_NOT_READY, eval.guidanceText)
    }
}
