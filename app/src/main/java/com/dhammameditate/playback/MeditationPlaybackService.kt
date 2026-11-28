package com.dhammameditate.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.annotation.StringRes
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
import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.preferences.AppLanguageStore
import com.dhammameditate.data.repository.SessionRepository
import com.dhammameditate.data.repository.TrackRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class MeditationPlaybackService : MediaSessionService() {

    private data class ActiveQueueEntry(
        val trackId: String,
        val trackTitle: String,
        val zone: QueueZone,
        val zoneTrackNumber: Int,
        val zoneTrackCount: Int
    )

    private data class QueueTrackSpec(
        val mediaId: String,
        val track: TrackEntity
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var trackRepository: TrackRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var stateStore: PlaybackStateStore
    private lateinit var appLanguageStore: AppLanguageStore
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var restrictedPlayer: RestrictedMeditationPlayer
    private lateinit var stopResetButton: CommandButton
    private var mediaSession: MediaSession? = null

    private val timerEngine = MeditationTimerEngine(clockMs = { SystemClock.elapsedRealtime() })
    private var tickJob: Job? = null
    private var startSessionJob: Job? = null
    private var pendingStartTrackId: String? = null
    private var pendingStartSessionId: String? = null
    private var isInternalPlayerChange: Boolean = false

    private var activeSessionId: String? = null
    private var activeSessionName: String? = null
    private var activeQueueEntries: List<ActiveQueueEntry> = emptyList()
    private var activeTrackIds: Set<String> = emptySet()
    private var activeIntroCount: Int = 0
    private var activeMainCount: Int = 0
    private var currentQueueIndex: Int = 0
    private var activeTargetDurationMs: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (isInternalPlayerChange) return
            val status = timerEngine.currentStatus()
            if (
                !playWhenReady &&
                (
                    status == PlaybackStatus.INTRO_PLAYING ||
                        status == PlaybackStatus.PLAYING ||
                        status == PlaybackStatus.FADING
                    )
            ) {
                pauseTimerOnly()
            } else if (
                playWhenReady &&
                exoPlayer.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                (status == PlaybackStatus.INTRO_PAUSED || status == PlaybackStatus.PAUSED)
            ) {
                resumeTimerOnly()
            }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (isInternalPlayerChange) return
            val status = timerEngine.currentStatus()
            if (
                playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                (
                    status == PlaybackStatus.INTRO_PLAYING ||
                        status == PlaybackStatus.PLAYING ||
                        status == PlaybackStatus.FADING
                    )
            ) {
                pauseTimerOnly()
            } else if (
                playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                exoPlayer.playWhenReady &&
                (status == PlaybackStatus.INTRO_PAUSED || status == PlaybackStatus.PAUSED)
            ) {
                resumeTimerOnly()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isInternalPlayerChange) return
            if (activeQueueEntries.isEmpty()) return
            val status = timerEngine.currentStatus()
            if (status == PlaybackStatus.IDLE || status == PlaybackStatus.COMPLETED) return
            val newIndex = exoPlayer.currentMediaItemIndex
            if (newIndex !in activeQueueEntries.indices) return
            currentQueueIndex = newIndex

            if (
                newIndex >= activeIntroCount &&
                (status == PlaybackStatus.INTRO_PLAYING || status == PlaybackStatus.INTRO_PAUSED)
            ) {
                val transitionedTick = timerEngine.onIntroComplete(SystemClock.elapsedRealtime())
                if (
                    isPlaybackBlockedOrSuppressed() &&
                    transitionedTick.status != PlaybackStatus.PAUSED
                ) {
                    pauseTimerOnly()
                } else {
                    publishSnapshot(tick = transitionedTick, errorMessage = null)
                    if (
                        transitionedTick.status == PlaybackStatus.PLAYING ||
                        transitionedTick.status == PlaybackStatus.FADING
                    ) {
                        startTickLoop()
                    }
                }
            } else {
                val currentTick = timerEngine.tick(SystemClock.elapsedRealtime())
                if (currentTick.status == PlaybackStatus.COMPLETED) {
                    finishSessionAtZero(currentTick)
                } else {
                    if (currentTick.status == PlaybackStatus.FADING) {
                        exoPlayer.volume = currentTick.volume
                    }
                    publishSnapshot(tick = currentTick, errorMessage = null)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (isInternalPlayerChange) return
            if (playbackState == Player.STATE_ENDED) {
                val status = timerEngine.currentStatus()
                if (
                    status == PlaybackStatus.PLAYING ||
                    status == PlaybackStatus.FADING ||
                    status == PlaybackStatus.INTRO_PLAYING
                ) {
                    completeSessionEarly()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            tickJob?.cancel()
            tickJob = null
            val resetTick = timerEngine.stopAndReset()
            currentQueueIndex = 0
            withInternalPlayerChange {
                exoPlayer.volume = 1.0f
                exoPlayer.pause()
                if (exoPlayer.mediaItemCount > 0) {
                    exoPlayer.seekTo(0, 0L)
                }
            }
            val currentEntry = activeQueueEntries.getOrNull(currentQueueIndex)
            publishSnapshot(
                tick = resetTick,
                errorMessage = localizedString(R.string.playback_error_failed),
                errorTrackId = if (activeSessionId == null) currentEntry?.trackId else null,
                errorSessionId = activeSessionId
            )
        }
    }

    private fun localizedString(@StringRes resId: Int, vararg args: Any): String {
        val localized = appLanguageStore.localizedContext(this)
        return if (args.isEmpty()) {
            localized.getString(resId)
        } else {
            localized.getString(resId, *args)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as DhammaMeditateApp).container
        trackRepository = container.trackRepository
        sessionRepository = container.sessionRepository
        stateStore = container.playbackStateStore
        appLanguageStore = container.appLanguageStore

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
            onStopRequested = { stopAndReset(clearSelection = false) }
        )

        stopResetButton = buildStopResetButton()

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
                    stopAndReset(clearSelection = false)
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

        serviceScope.launch {
            appLanguageStore.language.drop(1).collect {
                refreshLocalizedMediaMetadata()
            }
        }
    }

    private fun buildStopResetButton(): CommandButton {
        return CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName(localizedString(R.string.action_stop_reset))
            .setSessionCommand(STOP_RESET_COMMAND)
            .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private fun refreshLocalizedMediaMetadata() {
        stopResetButton = buildStopResetButton()
        mediaSession?.setMediaButtonPreferences(listOf(stopResetButton))

        if (activeSessionId == PlaybackClient.QUICK_MEDITATE_SESSION_ID) {
            val localizedQuickTitle = localizedString(R.string.quick_meditate_title)
            activeSessionName = localizedQuickTitle
            stateStore.update { snapshot ->
                if (snapshot.activeSessionId == PlaybackClient.QUICK_MEDITATE_SESSION_ID) {
                    snapshot.copy(activeSessionName = localizedQuickTitle)
                } else {
                    snapshot
                }
            }
        }

        if (activeQueueEntries.isEmpty() || exoPlayer.mediaItemCount != activeQueueEntries.size) {
            return
        }
        val sessionName = activeSessionName
        withInternalPlayerChange {
            activeQueueEntries.forEachIndexed { index, entry ->
                val subtitle = if (sessionName != null) {
                    val resId = if (entry.zone == QueueZone.INTRO) {
                        R.string.notification_session_intro
                    } else {
                        R.string.notification_session_main
                    }
                    localizedString(
                        resId,
                        sessionName,
                        entry.zoneTrackNumber.toString(),
                        entry.zoneTrackCount.toString()
                    )
                } else {
                    localizedString(R.string.notification_subtitle)
                }
                val existingItem = exoPlayer.getMediaItemAt(index)
                val updatedMetadata = existingItem.mediaMetadata.buildUpon()
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .build()
                val updatedItem = existingItem.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()
                exoPlayer.replaceMediaItem(index, updatedItem)
            }
        }
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
                    startQuickTrackSession(trackId, targetDurationMs)
                }
            }

            ACTION_START_SAVED_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (!sessionId.isNullOrBlank()) {
                    startSavedSession(sessionId)
                }
            }

            ACTION_START_QUICK_MEDITATE -> {
                val introIds = intent.getStringArrayListExtra(EXTRA_INTRO_TRACK_IDS).orEmpty()
                val mainIds = intent.getStringArrayListExtra(EXTRA_MAIN_TRACK_IDS).orEmpty()
                val targetDurationMs = intent.getLongExtra(EXTRA_TARGET_DURATION_MS, 0L)
                startQuickMeditateSession(
                    introTrackIds = introIds,
                    mainTrackIds = mainIds,
                    requestedTargetDurationMs = targetDurationMs
                )
            }

            ACTION_PAUSE_SESSION -> pauseSession()
            ACTION_RESUME_SESSION -> resumeSession()
            ACTION_SKIP_TO_MAIN -> skipIntroToMain()
            ACTION_STOP_RESET -> stopAndReset(clearSelection = false)

            ACTION_STOP_IF_TRACK -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                if (trackId != null) {
                    if (pendingStartTrackId == trackId) {
                        startSessionJob?.cancel()
                        startSessionJob = null
                        pendingStartTrackId = null
                    }
                    val snapshot = stateStore.state.value
                    val matchesActive = trackId in activeTrackIds ||
                        snapshot.activeTrackId == trackId ||
                        trackId in snapshot.activeTrackIds
                    if (matchesActive) {
                        stopAndReset(clearSelection = true)
                    }
                }
            }

            ACTION_STOP_IF_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (sessionId != null) {
                    if (pendingStartSessionId == sessionId) {
                        startSessionJob?.cancel()
                        startSessionJob = null
                        pendingStartSessionId = null
                    }
                    if (activeSessionId == sessionId || stateStore.state.value.activeSessionId == sessionId) {
                        stopAndReset(clearSelection = true)
                    }
                }
            }

            ACTION_REFRESH_TRACK_TITLE -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                val newTitle = intent.getStringExtra(EXTRA_TRACK_TITLE)
                if (!trackId.isNullOrBlank() && !newTitle.isNullOrBlank()) {
                    refreshTrackTitle(trackId, newTitle)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val status = timerEngine.currentStatus()
        if (
            status != PlaybackStatus.INTRO_PLAYING &&
            status != PlaybackStatus.PLAYING &&
            status != PlaybackStatus.FADING
        ) {
            stopAndReset(clearSelection = false)
            stopSelf()
        }
    }

    override fun onDestroy() {
        val status = timerEngine.currentStatus()
        startSessionJob?.cancel()
        startSessionJob = null
        pendingStartTrackId = null
        pendingStartSessionId = null
        tickJob?.cancel()
        tickJob = null
        if (
            status == PlaybackStatus.INTRO_PLAYING ||
            status == PlaybackStatus.INTRO_PAUSED ||
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

    private fun startQuickTrackSession(trackId: String, requestedTargetDurationMs: Long) {
        startSessionJob?.cancel()
        pendingStartTrackId = trackId
        pendingStartSessionId = null
        startSessionJob = serviceScope.launch {
            try {
                val track = trackRepository.getTrack(trackId)
                if (track == null) {
                    publishError(
                        message = localizedString(R.string.playback_error_track_not_found),
                        errorTrackId = trackId,
                        errorSessionId = null
                    )
                    return@launch
                }

                val audioFile = trackRepository.resolveAudioFile(track)
                val isReadable = withContext(Dispatchers.IO) {
                    audioFile.exists() && audioFile.canRead()
                }
                if (!isReadable) {
                    publishError(
                        message = localizedString(R.string.playback_error_file_missing),
                        errorTrackId = trackId,
                        errorSessionId = null
                    )
                    return@launch
                }

                val capped = TimerValidation.capDurationMs(requestedTargetDurationMs, track.durationMs)
                if (capped.durationMs <= 0L) {
                    publishError(
                        message = localizedString(R.string.playback_error_invalid_timer),
                        errorTrackId = trackId,
                        errorSessionId = null
                    )
                    return@launch
                }

                tickJob?.cancel()
                tickJob = null

                activeSessionId = null
                activeSessionName = null
                activeQueueEntries = listOf(
                    ActiveQueueEntry(
                        trackId = track.id,
                        trackTitle = track.title,
                        zone = QueueZone.MAIN,
                        zoneTrackNumber = 1,
                        zoneTrackCount = 1
                    )
                )
                activeTrackIds = setOf(track.id)
                activeIntroCount = 0
                activeMainCount = 1
                currentQueueIndex = 0
                activeTargetDurationMs = capped.durationMs

                val subtitle = localizedString(R.string.notification_subtitle)
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(Uri.fromFile(audioFile))
                    .setMediaMetadata(metadata)
                    .build()

                val startTick = timerEngine.start(
                    targetDurationMs = capped.durationMs,
                    hasIntro = false,
                    nowMs = SystemClock.elapsedRealtime()
                )

                // Start-Fresh invariant: always index 0, 00:00, and full 1.0f volume
                withInternalPlayerChange {
                    exoPlayer.setMediaItems(listOf(mediaItem), /* startIndex = */ 0, /* startPositionMs = */ 0L)
                    exoPlayer.volume = 1.0f
                    exoPlayer.prepare()
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

    private fun startSavedSession(sessionId: String) {
        startSessionJob?.cancel()
        pendingStartTrackId = null
        pendingStartSessionId = sessionId
        startSessionJob = serviceScope.launch {
            try {
                val session = sessionRepository.getSession(sessionId)
                if (session == null) {
                    publishError(
                        message = localizedString(R.string.playback_error_session_not_found),
                        errorTrackId = null,
                        errorSessionId = sessionId
                    )
                    return@launch
                }

                startTwoZoneSession(
                    sessionId = session.id,
                    sessionName = session.name,
                    introItems = session.introItems.map { QueueTrackSpec(it.entryId, it.track) },
                    mainItems = session.mainItems.map { QueueTrackSpec(it.entryId, it.track) },
                    requestedTargetDurationMs = session.targetDurationMs
                )
            } finally {
                if (pendingStartSessionId == sessionId) {
                    pendingStartSessionId = null
                }
            }
        }
    }

    private fun startQuickMeditateSession(
        introTrackIds: List<String>,
        mainTrackIds: List<String>,
        requestedTargetDurationMs: Long
    ) {
        val sessionId = PlaybackClient.QUICK_MEDITATE_SESSION_ID
        startSessionJob?.cancel()
        pendingStartTrackId = null
        pendingStartSessionId = sessionId
        startSessionJob = serviceScope.launch {
            try {
                val uniqueIds = (introTrackIds + mainTrackIds).distinct()
                val tracksById = uniqueIds.mapNotNull { id ->
                    trackRepository.getTrack(id)?.let { id to it }
                }.toMap()

                val introItems = introTrackIds.mapIndexedNotNull { index, id ->
                    tracksById[id]?.let { QueueTrackSpec("quick_intro_${index}_$id", it) }
                }
                val mainItems = mainTrackIds.mapIndexedNotNull { index, id ->
                    tracksById[id]?.let { QueueTrackSpec("quick_main_${index}_$id", it) }
                }

                startTwoZoneSession(
                    sessionId = sessionId,
                    sessionName = localizedString(R.string.quick_meditate_title),
                    introItems = introItems,
                    mainItems = mainItems,
                    requestedTargetDurationMs = requestedTargetDurationMs
                )
            } finally {
                if (pendingStartSessionId == sessionId) {
                    pendingStartSessionId = null
                }
            }
        }
    }

    private suspend fun startTwoZoneSession(
        sessionId: String,
        sessionName: String,
        introItems: List<QueueTrackSpec>,
        mainItems: List<QueueTrackSpec>,
        requestedTargetDurationMs: Long
    ) {
        if (mainItems.isEmpty()) {
            publishError(
                message = localizedString(R.string.playback_error_session_needs_main),
                errorTrackId = null,
                errorSessionId = sessionId
            )
            return
        }

        val mainTotalDurationMs = mainItems.sumOf { it.track.durationMs }
        val capped = TimerValidation.capDurationMs(
            requestedMs = requestedTargetDurationMs,
            maxDurationMs = mainTotalDurationMs,
            scopeLabel = TimerValidation.SCOPE_MAIN_QUEUE
        )
        if (capped.durationMs <= 0L) {
            publishError(
                message = localizedString(R.string.playback_error_session_invalid_timer),
                errorTrackId = null,
                errorSessionId = sessionId
            )
            return
        }

        val introCount = introItems.size
        val mainCount = mainItems.size

        val queueEntries = ArrayList<ActiveQueueEntry>(introCount + mainCount)
        val mediaItems = ArrayList<MediaItem>(introCount + mainCount)

        val (resolvedIntroFiles, resolvedMainFiles) = withContext(Dispatchers.IO) {
            val intro = introItems.map { item ->
                val file = trackRepository.resolveAudioFile(item.track)
                Triple(item, file, file.exists() && file.canRead())
            }
            val main = mainItems.map { item ->
                val file = trackRepository.resolveAudioFile(item.track)
                Triple(item, file, file.exists() && file.canRead())
            }
            intro to main
        }

        for ((index, resolved) in resolvedIntroFiles.withIndex()) {
            val (item, file, isReadable) = resolved
            if (!isReadable) {
                publishError(
                    message = localizedString(
                        R.string.playback_error_missing_intro_track,
                        item.track.title
                    ),
                    errorTrackId = null,
                    errorSessionId = sessionId
                )
                return
            }
            val trackNum = index + 1
            val subtitle = localizedString(
                R.string.notification_session_intro,
                sessionName,
                trackNum.toString(),
                introCount.toString()
            )
            val metadata = MediaMetadata.Builder()
                .setTitle(item.track.title)
                .setSubtitle(subtitle)
                .setArtist(subtitle)
                .build()
            mediaItems.add(
                MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(metadata)
                    .build()
            )
            queueEntries.add(
                ActiveQueueEntry(
                    trackId = item.track.id,
                    trackTitle = item.track.title,
                    zone = QueueZone.INTRO,
                    zoneTrackNumber = trackNum,
                    zoneTrackCount = introCount
                )
            )
        }

        for ((index, resolved) in resolvedMainFiles.withIndex()) {
            val (item, file, isReadable) = resolved
            if (!isReadable) {
                publishError(
                    message = localizedString(
                        R.string.playback_error_missing_main_track,
                        item.track.title
                    ),
                    errorTrackId = null,
                    errorSessionId = sessionId
                )
                return
            }
            val trackNum = index + 1
            val subtitle = localizedString(
                R.string.notification_session_main,
                sessionName,
                trackNum.toString(),
                mainCount.toString()
            )
            val metadata = MediaMetadata.Builder()
                .setTitle(item.track.title)
                .setSubtitle(subtitle)
                .setArtist(subtitle)
                .build()
            mediaItems.add(
                MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(metadata)
                    .build()
            )
            queueEntries.add(
                ActiveQueueEntry(
                    trackId = item.track.id,
                    trackTitle = item.track.title,
                    zone = QueueZone.MAIN,
                    zoneTrackNumber = trackNum,
                    zoneTrackCount = mainCount
                )
            )
        }

        tickJob?.cancel()
        tickJob = null

        activeSessionId = sessionId
        activeSessionName = sessionName
        activeQueueEntries = queueEntries
        activeTrackIds = queueEntries.map { it.trackId }.toSet()
        activeIntroCount = introCount
        activeMainCount = mainCount
        currentQueueIndex = 0
        activeTargetDurationMs = capped.durationMs

        val hasIntro = introCount > 0
        val startTick = timerEngine.start(
            targetDurationMs = capped.durationMs,
            hasIntro = hasIntro,
            nowMs = SystemClock.elapsedRealtime()
        )

        // Start-Fresh invariant: always index 0, 00:00, and full 1.0f volume
        withInternalPlayerChange {
            exoPlayer.setMediaItems(mediaItems, /* startIndex = */ 0, /* startPositionMs = */ 0L)
            exoPlayer.volume = 1.0f
            exoPlayer.prepare()
            exoPlayer.play()
        }

        if (isPlaybackBlockedOrSuppressed()) {
            pauseTimerOnly()
        } else {
            publishSnapshot(tick = startTick, errorMessage = null)
            if (!hasIntro) {
                startTickLoop()
            }
        }
    }

    private fun handleExternalPlay() {
        when (timerEngine.currentStatus()) {
            PlaybackStatus.INTRO_PAUSED, PlaybackStatus.PAUSED -> resumeSession()
            PlaybackStatus.IDLE, PlaybackStatus.COMPLETED -> {
                val sessionId = activeSessionId
                if (sessionId == PlaybackClient.QUICK_MEDITATE_SESSION_ID) {
                    val introIds = activeQueueEntries
                        .filter { it.zone == QueueZone.INTRO }
                        .map { it.trackId }
                    val mainIds = activeQueueEntries
                        .filter { it.zone == QueueZone.MAIN }
                        .map { it.trackId }
                    if (mainIds.isNotEmpty() && activeTargetDurationMs > 0L) {
                        startQuickMeditateSession(introIds, mainIds, activeTargetDurationMs)
                    }
                } else if (sessionId != null) {
                    startSavedSession(sessionId)
                } else {
                    val quickTrackId = activeQueueEntries.firstOrNull()?.trackId
                    val durationMs = activeTargetDurationMs
                    if (quickTrackId != null && durationMs > 0L) {
                        startQuickTrackSession(quickTrackId, durationMs)
                    }
                }
            }

            PlaybackStatus.INTRO_PLAYING, PlaybackStatus.PLAYING, PlaybackStatus.FADING -> Unit
        }
    }

    private fun pauseSession() {
        val status = timerEngine.currentStatus()
        if (
            status != PlaybackStatus.INTRO_PLAYING &&
            status != PlaybackStatus.PLAYING &&
            status != PlaybackStatus.FADING
        ) {
            // If paused transiently by audio focus suppression, an explicit Pause clears playWhenReady
            // so the player will not auto-resume once the interruption ends.
            if (exoPlayer.playWhenReady) {
                withInternalPlayerChange {
                    exoPlayer.pause()
                }
            }
            return
        }
        tickJob?.cancel()
        tickJob = null
        val pausedTick = timerEngine.pause(SystemClock.elapsedRealtime())
        if (pausedTick.status == PlaybackStatus.COMPLETED) {
            finishSessionAtZero(pausedTick)
            return
        }
        withInternalPlayerChange {
            exoPlayer.pause()
        }
        publishSnapshot(tick = pausedTick, errorMessage = null)
    }

    private fun pauseTimerOnly() {
        val status = timerEngine.currentStatus()
        if (
            status != PlaybackStatus.INTRO_PLAYING &&
            status != PlaybackStatus.PLAYING &&
            status != PlaybackStatus.FADING
        ) {
            return
        }
        tickJob?.cancel()
        tickJob = null
        val pausedTick = timerEngine.pause(SystemClock.elapsedRealtime())
        if (pausedTick.status == PlaybackStatus.COMPLETED) {
            finishSessionAtZero(pausedTick)
            return
        }
        publishSnapshot(tick = pausedTick, errorMessage = null)
    }

    private fun resumeSession() {
        val status = timerEngine.currentStatus()
        if (status != PlaybackStatus.INTRO_PAUSED && status != PlaybackStatus.PAUSED) return
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
            if (
                resumedTick.status == PlaybackStatus.PLAYING ||
                resumedTick.status == PlaybackStatus.FADING
            ) {
                startTickLoop()
            }
        }
    }

    private fun resumeTimerOnly() {
        if (isPlaybackBlockedOrSuppressed()) return
        val status = timerEngine.currentStatus()
        if (status != PlaybackStatus.INTRO_PAUSED && status != PlaybackStatus.PAUSED) return
        val resumedTick = timerEngine.resume(SystemClock.elapsedRealtime())
        if (resumedTick.status == PlaybackStatus.COMPLETED) {
            finishSessionAtZero(resumedTick)
            return
        }
        exoPlayer.volume = resumedTick.volume
        publishSnapshot(tick = resumedTick, errorMessage = null)
        if (
            resumedTick.status == PlaybackStatus.PLAYING ||
            resumedTick.status == PlaybackStatus.FADING
        ) {
            startTickLoop()
        }
    }

    private fun skipIntroToMain() {
        val status = timerEngine.currentStatus()
        if (status != PlaybackStatus.INTRO_PLAYING && status != PlaybackStatus.INTRO_PAUSED) return
        if (
            activeIntroCount <= 0 ||
            activeMainCount <= 0 ||
            activeIntroCount >= activeQueueEntries.size ||
            activeIntroCount >= exoPlayer.mediaItemCount
        ) {
            return
        }

        val skippedTick = timerEngine.skipIntroToMain(SystemClock.elapsedRealtime())
        currentQueueIndex = activeIntroCount
        withInternalPlayerChange {
            exoPlayer.seekTo(activeIntroCount, 0L)
            exoPlayer.volume = skippedTick.volume
            exoPlayer.play()
        }
        if (isPlaybackBlockedOrSuppressed()) {
            pauseTimerOnly()
        } else {
            publishSnapshot(tick = skippedTick, errorMessage = null)
            if (
                skippedTick.status == PlaybackStatus.PLAYING ||
                skippedTick.status == PlaybackStatus.FADING
            ) {
                startTickLoop()
            }
        }
    }

    private fun stopAndReset(clearSelection: Boolean) {
        startSessionJob?.cancel()
        startSessionJob = null
        pendingStartTrackId = null
        pendingStartSessionId = null
        tickJob?.cancel()
        tickJob = null
        val resetTick = timerEngine.stopAndReset()
        currentQueueIndex = 0
        withInternalPlayerChange {
            exoPlayer.pause()
            if (exoPlayer.mediaItemCount > 0) {
                exoPlayer.seekTo(0, 0L)
            }
            exoPlayer.volume = 1.0f
            if (clearSelection) {
                exoPlayer.clearMediaItems()
            }
        }
        if (clearSelection) {
            clearActiveMetadata()
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
        currentQueueIndex = 0
        withInternalPlayerChange {
            exoPlayer.volume = 0.0f
            exoPlayer.pause()
            if (exoPlayer.mediaItemCount > 0) {
                exoPlayer.seekTo(0, 0L)
            }
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

                    PlaybackStatus.INTRO_PLAYING,
                    PlaybackStatus.INTRO_PAUSED,
                    PlaybackStatus.PAUSED,
                    PlaybackStatus.IDLE -> {
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
        errorTrackId: String? = null,
        errorSessionId: String? = null
    ) {
        val currentEntry = activeQueueEntries.getOrNull(currentQueueIndex)
            ?: activeQueueEntries.firstOrNull()
        stateStore.set(
            PlaybackSnapshot(
                status = tick.status,
                activeSessionId = activeSessionId,
                activeSessionName = activeSessionName,
                activeTrackId = currentEntry?.trackId,
                activeTrackTitle = currentEntry?.trackTitle,
                activeTrackIds = activeTrackIds,
                activeIntroTrackIds = activeQueueEntries
                    .filter { it.zone == QueueZone.INTRO }
                    .map { it.trackId },
                activeMainTrackIds = activeQueueEntries
                    .filter { it.zone == QueueZone.MAIN }
                    .map { it.trackId },
                currentZone = currentEntry?.zone,
                currentQueueIndex = currentQueueIndex,
                zoneTrackNumber = currentEntry?.zoneTrackNumber ?: 0,
                zoneTrackCount = currentEntry?.zoneTrackCount ?: 0,
                introCount = activeIntroCount,
                mainCount = activeMainCount,
                targetDurationMs = tick.targetDurationMs,
                remainingMs = tick.remainingMs,
                elapsedMs = tick.elapsedMs,
                volume = tick.volume,
                errorMessage = errorMessage,
                errorTrackId = errorTrackId,
                errorSessionId = errorSessionId
            )
        )
    }

    private fun publishError(
        message: String,
        errorTrackId: String?,
        errorSessionId: String?
    ) {
        tickJob?.cancel()
        tickJob = null
        timerEngine.stopAndReset()
        withInternalPlayerChange {
            exoPlayer.volume = 1.0f
            exoPlayer.pause()
            if (exoPlayer.mediaItemCount > 0) {
                exoPlayer.seekTo(0, 0L)
            }
            exoPlayer.clearMediaItems()
        }
        clearActiveMetadata()
        stateStore.set(
            PlaybackSnapshot(
                status = PlaybackStatus.IDLE,
                errorMessage = message,
                errorTrackId = errorTrackId,
                errorSessionId = errorSessionId
            )
        )
    }

    private fun refreshTrackTitle(trackId: String, newTitle: String) {
        if (trackId !in activeTrackIds && activeQueueEntries.none { it.trackId == trackId }) {
            return
        }

        activeQueueEntries = activeQueueEntries.map { entry ->
            if (entry.trackId == trackId) {
                entry.copy(trackTitle = newTitle)
            } else {
                entry
            }
        }

        withInternalPlayerChange {
            for ((index, entry) in activeQueueEntries.withIndex()) {
                if (entry.trackId == trackId && index < exoPlayer.mediaItemCount) {
                    val existingItem = exoPlayer.getMediaItemAt(index)
                    val updatedMetadata = existingItem.mediaMetadata.buildUpon()
                        .setTitle(newTitle)
                        .build()
                    val updatedItem = existingItem.buildUpon()
                        .setMediaMetadata(updatedMetadata)
                        .build()
                    exoPlayer.replaceMediaItem(index, updatedItem)
                }
            }
        }

        val currentEntry = activeQueueEntries.getOrNull(currentQueueIndex)
            ?: activeQueueEntries.firstOrNull()
        if (currentEntry?.trackId == trackId) {
            stateStore.update { snapshot ->
                if (snapshot.activeTrackId == trackId) {
                    snapshot.copy(activeTrackTitle = newTitle)
                } else {
                    snapshot
                }
            }
        }
    }

    private fun clearActiveMetadata() {
        activeSessionId = null
        activeSessionName = null
        activeQueueEntries = emptyList()
        activeTrackIds = emptySet()
        activeIntroCount = 0
        activeMainCount = 0
        currentQueueIndex = 0
        activeTargetDurationMs = 0L
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
        const val ACTION_START_SAVED_SESSION = "com.dhammameditate.action.START_SAVED_SESSION"
        const val ACTION_START_QUICK_MEDITATE = "com.dhammameditate.action.START_QUICK_MEDITATE"
        const val ACTION_PAUSE_SESSION = "com.dhammameditate.action.PAUSE_SESSION"
        const val ACTION_RESUME_SESSION = "com.dhammameditate.action.RESUME_SESSION"
        const val ACTION_SKIP_TO_MAIN = "com.dhammameditate.action.SKIP_TO_MAIN"
        const val ACTION_STOP_RESET = "ACTION_STOP_RESET"
        const val ACTION_STOP_IF_TRACK = "com.dhammameditate.action.STOP_IF_TRACK"
        const val ACTION_STOP_IF_SESSION = "com.dhammameditate.action.STOP_IF_SESSION"
        const val ACTION_REFRESH_TRACK_TITLE = "com.dhammameditate.action.REFRESH_TRACK_TITLE"

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_INTRO_TRACK_IDS = "extra_intro_track_ids"
        const val EXTRA_MAIN_TRACK_IDS = "extra_main_track_ids"
        const val EXTRA_TARGET_DURATION_MS = "extra_target_duration_ms"

        private const val NORMAL_TICK_MS = 250L
        private const val FADE_TICK_MS = 50L

        val STOP_RESET_COMMAND = SessionCommand(ACTION_STOP_RESET, Bundle.EMPTY)
    }
}
