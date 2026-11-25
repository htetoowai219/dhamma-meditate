package com.dhammameditate.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhammameditate.data.repository.SessionRepository
import com.dhammameditate.data.repository.SessionWithQueues
import com.dhammameditate.data.repository.TrackRepository
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.PlaybackSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<SessionWithQueues> = emptyList(),
    val hasLibraryTracks: Boolean = false,
    val playback: PlaybackSnapshot = PlaybackSnapshot()
)

class SessionsViewModel(
    private val sessionRepository: SessionRepository,
    private val trackRepository: TrackRepository,
    private val playbackClient: PlaybackClient
) : ViewModel() {

    val uiState: StateFlow<SessionsUiState> = combine(
        sessionRepository.observeSessions(),
        trackRepository.observeTracks(),
        playbackClient.state
    ) { sessions, tracks, playback ->
        SessionsUiState(
            sessions = sessions,
            hasLibraryTracks = tracks.isNotEmpty(),
            playback = playback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SessionsUiState()
    )

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            playbackClient.stopIfActiveSessionAndWait(sessionId)
            sessionRepository.deleteSession(sessionId)
        }
    }

    companion object {
        fun factory(
            sessionRepository: SessionRepository,
            trackRepository: TrackRepository,
            playbackClient: PlaybackClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionsViewModel(
                    sessionRepository = sessionRepository,
                    trackRepository = trackRepository,
                    playbackClient = playbackClient
                ) as T
            }
        }
    }
}
