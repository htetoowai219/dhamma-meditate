package com.dhammameditate.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeCurveTest {

    @Test
    fun `gainForProgress hits exact endpoints 1 and 0`() {
        assertEquals(1.0f, FadeCurve.gainForProgress(0.0), 0.0f)
        assertEquals(0.0f, FadeCurve.gainForProgress(1.0), 0.0f)
        assertEquals(1.0f, FadeCurve.gainForProgress(-0.5), 0.0f)
        assertEquals(0.0f, FadeCurve.gainForProgress(1.5), 0.0f)
    }

    @Test
    fun `gainForProgress is strictly monotonically decreasing and convex exponential`() {
        var previous = FadeCurve.gainForProgress(0.0)
        for (step in 1..100) {
            val p = step / 100.0
            val current = FadeCurve.gainForProgress(p)
            assertTrue("Expected gain($p)=$current < previous=$previous", current < previous)
            previous = current
        }
        // Exponential decay with k=3.5 drops faster early than linear (at p=0.5, gain < 0.5)
        val midpointGain = FadeCurve.gainForProgress(0.5)
        assertTrue("Expected exponential midpoint < 0.25, got $midpointGain", midpointGain < 0.25f)
        assertTrue("Expected exponential midpoint > 0.10, got $midpointGain", midpointGain > 0.10f)
    }

    @Test
    fun `gain stays at full volume before fade window and reaches zero at end`() {
        val target = 60_000L
        assertEquals(1.0f, FadeCurve.gain(remainingMs = 30_000L, targetDurationMs = target), 0.0f)
        assertEquals(1.0f, FadeCurve.gain(remainingMs = 10_000L, targetDurationMs = target), 0.0f)
        val halfway = FadeCurve.gain(remainingMs = 5_000L, targetDurationMs = target)
        assertEquals(FadeCurve.gainForProgress(0.5), halfway, 0.0001f)
        assertEquals(0.0f, FadeCurve.gain(remainingMs = 0L, targetDurationMs = target), 0.0f)
    }

    @Test
    fun `short track under 10 seconds clamps effective fade window to target duration`() {
        val shortTarget = 4_000L
        assertEquals(4_000L, FadeCurve.effectiveFadeMs(shortTarget))
        assertEquals(1.0f, FadeCurve.gain(remainingMs = 4_000L, targetDurationMs = shortTarget), 0.0f)
        assertEquals(
            FadeCurve.gainForProgress(0.5),
            FadeCurve.gain(remainingMs = 2_000L, targetDurationMs = shortTarget),
            0.0001f
        )
        assertEquals(0.0f, FadeCurve.gain(remainingMs = 0L, targetDurationMs = shortTarget), 0.0f)
    }
}
