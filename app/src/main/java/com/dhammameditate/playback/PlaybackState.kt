package com.dhammameditate.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackSnapshot(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val activeTrackId: String? = null,
    val activeTrackTitle: String? = null,
    val targetDurationMs: Long = 0L,
    val remainingMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val volume: Float = 1.0f,
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = status == PlaybackStatus.PLAYING ||
            status == PlaybackStatus.PAUSED ||
            status == PlaybackStatus.FADING
}

class PlaybackStateStore {
    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    fun update(transform: (PlaybackSnapshot) -> PlaybackSnapshot) {
        _state.update(transform)
    }

    fun set(snapshot: PlaybackSnapshot) {
        _state.value = snapshot
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
