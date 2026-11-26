package com.dhammameditate.ui.sessionplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhammameditate.R
import com.dhammameditate.data.repository.QueueTrackItem
import com.dhammameditate.data.repository.SessionWithQueues
import com.dhammameditate.playback.DistractionChecker
import com.dhammameditate.playback.PlaybackStatus
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.rememberSessionStartLauncher
import com.dhammameditate.ui.theme.AlertRed
import com.dhammameditate.ui.theme.AlertSurface
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.CountdownTextStyle
import com.dhammameditate.ui.theme.InkPrimary
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LotusGold
import com.dhammameditate.ui.theme.MaroonOutline
import com.dhammameditate.ui.theme.MaroonSurfaceSubtle
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.ProgressTrackDark
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.WhiteMuted

@Composable
fun SessionPlayerScreen(
    viewModel: SessionPlayerViewModel,
    distractionChecker: DistractionChecker,
    askedNotificationPermission: Boolean,
    onNotificationPermissionAsked: () -> Unit,
    onEditSession: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requestStartSession = rememberSessionStartLauncher(
        distractionChecker = distractionChecker,
        status = uiState.status,
        canStart = uiState.canStart,
        askedNotificationPermission = askedNotificationPermission,
        onNotificationPermissionAsked = onNotificationPermissionAsked,
        onStartSession = viewModel::startSession
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MonasticMaroon)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        SessionPlayerTopBar(
            canEdit = uiState.canEdit,
            onEditClick = { uiState.session?.let { onEditSession(it.id) } },
            onBack = onBack
        )

        val session = uiState.session
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Saffron, strokeWidth = 2.5.dp)
                }
            }

            session == null -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = CardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, OutlineNeutral),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.session_player_unavailable_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.session_player_unavailable_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Saffron,
                                    contentColor = PureWhite
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_back_to_sessions),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ZoneStatusPill(
                        label = zonePillLabel(uiState, session),
                        isHighlighted = uiState.isActive
                    )

                    if (!uiState.activeTrackTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.activeTrackTitle.orEmpty(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                            color = WhiteMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = TimerValidation.formatDuration(uiState.displayedRemainingMs),
                        style = CountdownTextStyle,
                        color = PureWhite
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = sessionStatusSubtitle(uiState.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiteMuted
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Strictly non-interactive flat 4dp progress bar
                    SessionProgressBar(
                        progress = uiState.progressFraction,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    if (session.needsMainTrack) {
                        NeedsMainTrackBanner(
                            onEditSession = { onEditSession(session.id) },
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }

                    uiState.errorMessage?.let { message ->
                        SessionErrorBanner(
                            message = message,
                            onDismiss = viewModel::dismissError,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }

                    // Strictly read-only queue overview (no seek, no skip, no row click handlers)
                    ReadOnlyQueueOverviewCard(
                        session = session,
                        activeQueueIndex = uiState.currentQueueIndex
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                SessionTransportControls(
                    status = uiState.status,
                    canStart = uiState.canStart,
                    canSkipToMain = uiState.canSkipToMain,
                    onStart = requestStartSession,
                    onPause = viewModel::pauseSession,
                    onResume = viewModel::resumeSession,
                    onSkipToMain = viewModel::skipToMain,
                    onStopAndReset = viewModel::stopAndReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionPlayerTopBar(
    canEdit: Boolean,
    onEditClick: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.editor_back_to_sessions),
                tint = PureWhite
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.session_player_title),
            style = MaterialTheme.typography.titleMedium,
            color = PureWhite,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onEditClick,
            enabled = canEdit
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.session_player_edit_cd),
                tint = if (canEdit) PureWhite else WhiteMuted.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun ZoneStatusPill(
    label: String,
    isHighlighted: Boolean
) {
    Surface(
        color = MaroonSurfaceSubtle,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isHighlighted) LotusGold else MaroonOutline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isHighlighted) PureWhite else WhiteMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SessionProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(ProgressTrackDark)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .background(LotusGold)
            )
        }
    }
}

@Composable
private fun NeedsMainTrackBanner(
    onEditSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AlertSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AlertRed),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.session_player_needs_main_banner),
                style = MaterialTheme.typography.bodySmall,
                color = AlertRed,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEditSession) {
                Text(
                    text = stringResource(R.string.editor_title_edit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MonasticMaroon
                )
            }
        }
    }
}

