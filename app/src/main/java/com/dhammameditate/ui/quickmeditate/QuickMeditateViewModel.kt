package com.dhammameditate.ui.quickmeditate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhammameditate.R
import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.preferences.QuickMeditateDraft
import com.dhammameditate.data.preferences.QuickMeditateStore
import com.dhammameditate.data.repository.TrackRepository
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.TimerCapNotice
import com.dhammameditate.playback.TimerCapScope
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.UiText
import com.dhammameditate.ui.common.toUiText
import com.dhammameditate.ui.editor.EditorQueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class QuickMeditateUiState(
    val isLoading: Boolean = true,
    val introItems: List<EditorQueueItem> = emptyList(),
    val mainItems: List<EditorQueueItem> = emptyList(),
    val libraryTracks: List<TrackEntity> = emptyList(),
    val targetDurationMs: Long = 0L,
    val isFullSelected: Boolean = false,
    val customMinutesInput: String = "",
    val capNotice: UiText? = null,
    val isQuickMeditateActive: Boolean = false
) {
    val introTotalDurationMs: Long get() = introItems.sumOf { it.track.durationMs }
    val mainTotalDurationMs: Long get() = mainItems.sumOf { it.track.durationMs }
    val hasInvalidCustomInput: Boolean
        get() = customMinutesInput.isNotEmpty() && (customMinutesInput.toIntOrNull() ?: 0) < 1

    val hasAnySelection: Boolean
        get() = introItems.isNotEmpty() ||
            mainItems.isNotEmpty() ||
            targetDurationMs > 0L ||
            customMinutesInput.isNotEmpty()

    val canStart: Boolean
        get() = !isLoading &&
            mainItems.isNotEmpty() &&
            mainTotalDurationMs > 0L &&
            targetDurationMs in 1L..mainTotalDurationMs &&
            !hasInvalidCustomInput
}

private data class QuickMeditateDraftState(
    val isLoading: Boolean = true,
    val introItems: List<EditorQueueItem> = emptyList(),
    val mainItems: List<EditorQueueItem> = emptyList(),
    val targetDurationMs: Long = 0L,
    val isFullSelected: Boolean = false,
    val customMinutesInput: String = "",
    val capNotice: UiText? = null
)

