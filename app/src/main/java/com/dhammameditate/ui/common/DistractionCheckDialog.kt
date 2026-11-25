package com.dhammameditate.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhammameditate.playback.DistractionChecker
import com.dhammameditate.playback.DistractionEvaluation
import com.dhammameditate.playback.DistractionSettingsNavigator
import com.dhammameditate.playback.PlaybackStatus
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.InkPrimary
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LightNeutral
import com.dhammameditate.ui.theme.LotusGold
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron

/**
 * Coordinates the pre-session Distraction Check dialog and one-time API 33+ notification
 * permission prompt for fresh "Start Session" taps across both Session Player and Quick-Play.
 *
 * Ensures zero Binder IPC runs directly in Composition, waits for async session/track loading
 * (`canStart`) after process death before rendering a restored dialog, and guards against
 * same-frame double-taps.
 */
@Composable
fun rememberSessionStartLauncher(
    distractionChecker: DistractionChecker,
    status: PlaybackStatus,
    canStart: Boolean,
    askedNotificationPermission: Boolean,
    onNotificationPermissionAsked: () -> Unit,
    onStartSession: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val latestCanStart by rememberUpdatedState(canStart)
    val latestAskedNotification by rememberUpdatedState(askedNotificationPermission)
    val latestOnNotificationAsked by rememberUpdatedState(onNotificationPermissionAsked)
    val latestOnStartSession by rememberUpdatedState(onStartSession)

    var showDistractionDialog by rememberSaveable { mutableStateOf(false) }
    var distractionEvaluation by remember { mutableStateOf<DistractionEvaluation?>(null) }

    LaunchedEffect(showDistractionDialog, canStart) {
        if (showDistractionDialog && canStart && distractionEvaluation == null) {
            distractionEvaluation = distractionChecker.evaluate()
        }
    }

    LaunchedEffect(status) {
        if (status != PlaybackStatus.IDLE && status != PlaybackStatus.COMPLETED) {
            showDistractionDialog = false
            distractionEvaluation = null
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with foreground playback regardless of whether permission was granted or denied
        latestOnStartSession()
    }

    val proceedToNotificationAndStart: () -> Unit = {
        val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !latestAskedNotification &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsPrompt) {
            latestOnNotificationAsked()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            latestOnStartSession()
        }
    }

    val currentEval = distractionEvaluation
    if (showDistractionDialog && canStart && currentEval != null) {
        DistractionCheckDialog(
            evaluation = currentEval,
            onOpenSettings = {
                DistractionSettingsNavigator.openAirplaneOrDeviceSettings(context)
            },
            onRecheck = {
                distractionEvaluation = distractionChecker.evaluate()
            },
            onProceedToStart = {
                if (!showDistractionDialog || !latestCanStart) return@DistractionCheckDialog
                showDistractionDialog = false
                distractionEvaluation = null
                proceedToNotificationAndStart()
            },
            onDismiss = {
                showDistractionDialog = false
                distractionEvaluation = null
            }
        )
    }

    return {
        if (latestCanStart && !showDistractionDialog) {
            val eval = distractionChecker.evaluate()
            if (eval.isReady) {
                proceedToNotificationAndStart()
            } else {
                distractionEvaluation = eval
                showDistractionDialog = true
            }
        }
    }
}

@Composable
fun DistractionCheckDialog(
    evaluation: DistractionEvaluation,
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit,
    onProceedToStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestRecheck by rememberUpdatedState(onRecheck)

    DisposableEffect(lifecycleOwner) {
        var hasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    if (hasPaused) {
                        hasPaused = false
                        latestRecheck()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isReady = evaluation.isReady

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
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(LightNeutral),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isReady) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.AirplanemodeActive
                        },
                        contentDescription = null,
                        tint = if (isReady) LotusGold else MonasticMaroon,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isReady) "Ready to Meditate" else "Silence Distractions?",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = evaluation.statusSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = evaluation.guidanceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isReady) {
                        Button(
                            onClick = onProceedToStart,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Saffron,
                                contentColor = PureWhite
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Start Session",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MonasticMaroon),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MonasticMaroon
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Open Settings",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = onProceedToStart,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Saffron,
                                contentColor = PureWhite
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Continue anyway",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                    }
                }
            }
        }
    }
}