@Composable
private fun SessionErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AlertSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AlertRed),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AlertRed,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.player_dismiss_error_cd),
                    tint = AlertRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyQueueOverviewCard(
    session: SessionWithQueues,
    activeQueueIndex: Int?
) {
    Surface(
        color = MaroonSurfaceSubtle,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaroonOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (session.introItems.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.session_player_queue_intro_header,
                        TimerValidation.formatDuration(session.introTotalDurationMs)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = WhiteMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                session.introItems.forEachIndexed { index, item ->
                    ReadOnlyQueueRow(
                        item = item,
                        isActive = activeQueueIndex == index
                    )
                    if (index < session.introItems.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = MaroonOutline)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = stringResource(
                    R.string.session_player_queue_main_header,
                    TimerValidation.formatDuration(session.mainTotalDurationMs)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = WhiteMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (session.mainItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_player_queue_main_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = WhiteMuted
                )
            } else {
                val mainOffset = session.introItems.size
                session.mainItems.forEachIndexed { index, item ->
                    ReadOnlyQueueRow(
                        item = item,
                        isActive = activeQueueIndex == mainOffset + index
                    )
                    if (index < session.mainItems.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyQueueRow(
    item: QueueTrackItem,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isActive) LotusGold else MaroonOutline)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = item.track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isActive) PureWhite else WhiteMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = TimerValidation.formatDuration(item.track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) LotusGold else WhiteMuted
        )
    }
}

@Composable
private fun SessionTransportControls(
    status: PlaybackStatus,
    canStart: Boolean,
    canSkipToMain: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkipToMain: () -> Unit,
    onStopAndReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (status) {
            PlaybackStatus.IDLE, PlaybackStatus.COMPLETED -> {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Saffron,
                        contentColor = PureWhite,
                        disabledContainerColor = Saffron.copy(alpha = 0.45f),
                        disabledContentColor = PureWhite.copy(alpha = 0.7f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = stringResource(R.string.transport_start_session),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            PlaybackStatus.INTRO_PLAYING, PlaybackStatus.PLAYING, PlaybackStatus.FADING -> {
                Button(
                    onClick = onPause,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Saffron,
                        contentColor = PureWhite
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = stringResource(R.string.transport_pause),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                SecondaryTransportRow(
                    canSkipToMain = canSkipToMain,
                    onSkipToMain = onSkipToMain,
                    onStopAndReset = onStopAndReset
                )
            }

            PlaybackStatus.INTRO_PAUSED, PlaybackStatus.PAUSED -> {
                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Saffron,
                        contentColor = PureWhite
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = stringResource(R.string.transport_continue),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                SecondaryTransportRow(
                    canSkipToMain = canSkipToMain,
                    onSkipToMain = onSkipToMain,
                    onStopAndReset = onStopAndReset
                )
            }
        }
    }
}

@Composable
private fun SecondaryTransportRow(
    canSkipToMain: Boolean,
    onSkipToMain: () -> Unit,
    onStopAndReset: () -> Unit
) {
    if (canSkipToMain) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSkipToMain,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LotusGold),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaroonSurfaceSubtle,
                    contentColor = PureWhite
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.transport_skip_to_main),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onStopAndReset,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaroonOutline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PureWhite
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_stop_reset),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onStopAndReset,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaroonOutline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PureWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
        ) {
            Text(
                text = stringResource(R.string.action_stop_reset),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun zonePillLabel(
    uiState: SessionPlayerUiState,
    session: SessionWithQueues
): String = when (uiState.status) {
    PlaybackStatus.INTRO_PLAYING, PlaybackStatus.INTRO_PAUSED ->
        stringResource(
            R.string.session_player_pill_intro,
            uiState.zoneTrackNumber.toString(),
            uiState.zoneTrackCount.toString()
        )

    PlaybackStatus.PLAYING, PlaybackStatus.PAUSED, PlaybackStatus.FADING ->
        stringResource(
            R.string.session_player_pill_main,
            uiState.zoneTrackNumber.toString(),
            uiState.zoneTrackCount.toString()
        )

    PlaybackStatus.COMPLETED -> stringResource(R.string.session_player_pill_completed)

    PlaybackStatus.IDLE -> if (session.introItems.isNotEmpty()) {
        stringResource(R.string.session_player_pill_ready_intro_main)
    } else {
        stringResource(R.string.session_player_pill_ready_main_only)
    }
}

@Composable
private fun sessionStatusSubtitle(status: PlaybackStatus): String = stringResource(
    when (status) {
        PlaybackStatus.IDLE -> R.string.status_ready
        PlaybackStatus.INTRO_PLAYING -> R.string.status_intro_playing_session
        PlaybackStatus.INTRO_PAUSED -> R.string.status_intro_paused_session
        PlaybackStatus.PLAYING -> R.string.status_playing_main
        PlaybackStatus.PAUSED -> R.string.status_paused
        PlaybackStatus.FADING -> R.string.status_fading
        PlaybackStatus.COMPLETED -> R.string.status_completed
    }
)
