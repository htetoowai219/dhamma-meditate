package com.dhammameditate.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeditationTimerEngineTest {

    @Test
    fun `monotonic progression transitions playing to fading to completed without drift`() {
        var now = 10_000L
        val engine = MeditationTimerEngine(clockMs = { now })

        val started = engine.start(targetDurationMs = 30_000L)
        assertEquals(PlaybackStatus.PLAYING, started.status)
        assertEquals(0L, started.elapsedMs)
        assertEquals(30_000L, started.remainingMs)
        assertEquals(1.0f, started.volume, 0.0f)

        // Advance 15 seconds (simulating screen off without intermediate ticks)
        now += 15_000L
        val mid = engine.tick()
        assertEquals(PlaybackStatus.PLAYING, mid.status)
        assertEquals(15_000L, mid.elapsedMs)
        assertEquals(15_000L, mid.remainingMs)
        assertEquals(1.0f, mid.volume, 0.0f)

        // Advance 5 more seconds -> exactly 10s left -> enters FADING
        now += 5_000L
        val fadeStart = engine.tick()
        assertEquals(PlaybackStatus.FADING, fadeStart.status)
        assertEquals(10_000L, fadeStart.remainingMs)
        assertEquals(1.0f, fadeStart.volume, 0.0001f)

        // Advance 5 seconds into fade
        now += 5_000L
        val midFade = engine.tick()
        assertEquals(PlaybackStatus.FADING, midFade.status)
        assertEquals(5_000L, midFade.remainingMs)
        assertEquals(FadeCurve.gainForProgress(0.5), midFade.volume, 0.0001f)

        // Advance past end -> COMPLETED with exact 0.0f volume and 0ms remaining
        now += 6_000L
        val done = engine.tick()
        assertEquals(PlaybackStatus.COMPLETED, done.status)
        assertEquals(30_000L, done.elapsedMs)
        assertEquals(0L, done.remainingMs)
        assertEquals(0.0f, done.volume, 0.0f)
    }

    @Test
    fun `pause and continue freeze countdown accurately across long pauses`() {
        var now = 1_000L
        val engine = MeditationTimerEngine(clockMs = { now })

        engine.start(targetDurationMs = 60_000L)
        now += 20_000L

        val paused = engine.pause()
        assertEquals(PlaybackStatus.PAUSED, paused.status)
        assertEquals(20_000L, paused.elapsedMs)
        assertEquals(40_000L, paused.remainingMs)

        // Wait 5 minutes while paused — elapsed and remaining must remain frozen
        now += 300_000L
        val stillPaused = engine.tick()
        assertEquals(PlaybackStatus.PAUSED, stillPaused.status)
        assertEquals(20_000L, stillPaused.elapsedMs)
        assertEquals(40_000L, stillPaused.remainingMs)

        val resumed = engine.resume()
        assertEquals(PlaybackStatus.PLAYING, resumed.status)
        assertEquals(20_000L, resumed.elapsedMs)
        assertEquals(40_000L, resumed.remainingMs)

        // Advance 35 seconds -> 55s elapsed, 5s remaining -> FADING
        now += 35_000L
        val fading = engine.tick()
        assertEquals(PlaybackStatus.FADING, fading.status)
        assertEquals(55_000L, fading.elapsedMs)
        assertEquals(5_000L, fading.remainingMs)
        assertTrue(fading.volume < 0.25f && fading.volume > 0.10f)

        // Pause inside fade window and resume directly back into FADING
        val pausedInFade = engine.pause()
        assertEquals(PlaybackStatus.PAUSED, pausedInFade.status)
        now += 120_000L
        val resumedInFade = engine.resume()
        assertEquals(PlaybackStatus.FADING, resumedInFade.status)
        assertEquals(5_000L, resumedInFade.remainingMs)
    }

    @Test
    fun `short track under 10s enters fading immediately and completes cleanly`() {
        var now = 5_000L
        val engine = MeditationTimerEngine(clockMs = { now })

        val start = engine.start(targetDurationMs = 6_000L)
        assertEquals(PlaybackStatus.FADING, start.status)
        assertEquals(1.0f, start.volume, 0.0f)

        now += 3_000L
        val half = engine.tick()
        assertEquals(PlaybackStatus.FADING, half.status)
        assertEquals(3_000L, half.remainingMs)
        assertEquals(FadeCurve.gainForProgress(0.5), half.volume, 0.0001f)

        now += 3_000L
        val end = engine.tick()
        assertEquals(PlaybackStatus.COMPLETED, end.status)
        assertEquals(0L, end.remainingMs)
        assertEquals(0.0f, end.volume, 0.0f)
    }

    @Test
    fun `stopAndReset and subsequent start always begin fresh at zero and full volume`() {
        var now = 100L
        val engine = MeditationTimerEngine(clockMs = { now })

        engine.start(targetDurationMs = 20_000L)
        now += 15_000L
        engine.tick()

        val reset = engine.stopAndReset()
        assertEquals(PlaybackStatus.IDLE, reset.status)
        assertEquals(0L, reset.elapsedMs)
        assertEquals(20_000L, reset.remainingMs)
        assertEquals(1.0f, reset.volume, 0.0f)

        now += 500L
        val freshStart = engine.start(targetDurationMs = 20_000L)
        assertEquals(PlaybackStatus.PLAYING, freshStart.status)
        assertEquals(0L, freshStart.elapsedMs)
        assertEquals(20_000L, freshStart.remainingMs)
        assertEquals(1.0f, freshStart.volume, 0.0f)
    }

    @Test
    fun `completeEarly transitions cleanly to completed at zero volume`() {
        var now = 0L
        val engine = MeditationTimerEngine(clockMs = { now })

        engine.start(targetDurationMs = 30_000L)
        now += 29_900L
        val completed = engine.completeEarly()

        assertEquals(PlaybackStatus.COMPLETED, completed.status)
        assertEquals(30_000L, completed.elapsedMs)
        assertEquals(0L, completed.remainingMs)
        assertEquals(0.0f, completed.volume, 0.0f)
    }

    @Test
    fun `intro queue freezes countdown through playback and pause resume until onIntroComplete`() {
        var now = 1_000L
        val engine = MeditationTimerEngine(clockMs = { now })

        val started = engine.start(targetDurationMs = 45_000L, hasIntro = true)
        assertEquals(PlaybackStatus.INTRO_PLAYING, started.status)
        assertEquals(0L, started.elapsedMs)
        assertEquals(45_000L, started.remainingMs)
        assertEquals(1.0f, started.volume, 0.0f)

        // Advance 3 minutes during Intro -> timer must stay completely frozen
        now += 180_000L
        val duringIntro = engine.tick()
        assertEquals(PlaybackStatus.INTRO_PLAYING, duringIntro.status)
        assertEquals(0L, duringIntro.elapsedMs)
        assertEquals(45_000L, duringIntro.remainingMs)
        assertEquals(1.0f, duringIntro.volume, 0.0f)

        // Pause and resume during Intro -> remains INTRO_PAUSED -> INTRO_PLAYING with timer frozen
        val introPaused = engine.pause()
        assertEquals(PlaybackStatus.INTRO_PAUSED, introPaused.status)
        assertEquals(0L, introPaused.elapsedMs)
        assertEquals(45_000L, introPaused.remainingMs)
        assertEquals(1.0f, introPaused.volume, 0.0f)

        now += 60_000L
        val stillIntroPaused = engine.tick()
        assertEquals(PlaybackStatus.INTRO_PAUSED, stillIntroPaused.status)
        assertEquals(0L, stillIntroPaused.elapsedMs)
        assertEquals(45_000L, stillIntroPaused.remainingMs)

        val introResumed = engine.resume()
        assertEquals(PlaybackStatus.INTRO_PLAYING, introResumed.status)
        assertEquals(0L, introResumed.elapsedMs)
        assertEquals(45_000L, introResumed.remainingMs)

        // Transition Intro -> Main starts monotonic countdown from 0 elapsed
        now += 10_000L
        val mainStarted = engine.onIntroComplete()
        assertEquals(PlaybackStatus.PLAYING, mainStarted.status)
        assertEquals(0L, mainStarted.elapsedMs)
        assertEquals(45_000L, mainStarted.remainingMs)
        assertEquals(1.0f, mainStarted.volume, 0.0f)

        // Advance 12s into Main
        now += 12_000L
        val mainMid = engine.tick()
        assertEquals(PlaybackStatus.PLAYING, mainMid.status)
        assertEquals(12_000L, mainMid.elapsedMs)
        assertEquals(33_000L, mainMid.remainingMs)
    }

    @Test
    fun `onIntroComplete is strictly one-shot and does not reset timer on Main to Main transitions`() {
        var now = 2_000L
        val engine = MeditationTimerEngine(clockMs = { now })

        engine.start(targetDurationMs = 60_000L, hasIntro = true)
        now += 30_000L
        val mainStarted = engine.onIntroComplete()
        assertEquals(PlaybackStatus.PLAYING, mainStarted.status)
        assertEquals(0L, mainStarted.elapsedMs)

        // Advance 25s into Main track 1, then simulate Main track 2 calling onIntroComplete again
        now += 25_000L
        val duplicateCall = engine.onIntroComplete()
        assertEquals(PlaybackStatus.PLAYING, duplicateCall.status)
        assertEquals(25_000L, duplicateCall.elapsedMs)
        assertEquals(35_000L, duplicateCall.remainingMs)

        // Also calling onIntroComplete when already in FADING does not reset fade
        now += 30_000L // 55s elapsed, 5s left -> FADING
        val inFade = engine.tick()
        assertEquals(PlaybackStatus.FADING, inFade.status)
        val duplicateInFade = engine.onIntroComplete()
        assertEquals(PlaybackStatus.FADING, duplicateInFade.status)
        assertEquals(55_000L, duplicateInFade.elapsedMs)
        assertEquals(5_000L, duplicateInFade.remainingMs)
    }

    @Test
    fun `onIntroComplete while intro paused anchors Main at zero in paused state`() {
        var now = 5_000L
        val engine = MeditationTimerEngine(clockMs = { now })

        engine.start(targetDurationMs = 30_000L, hasIntro = true)
        engine.pause()
        assertEquals(PlaybackStatus.INTRO_PAUSED, engine.currentStatus())

        val transitionedPaused = engine.onIntroComplete()
        assertEquals(PlaybackStatus.PAUSED, transitionedPaused.status)
        assertEquals(0L, transitionedPaused.elapsedMs)
        assertEquals(30_000L, transitionedPaused.remainingMs)

        now += 20_000L
        val resumed = engine.resume()
        assertEquals(PlaybackStatus.PLAYING, resumed.status)
        assertEquals(0L, resumed.elapsedMs)
        assertEquals(30_000L, resumed.remainingMs)

        now += 10_000L
        val ticked = engine.tick()
        assertEquals(10_000L, ticked.elapsedMs)
        assertEquals(20_000L, ticked.remainingMs)
    }

    @Test
    fun `short main duration under 10s enters fading immediately after intro completes`() {
        var now = 10_000L
        val engine = MeditationTimerEngine(clockMs = { now })

        val introStart = engine.start(targetDurationMs = 8_000L, hasIntro = true)
        assertEquals(PlaybackStatus.INTRO_PLAYING, introStart.status)
        assertEquals(1.0f, introStart.volume, 0.0f)

        now += 40_000L
        val mainStart = engine.onIntroComplete()
        assertEquals(PlaybackStatus.FADING, mainStart.status)
        assertEquals(0L, mainStart.elapsedMs)
        assertEquals(8_000L, mainStart.remainingMs)
        assertEquals(1.0f, mainStart.volume, 0.0001f)

        now += 4_000L
        val halfFade = engine.tick()
        assertEquals(PlaybackStatus.FADING, halfFade.status)
        assertEquals(4_000L, halfFade.remainingMs)
        assertEquals(FadeCurve.gainForProgress(0.5), halfFade.volume, 0.0001f)

        now += 4_000L
        val completed = engine.tick()
        assertEquals(PlaybackStatus.COMPLETED, completed.status)
        assertEquals(0L, completed.remainingMs)
        assertEquals(0.0f, completed.volume, 0.0f)
    }

    @Test
    fun `session with zero intro tracks starts directly in Main playback`() {
        var now = 0L
        val engine = MeditationTimerEngine(clockMs = { now })

        val directMain = engine.start(targetDurationMs = 30_000L, hasIntro = false)
        assertEquals(PlaybackStatus.PLAYING, directMain.status)
        assertEquals(0L, directMain.elapsedMs)
        assertEquals(30_000L, directMain.remainingMs)

        now += 5_000L
        val ticked = engine.tick()
        assertEquals(5_000L, ticked.elapsedMs)
        assertEquals(25_000L, ticked.remainingMs)
    }
}
