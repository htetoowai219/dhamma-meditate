package com.dhammameditate.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.repository.SessionDraft
import com.dhammameditate.data.repository.SessionRepository
import com.dhammameditate.data.repository.TrackRepository
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.TimerValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorQueueItem(
    val localKey: String,
    val track: TrackEntity
)

data class SessionEditorUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val sessionNotFound: Boolean = false,
    val name: String = "",
    val nameTouched: Boolean = false,
    val introItems: List<EditorQueueItem> = emptyList(),
    val mainItems: List<EditorQueueItem> = emptyList(),
    val libraryTracks: List<TrackEntity> = emptyList(),
    val targetDurationMs: Long = 0L,
    val isFullSelected: Boolean = false,
    val customMinutesInput: String = "",
    val capNotice: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false,
    val isEditingActiveSession: Boolean = false
) {
    val introTotalDurationMs: Long get() = introItems.sumOf { it.track.durationMs }
    val mainTotalDurationMs: Long get() = mainItems.sumOf { it.track.durationMs }
    val showNameError: Boolean get() = nameTouched && name.isBlank()
    val hasInvalidCustomInput: Boolean
        get() = customMinutesInput.isNotEmpty() && (customMinutesInput.toIntOrNull() ?: 0) < 1

    val canSave: Boolean
        get() = !isLoading &&
            !sessionNotFound &&
            !isSaving &&
            name.isNotBlank() &&
            mainItems.isNotEmpty() &&
            mainTotalDurationMs > 0L &&
            targetDurationMs in 1L..mainTotalDurationMs &&
            !hasInvalidCustomInput
}

private data class EditorDraftState(
    val isLoading: Boolean = true,
    val sessionNotFound: Boolean = false,
    val name: String = "",
    val nameTouched: Boolean = false,
    val introItems: List<EditorQueueItem> = emptyList(),
    val mainItems: List<EditorQueueItem> = emptyList(),
    val targetDurationMs: Long = 0L,
    val isFullSelected: Boolean = false,
    val customMinutesInput: String = "",
    val capNotice: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false
)

