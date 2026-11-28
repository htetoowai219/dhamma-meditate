package com.dhammameditate.ui.quickmeditate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhammameditate.R
import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.playback.DistractionChecker
import com.dhammameditate.playback.PlaybackStatus
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.common.ProvideLocalizedDialogContext
import com.dhammameditate.ui.common.rememberSessionStartLauncher
import com.dhammameditate.ui.editor.QueueZoneCard
import com.dhammameditate.ui.editor.SessionTimerCard
import com.dhammameditate.ui.editor.TrackPickerSheetContent
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LightNeutral
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.SaffronTintSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMeditateScreen(
    viewModel: QuickMeditateViewModel,
    distractionChecker: DistractionChecker,
    askedNotificationPermission: Boolean,
    onNotificationPermissionAsked: () -> Unit,
    onBack: () -> Unit,
    onStarted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activePickerZone by remember { mutableStateOf<QueueZone?>(null) }

    val launchQuickStart = rememberSessionStartLauncher(
        distractionChecker = distractionChecker,
        status = PlaybackStatus.IDLE,
        canStart = uiState.canStart,
        askedNotificationPermission = askedNotificationPermission,
        onNotificationPermissionAsked = onNotificationPermissionAsked,
        onStartSession = { viewModel.startQuickMeditate(onStarted) }
    )

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightNeutral)
    ) {
        QuickMeditateTopBar(
            canReset = !uiState.isLoading && uiState.hasAnySelection,
            onBack = onBack,
            onReset = viewModel::resetAll
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Saffron, strokeWidth = 2.5.dp)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (uiState.isQuickMeditateActive) {
                    Surface(
                        color = SaffronTintSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Saffron),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.quick_meditate_active_notice),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MonasticMaroon,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                // 1. Intro Queue Card (Optional · Untimed)
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
                    onRemove = { index -> viewModel.removeTrack(QueueZone.INTRO, index) }
                )

                // 2. Main Queue Card (Required · Timed)
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
                    onRemove = { index -> viewModel.removeTrack(QueueZone.MAIN, index) }
                )

                // 3. Meditation Timer Card (Validated against combined Main Queue)
                SessionTimerCard(
                    mainTotalDurationMs = uiState.mainTotalDurationMs,
                    selectedDurationMs = uiState.targetDurationMs,
                    isFullSelected = uiState.isFullSelected,
                    customMinutesInput = uiState.customMinutesInput,
                    capNotice = uiState.capNotice,
                    enabled = uiState.mainItems.isNotEmpty(),
                    onSelectPreset = viewModel::selectPresetMinutes,
                    onSelectFull = viewModel::selectFullMainQueue,
                    onCustomMinutesChanged = viewModel::onCustomMinutesChanged
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Anchored bottom Start Meditation CTA
            Surface(
                color = LightNeutral,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = OutlineNeutral)
                    Button(
                        onClick = launchQuickStart,
                        enabled = uiState.canStart,
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
                    ) {
                        Text(
                            text = stringResource(R.string.quick_meditate_start_button),
                            style = MaterialTheme.typography.labelLarge
                        )
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
private fun QuickMeditateTopBar(
    canReset: Boolean,
    onBack: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MonasticMaroon)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = PureWhite
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.quick_meditate_title),
            style = MaterialTheme.typography.titleLarge,
            color = PureWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onReset,
            enabled = canReset,
            colors = ButtonDefaults.textButtonColors(
                contentColor = PureWhite,
                disabledContentColor = PureWhite.copy(alpha = 0.4f)
            )
        ) {
            Text(
                text = stringResource(R.string.quick_meditate_reset),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
