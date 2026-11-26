package com.dhammameditate.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhammameditate.R
import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.ProvideLocalizedDialogContext
import com.dhammameditate.ui.common.UiText
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
import com.dhammameditate.ui.theme.SaffronTintSurface
import com.dhammameditate.ui.tutorial.TutorialTargetId
import com.dhammameditate.ui.tutorial.tutorialTarget

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionEditorScreen(
    viewModel: SessionEditorViewModel,
    activeTutorialTargetId: TutorialTargetId? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activePickerZone by remember { mutableStateOf<QueueZone?>(null) }
    val introBringIntoView = remember { BringIntoViewRequester() }
    val mainBringIntoView = remember { BringIntoViewRequester() }
    val timerBringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(activeTutorialTargetId, uiState.isLoading, uiState.sessionNotFound) {
        if (uiState.isLoading || uiState.sessionNotFound) return@LaunchedEffect
        when (activeTutorialTargetId) {
            TutorialTargetId.EDITOR_INTRO_CARD -> introBringIntoView.bringIntoView()
            TutorialTargetId.EDITOR_MAIN_CARD -> mainBringIntoView.bringIntoView()
            TutorialTargetId.EDITOR_TIMER_CARD -> timerBringIntoView.bringIntoView()
            else -> Unit
        }
    }

    BackHandler(enabled = !uiState.isSaving && activeTutorialTargetId == null, onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightNeutral)
    ) {
        EditorTopBar(
            title = stringResource(
                if (uiState.isEditMode) R.string.editor_title_edit else R.string.editor_title_new
            ),
            backEnabled = !uiState.isSaving,
            onBack = onBack
        )

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

            uiState.sessionNotFound -> {
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
                                text = stringResource(R.string.editor_not_found_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.editor_not_found_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
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
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (uiState.isEditingActiveSession) {
                        Surface(
                            color = SaffronTintSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Saffron),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.editor_active_warning),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MonasticMaroon,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }

                    uiState.saveError?.let { error ->
                        Surface(
                            color = AlertSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, AlertRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertRed,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }

                    // 1. Session Name Card
                    SessionNameCard(
                        name = uiState.name,
                        showError = uiState.showNameError,
                        onNameChanged = viewModel::onNameChanged
                    )

                    // 2. Intro Queue Card (Optional · Untimed)
                    QueueZoneCard(
                        title = stringResource(R.string.editor_intro_title),
                        subtitle = stringResource(R.string.editor_intro_subtitle),
                        items = uiState.introItems,
                        emptyMessage = stringResource(R.string.editor_intro_empty),
                        emptyIsError = false,
                        footerText = if (uiState.introItems.isNotEmpty()) {
                            stringResource(
                                R.string.editor_intro_footer,
                                TimerValidation.formatDuration(uiState.introTotalDurationMs)
                            )
                        } else {
                            null
                        },
                        onAddTrackClick = { activePickerZone = QueueZone.INTRO },
                        onMoveUp = { index -> viewModel.moveTrackUp(QueueZone.INTRO, index) },
                        onMoveDown = { index -> viewModel.moveTrackDown(QueueZone.INTRO, index) },
                        onRemove = { index -> viewModel.removeTrack(QueueZone.INTRO, index) },
                        modifier = Modifier
                            .bringIntoViewRequester(introBringIntoView)
                            .tutorialTarget(TutorialTargetId.EDITOR_INTRO_CARD)
                    )

                    // 3. Main Queue Card (Required · Timed)
                    QueueZoneCard(
                        title = stringResource(R.string.editor_main_title),
                        subtitle = stringResource(
                            R.string.editor_main_subtitle,
                            TimerValidation.formatDuration(uiState.mainTotalDurationMs)
                        ),
                        items = uiState.mainItems,
                        emptyMessage = stringResource(R.string.editor_main_empty),
                        emptyIsError = true,
                        footerText = null,
                        onAddTrackClick = { activePickerZone = QueueZone.MAIN },
                        onMoveUp = { index -> viewModel.moveTrackUp(QueueZone.MAIN, index) },
                        onMoveDown = { index -> viewModel.moveTrackDown(QueueZone.MAIN, index) },
                        onRemove = { index -> viewModel.removeTrack(QueueZone.MAIN, index) },
                        modifier = Modifier
                            .bringIntoViewRequester(mainBringIntoView)
                            .tutorialTarget(TutorialTargetId.EDITOR_MAIN_CARD)
                    )

                    // 4. Meditation Timer Card (Validated against combined Main Queue)
                    SessionTimerCard(
                        mainTotalDurationMs = uiState.mainTotalDurationMs,
                        selectedDurationMs = uiState.targetDurationMs,
                        isFullSelected = uiState.isFullSelected,
                        customMinutesInput = uiState.customMinutesInput,
                        capNotice = uiState.capNotice,
                        enabled = uiState.mainItems.isNotEmpty(),
                        onSelectPreset = viewModel::selectPresetMinutes,
                        onSelectFull = viewModel::selectFullMainQueue,
                        onCustomMinutesChanged = viewModel::onCustomMinutesChanged,
                        modifier = Modifier
                            .bringIntoViewRequester(timerBringIntoView)
                            .tutorialTarget(TutorialTargetId.EDITOR_TIMER_CARD)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Anchored bottom Save CTA
                Surface(
                    color = LightNeutral,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(thickness = 1.dp, color = OutlineNeutral)
                        Button(
                            onClick = { viewModel.saveSession(onSaved) },
                            enabled = uiState.canSave,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Saffron,
                                contentColor = PureWhite,
                                disabledContainerColor = OutlineNeutral,
                                disabledContentColor = InkSecondary
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .height(54.dp)
                                .tutorialTarget(TutorialTargetId.EDITOR_SAVE_BUTTON)
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    color = PureWhite,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.editor_saving),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.editor_save_button),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    activePickerZone?.let { targetZone ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activePickerZone = null },
            sheetState = sheetState,
            containerColor = CardSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 0.dp
        ) {
            ProvideLocalizedDialogContext {
                TrackPickerSheetContent(
                    zone = targetZone,
                    tracks = uiState.libraryTracks,
                    onSelectTrack = { track ->
                        viewModel.addTrack(targetZone, track)
                        activePickerZone = null
                    },
                    onDismiss = { activePickerZone = null }
                )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    backEnabled: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MonasticMaroon)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            enabled = backEnabled
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = PureWhite
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = PureWhite
        )
    }
}

@Composable
private fun SessionNameCard(
    name: String,
    showError: Boolean,
    onNameChanged: (String) -> Unit
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_session_name_label),
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                singleLine = true,
                isError = showError,
                label = {
                    Text(
                        text = stringResource(R.string.editor_session_name_label),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Saffron,
                    unfocusedBorderColor = OutlineNeutral,
                    errorBorderColor = AlertRed,
                    focusedLabelColor = MonasticMaroon,
                    unfocusedLabelColor = InkSecondary,
                    errorLabelColor = AlertRed,
                    focusedTextColor = InkPrimary,
                    unfocusedTextColor = InkPrimary,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = PureWhite,
                    errorContainerColor = PureWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (showError) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.editor_session_name_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed
                )
            }
        }
    }
}