class SessionEditorViewModel(
    private val sessionId: String?,
    private val sessionRepository: SessionRepository,
    private val trackRepository: TrackRepository,
    private val playbackClient: PlaybackClient
) : ViewModel() {

    private val draftState = MutableStateFlow(EditorDraftState(isLoading = sessionId != null))

    val uiState: StateFlow<SessionEditorUiState> = combine(
        draftState,
        trackRepository.observeTracks(),
        playbackClient.state
    ) { draft, libraryTracks, playback ->
        val liveTracksById = libraryTracks.associateBy { it.id }
        val liveIntroItems = draft.introItems.map { item ->
            val latest = liveTracksById[item.track.id]
            if (latest != null && latest != item.track) item.copy(track = latest) else item
        }
        val liveMainItems = draft.mainItems.map { item ->
            val latest = liveTracksById[item.track.id]
            if (latest != null && latest != item.track) item.copy(track = latest) else item
        }
        SessionEditorUiState(
            isEditMode = sessionId != null,
            isLoading = draft.isLoading,
            sessionNotFound = draft.sessionNotFound,
            name = draft.name,
            nameTouched = draft.nameTouched,
            introItems = liveIntroItems,
            mainItems = liveMainItems,
            libraryTracks = libraryTracks,
            targetDurationMs = draft.targetDurationMs,
            isFullSelected = draft.isFullSelected,
            customMinutesInput = draft.customMinutesInput,
            capNotice = draft.capNotice,
            saveError = draft.saveError,
            isSaving = draft.isSaving,
            isEditingActiveSession = sessionId != null &&
                playback.isActive &&
                playback.activeSessionId == sessionId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SessionEditorUiState(
            isEditMode = sessionId != null,
            isLoading = sessionId != null
        )
    )

    init {
        if (sessionId != null) {
            viewModelScope.launch {
                val existing = sessionRepository.getSession(sessionId)
                if (existing == null) {
                    draftState.value = EditorDraftState(
                        isLoading = false,
                        sessionNotFound = true
                    )
                    return@launch
                }
                val mainTotal = existing.mainTotalDurationMs
                val safeTarget = if (mainTotal <= 0L) {
                    0L
                } else {
                    existing.targetDurationMs.coerceIn(0L, mainTotal)
                }
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

                draftState.value = EditorDraftState(
                    isLoading = false,
                    sessionNotFound = false,
                    name = existing.name,
                    nameTouched = false,
                    introItems = existing.introItems.map { item ->
                        EditorQueueItem(
                            localKey = item.entryId,
                            track = item.track
                        )
                    },
                    mainItems = existing.mainItems.map { item ->
                        EditorQueueItem(
                            localKey = item.entryId,
                            track = item.track
                        )
                    },
                    targetDurationMs = safeTarget,
                    isFullSelected = isFull,
                    customMinutesInput = customMinutes,
                    capNotice = null
                )
            }
        }
    }

    fun onNameChanged(newName: String) {
        draftState.update { state ->
            state.copy(
                name = newName,
                nameTouched = true,
                saveError = null
            )
        }
    }

    fun addTrack(zone: QueueZone, track: TrackEntity) {
        val entry = EditorQueueItem(
            localKey = UUID.randomUUID().toString(),
            track = track
        )
        draftState.update { state ->
            when (zone) {
                QueueZone.INTRO -> state.copy(
                    introItems = state.introItems + entry,
                    saveError = null
                )

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
                            capNotice = null,
                            saveError = null
                        )
                    } else if (state.customMinutesInput.isNotEmpty()) {
                        val parsed = state.customMinutesInput.toIntOrNull() ?: 0
                        if (parsed < 1) {
                            state.copy(
                                mainItems = updatedMain,
                                targetDurationMs = reconciled.durationMs,
                                isFullSelected = state.isFullSelected && reconciled.durationMs == newMainTotal,
                                customMinutesInput = state.customMinutesInput,
                                capNotice = "Enter at least 1 minute",
                                saveError = null
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
                                capNotice = capped.noticeMessage,
                                saveError = null
                            )
                        }
                    } else {
                        state.copy(
                            mainItems = updatedMain,
                            targetDurationMs = reconciled.durationMs,
                            isFullSelected = state.isFullSelected && reconciled.durationMs == newMainTotal,
                            customMinutesInput = "",
                            capNotice = null,
                            saveError = null
                        )
                    }
                }
            }
        }
    }

    fun moveTrackUp(zone: QueueZone, index: Int) {
        draftState.update { state ->
            when (zone) {
                QueueZone.INTRO -> {
                    if (index <= 0 || index >= state.introItems.size) return@update state
                    state.copy(introItems = state.introItems.swapped(index, index - 1))
                }

                QueueZone.MAIN -> {
                    if (index <= 0 || index >= state.mainItems.size) return@update state
                    state.copy(mainItems = state.mainItems.swapped(index, index - 1))
                }
            }
        }
    }

    fun moveTrackDown(zone: QueueZone, index: Int) {
        draftState.update { state ->
            when (zone) {
                QueueZone.INTRO -> {
                    if (index < 0 || index >= state.introItems.lastIndex) return@update state
                    state.copy(introItems = state.introItems.swapped(index, index + 1))
                }

                QueueZone.MAIN -> {
                    if (index < 0 || index >= state.mainItems.lastIndex) return@update state
                    state.copy(mainItems = state.mainItems.swapped(index, index + 1))
                }
            }
        }
    }

    fun removeTrack(zone: QueueZone, index: Int) {
        draftState.update { state ->
            when (zone) {
                QueueZone.INTRO -> {
                    if (index !in state.introItems.indices) return@update state
                    val updated = state.introItems.toMutableList().apply { removeAt(index) }
                    state.copy(introItems = updated)
                }

                QueueZone.MAIN -> {
                    if (index !in state.mainItems.indices) return@update state
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
                        hasPreservedInvalidCustomInput -> "Enter at least 1 minute"
                        else -> reconciled.noticeMessage
                    }
                    state.copy(
                        mainItems = updatedMain,
                        targetDurationMs = reconciled.durationMs,
                        isFullSelected = nextIsFull,
                        customMinutesInput = nextCustomInput,
                        capNotice = nextCapNotice,
                        saveError = null
                    )
                }
            }
        }
    }

    fun selectPresetMinutes(minutes: Int) {
        val state = draftState.value
        val mainTotal = state.mainItems.sumOf { it.track.durationMs }
        if (!TimerValidation.isPresetEnabled(minutes, mainTotal)) return

        draftState.update {
            it.copy(
                targetDurationMs = minutes.toLong() * 60_000L,
                isFullSelected = false,
                customMinutesInput = "",
                capNotice = null,
                saveError = null
            )
        }
    }

    fun selectFullMainQueue() {
        val state = draftState.value
        val mainTotal = state.mainItems.sumOf { it.track.durationMs }
        if (mainTotal <= 0L) return

        draftState.update {
            it.copy(
                targetDurationMs = mainTotal,
                isFullSelected = true,
                customMinutesInput = "",
                capNotice = null,
                saveError = null
            )
        }
    }

    fun onCustomMinutesChanged(rawInput: String) {
        val state = draftState.value
        val mainTotal = state.mainItems.sumOf { it.track.durationMs }
        if (mainTotal <= 0L) return

        val digitsOnly = rawInput.filter { it.isDigit() }.take(4)
        if (digitsOnly.isEmpty()) {
            draftState.update {
                it.copy(
                    customMinutesInput = "",
                    capNotice = null,
                    saveError = null
                )
            }
            return
        }

        val parsed = digitsOnly.toIntOrNull() ?: return
        if (parsed < 1) {
            draftState.update {
                it.copy(
                    customMinutesInput = digitsOnly,
                    capNotice = "Enter at least 1 minute",
                    saveError = null
                )
            }
            return
        }

        val capped = TimerValidation.validateCustomMinutes(
            minutes = parsed,
            maxDurationMs = mainTotal,
            scopeLabel = TimerValidation.SCOPE_MAIN_QUEUE
        )
        draftState.update {
            it.copy(
                targetDurationMs = capped.durationMs,
                isFullSelected = capped.wasCapped,
                customMinutesInput = digitsOnly,
                capNotice = capped.noticeMessage,
                saveError = null
            )
        }
    }

    fun saveSession(onSaved: (String) -> Unit) {
        draftState.update { it.copy(nameTouched = true, saveError = null) }
        val current = uiState.value
        if (!current.canSave) return

        val capped = TimerValidation.capDurationMs(
            requestedMs = current.targetDurationMs,
            maxDurationMs = current.mainTotalDurationMs,
            scopeLabel = TimerValidation.SCOPE_MAIN_QUEUE
        )
        if (capped.durationMs <= 0L) {
            draftState.update { it.copy(capNotice = "Select a valid timer duration.") }
            return
        }

        viewModelScope.launch {
            draftState.update { it.copy(isSaving = true, saveError = null) }
            try {
                if (sessionId != null) {
                    playbackClient.stopIfActiveSessionAndWait(sessionId)
                }
                val savedId = sessionRepository.saveSession(
                    SessionDraft(
                        sessionId = sessionId,
                        name = current.name.trim(),
                        introTrackIds = current.introItems.map { it.track.id },
                        mainTrackIds = current.mainItems.map { it.track.id },
                        targetDurationMs = capped.durationMs
                    )
                )
                draftState.update { it.copy(isSaving = false) }
                onSaved(savedId)
            } catch (e: Exception) {
                draftState.update {
                    it.copy(
                        isSaving = false,
                        saveError = e.message ?: "Could not save session. Please check your tracks and try again."
                    )
                }
            }
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
            sessionId: String?,
            sessionRepository: SessionRepository,
            trackRepository: TrackRepository,
            playbackClient: PlaybackClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionEditorViewModel(
                    sessionId = sessionId,
                    sessionRepository = sessionRepository,
                    trackRepository = trackRepository,
                    playbackClient = playbackClient
                ) as T
            }
        }
    }
}
