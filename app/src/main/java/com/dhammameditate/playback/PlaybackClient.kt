package com.dhammameditate.playback

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class PlaybackClient(
    private val context: Context,
    private val stateStore: PlaybackStateStore
) {
    val state: StateFlow<PlaybackSnapshot> = stateStore.state

    fun startSession(trackId: String, targetDurationMs: Long) {
        stateStore.clearError()
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_START_SESSION
            putExtra(MeditationPlaybackService.EXTRA_TRACK_ID, trackId)
            putExtra(MeditationPlaybackService.EXTRA_TARGET_DURATION_MS, targetDurationMs)
        }
        context.startService(intent)
    }

    fun startSavedSession(sessionId: String) {
        stateStore.clearError()
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_START_SAVED_SESSION
            putExtra(MeditationPlaybackService.EXTRA_SESSION_ID, sessionId)
        }
        context.startService(intent)
    }

    fun pause() {
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_PAUSE_SESSION
        }
        context.startService(intent)
    }

    fun resume() {
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_RESUME_SESSION
        }
        context.startService(intent)
    }

    fun skipToMain() {
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_SKIP_TO_MAIN
        }
        context.startService(intent)
    }

    fun stopAndReset() {
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_STOP_RESET
        }
        context.startService(intent)
    }

    suspend fun stopIfActiveTrackAndWait(trackId: String) {
        val current = stateStore.state.value
        if (current.errorTrackId == trackId) {
            stateStore.clearError()
        }
        val isTrackLoaded = current.activeTrackId == trackId || trackId in current.activeTrackIds
        if (!isTrackLoaded) return

        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_STOP_IF_TRACK
            putExtra(MeditationPlaybackService.EXTRA_TRACK_ID, trackId)
        }
        context.startService(intent)
        val cleared = withTimeoutOrNull(STOP_WAIT_TIMEOUT_MS) {
            stateStore.state.first { snapshot ->
                snapshot.activeTrackId != trackId && trackId !in snapshot.activeTrackIds
            }
        }
        val latest = stateStore.state.value
        if (cleared == null && (latest.activeTrackId == trackId || trackId in latest.activeTrackIds)) {
            stateStore.set(PlaybackSnapshot())
        }
    }

    suspend fun stopIfActiveSessionAndWait(sessionId: String) {
        val current = stateStore.state.value
        if (current.errorSessionId == sessionId) {
            stateStore.clearError()
        }
        if (current.activeSessionId != sessionId) return

        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_STOP_IF_SESSION
            putExtra(MeditationPlaybackService.EXTRA_SESSION_ID, sessionId)
        }
        context.startService(intent)
        val cleared = withTimeoutOrNull(STOP_WAIT_TIMEOUT_MS) {
            stateStore.state.first { snapshot ->
                snapshot.activeSessionId != sessionId
            }
        }
        if (cleared == null && stateStore.state.value.activeSessionId == sessionId) {
            stateStore.set(PlaybackSnapshot())
        }
    }

    fun notifyTrackRenamed(trackId: String, newTitle: String) {
        val current = stateStore.state.value
        val isTrackLoaded = current.activeTrackId == trackId || trackId in current.activeTrackIds
        if (!isTrackLoaded) return

        if (current.activeTrackId == trackId) {
            stateStore.update { snapshot ->
                if (snapshot.activeTrackId == trackId) {
                    snapshot.copy(activeTrackTitle = newTitle)
                } else {
                    snapshot
                }
            }
        }

        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_REFRESH_TRACK_TITLE
            putExtra(MeditationPlaybackService.EXTRA_TRACK_ID, trackId)
            putExtra(MeditationPlaybackService.EXTRA_TRACK_TITLE, newTitle)
        }
        context.startService(intent)
    }

    fun clearError() {
        stateStore.clearError()
    }

    private companion object {
        const val STOP_WAIT_TIMEOUT_MS = 1_500L
    }
}