class QuickMeditateViewModel(
    private val quickMeditateStore: QuickMeditateStore,
    private val trackRepository: TrackRepository,
    private val playbackClient: PlaybackClient
) : ViewModel() {

    private val draftState = MutableStateFlow(QuickMeditateDraftState(isLoading = true))

    val uiState: StateFlow<QuickMeditateUiState> = combine(
        draftState,
        trackRepository.observeTracks(),
        playbackClient.state
    ) { draft, libraryTracks, playback ->
        val liveTracksById = libraryTracks.associateBy { it.id }
        val liveIntroItems = draft.introItems.mapNotNull { item ->
            liveTracksById[item.track.id]?.let { latest ->
                if (latest != item.track) item.copy(track = latest) else item
            }
        }
        val liveMainItems = draft.mainItems.mapNotNull { item ->
            liveTracksById[item.track.id]?.let { latest ->
                if (latest != item.track) item.copy(track = latest) else item
            }
        }
        val liveMainTotal = liveMainItems.sumOf { it.track.durationMs }
        val safeTargetDurationMs = when {
            liveMainTotal <= 0L -> 0L
            draft.targetDurationMs > liveMainTotal -> liveMainTotal
            else -> draft.targetDurationMs
        }
        val safeIsFull = liveMainTotal > 0L &&
            (draft.isFullSelected || draft.targetDurationMs > liveMainTotal) &&
            safeTargetDurationMs == liveMainTotal

        QuickMeditateUiState(
            isLoading = draft.isLoading,
            introItems = liveIntroItems,
            mainItems = liveMainItems,
            libraryTracks = libraryTracks,
            targetDurationMs = safeTargetDurationMs,
            isFullSelected = safeIsFull,
            customMinutesInput = if (liveMainTotal <= 0L) "" else draft.customMinutesInput,
            capNotice = if (liveMainTotal <= 0L) null else draft.capNotice,
            isQuickMeditateActive = playback.isActive &&
                playback.activeSessionId == PlaybackClient.QUICK_MEDITATE_SESSION_ID
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = QuickMeditateUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            trackRepository.observeTracks().collect { tracks ->
                val tracksById = tracks.associateBy { it.id }
                if (draftState.value.isLoading) {
                    val storedDraft = quickMeditateStore.draft.value
                    val resolved = storedDraft.reconcile(tracksById)

                    if (resolved.droppedMissingTracks || resolved.wasCapped || resolved.targetDurationMs != storedDraft.targetDurationMs) {
                        quickMeditateStore.saveDraft(resolved.toReconciledDraft())
                    }

                    val mainTotal = resolved.mainTotalDurationMs
                    val safeTarget = resolved.targetDurationMs
                    val matchesPreset = TimerValidation.PRESET_MINUTES.any {
                        it.toLong() * 60_000L == safeTarget
                    }
                    val isFull = mainTotal > 0L && !matchesPreset && safeTarget == mainTotal
                    val customMinutes = if (
                        safeTarget > 0L &&
                        !matchesPreset &&
                        !isFull &&
                        safeTarget % 60_000L == 0L
                    ) {
                        (safeTarget / 60_000L).toString()
                    } else {
                        ""
                    }
                    val initialCapNotice = if (resolved.wasCapped && mainTotal > 0L) {
                        TimerCapNotice(
                            scope = TimerCapScope.MAIN_QUEUE,
                            cappedDurationMs = mainTotal
                        ).toUiText()
                    } else {
                        null
                    }

                    draftState.value = QuickMeditateDraftState(
                        isLoading = false,
                        introItems = resolved.introTracks.map { track ->
                            EditorQueueItem(
                                localKey = UUID.randomUUID().toString(),
                                track = track
                            )
                        },
                        mainItems = resolved.mainTracks.map { track ->
                            EditorQueueItem(
                                localKey = UUID.randomUUID().toString(),
                                track = track
                            )
                        },
                        targetDurationMs = safeTarget,
                        isFullSelected = isFull,
                        customMinutesInput = customMinutes,
                        capNotice = initialCapNotice
                    )
                } else {
                    var persistedDraftToSave: QuickMeditateDraft? = null
                    draftState.update { current ->
                        val nextIntro = current.introItems.mapNotNull { item ->
                            tracksById[item.track.id]?.let { latest ->
                                if (latest != item.track) item.copy(track = latest) else item
                            }
                        }
                        val nextMain = current.mainItems.mapNotNull { item ->
                            tracksById[item.track.id]?.let { latest ->
                                if (latest != item.track) item.copy(track = latest) else item
                            }
                        }
                        val introDropped = nextIntro.size != current.introItems.size
                        val mainDropped = nextMain.size != current.mainItems.size
                        val previousMainTotal = current.mainItems.sumOf { it.track.durationMs }
                        val newMainTotal = nextMain.sumOf { it.track.durationMs }

                        if (!introDropped && !mainDropped && previousMainTotal == newMainTotal) {
                            if (nextIntro == current.introItems && nextMain == current.mainItems) {
                                current
                            } else {
                                current.copy(introItems = nextIntro, mainItems = nextMain)
                            }
                        } else {
                            val reconciled = TimerValidation.reconcileTimerAfterMainChange(
                                currentDurationMs = current.targetDurationMs,
                                previousMainTotalMs = previousMainTotal,
                                newMainTotalMs = newMainTotal
                            )
                            val nextIsFull = when {
                                newMainTotal <= 0L -> false
                                reconciled.wasCapped -> true
                                else -> current.isFullSelected && reconciled.durationMs == newMainTotal
                            }
                            val nextCustomInput = when {
                                newMainTotal <= 0L || reconciled.wasCapped -> ""
                                else -> current.customMinutesInput
                            }
                            val nextCapNotice = when {
                                newMainTotal <= 0L -> null
                                reconciled.wasCapped -> reconciled.capNotice?.toUiText()
                                else -> current.capNotice
                            }
                            persistedDraftToSave = QuickMeditateDraft(
                                introTrackIds = nextIntro.map { it.track.id },
                                mainTrackIds = nextMain.map { it.track.id },
                                targetDurationMs = reconciled.durationMs
                            )
                            current.copy(
                                introItems = nextIntro,
                                mainItems = nextMain,
                                targetDurationMs = reconciled.durationMs,
                                isFullSelected = nextIsFull,
                                customMinutesInput = nextCustomInput,
                                capNotice = nextCapNotice
                            )
                        }
                    }
                    persistedDraftToSave?.let { quickMeditateStore.saveDraft(it) }
                }
            }
        }
    }

    fun addTrack(zone: QueueZone, track: TrackEntity) {
        val entry = EditorQueueItem(
            localKey = UUID.randomUUID().toString(),
            track = track
        )
        updateAndPersist { state ->
            when (zone) {
                QueueZone.INTRO -> state.copy(introItems = state.introItems + entry)

                QueueZone.MAIN -> {
                    val previousMainTotal = state.mainItems.sumOf { it.track.durationMs }
                    val updatedMain = state.mainItems + entry
                    val newMainTotal = updatedMain.sumOf { it.track.durationMs }
                    val reconciled = TimerValidation.reconcileTimerAfterMainChange(
                        currentDurationMs = state.targetDurationMs,
                        previousMainTotalMs = previousMainTotal,
                        newMainTotalMs = newMainTotal
                    )
                    if (previousMainTotal <= 0L) {
                        val matchesPreset = TimerValidation.PRESET_MINUTES.any {
                            it.toLong() * 60_000L == reconciled.durationMs
                        }
                        state.copy(
                            mainItems = updatedMain,
                            targetDurationMs = reconciled.durationMs,
                            isFullSelected = !matchesPreset && reconciled.durationMs == newMainTotal,
                            customMinutesInput = "",
                            capNotice = null
                        )
                    } else if (state.customMinutesInput.isNotEmpty()) {
                        val parsed = state.customMinutesInput.toIntOrNull() ?: 0
                        if (parsed < 1) {
                            state.copy(
                                mainItems = updatedMain,
                                targetDurationMs = reconciled.durationMs,
                                isFullSelected = state.isFullSelected && reconciled.durationMs == newMainTotal,
                                customMinutesInput = state.customMinutesInput,
                                capNotice = UiText.Resource(R.string.timer_error_min_one_minute)
                            )
                        } else {
                            val capped = TimerValidation.validateCustomMinutes(
                                minutes = parsed,
                                maxDurationMs = newMainTotal,
                                scopeLabel = TimerValidation.SCOPE_MAIN_QUEUE
                            )
                            state.copy(
                                mainItems = updatedMain,
                                targetDurationMs = capped.durationMs,
                                isFullSelected = capped.wasCapped,
                                customMinutesInput = state.customMinutesInput,
                                capNotice = capped.capNotice?.toUiText()
                            )
                        }
                    } else {
                        state.copy(
                            mainItems = updatedMain,
                            targetDurationMs = reconciled.durationMs,
                            isFullSelected = state.isFullSelected && reconciled.durationMs == newMainTotal,
                            customMinutesInput = "",
                            capNotice = null
                        )
                    }
                }
            }
        }
    }

    fun moveTrackUp(zone: QueueZone, index: Int) {
        updateAndPersist { state ->
            when (zone) {
                QueueZone.INTRO -> {
                    if (index <= 0 || index >= state.introItems.size) return@updateAndPersist state
                    state.copy(introItems = state.introItems.swapped(index, index - 1))
                }

                QueueZone.MAIN -> {
                    if (index <= 0 || index >= state.mainItems.size) return@updateAndPersist state
                    state.copy(mainItems = state.mainItems.swapped(index, index - 1))
                }
            }
        }
    }

    fun moveTrackDown(zone: QueueZone, index: Int) {
        updateAndPersist { state ->
            when (zone) {
                QueueZone.INTRO -> {
                    if (index < 0 || index >= state.introItems.lastIndex) return@updateAndPersist state
                    state.copy(introItems = state.introItems.swapped(index, index + 1))
                }

                QueueZone.MAIN -> {
                    if (index < 0 || index >= state.mainItems.lastIndex) return@updateAndPersist state
                    state.copy(mainItems = state.mainItems.swapped(index, index + 1))
                }
            }
        }
    }

    fun removeTrack(zone: QueueZone, index: Int) {
        updateAndPersist { state ->
            when (zone) {
                QueueZone.INTRO -> {
                    if (index !in state.introItems.indices) return@updateAndPersist state
                    val updated = state.introItems.toMutableList().apply { removeAt(index) }
                    state.copy(introItems = updated)
                }

                QueueZone.MAIN -> {
                    if (index !in state.mainItems.indices) return@updateAndPersist state
                    val previousMainTotal = state.mainItems.sumOf { it.track.durationMs }
                    val updatedMain = state.mainItems.toMutableList().apply { removeAt(index) }
                    val newMainTotal = updatedMain.sumOf { it.track.durationMs }
                    val reconciled = TimerValidation.reconcileTimerAfterMainChange(
                        currentDurationMs = state.targetDurationMs,
                        previousMainTotalMs = previousMainTotal,
                        newMainTotalMs = newMainTotal
                    )
                    val nextIsFull = when {
                        newMainTotal <= 0L -> false
                        reconciled.wasCapped -> true
                        else -> state.isFullSelected && reconciled.durationMs == newMainTotal
                    }
                    val nextCustomInput = when {
                        newMainTotal <= 0L || reconciled.wasCapped -> ""
                        else -> state.customMinutesInput
                    }
                    val hasPreservedInvalidCustomInput = newMainTotal > 0L &&
                        !reconciled.wasCapped &&
                        nextCustomInput.isNotEmpty() &&
                        (nextCustomInput.toIntOrNull() ?: 0) < 1
                    val nextCapNotice = when {
                        hasPreservedInvalidCustomInput -> UiText.Resource(R.string.timer_error_min_one_minute)
                        else -> reconciled.capNotice?.toUiText()
                    }
                    state.copy(
                        mainItems = updatedMain,
                        targetDurationMs = reconciled.durationMs,
                        isFullSelected = nextIsFull,
                        customMinutesInput = nextCustomInput,
                        capNotice = nextCapNotice
                    )
                }
            }
        }
    }

    fun selectPresetMinutes(minutes: Int) {
        val state = draftState.value
        val mainTotal = state.mainItems.sumOf { it.track.durationMs }
        if (!TimerValidation.isPresetEnabled(minutes, mainTotal)) return

        updateAndPersist {
            it.copy(
                targetDurationMs = minutes.toLong() * 60_000L,
                isFullSelected = false,
                customMinutesInput = "",
                capNotice = null
            )
        }
    }

    fun selectFullMainQueue() {
        val state = draftState.value
        val mainTotal = state.mainItems.sumOf { it.track.durationMs }
        if (mainTotal <= 0L) return

        updateAndPersist {
            it.copy(
                targetDurationMs = mainTotal,
                isFullSelected = true,
                customMinutesInput = "",
                capNotice = null
            )
        }
    }

    fun onCustomMinutesChanged(rawInput: String) {
        val state = draftState.value
        val mainTotal = state.mainItems.sumOf { it.track.durationMs }
        if (mainTotal <= 0L) return

        val digitsOnly = rawInput.filter { it in '0'..'9' }.take(4)
        if (digitsOnly.isEmpty()) {
            draftState.update {
                it.copy(
                    customMinutesInput = "",
                    capNotice = null
                )
            }
            return
        }

        val parsed = digitsOnly.toIntOrNull() ?: return
        if (parsed < 1) {
            draftState.update {
                it.copy(
                    customMinutesInput = digitsOnly,
                    capNotice = UiText.Resource(R.string.timer_error_min_one_minute)
                )
            }
            return
        }

        val capped = TimerValidation.validateCustomMinutes(
            minutes = parsed,
            maxDurationMs = mainTotal,
            scopeLabel = TimerValidation.SCOPE_MAIN_QUEUE
        )
        updateAndPersist {
            it.copy(
                targetDurationMs = capped.durationMs,
                isFullSelected = capped.wasCapped,
                customMinutesInput = digitsOnly,
                capNotice = capped.capNotice?.toUiText()
            )
        }
    }

    fun resetAll() {
        draftState.value = QuickMeditateDraftState(isLoading = false)
        quickMeditateStore.clear()
    }

    fun startQuickMeditate(onStarted: () -> Unit) {
        val current = uiState.value
        if (!current.canStart) return

        val capped = TimerValidation.capDurationMs(
            requestedMs = current.targetDurationMs,
            maxDurationMs = current.mainTotalDurationMs,
            scopeLabel = TimerValidation.SCOPE_MAIN_QUEUE
        )
        if (capped.durationMs <= 0L) {
            draftState.update { it.copy(capNotice = UiText.Resource(R.string.timer_error_invalid_duration)) }
            return
        }

        val introIds = current.introItems.map { it.track.id }
        val mainIds = current.mainItems.map { it.track.id }
        quickMeditateStore.saveDraft(
            QuickMeditateDraft(
                introTrackIds = introIds,
                mainTrackIds = mainIds,
                targetDurationMs = capped.durationMs
            )
        )
        playbackClient.startQuickMeditate(
            introTrackIds = introIds,
            mainTrackIds = mainIds,
            targetDurationMs = capped.durationMs
        )
        onStarted()
    }

    private fun updateAndPersist(transform: (QuickMeditateDraftState) -> QuickMeditateDraftState) {
        var updated: QuickMeditateDraftState? = null
        draftState.update { current ->
            transform(current).also { updated = it }
        }
        val next = updated ?: return
        if (!next.isLoading) {
            quickMeditateStore.saveDraft(
                QuickMeditateDraft(
                    introTrackIds = next.introItems.map { it.track.id },
                    mainTrackIds = next.mainItems.map { it.track.id },
                    targetDurationMs = next.targetDurationMs
                )
            )
        }
    }

    private fun <T> List<T>.swapped(firstIndex: Int, secondIndex: Int): List<T> {
        val copy = toMutableList()
        val temp = copy[firstIndex]
        copy[firstIndex] = copy[secondIndex]
        copy[secondIndex] = temp
        return copy
    }

    companion object {
        fun factory(
            quickMeditateStore: QuickMeditateStore,
            trackRepository: TrackRepository,
            playbackClient: PlaybackClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QuickMeditateViewModel(
                    quickMeditateStore = quickMeditateStore,
                    trackRepository = trackRepository,
                    playbackClient = playbackClient
                ) as T
            }
        }
    }
}
