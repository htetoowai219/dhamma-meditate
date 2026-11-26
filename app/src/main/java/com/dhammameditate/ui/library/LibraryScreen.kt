package com.dhammameditate.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.ActiveSessionBanner
import com.dhammameditate.ui.theme.AlertRed
import com.dhammameditate.ui.theme.AlertSurface
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.DeepSaffron
import com.dhammameditate.ui.theme.InkPrimary
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LightNeutral
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.WhiteMuted

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenTrack: (String) -> Unit,
    onOpenActivePlayback: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightNeutral)
    ) {
        LibraryHeader()

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

            if (uiState.importFailures.isNotEmpty()) {
                ImportErrorBanner(
                    failures = uiState.importFailures,
                    onDismiss = viewModel::dismissImportErrors,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
            }

            if (uiState.tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyLibraryCard()
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
                        items = uiState.tracks,
                        key = { it.id }
                    ) { track ->
                        TrackCard(
                            track = track,
                            isActiveTrack = uiState.playback.isActive && uiState.playback.activeTrackId == track.id,
                            onClick = { onOpenTrack(track.id) },
                            onRenameClick = { viewModel.requestRenameTrack(track) },
                            onDeleteClick = { viewModel.requestDeleteTrack(track) }
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
                onClick = {
                    documentPickerLauncher.launch(arrayOf("audio/*", "application/ogg"))
                },
                enabled = !uiState.isImporting,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Saffron,
                    contentColor = PureWhite,
                    disabledContainerColor = DeepSaffron,
                    disabledContentColor = PureWhite
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(54.dp)
            ) {
                if (uiState.isImporting) {
                    CircularProgressIndicator(
                        color = PureWhite,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Importing...",
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(
                        text = "Import Audio",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    uiState.pendingTrackDeletion?.let { pending ->
        val track = pending.track
        val summary = pending.referenceSummary
        val isTrackInActivePlayback = uiState.playback.isActive &&
            (uiState.playback.activeTrackId == track.id || track.id in uiState.playback.activeTrackIds)

        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteTrack,
            modifier = Modifier.border(1.dp, OutlineNeutral, RoundedCornerShape(16.dp)),
            containerColor = CardSurface,
            titleContentColor = InkPrimary,
            textContentColor = InkSecondary,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Delete track?",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Remove \"${track.title}\" from your offline library? This deletes the app-private audio copy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    if (isTrackInActivePlayback) {
                        Text(
                            text = "Active playback using this track will stop and reset.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = AlertRed
                        )
                    }
                    if (summary.isReferenced) {
                        Text(
                            text = "Used in ${summary.affectedSessions.size} saved ${if (summary.affectedSessions.size == 1) "session" else "sessions"} (timers will adjust if shortened):",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = InkPrimary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            summary.affectedSessions.forEach { affected ->
                                Column {
                                    Text(
                                        text = "• ${affected.sessionName}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = InkPrimary
                                    )
                                    if (affected.willEmptyMainQueue) {
                                        Text(
                                            text = "  Will need a new Main track before playing",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AlertRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteTrack) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = AlertRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteTrack) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                }
            }
        )
    }

    when (val importRename = uiState.importRenameState) {
        ImportRenameState.Hidden -> Unit

        is ImportRenameState.BatchPrompt -> {
            AlertDialog(
                onDismissRequest = viewModel::skipAllImportRenames,
                modifier = Modifier.border(1.dp, OutlineNeutral, RoundedCornerShape(16.dp)),
                containerColor = CardSurface,
                titleContentColor = InkPrimary,
                textContentColor = InkSecondary,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Rename imported tracks?",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                },
                text = {
                    Text(
                        text = "Imported ${importRename.tracks.size} tracks. Would you like to customize their display names?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::beginBatchImportRename) {
                        Text(
                            text = "Rename tracks",
                            style = MaterialTheme.typography.labelLarge,
                            color = MonasticMaroon
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::skipAllImportRenames) {
                        Text(
                            text = "Skip all",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                    }
                }
            )
        }

        is ImportRenameState.Renaming -> {
            TrackRenameDialog(
                title = if (importRename.isBatch) {
                    "Rename track (${importRename.stepNumber} of ${importRename.totalCount})"
                } else {
                    "Name imported track"
                },
                durationMs = importRename.currentTrack.durationMs,
                draftTitle = importRename.draftTitle,
                showBlankError = importRename.showBlankError,
                canSave = importRename.canSave,
                confirmLabel = if (importRename.isBatch && !importRename.isLastStep) {
                    "Save & Next"
                } else {
                    "Save"
                },
                secondaryLabel = if (importRename.isBatch) "Skip" else "Keep default",
                tertiaryLabel = if (importRename.isBatch) "Skip remaining" else null,
                imeAction = if (importRename.isBatch && !importRename.isLastStep) {
                    ImeAction.Next
                } else {
                    ImeAction.Done
                },
                onDraftTitleChange = viewModel::onImportRenameDraftChanged,
                onConfirm = viewModel::confirmCurrentImportRename,
                onSecondary = if (importRename.isBatch) {
                    viewModel::skipCurrentImportRename
                } else {
                    viewModel::skipAllImportRenames
                },
                onTertiary = if (importRename.isBatch) {
                    viewModel::skipAllImportRenames
                } else {
                    null
                },
                onDismiss = viewModel::skipAllImportRenames
            )
        }
    }

    uiState.pendingTrackRename?.let { pending ->
        TrackRenameDialog(
            title = "Rename track",
            durationMs = pending.track.durationMs,
            draftTitle = pending.draftTitle,
            showBlankError = pending.showBlankError,
            canSave = pending.canSave,
            confirmLabel = "Save",
            secondaryLabel = "Cancel",
            tertiaryLabel = null,
            imeAction = ImeAction.Done,
            onDraftTitleChange = viewModel::onExistingTrackRenameDraftChanged,
            onConfirm = viewModel::confirmRenameTrack,
            onSecondary = viewModel::dismissRenameTrack,
            onTertiary = null,
            onDismiss = viewModel::dismissRenameTrack
        )
    }
}

@Composable
private fun LibraryHeader() {
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
            text = "Offline Audio Library",
            style = MaterialTheme.typography.bodyMedium,
            color = WhiteMuted
        )
    }
}

@Composable
private fun ImportErrorBanner(
    failures: List<ImportFailureItem>,
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
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (failures.size == 1) {
                        "Could not import 1 file"
                    } else {
                        "Could not import ${failures.size} files"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = AlertRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                failures.forEach { failure ->
                    Text(
                        text = "${failure.fileName}: ${failure.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss import error",
                    tint = AlertRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard() {
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
                    imageVector = Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No offline audio yet",
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Import local MP3, M4A, AAC, WAV, FLAC, or OGG recordings. Files are copied into private offline storage so they remain available anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
    }
}

@Composable
private fun TrackRenameDialog(
    title: String,
    durationMs: Long,
    draftTitle: String,
    showBlankError: Boolean,
    canSave: Boolean,
    confirmLabel: String,
    secondaryLabel: String,
    tertiaryLabel: String?,
    imeAction: ImeAction,
    onDraftTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onSecondary: () -> Unit,
    onTertiary: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = CardSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, OutlineNeutral),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = TimerValidation.formatDuration(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = onDraftTitleChange,
                    label = { Text(text = "Track name") },
                    singleLine = true,
                    isError = showBlankError,
                    supportingText = if (showBlankError) {
                        {
                            Text(
                                text = TrackTitleValidation.BLANK_ERROR_MESSAGE,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertRed
                            )
                        }
                    } else {
                        null
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onDone = { onConfirm() },
                        onNext = { onConfirm() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MonasticMaroon,
                        unfocusedBorderColor = OutlineNeutral,
                        errorBorderColor = AlertRed,
                        focusedLabelColor = MonasticMaroon,
                        unfocusedLabelColor = InkSecondary,
                        errorLabelColor = AlertRed,
                        cursorColor = MonasticMaroon
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSecondary) {
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onConfirm,
                        enabled = canSave
                    ) {
                        Text(
                            text = confirmLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canSave) MonasticMaroon else InkSecondary.copy(alpha = 0.45f)
                        )
                    }
                }
                if (tertiaryLabel != null && onTertiary != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TextButton(onClick = onTertiary) {
                            Text(
                                text = tertiaryLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: TrackEntity,
    isActiveTrack: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isActiveTrack) MonasticMaroon else OutlineNeutral),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Saffron),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = TimerValidation.formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
            IconButton(onClick = onRenameClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Rename ${track.title}",
                    tint = InkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete ${track.title}",
                    tint = InkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Open ${track.title}",
                tint = InkSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