@Composable
private fun QueueZoneCard(
    title: String,
    subtitle: String,
    items: List<EditorQueueItem>,
    emptyMessage: String,
    emptyIsError: Boolean,
    footerText: String?,
    onAddTrackClick: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onAddTrackClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MonasticMaroon),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MonasticMaroon
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.editor_add_track),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (items.isEmpty()) {
                Surface(
                    color = if (emptyIsError) AlertSurface else LightNeutral,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (emptyIsError) AlertRed else OutlineNeutral),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (emptyIsError) AlertRed else InkSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEachIndexed { index, item ->
                        QueueTrackRow(
                            positionNumber = index + 1,
                            track = item.track,
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            onMoveUp = { onMoveUp(index) },
                            onMoveDown = { onMoveDown(index) },
                            onRemove = { onRemove(index) }
                        )
                    }
                }
            }

            if (footerText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = footerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    positionNumber: Int,
    track: TrackEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(LightNeutral),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = positionNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkPrimary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = InkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = TimerValidation.formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowUpward,
                    contentDescription = stringResource(R.string.editor_move_up_cd),
                    tint = if (canMoveUp) InkPrimary else OutlineNeutral,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = stringResource(R.string.editor_move_down_cd),
                    tint = if (canMoveDown) InkPrimary else OutlineNeutral,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.editor_remove_track_cd, track.title),
                    tint = InkSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionTimerCard(
    mainTotalDurationMs: Long,
    selectedDurationMs: Long,
    isFullSelected: Boolean,
    customMinutesInput: String,
    capNotice: UiText?,
    enabled: Boolean,
    onSelectPreset: (Int) -> Unit,
    onSelectFull: () -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_timer_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = InkPrimary
                )
                Text(
                    text = if (enabled && selectedDurationMs > 0L) {
                        TimerValidation.formatDuration(selectedDurationMs)
                    } else {
                        "--:--"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MonasticMaroon
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.editor_timer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row 1: 10m, 15m, 30m
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 15, 30).forEach { minutes ->
                    val chipEnabled = enabled && TimerValidation.isPresetEnabled(minutes, mainTotalDurationMs)
                    val selected = enabled &&
                        !isFullSelected &&
                        customMinutesInput.isEmpty() &&
                        selectedDurationMs == minutes.toLong() * 60_000L
                    EditorPresetChip(
                        label = stringResource(R.string.timer_preset_minutes, minutes.toString()),
                        selected = selected,
                        enabled = chipEnabled,
                        onClick = { onSelectPreset(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: 45m, 60m, Full
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(45, 60).forEach { minutes ->
                    val chipEnabled = enabled && TimerValidation.isPresetEnabled(minutes, mainTotalDurationMs)
                    val selected = enabled &&
                        !isFullSelected &&
                        customMinutesInput.isEmpty() &&
                        selectedDurationMs == minutes.toLong() * 60_000L
                    EditorPresetChip(
                        label = stringResource(R.string.timer_preset_minutes, minutes.toString()),
                        selected = selected,
                        enabled = chipEnabled,
                        onClick = { onSelectPreset(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
                EditorPresetChip(
                    label = stringResource(R.string.timer_preset_full),
                    selected = enabled && isFullSelected && customMinutesInput.isEmpty(),
                    enabled = enabled,
                    onClick = onSelectFull,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = customMinutesInput,
                onValueChange = onCustomMinutesChanged,
                enabled = enabled,
                singleLine = true,
                label = {
                    Text(
                        text = stringResource(R.string.timer_custom_minutes_label),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Saffron,
                    unfocusedBorderColor = OutlineNeutral,
                    disabledBorderColor = OutlineNeutral,
                    focusedLabelColor = MonasticMaroon,
                    unfocusedLabelColor = InkSecondary,
                    focusedTextColor = InkPrimary,
                    unfocusedTextColor = InkPrimary,
                    disabledTextColor = InkSecondary,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = PureWhite,
                    disabledContainerColor = LightNeutral
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (capNotice != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = capNotice.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed
                )
            }
        }
    }
}

@Composable
private fun EditorPresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) Saffron else LightNeutral
    val borderColor = if (selected) Saffron else OutlineNeutral
    val textColor = when {
        selected -> PureWhite
        enabled -> InkPrimary
        else -> InkSecondary.copy(alpha = 0.45f)
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrackPickerSheetContent(
    zone: QueueZone,
    tracks: List<TrackEntity>,
    onSelectTrack: (TrackEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val headerTitle = stringResource(
        if (zone == QueueZone.INTRO) R.string.picker_add_intro else R.string.picker_add_main
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleLarge,
                color = InkPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.picker_close_cd),
                    tint = InkSecondary
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = OutlineNeutral)

        if (tracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.picker_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.picker_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = tracks,
                    key = { it.id }
                ) { track ->
                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, OutlineNeutral),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectTrack(track) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(DeepSaffron),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = InkPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = TimerValidation.formatDuration(track.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
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
