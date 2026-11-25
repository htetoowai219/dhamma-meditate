package com.dhammameditate.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhammameditate.playback.PlaybackStatus
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.theme.AlertRed
import com.dhammameditate.ui.theme.AlertSurface
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.CountdownTextStyle
import com.dhammameditate.ui.theme.InkPrimary
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LightNeutral
import com.dhammameditate.ui.theme.LotusGold
import com.dhammameditate.ui.theme.MaroonOutline
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.ProgressTrackDark
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.WhiteMuted

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with foreground playback regardless of whether permission was granted or denied
        viewModel.startSession()
    }

    val requestStartSession: () -> Unit = {
        val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !askedNotificationPermission &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsPrompt) {
            askedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startSession()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MonasticMaroon)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        PlayerTopBar(onBack = onBack)

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

            uiState.track == null -> {
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
                                text = "Track unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This audio file is no longer in your offline library.",
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
                                Text("Back to Library", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            else -> {
                val track = uiState.track!!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Track length · ${TimerValidation.formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiteMuted
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = TimerValidation.formatDuration(uiState.displayedRemainingMs),
                        style = CountdownTextStyle,
                        color = PureWhite
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = statusLabel(uiState.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiteMuted
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Strictly non-interactive flat 4dp progress bar
                    NonInteractiveProgressBar(
                        progress = uiState.progressFraction,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.errorMessage != null) {
                        PlayerErrorBanner(
                            message = uiState.errorMessage!!,
                            onDismiss = viewModel::dismissError,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    TimerSelectorCard(
                        trackDurationMs = track.durationMs,
                        selectedDurationMs = uiState.selectedDurationMs,
                        isFullSelected = uiState.isFullSelected,
                        customMinutesInput = uiState.customMinutesInput,
                        capNotice = uiState.capNotice,
                        isLocked = uiState.isTimerLocked,
                        onSelectPreset = viewModel::selectPresetMinutes,
                        onSelectFull = viewModel::selectFullTrack,
                        onCustomMinutesChanged = viewModel::onCustomMinutesChanged
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Bottom restricted transport controls (Start / Pause-Continue / Stop & Reset only)
                RestrictedTransportControls(
                    status = uiState.status,
                    onStart = requestStartSession,
                    onPause = viewModel::pauseSession,
                    onResume = viewModel::resumeSession,
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
private fun PlayerTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to Library",
                tint = PureWhite
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Timed Session",
            style = MaterialTheme.typography.titleMedium,
            color = PureWhite
        )
    }
}

@Composable
private fun NonInteractiveProgressBar(
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
private fun PlayerErrorBanner(
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
                    contentDescription = "Dismiss playback error",
                    tint = AlertRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TimerSelectorCard(
    trackDurationMs: Long,
    selectedDurationMs: Long,
    isFullSelected: Boolean,
    customMinutesInput: String,
    capNotice: String?,
    isLocked: Boolean,
    onSelectPreset: (Int) -> Unit,
    onSelectFull: () -> Unit,
    onCustomMinutesChanged: (String) -> Unit
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.65f else 1f)
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
                    text = "Session Duration",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                if (isLocked) {
                    Text(
                        text = "Locked while active",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 1: 10m, 15m, 30m
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 15, 30).forEach { minutes ->
                    val enabled = !isLocked && TimerValidation.isPresetEnabled(minutes, trackDurationMs)
                    val selected = !isFullSelected &&
                        customMinutesInput.isEmpty() &&
                        selectedDurationMs == minutes.toLong() * 60_000L
                    DurationPresetChip(
                        label = "${minutes}m",
                        selected = selected,
                        enabled = enabled,
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
                    val enabled = !isLocked && TimerValidation.isPresetEnabled(minutes, trackDurationMs)
                    val selected = !isFullSelected &&
                        customMinutesInput.isEmpty() &&
                        selectedDurationMs == minutes.toLong() * 60_000L
                    DurationPresetChip(
                        label = "${minutes}m",
                        selected = selected,
                        enabled = enabled,
                        onClick = { onSelectPreset(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
                DurationPresetChip(
                    label = "Full",
                    selected = isFullSelected && customMinutesInput.isEmpty(),
                    enabled = !isLocked,
                    onClick = onSelectFull,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = customMinutesInput,
                onValueChange = onCustomMinutesChanged,
                enabled = !isLocked,
                singleLine = true,
                label = {
                    Text(
                        text = "Custom minutes (min 1)",
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
                    text = capNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed
                )
            }
        }
    }
}

@Composable
private fun DurationPresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        selected -> Saffron
        else -> LightNeutral
    }
    val borderColor = when {
        selected -> Saffron
        else -> OutlineNeutral
    }
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
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}

@Composable
private fun RestrictedTransportControls(
    status: PlaybackStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
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
                        text = "Start Session",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            PlaybackStatus.PLAYING, PlaybackStatus.FADING -> {
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
                        text = "Pause",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = onStopAndReset,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaroonOutline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Stop & Reset",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            PlaybackStatus.PAUSED -> {
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
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = onStopAndReset,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaroonOutline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Stop & Reset",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: PlaybackStatus): String = when (status) {
    PlaybackStatus.IDLE -> "Ready"
    PlaybackStatus.PLAYING -> "Playing"
    PlaybackStatus.PAUSED -> "Paused"
    PlaybackStatus.FADING -> "Fading out..."
    PlaybackStatus.COMPLETED -> "Session complete"
}
