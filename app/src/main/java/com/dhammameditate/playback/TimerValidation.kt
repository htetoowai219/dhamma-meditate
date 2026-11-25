package com.dhammameditate.playback

import java.util.Locale

data class CappedDurationResult(
    val durationMs: Long,
    val wasCapped: Boolean,
    val noticeMessage: String?
)

object TimerValidation {
    const val MIN_DURATION_MS: Long = 1_000L
    const val DEFAULT_DURATION_MS: Long = 30 * 60_000L
    val PRESET_MINUTES: List<Int> = listOf(10, 15, 30, 45, 60)

    fun defaultDurationMs(trackDurationMs: Long): Long {
        if (trackDurationMs <= 0L) return 0L
        return minOf(DEFAULT_DURATION_MS, trackDurationMs)
    }

    fun isPresetEnabled(minutes: Int, trackDurationMs: Long): Boolean {
        if (minutes <= 0 || trackDurationMs <= 0L) return false
        return minutes.toLong() * 60_000L <= trackDurationMs
    }

    fun validateCustomMinutes(minutes: Int, trackDurationMs: Long): CappedDurationResult {
        if (trackDurationMs <= 0L) {
            return CappedDurationResult(
                durationMs = 0L,
                wasCapped = false,
                noticeMessage = null
            )
        }
        val clampedMinutes = minutes.coerceAtLeast(1)
        val requestedMs = clampedMinutes.toLong() * 60_000L
        return capDurationMs(requestedMs, trackDurationMs)
    }

    fun capDurationMs(requestedMs: Long, trackDurationMs: Long): CappedDurationResult {
        if (trackDurationMs <= 0L) {
            return CappedDurationResult(0L, false, null)
        }
        val minBound = minOf(MIN_DURATION_MS, trackDurationMs)
        if (requestedMs > trackDurationMs) {
            return CappedDurationResult(
                durationMs = trackDurationMs,
                wasCapped = true,
                noticeMessage = "Adjusted to track length (${formatDuration(trackDurationMs)})"
            )
        }
        return CappedDurationResult(
            durationMs = requestedMs.coerceAtLeast(minBound),
            wasCapped = false,
            noticeMessage = null
        )
    }

    fun formatDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = when {
            safeMs == 0L -> 0L
            safeMs < 1_000L -> 1L
            else -> safeMs / 1_000L
        }
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
