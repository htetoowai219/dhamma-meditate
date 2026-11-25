package com.dhammameditate.ui.sessionplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.data.repository.SessionRepository
import com.dhammameditate.data.repository.SessionWithQueues
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.PlaybackSnapshot
import com.dhammameditate.playback.PlaybackStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class SessionPlayerUiState(
    val isLoading: Boolean = true,
    val session: SessionWithQueues? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentZone: QueueZone? = null,
    val currentQueueIndex: Int? = null,
    val zoneTrackNumber: Int = 0,
    val zoneTrackCount: Int = 0,
    val activeTrackTitle: String? = null,
    val displayedRemainingMs: Long = 0L,
    val progressFraction: Float = 0f,
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = status == PlaybackStatus.INTRO_PLAYING ||
            status == PlaybackStatus.INTRO_PAUSED ||
            status == PlaybackStatus.PLAYING ||
            status == PlaybackStatus.PAUSED ||
            status == PlaybackStatus.FADING

    val canEdit: Boolean
        get() = session != null && !isActive

    val canStart: Boolean
        get() = session != null && session.isPlayable
}

private data class SessionLoadResult(
    val isLoading: Boolean,
    val session: SessionWithQueues?
)

class SessionPlayerViewModel(
    private val sessionId: String,
    sessionRepository: SessionRepository,
    private val playbackClient: PlaybackClient
) : ViewModel() {

    val uiState: StateFlow<SessionPlayerUiState> = combine(
        sessionRepository.observeSession(sessionId)
            .map { SessionLoadResult(isLoading = false, session = it) }
            .onStart { emit(SessionLoadResult(isLoading = true, session = null)) },
        playbackClient.state
    ) { loadResult, playback ->
        buildUiState(loadResult, playback)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SessionPlayerUiState()
    )

    private fun buildUiState(
        loadResult: SessionLoadResult,
        playback: PlaybackSnapshot
    ): SessionPlayerUiState {
        val session = loadResult.session
        val isThisSessionLoaded = playback.activeSessionId == sessionId
        val status = if (isThisSessionLoaded) playback.status else PlaybackStatus.IDLE
        val isSessionActive = status == PlaybackStatus.INTRO_PLAYING ||
            status == PlaybackStatus.INTRO_PAUSED ||
            status == PlaybackStatus.PLAYING ||
            status == PlaybackStatus.PAUSED ||
            status == PlaybackStatus.FADING

        val displayedRemainingMs = when {
            !isThisSessionLoaded || status == PlaybackStatus.IDLE -> session?.targetDurationMs ?: 0L
            status == PlaybackStatus.INTRO_PLAYING || status == PlaybackStatus.INTRO_PAUSED -> playback.targetDurationMs
            status == PlaybackStatus.COMPLETED -> 0L
            else -> playback.remainingMs
        }

        val progressFraction = when {
            !isThisSessionLoaded || status == PlaybackStatus.IDLE -> 0f
            status == PlaybackStatus.INTRO_PLAYING || status == PlaybackStatus.INTRO_PAUSED -> 0f
            status == PlaybackStatus.COMPLETED -> 1f
            playback.targetDurationMs > 0L -> (playback.elapsedMs.toFloat() / playback.targetDurationMs.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

        val activeTrackTitle = if (isThisSessionLoaded && isSessionActive) {
            playback.activeTrackTitle
        } else {
            session?.allItems?.firstOrNull()?.track?.title
        }

        val currentQueueIndex = if (isThisSessionLoaded && isSessionActive) {
            playback.currentQueueIndex
        } else {
            null
        }

        val errorMessage = if (
            playback.errorMessage != null &&
            (
                playback.errorSessionId == sessionId ||
                    (playback.errorSessionId == null && playback.errorTrackId == null && isThisSessionLoaded)
                )
        ) {
            playback.errorMessage
        } else {
            null
        }

        return SessionPlayerUiState(
            isLoading = loadResult.isLoading,
            session = session,
            status = status,
            currentZone = if (isThisSessionLoaded && isSessionActive) playback.currentZone else null,
            currentQueueIndex = currentQueueIndex,
            zoneTrackNumber = if (isThisSessionLoaded && isSessionActive) playback.zoneTrackNumber else 0,
            zoneTrackCount = if (isThisSessionLoaded && isSessionActive) playback.zoneTrackCount else 0,
            activeTrackTitle = activeTrackTitle,
            displayedRemainingMs = displayedRemainingMs,
            progressFraction = progressFraction,
            errorMessage = errorMessage
        )
    }

    fun startSession() {
        val current = uiState.value
        val session = current.session ?: return
        if (!session.isPlayable) return
        playbackClient.startSavedSession(session.id)
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
            sessionId: String,
            sessionRepository: SessionRepository,
            playbackClient: PlaybackClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionPlayerViewModel(
                    sessionId = sessionId,
                    sessionRepository = sessionRepository,
                    playbackClient = playbackClient
                ) as T
            }
        }
    }
}
