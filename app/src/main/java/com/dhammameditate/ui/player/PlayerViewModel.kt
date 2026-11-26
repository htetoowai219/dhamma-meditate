package com.dhammameditate.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhammameditate.R
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.repository.TrackRepository
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.PlaybackSnapshot
import com.dhammameditate.playback.PlaybackStatus
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.UiText
import com.dhammameditate.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val track: TrackEntity? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val selectedDurationMs: Long = 0L,
    val isFullSelected: Boolean = false,
    val customMinutesInput: String = "",
    val capNotice: UiText? = null,
    val displayedRemainingMs: Long = 0L,
    val progressFraction: Float = 0f,
    val errorMessage: String? = null
) {
    val isTimerLocked: Boolean
        get() = status == PlaybackStatus.INTRO_PLAYING ||
            status == PlaybackStatus.INTRO_PAUSED ||
            status == PlaybackStatus.PLAYING ||
            status == PlaybackStatus.PAUSED ||
            status == PlaybackStatus.FADING

    val hasInvalidCustomInput: Boolean
        get() = customMinutesInput.isNotEmpty() && (customMinutesInput.toIntOrNull() ?: 0) < 1

    val canStartSession: Boolean
        get() = track != null && selectedDurationMs > 0L && !hasInvalidCustomInput
}

private data class LocalSelectionState(
    val isLoading: Boolean = true,
    val selectedDurationMs: Long = 0L,
    val isFullSelected: Boolean = false,
    val customMinutesInput: String = "",
    val capNotice: UiText? = null
)

class PlayerViewModel(
    private val trackId: String,
    private val trackRepository: TrackRepository,
    private val playbackClient: PlaybackClient
) : ViewModel() {

    private val localState = MutableStateFlow(LocalSelectionState())

    val uiState: StateFlow<PlayerUiState> = combine(
        localState,
        trackRepository.observeTrack(trackId),
        playbackClient.state
    ) { local, observedTrack, playback ->
        buildUiState(local, observedTrack, playback)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PlayerUiState()
    )

    init {
        viewModelScope.launch {
            val track = trackRepository.getTrack(trackId)
            if (track == null) {
                localState.value = LocalSelectionState(isLoading = false)
                return@launch
            }
            val activePlayback = playbackClient.state.value
            val initialDuration = if (
                activePlayback.activeSessionId == null &&
                activePlayback.activeTrackId == trackId &&
                activePlayback.targetDurationMs > 0L
            ) {
                activePlayback.targetDurationMs.coerceAtMost(track.durationMs)
            } else {
                TimerValidation.defaultDurationMs(track.durationMs)
            }
            val matchesPreset = TimerValidation.PRESET_MINUTES.any {
                it.toLong() * 60_000L == initialDuration
            }
            localState.value = LocalSelectionState(
                isLoading = false,
                selectedDurationMs = initialDuration,
                isFullSelected = !matchesPreset && initialDuration == track.durationMs,
                customMinutesInput = "",
                capNotice = null
            )
        }
    }

    private fun buildUiState(
        local: LocalSelectionState,
        observedTrack: TrackEntity?,
        playback: PlaybackSnapshot
    ): PlayerUiState {
        val isThisTrackLoaded = playback.activeSessionId == null && playback.activeTrackId == trackId
        val status = if (isThisTrackLoaded) playback.status else PlaybackStatus.IDLE

        val displayedRemainingMs: Long
        val progressFraction: Float
        if (isThisTrackLoaded && status != PlaybackStatus.IDLE) {
            displayedRemainingMs = playback.remainingMs
            progressFraction = if (playback.targetDurationMs > 0L) {
                (playback.elapsedMs.toFloat() / playback.targetDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        } else {
            displayedRemainingMs = local.selectedDurationMs
            progressFraction = 0f
        }

        val errorMessage = if (
            playback.errorMessage != null &&
            (
                playback.errorTrackId == trackId ||
                    (
                        playback.errorTrackId == null &&
                            playback.errorSessionId == null &&
                            (isThisTrackLoaded || playback.activeTrackId == null)
                        )
                )
        ) {
            playback.errorMessage
        } else {
            null
        }

        return PlayerUiState(
            isLoading = local.isLoading,
            track = if (local.isLoading) null else observedTrack,
            status = status,
            selectedDurationMs = local.selectedDurationMs,
            isFullSelected = local.isFullSelected,
            customMinutesInput = local.customMinutesInput,
            capNotice = local.capNotice,
            displayedRemainingMs = displayedRemainingMs,
            progressFraction = progressFraction,
            errorMessage = errorMessage
        )
    }

    fun selectPresetMinutes(minutes: Int) {
        val state = uiState.value
        val track = state.track ?: return
        if (state.isTimerLocked) return
        if (!TimerValidation.isPresetEnabled(minutes, track.durationMs)) return

        resetIfCompleted(state.status)
        localState.value = localState.value.copy(
            selectedDurationMs = minutes.toLong() * 60_000L,
            isFullSelected = false,
            customMinutesInput = "",
            capNotice = null
        )
    }

    fun selectFullTrack() {
        val state = uiState.value
        val track = state.track ?: return
        if (state.isTimerLocked) return

        resetIfCompleted(state.status)
        localState.value = localState.value.copy(
            selectedDurationMs = track.durationMs,
            isFullSelected = true,
            customMinutesInput = "",
            capNotice = null
        )
    }

    fun onCustomMinutesChanged(rawInput: String) {
        val state = uiState.value
        val track = state.track ?: return
        if (state.isTimerLocked) return

        resetIfCompleted(state.status)
        val digitsOnly = rawInput.filter { it in '0'..'9' }.take(4)
        if (digitsOnly.isEmpty()) {
            localState.value = localState.value.copy(
                customMinutesInput = "",
                capNotice = null
            )
            return
        }

        val parsed = digitsOnly.toIntOrNull() ?: return
        if (parsed < 1) {
            localState.value = localState.value.copy(
                customMinutesInput = digitsOnly,
                capNotice = UiText.Resource(R.string.timer_error_min_one_minute)
            )
            return
        }

        val capped = TimerValidation.validateCustomMinutes(parsed, track.durationMs)
        localState.value = localState.value.copy(
            selectedDurationMs = capped.durationMs,
            isFullSelected = capped.wasCapped,
            customMinutesInput = digitsOnly,
            capNotice = capped.capNotice?.toUiText()
        )
    }

    private fun resetIfCompleted(status: PlaybackStatus) {
        if (status == PlaybackStatus.COMPLETED) {
            playbackClient.stopAndReset()
        }
    }

    fun startSession() {
        val state = uiState.value
        val track = state.track ?: return
        if (!state.canStartSession) return
        val capped = TimerValidation.capDurationMs(state.selectedDurationMs, track.durationMs)
        if (capped.durationMs <= 0L) return

        localState.value = localState.value.copy(
            selectedDurationMs = capped.durationMs,
            capNotice = capped.capNotice?.toUiText()
        )
        playbackClient.startSession(
            trackId = track.id,
            targetDurationMs = capped.durationMs
        )
    }

    fun pauseSession() {
        playbackClient.pause()
    }

    fun resumeSession() {
        playbackClient.resume()
    }

    fun stopAndReset() {
        playbackClient.stopAndReset()
    }

    fun dismissError() {
        playbackClient.clearError()
    }

    companion object {
        fun factory(
            trackId: String,
            trackRepository: TrackRepository,
            playbackClient: PlaybackClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(
                    trackId = trackId,
                    trackRepository = trackRepository,
                    playbackClient = playbackClient
                ) as T
            }
        }
    }
}
