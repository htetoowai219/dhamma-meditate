package com.dhammameditate.ui.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhammameditate.data.repository.SessionWithQueues
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.ActiveSessionBanner
import com.dhammameditate.ui.theme.AlertRed
import com.dhammameditate.ui.theme.AlertSurface
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.InkPrimary
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LightNeutral
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.SaffronTintSurface
import com.dhammameditate.ui.theme.WhiteMuted

@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onEditSession: (String) -> Unit,
    onOpenActivePlayback: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteSession by remember { mutableStateOf<SessionWithQueues?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightNeutral)
    ) {
        SessionsHeader()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.playback.isActive) {
                ActiveSessionBanner(
                    playback = uiState.playback,
                    onClick = onOpenActivePlayback,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                )
            }

            if (uiState.sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptySessionsCard(hasLibraryTracks = uiState.hasLibraryTracks)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.sessions,
                        key = { it.id }
                    ) { session ->
                        val isActiveSession = uiState.playback.isActive &&
                            uiState.playback.activeSessionId == session.id
                        SessionCard(
                            session = session,
                            isActiveSession = isActiveSession,
                            onClick = {
                                if (session.needsMainTrack) {
                                    onEditSession(session.id)
                                } else {
                                    onOpenSession(session.id)
                                }
                            },
                            onEditClick = { onEditSession(session.id) },
                            onDeleteClick = { pendingDeleteSession = session }
                        )
                    }
                }
            }
        }

        // Anchored flat primary CTA above bottom NavigationBar
        Surface(
            color = LightNeutral,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onNewSession,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Saffron,
                    contentColor = PureWhite
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(54.dp)
            ) {
                Text(
                    text = "New Session",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    pendingDeleteSession?.let { session ->
        val isActiveSession = uiState.playback.isActive && uiState.playback.activeSessionId == session.id
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            containerColor = CardSurface,
            titleContentColor = InkPrimary,
            textContentColor = InkSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Delete session?",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
            },
            text = {
                val extra = if (isActiveSession) {
                    " Active playback for this session will stop and reset."
                } else {
                    ""
                }
                Text(
                    text = "Remove \"${session.name}\"? Your imported audio files in the Library will remain intact.$extra",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        pendingDeleteSession = null
                    }
                ) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = AlertRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSession = null }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                }
            }
        )
    }
}

@Composable
private fun SessionsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MonasticMaroon)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(
            text = "DhammaMeditate",
            style = MaterialTheme.typography.headlineMedium,
            color = PureWhite
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Meditation Sessions",
            style = MaterialTheme.typography.bodyMedium,
            color = WhiteMuted
        )
    }
}

@Composable
private fun EmptySessionsCard(hasLibraryTracks: Boolean) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Saffron),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SelfImprovement,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No meditation sessions yet",
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasLibraryTracks) {
                    "Combine an optional untimed Intro Queue with a timed Main Queue and a saved countdown. Tap New Session below to create your first session."
                } else {
                    "Build reusable two-zone sessions with an optional untimed Intro Queue and a timed Main Queue. Import audio in the Library tab first, then tap New Session."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionWithQueues,
    isActiveSession: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val introSummary = if (session.introItems.isEmpty()) {
        "No Intro"
    } else {
        "Intro: ${session.introItems.size} ${trackWord(session.introItems.size)} (${TimerValidation.formatDuration(session.introTotalDurationMs)})"
    }
    val mainSummary = if (session.mainItems.isEmpty()) {
        "Main: 0 tracks"
    } else {
        "Main: ${session.mainItems.size} ${trackWord(session.mainItems.size)} (${TimerValidation.formatDuration(session.mainTotalDurationMs)})"
    }

    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isActiveSession) MonasticMaroon else OutlineNeutral),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$introSummary · $mainSummary",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit ${session.name}",
                        tint = InkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete ${session.name}",
                        tint = InkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (session.needsMainTrack) {
                Surface(
                    color = AlertSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AlertRed)
                ) {
                    Text(
                        text = "Needs Main Track",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlertRed,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else {
                Surface(
                    color = SaffronTintSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Saffron)
                ) {
                    Text(
                        text = "Timer ${TimerValidation.formatDuration(session.targetDurationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MonasticMaroon,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun trackWord(count: Int): String = if (count == 1) "track" else "tracks"
