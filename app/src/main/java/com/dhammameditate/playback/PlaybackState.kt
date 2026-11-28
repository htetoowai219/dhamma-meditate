package com.dhammameditate.playback

import com.dhammameditate.data.local.QueueZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackSnapshot(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val activeSessionId: String? = null,
    val activeSessionName: String? = null,
    val activeTrackId: String? = null,
    val activeTrackTitle: String? = null,
    val activeTrackIds: Set<String> = emptySet(),
    val activeIntroTrackIds: List<String> = emptyList(),
    val activeMainTrackIds: List<String> = emptyList(),
    val currentZone: QueueZone? = null,
    val currentQueueIndex: Int = 0,
    val zoneTrackNumber: Int = 0,
    val zoneTrackCount: Int = 0,
    val introCount: Int = 0,
    val mainCount: Int = 0,
    val targetDurationMs: Long = 0L,
    val remainingMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val volume: Float = 1.0f,
    val errorMessage: String? = null,
    val errorTrackId: String? = null,
    val errorSessionId: String? = null
) {
    val isActive: Boolean
        get() = status == PlaybackStatus.INTRO_PLAYING ||
            status == PlaybackStatus.INTRO_PAUSED ||
            status == PlaybackStatus.PLAYING ||
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
        _state.update {
            it.copy(
                errorMessage = null,
                errorTrackId = null,
                errorSessionId = null
            )
        }
    }
}
