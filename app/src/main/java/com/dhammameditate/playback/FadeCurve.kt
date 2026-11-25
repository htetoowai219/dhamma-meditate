package com.dhammameditate.playback

import kotlin.math.exp

object FadeCurve {
    const val FADE_DURATION_MS: Long = 10_000L
    const val DECAY_K: Double = 3.5

    private val EXP_NEG_K: Double = exp(-DECAY_K)
    private val DENOMINATOR: Double = 1.0 - EXP_NEG_K

    fun effectiveFadeMs(targetDurationMs: Long): Long =
        minOf(FADE_DURATION_MS, targetDurationMs.coerceAtLeast(0L))

    fun gainForProgress(progress: Double): Float {
        val p = progress.coerceIn(0.0, 1.0)
        if (p <= 0.0) return 1.0f
        if (p >= 1.0) return 0.0f
        val raw = (exp(-DECAY_K * p) - EXP_NEG_K) / DENOMINATOR
        return raw.toFloat().coerceIn(0.0f, 1.0f)
    }

    fun gain(remainingMs: Long, targetDurationMs: Long): Float {
        val effectiveFade = effectiveFadeMs(targetDurationMs)
        if (effectiveFade <= 0L || remainingMs <= 0L) return 0.0f
        if (remainingMs >= effectiveFade) return 1.0f
        val progress = (effectiveFade - remainingMs).toDouble() / effectiveFade.toDouble()
        return gainForProgress(progress)
    }
}
