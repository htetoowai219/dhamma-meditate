package com.dhammameditate.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhammameditate.R
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.InkPrimary
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.SaffronTintSurface

@Composable
fun TutorialSpotlightOverlay(
    state: TutorialSessionState,
    anchorRegistry: TutorialAnchorRegistry,
    hasLibraryTracks: Boolean,
    onTargetTapped: (TutorialTargetId) -> Unit,
    onConfirmStep: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    key(state.definition.id, state.currentStepIndex) {
        BackHandler(enabled = true) {
            onSkip()
        }
    }

    val density = LocalDensity.current
    val step = state.currentStep
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val latestOnTargetTapped by rememberUpdatedState(onTargetTapped)
    val latestOnConfirmStep by rememberUpdatedState(onConfirmStep)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayOriginInRoot = coords.positionInRoot()
            }
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val cutoutPaddingPx = with(density) { 6.dp.toPx() }
        val cutoutRadiusPx = with(density) { 16.dp.toPx() }
        val strokeWidthPx = with(density) { 2.dp.toPx() }

        val targetRectInRoot = anchorRegistry[step.targetId]
        val cutoutRect: Rect? = remember(
            targetRectInRoot,
            overlayOriginInRoot,
            containerWidthPx,
            containerHeightPx,
            cutoutPaddingPx
        ) {
            targetRectInRoot?.let { rootRect ->
                val localLeft = (rootRect.left - overlayOriginInRoot.x - cutoutPaddingPx)
                    .coerceIn(0f, containerWidthPx)
                val localTop = (rootRect.top - overlayOriginInRoot.y - cutoutPaddingPx)
                    .coerceIn(0f, containerHeightPx)
                val localRight = (rootRect.right - overlayOriginInRoot.x + cutoutPaddingPx)
                    .coerceIn(localLeft, containerWidthPx)
                val localBottom = (rootRect.bottom - overlayOriginInRoot.y + cutoutPaddingPx)
                    .coerceIn(localTop, containerHeightPx)
                if (localRight > localLeft && localBottom > localTop) {
                    Rect(localLeft, localTop, localRight, localBottom)
                } else {
                    null
                }
            }
        }

        val latestCutoutRect by rememberUpdatedState(cutoutRect)

        // Dimmed backdrop + spotlight cutout + selective pointer hit-testing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.definition.id, state.currentStepIndex, step.targetId, step.interaction) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var isTap = true
                        var lastPosition = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id }
                            if (tracked != null) {
                                if ((tracked.position - down.position).getDistance() > touchSlop) {
                                    isTap = false
                                }
                                lastPosition = tracked.position
                            }
                            event.changes.forEach { it.consume() }
                            if (event.changes.all { !it.pressed }) {
                                break
                            }
                        }
                        val activeCutout = latestCutoutRect
                        if (isTap && activeCutout != null && activeCutout.contains(lastPosition)) {
                            when (step.interaction) {
                                TutorialStepInteraction.TAP_TARGET -> latestOnTargetTapped(step.targetId)
                                TutorialStepInteraction.CONFIRM_ONLY -> latestOnConfirmStep()
                            }
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                drawRect(color = InkPrimary.copy(alpha = 0.68f))
                cutoutRect?.let { rect ->
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = CornerRadius(cutoutRadiusPx, cutoutRadiusPx),
                        blendMode = BlendMode.Clear
                    )
                    drawRoundRect(
                        color = Saffron,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = CornerRadius(cutoutRadiusPx, cutoutRadiusPx),
                        style = Stroke(width = strokeWidthPx)
                    )
                }
            }
        }

        // Auto-positioned floating flat tooltip card based on actual available vertical space
        val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
        val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val minReadableHeightPx = with(density) { 210.dp.toPx() }
        val spaceAbove = cutoutRect?.let { (it.top - statusBarTopPx).coerceAtLeast(0f) } ?: 0f
        val spaceBelow = cutoutRect?.let { (containerHeightPx - navBarBottomPx - it.bottom).coerceAtLeast(0f) } ?: 0f
        val useCenteredFallback = cutoutRect == null || maxOf(spaceAbove, spaceBelow) < minReadableHeightPx
        val placeAboveTarget = !useCenteredFallback && spaceAbove >= spaceBelow

        val tooltipModifier = when {
            useCenteredFallback -> Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 16.dp)

            placeAboveTarget -> {
                val bottomOffsetDp = with(density) {
                    (containerHeightPx - cutoutRect!!.top).coerceAtLeast(0f).toDp()
                } + 14.dp
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomOffsetDp)
            }

            else -> {
                val topOffsetDp = with(density) {
                    cutoutRect!!.bottom.coerceAtLeast(0f).toDp()
                } + 14.dp
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = topOffsetDp, bottom = 16.dp)
            }
        }

        TutorialTooltipCard(
            state = state,
            hasLibraryTracks = hasLibraryTracks,
            onConfirmStep = onConfirmStep,
            onSkip = onSkip,
            modifier = tooltipModifier
        )
    }
}

@Composable
private fun TutorialTooltipCard(
    state: TutorialSessionState,
    hasLibraryTracks: Boolean,
    onConfirmStep: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = state.currentStep
    val (stepStr, totalStr) = remember(state.stepNumber, state.totalSteps) {
        TutorialEngine.formatStepNumbers(state.stepNumber, state.totalSteps)
    }

    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SaffronTintSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Saffron),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_step_progress, stepStr, totalStr),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MonasticMaroon,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                TextButton(onClick = onSkip) {
                    Text(
                        text = stringResource(R.string.tutorial_action_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(step.titleRes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = InkPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(step.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )

            if (!hasLibraryTracks && step.emptyLibraryNoteRes != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = SaffronTintSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Saffron),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(step.emptyLibraryNoteRes),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MonasticMaroon,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            step.tapHintRes?.let { hintRes ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MonasticMaroon
                )
            }

            if (step.interaction == TutorialStepInteraction.CONFIRM_ONLY) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onConfirmStep,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Saffron,
                        contentColor = PureWhite
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (state.isLastStep) {
                                R.string.tutorial_action_got_it
                            } else {
                                R.string.tutorial_action_next
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
