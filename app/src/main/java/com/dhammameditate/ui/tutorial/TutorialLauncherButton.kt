package com.dhammameditate.ui.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhammameditate.R
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.NavActivePill
import com.dhammameditate.ui.theme.OutlineNeutral

@Composable
fun TutorialLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val launcherContentDescription = stringResource(R.string.tutorial_launcher_cd)
    Surface(
        color = NavActivePill,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OutlineNeutral),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = launcherContentDescription }
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = MonasticMaroon,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.tutorial_launcher_button),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MonasticMaroon,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
