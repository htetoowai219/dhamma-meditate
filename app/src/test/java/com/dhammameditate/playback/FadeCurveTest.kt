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

    @Test
    fun `gainForProgress matches normative shared spec reference vectors`() {
        // Normative vectors pinned in docs/spec/dhammameditate-shared-spec.md (DECAY_K = 3.5)
        assertEquals(1.000000f, FadeCurve.gainForProgress(0.00), 0.00001f)
        assertEquals(0.695493f, FadeCurve.gainForProgress(0.10), 0.00001f)
        assertEquals(0.398704f, FadeCurve.gainForProgress(0.25), 0.00001f)
        assertEquals(0.148047f, FadeCurve.gainForProgress(0.50), 0.00001f)
        assertEquals(0.043558f, FadeCurve.gainForProgress(0.75), 0.00001f)
        assertEquals(0.013049f, FadeCurve.gainForProgress(0.90), 0.00001f)
        assertEquals(0.000000f, FadeCurve.gainForProgress(1.00), 0.0f)
    }

    @Test
    fun `3000ms short target fade matches shared spec reference vectors`() {
        val shortTarget = 3_000L
        assertEquals(3_000L, FadeCurve.effectiveFadeMs(shortTarget))
        assertEquals(1.000000f, FadeCurve.gain(remainingMs = 3_000L, targetDurationMs = shortTarget), 0.0f)
        assertEquals(0.398704f, FadeCurve.gain(remainingMs = 2_250L, targetDurationMs = shortTarget), 0.00001f)
        assertEquals(0.148047f, FadeCurve.gain(remainingMs = 1_500L, targetDurationMs = shortTarget), 0.00001f)
        assertEquals(0.043558f, FadeCurve.gain(remainingMs = 750L, targetDurationMs = shortTarget), 0.00001f)
        assertEquals(0.000000f, FadeCurve.gain(remainingMs = 0L, targetDurationMs = shortTarget), 0.0f)
    }
}
