package com.dhammameditate.data.preferences

import android.content.Context
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.playback.TimerValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuickMeditateDraft(
    val introTrackIds: List<String> = emptyList(),
    val mainTrackIds: List<String> = emptyList(),
    val targetDurationMs: Long = 0L
) {
    val hasAnySelection: Boolean
        get() = introTrackIds.isNotEmpty() || mainTrackIds.isNotEmpty() || targetDurationMs > 0L

    fun reconcile(tracksById: Map<String, TrackEntity>): ResolvedQuickMeditateDraft {
        val resolvedIntro = introTrackIds.mapNotNull { tracksById[it] }
        val resolvedMain = mainTrackIds.mapNotNull { tracksById[it] }
        val droppedMissingTracks =
            resolvedIntro.size != introTrackIds.size || resolvedMain.size != mainTrackIds.size

        val mainTotalDurationMs = resolvedMain.sumOf { it.durationMs }
        val (reconciledTargetMs, wasCapped) = when {
            mainTotalDurationMs <= 0L -> 0L to false
            targetDurationMs <= 0L -> TimerValidation.defaultDurationMs(mainTotalDurationMs) to false
            targetDurationMs > mainTotalDurationMs -> mainTotalDurationMs to true
            else -> targetDurationMs to false
        }

        return ResolvedQuickMeditateDraft(
            introTracks = resolvedIntro,
            mainTracks = resolvedMain,
            targetDurationMs = reconciledTargetMs,
            wasCapped = wasCapped,
            droppedMissingTracks = droppedMissingTracks
        )
    }
}

data class ResolvedQuickMeditateDraft(
    val introTracks: List<TrackEntity>,
    val mainTracks: List<TrackEntity>,
    val targetDurationMs: Long,
    val wasCapped: Boolean,
    val droppedMissingTracks: Boolean
) {
    val mainTotalDurationMs: Long
        get() = mainTracks.sumOf { it.durationMs }

    fun toReconciledDraft(): QuickMeditateDraft = QuickMeditateDraft(
        introTrackIds = introTracks.map { it.id },
        mainTrackIds = mainTracks.map { it.id },
        targetDurationMs = targetDurationMs
    )
}

object QuickMeditateDraftCodec {
    private const val DELIMITER = ","

    fun encodeTrackIds(trackIds: List<String>): String =
        trackIds.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(DELIMITER)

    fun decodeTrackIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

class QuickMeditateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _draft = MutableStateFlow(readDraft())
    val draft: StateFlow<QuickMeditateDraft> = _draft.asStateFlow()

    fun saveDraft(draft: QuickMeditateDraft) {
        val sanitized = QuickMeditateDraft(
            introTrackIds = draft.introTrackIds.map { it.trim() }.filter { it.isNotEmpty() },
            mainTrackIds = draft.mainTrackIds.map { it.trim() }.filter { it.isNotEmpty() },
            targetDurationMs = draft.targetDurationMs.coerceAtLeast(0L)
        )
        if (_draft.value == sanitized) {
            return
        }
        prefs.edit()
            .putString(KEY_INTRO_IDS, QuickMeditateDraftCodec.encodeTrackIds(sanitized.introTrackIds))
            .putString(KEY_MAIN_IDS, QuickMeditateDraftCodec.encodeTrackIds(sanitized.mainTrackIds))
            .putLong(KEY_TARGET_DURATION_MS, sanitized.targetDurationMs)
            .apply()
        _draft.value = sanitized
    }

    fun clear() {
        val empty = QuickMeditateDraft()
        if (_draft.value == empty &&
            prefs.getString(KEY_INTRO_IDS, "").isNullOrEmpty() &&
            prefs.getString(KEY_MAIN_IDS, "").isNullOrEmpty() &&
            prefs.getLong(KEY_TARGET_DURATION_MS, 0L) == 0L
        ) {
            return
        }
        prefs.edit()
            .putString(KEY_INTRO_IDS, "")
            .putString(KEY_MAIN_IDS, "")
            .putLong(KEY_TARGET_DURATION_MS, 0L)
            .apply()
        _draft.value = empty
    }

    private fun readDraft(): QuickMeditateDraft = QuickMeditateDraft(
        introTrackIds = QuickMeditateDraftCodec.decodeTrackIds(prefs.getString(KEY_INTRO_IDS, "")),
        mainTrackIds = QuickMeditateDraftCodec.decodeTrackIds(prefs.getString(KEY_MAIN_IDS, "")),
        targetDurationMs = prefs.getLong(KEY_TARGET_DURATION_MS, 0L).coerceAtLeast(0L)
    )

    companion object {
        private const val PREFS_NAME = "dhamma_meditate_prefs"
        private const val KEY_INTRO_IDS = "quick_meditate_intro_ids"
        private const val KEY_MAIN_IDS = "quick_meditate_main_ids"
        private const val KEY_TARGET_DURATION_MS = "quick_meditate_target_duration_ms"
    }
}
