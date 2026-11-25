package com.dhammameditate.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.dhammameditate.DhammaMeditateApp
import com.dhammameditate.MainActivity
import com.dhammameditate.R
import com.dhammameditate.data.repository.TrackRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MeditationPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var trackRepository: TrackRepository
    private lateinit var stateStore: PlaybackStateStore
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var restrictedPlayer: RestrictedMeditationPlayer
    private var mediaSession: MediaSession? = null

    private val timerEngine = MeditationTimerEngine(clockMs = { SystemClock.elapsedRealtime() })
    private var tickJob: Job? = null
    private var startSessionJob: Job? = null
    private var pendingStartTrackId: String? = null
    private var isInternalPlayerChange: Boolean = false

    private var activeTrackId: String? = null
    private var activeTrackTitle: String? = null
    private var activeTargetDurationMs: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (isInternalPlayerChange) return
            val status = timerEngine.currentStatus()
            if (!playWhenReady && (status == PlaybackStatus.PLAYING || status == PlaybackStatus.FADING)) {
                pauseTimerOnly()
            } else if (
                playWhenReady &&
                exoPlayer.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                status == PlaybackStatus.PAUSED
            ) {
                resumeTimerOnly()
            }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (isInternalPlayerChange) return
            val status = timerEngine.currentStatus()
            if (
                playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                (status == PlaybackStatus.PLAYING || status == PlaybackStatus.FADING)
            ) {
                pauseTimerOnly()
            } else if (
                playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                exoPlayer.playWhenReady &&
                status == PlaybackStatus.PAUSED
            ) {
                resumeTimerOnly()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val status = timerEngine.currentStatus()
                if (status == PlaybackStatus.PLAYING || status == PlaybackStatus.FADING) {
                    completeSessionEarly()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            tickJob?.cancel()
            tickJob = null
            val resetTick = timerEngine.stopAndReset()
            withInternalPlayerChange {
                exoPlayer.volume = 1.0f
                exoPlayer.pause()
                exoPlayer.seekTo(0L)
            }
            publishSnapshot(
                tick = resetTick,
                errorMessage = "Audio playback failed: file may be missing or unreadable.",
                errorTrackId = activeTrackId
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as DhammaMeditateApp).container
        trackRepository = container.trackRepository
        stateStore = container.playbackStateStore

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exoPlayer.addListener(playerListener)

        restrictedPlayer = RestrictedMeditationPlayer(
            player = exoPlayer,
            onPlayRequested = { handleExternalPlay() },
            onPauseRequested = { pauseSession() },
            onStopRequested = { stopAndReset(clearTrack = false) }
        )

        val stopResetButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName(getString(R.string.action_stop_reset))
            .setSessionCommand(STOP_RESET_COMMAND)
            .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ACTIVE_SESSION, true)
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .remove(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
                    .add(STOP_RESET_COMMAND)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(RestrictedMeditationPlayer.ALLOWED_PLAYER_COMMANDS)
                    .setMediaButtonPreferences(listOf(stopResetButton))
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_STOP_RESET) {
                    stopAndReset(clearTrack = false)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
        }

        val session = MediaSession.Builder(this, restrictedPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .setMediaButtonPreferences(listOf(stopResetButton))
            .build()
        mediaSession = session
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                val targetDurationMs = intent.getLongExtra(EXTRA_TARGET_DURATION_MS, 0L)
                if (!trackId.isNullOrBlank() && targetDurationMs > 0L) {
                    startSession(trackId, targetDurationMs)
                }
            }

            ACTION_PAUSE_SESSION -> pauseSession()
            ACTION_RESUME_SESSION -> resumeSession()
            ACTION_STOP_RESET -> stopAndReset(clearTrack = false)
            ACTION_STOP_IF_TRACK -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                if (trackId != null) {
                    if (pendingStartTrackId == trackId) {
                        startSessionJob?.cancel()
                        startSessionJob = null
                        pendingStartTrackId = null
                    }
                    if (trackId == activeTrackId || stateStore.state.value.activeTrackId == trackId) {
                        stopAndReset(clearTrack = true)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val status = timerEngine.currentStatus()
        if (status != PlaybackStatus.PLAYING && status != PlaybackStatus.FADING) {
            stopAndReset(clearTrack = false)
            stopSelf()
        }
    }

    override fun onDestroy() {
        val status = timerEngine.currentStatus()
        startSessionJob?.cancel()
        startSessionJob = null
        pendingStartTrackId = null
        tickJob?.cancel()
        tickJob = null
        if (
            status == PlaybackStatus.PLAYING ||
            status == PlaybackStatus.PAUSED ||
            status == PlaybackStatus.FADING
        ) {
            timerEngine.stopAndReset()
            stateStore.set(PlaybackSnapshot())
        }
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onDestroy()
    }

    private fun startSession(trackId: String, requestedTargetDurationMs: Long) {
        startSessionJob?.cancel()
        pendingStartTrackId = trackId
        startSessionJob = serviceScope.launch {
            try {
                val track = trackRepository.getTrack(trackId)
                if (track == null) {
                    publishError(trackId, "Selected track could not be found in your library.")
                    return@launch
                }

                val audioFile = trackRepository.resolveAudioFile(track)
                if (!audioFile.exists() || !audioFile.canRead()) {
                    publishError(trackId, "Local audio file is missing or unreadable. Try re-importing it.")
                    return@launch
                }

                val capped = TimerValidation.capDurationMs(requestedTargetDurationMs, track.durationMs)
                if (capped.durationMs <= 0L) {
                    publishError(trackId, "Selected timer duration is invalid.")
                    return@launch
                }

                tickJob?.cancel()
                tickJob = null

                activeTrackId = track.id
                activeTrackTitle = track.title
                activeTargetDurationMs = capped.durationMs

                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setSubtitle(getString(R.string.notification_subtitle))
                    .setArtist(getString(R.string.notification_subtitle))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(Uri.fromFile(audioFile))
                    .setMediaMetadata(metadata)
                    .build()

                val startTick = timerEngine.start(
                    targetDurationMs = capped.durationMs,
                    nowMs = SystemClock.elapsedRealtime()
                )

                // Start-Fresh invariant: always 00:00 and full 1.0f volume
                withInternalPlayerChange {
                    exoPlayer.setMediaItem(mediaItem, /* startPositionMs = */ 0L)
                    exoPlayer.volume = 1.0f
                    exoPlayer.prepare()
                    exoPlayer.seekTo(0L)
                    exoPlayer.play()
                }

                if (isPlaybackBlockedOrSuppressed()) {
                    pauseTimerOnly()
                } else {
                    publishSnapshot(tick = startTick, errorMessage = null)
                    startTickLoop()
                }
            } finally {
                if (pendingStartTrackId == trackId) {
                    pendingStartTrackId = null
                }
            }
        }
    }

    private fun handleExternalPlay() {
        when (timerEngine.currentStatus()) {
            PlaybackStatus.PAUSED -> resumeSession()
            PlaybackStatus.IDLE, PlaybackStatus.COMPLETED -> {
                val trackId = activeTrackId
                val durationMs = activeTargetDurationMs
                if (trackId != null && durationMs > 0L) {
                    startSession(trackId, durationMs)
                }
            }
            PlaybackStatus.PLAYING, PlaybackStatus.FADING -> Unit
        }
    }

    private fun pauseSession() {
        val status = timerEngine.currentStatus()
        if (status != PlaybackStatus.PLAYING && status != PlaybackStatus.FADING) return
        tickJob?.cancel()
        tickJob = null
        val pausedTick = timerEngine.pause(SystemClock.elapsedRealtime())
        withInternalPlayerChange {
            exoPlayer.pause()
        }
        publishSnapshot(tick = pausedTick, errorMessage = null)
    }

    private fun pauseTimerOnly() {
        tickJob?.cancel()
        tickJob = null
        val pausedTick = timerEngine.pause(SystemClock.elapsedRealtime())
        publishSnapshot(tick = pausedTick, errorMessage = null)
    }

    private fun resumeSession() {
        if (timerEngine.currentStatus() != PlaybackStatus.PAUSED) return
        val resumedTick = timerEngine.resume(SystemClock.elapsedRealtime())
        if (resumedTick.status == PlaybackStatus.COMPLETED) {
            finishSessionAtZero(resumedTick)
            return
        }
        withInternalPlayerChange {
            exoPlayer.volume = resumedTick.volume
            exoPlayer.play()
        }
        if (isPlaybackBlockedOrSuppressed()) {
            pauseTimerOnly()
        } else {
            publishSnapshot(tick = resumedTick, errorMessage = null)
            startTickLoop()
        }
    }

    private fun resumeTimerOnly() {
        if (timerEngine.currentStatus() != PlaybackStatus.PAUSED) return
        val resumedTick = timerEngine.resume(SystemClock.elapsedRealtime())
        if (resumedTick.status == PlaybackStatus.COMPLETED) {
            finishSessionAtZero(resumedTick)
            return
        }
        exoPlayer.volume = resumedTick.volume
        publishSnapshot(tick = resumedTick, errorMessage = null)
        startTickLoop()
    }

    private fun stopAndReset(clearTrack: Boolean) {
        startSessionJob?.cancel()
        startSessionJob = null
        pendingStartTrackId = null
        tickJob?.cancel()
        tickJob = null
        val resetTick = timerEngine.stopAndReset()
        withInternalPlayerChange {
            exoPlayer.pause()
            exoPlayer.seekTo(0L)
            exoPlayer.volume = 1.0f
            if (clearTrack) {
                exoPlayer.clearMediaItems()
            }
        }
        if (clearTrack) {
            activeTrackId = null
            activeTrackTitle = null
            activeTargetDurationMs = 0L
            stateStore.set(PlaybackSnapshot())
        } else {
            publishSnapshot(tick = resetTick, errorMessage = null)
        }
    }

    private fun completeSessionEarly() {
        tickJob?.cancel()
        tickJob = null
        val completedTick = timerEngine.completeEarly()
        finishSessionAtZero(completedTick)
    }

    private fun finishSessionAtZero(completedTick: TimerTick) {
        tickJob?.cancel()
        tickJob = null
        withInternalPlayerChange {
            exoPlayer.volume = 0.0f
            exoPlayer.pause()
            exoPlayer.seekTo(0L)
        }
        publishSnapshot(tick = completedTick, errorMessage = null)
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            while (isActive) {
                val tick = timerEngine.tick(SystemClock.elapsedRealtime())
                when (tick.status) {
                    PlaybackStatus.PLAYING -> {
                        if (exoPlayer.volume != 1.0f) {
                            exoPlayer.volume = 1.0f
                        }
                        publishSnapshot(tick = tick, errorMessage = null)
                        val effectiveFade = FadeCurve.effectiveFadeMs(tick.targetDurationMs)
                        val msUntilFade = (tick.remainingMs - effectiveFade).coerceAtLeast(0L)
                        val delayMs = if (msUntilFade in 1L until NORMAL_TICK_MS) {
                            msUntilFade
                        } else {
                            NORMAL_TICK_MS
                        }
                        delay(delayMs)
                    }

                    PlaybackStatus.FADING -> {
                        exoPlayer.volume = tick.volume
                        publishSnapshot(tick = tick, errorMessage = null)
                        val delayMs = minOf(FADE_TICK_MS, tick.remainingMs.coerceAtLeast(1L))
                        delay(delayMs)
                    }

                    PlaybackStatus.COMPLETED -> {
                        finishSessionAtZero(tick)
                        break
                    }

                    PlaybackStatus.PAUSED, PlaybackStatus.IDLE -> {
                        publishSnapshot(tick = tick, errorMessage = null)
                        break
                    }
                }
            }
        }
    }

    private fun publishSnapshot(
        tick: TimerTick,
        errorMessage: String?,
        errorTrackId: String? = null
    ) {
        stateStore.set(
            PlaybackSnapshot(
                status = tick.status,
                activeTrackId = activeTrackId,
                activeTrackTitle = activeTrackTitle,
                targetDurationMs = tick.targetDurationMs,
                remainingMs = tick.remainingMs,
                elapsedMs = tick.elapsedMs,
                volume = tick.volume,
                errorMessage = errorMessage,
                errorTrackId = errorTrackId
            )
        )
    }

    private fun publishError(failedTrackId: String, message: String) {
        tickJob?.cancel()
        tickJob = null
        timerEngine.stopAndReset()
        withInternalPlayerChange {
            exoPlayer.volume = 1.0f
            exoPlayer.pause()
            exoPlayer.seekTo(0L)
            exoPlayer.clearMediaItems()
        }
        activeTrackId = null
        activeTrackTitle = null
        activeTargetDurationMs = 0L
        stateStore.set(
            PlaybackSnapshot(
                status = PlaybackStatus.IDLE,
                errorMessage = message,
                errorTrackId = failedTrackId
            )
        )
    }

    private fun isPlaybackBlockedOrSuppressed(): Boolean {
        return !exoPlayer.playWhenReady ||
            exoPlayer.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
    }

    private inline fun withInternalPlayerChange(block: () -> Unit) {
        isInternalPlayerChange = true
        try {
            block()
        } finally {
            isInternalPlayerChange = false
        }
    }

    companion object {
        const val ACTION_START_SESSION = "com.dhammameditate.action.START_SESSION"
        const val ACTION_PAUSE_SESSION = "com.dhammameditate.action.PAUSE_SESSION"
        const val ACTION_RESUME_SESSION = "com.dhammameditate.action.RESUME_SESSION"
        const val ACTION_STOP_RESET = "ACTION_STOP_RESET"
        const val ACTION_STOP_IF_TRACK = "com.dhammameditate.action.STOP_IF_TRACK"

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_TARGET_DURATION_MS = "extra_target_duration_ms"

        private const val NORMAL_TICK_MS = 250L
        private const val FADE_TICK_MS = 50L

        val STOP_RESET_COMMAND = SessionCommand(ACTION_STOP_RESET, Bundle.EMPTY)
    }
}
