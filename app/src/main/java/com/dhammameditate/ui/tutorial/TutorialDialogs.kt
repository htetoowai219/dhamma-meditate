package com.dhammameditate.ui.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dhammameditate.R
import com.dhammameditate.ui.common.ProvideLocalizedDialogContext
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.InkPrimary
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LightNeutral
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.SaffronTintSurface

@Composable
fun TutorialWelcomeDialog(
    onViewTutorials: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        modifier = Modifier.border(1.dp, OutlineNeutral, RoundedCornerShape(16.dp)),
        containerColor = CardSurface,
        titleContentColor = InkPrimary,
        textContentColor = InkSecondary,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(16.dp),
        title = {
            ProvideLocalizedDialogContext {
                Text(
                    text = stringResource(R.string.tutorial_welcome_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
            }
        },
        text = {
            ProvideLocalizedDialogContext {
                Text(
                    text = stringResource(R.string.tutorial_welcome_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        },
        confirmButton = {
            ProvideLocalizedDialogContext {
                TextButton(onClick = onViewTutorials) {
                    Text(
                        text = stringResource(R.string.tutorial_welcome_view),
                        style = MaterialTheme.typography.labelLarge,
                        color = MonasticMaroon
                    )
                }
            }
        },
        dismissButton = {
            ProvideLocalizedDialogContext {
                TextButton(onClick = onSkip) {
                    Text(
                        text = stringResource(R.string.tutorial_welcome_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                }
            }
        }
    )
}

@Composable
fun TutorialTopicListDialog(
    tutorials: List<TutorialDefinition>,
    onSelectTutorial: (TutorialDefinition) -> Unit,
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
                        text = stringResource(R.string.tutorial_list_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.tutorial_list_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        tutorials.forEach { definition ->
                            TutorialTopicRow(
                                definition = definition,
                                onClick = { onSelectTutorial(definition) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.tutorial_list_close),
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
private fun TutorialTopicRow(
    definition: TutorialDefinition,
    onClick: () -> Unit
) {
    val stepCountText = TutorialEngine.formatCount(definition.steps.size)
    val stepCountLabel = if (definition.steps.size == 1) {
        stringResource(R.string.tutorial_list_steps_one, stepCountText)
    } else {
        stringResource(R.string.tutorial_list_steps_other, stepCountText)
    }

    Surface(
        color = LightNeutral,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(definition.titleRes),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(definition.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = SaffronTintSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Saffron),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = stepCountLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MonasticMaroon,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = InkSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
