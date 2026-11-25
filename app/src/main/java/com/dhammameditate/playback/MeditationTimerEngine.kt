package com.dhammameditate.playback

enum class PlaybackStatus {
    IDLE,
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
 */
class MeditationTimerEngine(
    private val clockMs: () -> Long
) {
    private var status: PlaybackStatus = PlaybackStatus.IDLE
    private var targetDurationMs: Long = 0L
    private var accumulatedElapsedMs: Long = 0L
    private var segmentStartedAtMs: Long? = null

    fun currentStatus(): PlaybackStatus = status

    fun start(targetDurationMs: Long, nowMs: Long = clockMs()): TimerTick {
        require(targetDurationMs > 0L) { "targetDurationMs must be positive" }
        this.targetDurationMs = targetDurationMs
        this.accumulatedElapsedMs = 0L
        this.segmentStartedAtMs = nowMs
        val effectiveFade = FadeCurve.effectiveFadeMs(targetDurationMs)
        this.status = if (targetDurationMs <= effectiveFade) {
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
