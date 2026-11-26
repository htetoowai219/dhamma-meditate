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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhammameditate.R
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.preferences.AppLanguage
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.ActiveSessionBanner
import com.dhammameditate.ui.common.LanguageTogglePill
import com.dhammameditate.ui.common.ProvideLocalizedDialogContext
import com.dhammameditate.ui.common.asString
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
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
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
        LibraryHeader(
            currentLanguage = currentLanguage,
            onSelectLanguage = onSelectLanguage
        )

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
                        text = stringResource(R.string.library_importing),
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(
                        text = stringResource(R.string.library_import_button),
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
                ProvideLocalizedDialogContext {
                    Text(
                        text = stringResource(R.string.library_delete_track_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                }
            },
            text = {
                ProvideLocalizedDialogContext {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.library_delete_track_message, track.title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                        if (isTrackInActivePlayback) {
                            Text(
                                text = stringResource(R.string.library_delete_track_active_warning),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = AlertRed
                            )
                        }
                        if (summary.isReferenced) {
                            Text(
                                text = stringResource(
                                    if (summary.affectedSessions.size == 1) {
                                        R.string.library_delete_track_used_one
                                    } else {
                                        R.string.library_delete_track_used_other
                                    },
                                    summary.affectedSessions.size.toString()
                                ),
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
                                                text = "  ${stringResource(R.string.library_delete_track_empties_main)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AlertRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ProvideLocalizedDialogContext {
                    TextButton(onClick = viewModel::confirmDeleteTrack) {
                        Text(
                            text = stringResource(R.string.common_delete),
                            style = MaterialTheme.typography.labelLarge,
                            color = AlertRed
                        )
                    }
                }
            },
            dismissButton = {
                ProvideLocalizedDialogContext {
                    TextButton(onClick = viewModel::dismissDeleteTrack) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                    }
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
                    ProvideLocalizedDialogContext {
                        Text(
                            text = stringResource(R.string.rename_batch_prompt_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = InkPrimary
                        )
                    }
                },
                text = {
                    ProvideLocalizedDialogContext {
                        Text(
                            text = stringResource(
                                R.string.rename_batch_prompt_message,
                                importRename.tracks.size.toString()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                    }
                },
                confirmButton = {
                    ProvideLocalizedDialogContext {
                        TextButton(onClick = viewModel::beginBatchImportRename) {
                            Text(
                                text = stringResource(R.string.rename_batch_confirm),
                                style = MaterialTheme.typography.labelLarge,
                                color = MonasticMaroon
                            )
                        }
                    }
                },
                dismissButton = {
                    ProvideLocalizedDialogContext {
                        TextButton(onClick = viewModel::skipAllImportRenames) {
                            Text(
                                text = stringResource(R.string.rename_batch_skip_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = InkSecondary
                            )
                        }
                    }
                }
            )
        }

        is ImportRenameState.Renaming -> {
            key(importRename.currentTrack.id) {
                TrackRenameDialog(
                title = if (importRename.isBatch) {
                    stringResource(
                        R.string.rename_step_title,
                        importRename.stepNumber.toString(),
                        importRename.totalCount.toString()
                    )
                } else {
                    stringResource(R.string.rename_single_import_title)
                },
                durationMs = importRename.currentTrack.durationMs,
                draftTitle = importRename.draftTitle,
                showBlankError = importRename.showBlankError,
                canSave = importRename.canSave,
                confirmLabel = if (importRename.isBatch && !importRename.isLastStep) {
                    stringResource(R.string.rename_save_and_next)
                } else {
                    stringResource(R.string.common_save)
                },
                secondaryLabel = if (importRename.isBatch) {
                    stringResource(R.string.rename_skip)
                } else {
                    stringResource(R.string.rename_keep_default)
                },
                tertiaryLabel = if (importRename.isBatch) {
                    stringResource(R.string.rename_skip_remaining)
                } else {
                    null
                },
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
    }

    uiState.pendingTrackRename?.let { pending ->
        TrackRenameDialog(
            title = stringResource(R.string.rename_existing_title),
            durationMs = pending.track.durationMs,
            draftTitle = pending.draftTitle,
            showBlankError = pending.showBlankError,
            canSave = pending.canSave,
            confirmLabel = stringResource(R.string.common_save),
            secondaryLabel = stringResource(R.string.common_cancel),
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
private fun LibraryHeader(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MonasticMaroon)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.library_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = WhiteMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        LanguageTogglePill(
            currentLanguage = currentLanguage,
            onSelectLanguage = onSelectLanguage
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
                        stringResource(R.string.library_import_error_title_one)
                    } else {
                        stringResource(R.string.library_import_error_title_other, failures.size.toString())
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = AlertRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                failures.forEach { failure ->
                    Text(
                        text = "${failure.fileName.asString()}: ${failure.reason.asString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.library_dismiss_import_error_cd),
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
                text = stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        ProvideLocalizedDialogContext {
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
                    label = { Text(text = stringResource(R.string.rename_field_label)) },
                    singleLine = true,
                    isError = showBlankError,
                    supportingText = if (showBlankError) {
                        {
                            Text(
                                text = stringResource(R.string.rename_blank_error),
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = onSecondary) {
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                    }
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
                    contentDescription = stringResource(R.string.library_rename_cd, track.title),
                    tint = InkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.library_delete_cd, track.title),
                    tint = InkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.library_open_cd, track.title),
                tint = InkSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
