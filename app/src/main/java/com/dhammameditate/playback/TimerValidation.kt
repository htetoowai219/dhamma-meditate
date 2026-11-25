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
    const val SCOPE_TRACK: String = "track length"
    const val SCOPE_MAIN_QUEUE: String = "Main Queue length"
    val PRESET_MINUTES: List<Int> = listOf(10, 15, 30, 45, 60)

    fun defaultDurationMs(maxDurationMs: Long): Long {
        if (maxDurationMs <= 0L) return 0L
        return minOf(DEFAULT_DURATION_MS, maxDurationMs)
    }

    fun isPresetEnabled(minutes: Int, maxDurationMs: Long): Boolean {
        if (minutes <= 0 || maxDurationMs <= 0L) return false
        return minutes.toLong() * 60_000L <= maxDurationMs
    }

    fun validateCustomMinutes(
        minutes: Int,
        maxDurationMs: Long,
        scopeLabel: String = SCOPE_TRACK
    ): CappedDurationResult {
        if (maxDurationMs <= 0L) {
            return CappedDurationResult(
                durationMs = 0L,
                wasCapped = false,
                noticeMessage = null
            )
        }
        val clampedMinutes = minutes.coerceAtLeast(1)
        val requestedMs = clampedMinutes.toLong() * 60_000L
        return capDurationMs(requestedMs, maxDurationMs, scopeLabel)
    }

    fun capDurationMs(
        requestedMs: Long,
        maxDurationMs: Long,
        scopeLabel: String = SCOPE_TRACK
    ): CappedDurationResult {
        if (maxDurationMs <= 0L) {
            return CappedDurationResult(0L, false, null)
        }
        val minBound = minOf(MIN_DURATION_MS, maxDurationMs)
        if (requestedMs > maxDurationMs) {
            return CappedDurationResult(
                durationMs = maxDurationMs,
                wasCapped = true,
                noticeMessage = "Adjusted to $scopeLabel (${formatDuration(maxDurationMs)})"
            )
        }
        return CappedDurationResult(
            durationMs = requestedMs.coerceAtLeast(minBound),
            wasCapped = false,
            noticeMessage = null
        )
    }

    fun reconcileTimerAfterMainChange(
        currentDurationMs: Long,
        previousMainTotalMs: Long,
        newMainTotalMs: Long
    ): CappedDurationResult {
        if (newMainTotalMs <= 0L) {
            return CappedDurationResult(
                durationMs = 0L,
                wasCapped = false,
                noticeMessage = null
            )
        }
        if (previousMainTotalMs <= 0L || currentDurationMs <= 0L) {
            return CappedDurationResult(
                durationMs = defaultDurationMs(newMainTotalMs),
                wasCapped = false,
                noticeMessage = null
            )
        }
        if (currentDurationMs > newMainTotalMs) {
            return CappedDurationResult(
                durationMs = newMainTotalMs,
                wasCapped = true,
                noticeMessage = "Adjusted to $SCOPE_MAIN_QUEUE (${formatDuration(newMainTotalMs)})"
            )
        }
        return CappedDurationResult(
            durationMs = currentDurationMs,
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
