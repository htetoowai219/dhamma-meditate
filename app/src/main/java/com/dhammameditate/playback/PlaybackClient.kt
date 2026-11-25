package com.dhammameditate.playback

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

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

    fun stopAndReset() {
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_STOP_RESET
        }
        context.startService(intent)
    }

    fun stopIfActiveTrack(trackId: String) {
        if (stateStore.state.value.activeTrackId != trackId) return
        val intent = Intent(context, MeditationPlaybackService::class.java).apply {
            action = MeditationPlaybackService.ACTION_STOP_IF_TRACK
            putExtra(MeditationPlaybackService.EXTRA_TRACK_ID, trackId)
        }
        context.startService(intent)
    }

    fun clearError() {
        stateStore.clearError()
    }
}
