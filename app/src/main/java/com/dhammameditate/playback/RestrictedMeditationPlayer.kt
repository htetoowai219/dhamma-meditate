package com.dhammameditate.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * ForwardingPlayer wrapper that strips all seek, scrub, skip, next/previous, and speed
 * commands from MediaSession and lock-screen/headset controllers, routing Play/Pause/Stop
 * through MeditationPlaybackService so the monotonic timer stays in sync.
 */
@OptIn(UnstableApi::class)
class RestrictedMeditationPlayer(
    player: Player,
    private val onPlayRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onStopRequested: () -> Unit
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        val builder = Player.Commands.Builder()
        for (command in ALLOWED_COMMAND_LIST) {
            if (base.contains(command)) {
                builder.add(command)
            }
        }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return command in ALLOWED_COMMAND_SET && super.isCommandAvailable(command)
    }

    override fun play() {
        onPlayRequested()
    }

    override fun pause() {
        onPauseRequested()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            onPlayRequested()
        } else {
            onPauseRequested()
        }
    }

    override fun stop() {
        onStopRequested()
    }

    // Explicitly block all seek, skip, and speed changes (including headset seek events)
    override fun seekToDefaultPosition() = Unit
    override fun seekToDefaultPosition(mediaItemIndex: Int) = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) = Unit
    override fun seekBack() = Unit
    override fun seekForward() = Unit
    override fun seekToPreviousMediaItem() = Unit
    override fun seekToPrevious() = Unit
    override fun seekToNextMediaItem() = Unit
    override fun seekToNext() = Unit
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit

    companion object {
        val ALLOWED_COMMAND_LIST: IntArray = intArrayOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE
        )

        private val ALLOWED_COMMAND_SET: Set<Int> = ALLOWED_COMMAND_LIST.toSet()

        val ALLOWED_PLAYER_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(*ALLOWED_COMMAND_LIST)
            .build()
    }
}
