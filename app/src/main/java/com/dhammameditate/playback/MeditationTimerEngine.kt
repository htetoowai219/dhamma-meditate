package com.dhammameditate.playback

enum class PlaybackStatus {
    IDLE,
    INTRO_PLAYING,
    INTRO_PAUSED,
    PLAYING,
    PAUSED,
    FADING,
    COMPLETED
}

data class TimerTick(
    val status: PlaybackStatus,
    val targetDurationMs: Long,
    val elapsedMs: Long,
    val remainingMs: Long,
    val volume: Float
)

/**
 * Pure monotonic meditation timer state machine.
 * Elapsed and remaining times are derived solely from monotonic clock deltas,
 * never from coroutine delay() tick counts.
 *
 * When a session includes an Intro Queue (`hasIntro = true`), the countdown stays
 * frozen in INTRO_PLAYING / INTRO_PAUSED until `onIntroComplete()` fires once at
 * the Intro -> Main boundary.
 */
class MeditationTimerEngine(
    private val clockMs: () -> Long
) {
    private var status: PlaybackStatus = PlaybackStatus.IDLE
    private var targetDurationMs: Long = 0L
    private var accumulatedElapsedMs: Long = 0L
    private var segmentStartedAtMs: Long? = null

    fun currentStatus(): PlaybackStatus = status

    fun start(
        targetDurationMs: Long,
        hasIntro: Boolean = false,
        nowMs: Long = clockMs()
    ): TimerTick {
        require(targetDurationMs > 0L) { "targetDurationMs must be positive" }
        this.targetDurationMs = targetDurationMs
        this.accumulatedElapsedMs = 0L
        if (hasIntro) {
            this.segmentStartedAtMs = null
            this.status = PlaybackStatus.INTRO_PLAYING
        } else {
            this.segmentStartedAtMs = nowMs
            val effectiveFade = FadeCurve.effectiveFadeMs(targetDurationMs)
            this.status = if (targetDurationMs <= effectiveFade) {
                PlaybackStatus.FADING
            } else {
                PlaybackStatus.PLAYING
            }
        }
        return tick(nowMs)
    }

    /**
     * One-shot transition from Intro Queue into Main Queue playback.
     * Calling this outside INTRO_PLAYING / INTRO_PAUSED is a safe no-op so
     * subsequent Main -> Main track transitions never re-anchor the timer.
     */
    fun onIntroComplete(nowMs: Long = clockMs()): TimerTick {
        return when (status) {
            PlaybackStatus.INTRO_PLAYING -> {
                accumulatedElapsedMs = 0L
                segmentStartedAtMs = nowMs
                val effectiveFade = FadeCurve.effectiveFadeMs(targetDurationMs)
                status = if (targetDurationMs <= effectiveFade) {
                    PlaybackStatus.FADING
                } else {
                    PlaybackStatus.PLAYING
                }
                tick(nowMs)
            }

            PlaybackStatus.INTRO_PAUSED -> {
                accumulatedElapsedMs = 0L
                segmentStartedAtMs = null
                status = PlaybackStatus.PAUSED
                tick(nowMs)
            }

            else -> tick(nowMs)
        }
    }

    /**
     * Explicit in-app skip past all remaining Intro tracks directly into Main Queue playback.
     * Unlike [onIntroComplete], this immediately starts Main playback and countdown from both
     * INTRO_PLAYING and INTRO_PAUSED. Calling this in any non-Intro state is a safe no-op.
     */
    fun skipIntroToMain(nowMs: Long = clockMs()): TimerTick {
        if (status != PlaybackStatus.INTRO_PLAYING && status != PlaybackStatus.INTRO_PAUSED) {
            return tick(nowMs)
        }
        accumulatedElapsedMs = 0L
        segmentStartedAtMs = nowMs
        val effectiveFade = FadeCurve.effectiveFadeMs(targetDurationMs)
        status = if (targetDurationMs <= effectiveFade) {
            PlaybackStatus.FADING
        } else {
            PlaybackStatus.PLAYING
        }
        return tick(nowMs)
    }

    fun tick(nowMs: Long = clockMs()): TimerTick {
        return when (status) {
            PlaybackStatus.IDLE -> TimerTick(
                status = PlaybackStatus.IDLE,
                targetDurationMs = targetDurationMs,
                elapsedMs = 0L,
                remainingMs = targetDurationMs,
                volume = 1.0f
            )

            PlaybackStatus.INTRO_PLAYING -> TimerTick(
                status = PlaybackStatus.INTRO_PLAYING,
                targetDurationMs = targetDurationMs,
                elapsedMs = 0L,
                remainingMs = targetDurationMs,
                volume = 1.0f
            )

            PlaybackStatus.INTRO_PAUSED -> TimerTick(
                status = PlaybackStatus.INTRO_PAUSED,
                targetDurationMs = targetDurationMs,
                elapsedMs = 0L,
                remainingMs = targetDurationMs,
                volume = 1.0f
            )

            PlaybackStatus.COMPLETED -> TimerTick(
                status = PlaybackStatus.COMPLETED,
                targetDurationMs = targetDurationMs,
                elapsedMs = targetDurationMs,
                remainingMs = 0L,
                volume = 0.0f
            )

            PlaybackStatus.PAUSED -> {
                val elapsed = accumulatedElapsedMs.coerceIn(0L, targetDurationMs)
                val remaining = (targetDurationMs - elapsed).coerceAtLeast(0L)
                val volume = FadeCurve.gain(remaining, targetDurationMs)
                TimerTick(
                    status = PlaybackStatus.PAUSED,
                    targetDurationMs = targetDurationMs,
                    elapsedMs = elapsed,
                    remainingMs = remaining,
                    volume = volume
                )
            }

            PlaybackStatus.PLAYING, PlaybackStatus.FADING -> {
                val startedAt = segmentStartedAtMs ?: nowMs
                val segmentDelta = (nowMs - startedAt).coerceAtLeast(0L)
                val elapsed = (accumulatedElapsedMs + segmentDelta).coerceAtMost(targetDurationMs)
                val remaining = (targetDurationMs - elapsed).coerceAtLeast(0L)

                if (remaining == 0L) {
                    accumulatedElapsedMs = targetDurationMs
                    segmentStartedAtMs = null
                    status = PlaybackStatus.COMPLETED
                    return TimerTick(
                        status = PlaybackStatus.COMPLETED,
                        targetDurationMs = targetDurationMs,
                        elapsedMs = targetDurationMs,
                        remainingMs = 0L,
                        volume = 0.0f
                    )
                }

                val effectiveFade = FadeCurve.effectiveFadeMs(targetDurationMs)
                status = if (remaining <= effectiveFade) {
                    PlaybackStatus.FADING
                } else {
                    PlaybackStatus.PLAYING
                }
                val volume = if (status == PlaybackStatus.FADING) {
                    FadeCurve.gain(remaining, targetDurationMs)
                } else {
                    1.0f
                }

                TimerTick(
                    status = status,
                    targetDurationMs = targetDurationMs,
                    elapsedMs = elapsed,
                    remainingMs = remaining,
                    volume = volume
                )
            }
        }
    }

    fun pause(nowMs: Long = clockMs()): TimerTick {
        if (status == PlaybackStatus.INTRO_PLAYING) {
            segmentStartedAtMs = null
            status = PlaybackStatus.INTRO_PAUSED
            return tick(nowMs)
        }
        if (status != PlaybackStatus.PLAYING && status != PlaybackStatus.FADING) {
            return tick(nowMs)
        }
        val current = tick(nowMs)
        if (current.status == PlaybackStatus.COMPLETED) {
            return current
        }
        accumulatedElapsedMs = current.elapsedMs
        segmentStartedAtMs = null
        status = PlaybackStatus.PAUSED
        return tick(nowMs)
    }

    fun resume(nowMs: Long = clockMs()): TimerTick {
        if (status == PlaybackStatus.INTRO_PAUSED) {
            segmentStartedAtMs = null
            status = PlaybackStatus.INTRO_PLAYING
            return tick(nowMs)
        }
        if (status != PlaybackStatus.PAUSED) {
            return tick(nowMs)
        }
        val remaining = (targetDurationMs - accumulatedElapsedMs).coerceAtLeast(0L)
        if (remaining <= 0L) {
            accumulatedElapsedMs = targetDurationMs
            segmentStartedAtMs = null
            status = PlaybackStatus.COMPLETED
            return tick(nowMs)
        }
        segmentStartedAtMs = nowMs
        val effectiveFade = FadeCurve.effectiveFadeMs(targetDurationMs)
        status = if (remaining <= effectiveFade) {
            PlaybackStatus.FADING
        } else {
            PlaybackStatus.PLAYING
        }
        return tick(nowMs)
    }

    fun stopAndReset(): TimerTick {
        accumulatedElapsedMs = 0L
        segmentStartedAtMs = null
        status = PlaybackStatus.IDLE
        return TimerTick(
            status = PlaybackStatus.IDLE,
            targetDurationMs = targetDurationMs,
            elapsedMs = 0L,
            remainingMs = targetDurationMs,
            volume = 1.0f
        )
    }

    fun completeEarly(): TimerTick {
        accumulatedElapsedMs = targetDurationMs
        segmentStartedAtMs = null
        status = PlaybackStatus.COMPLETED
        return TimerTick(
            status = PlaybackStatus.COMPLETED,
            targetDurationMs = targetDurationMs,
            elapsedMs = targetDurationMs,
            remainingMs = 0L,
            volume = 0.0f
        )
    }
}
