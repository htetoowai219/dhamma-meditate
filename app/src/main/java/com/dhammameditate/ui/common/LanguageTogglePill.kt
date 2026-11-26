package com.dhammameditate.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhammameditate.data.preferences.AppLanguage
import com.dhammameditate.ui.theme.MaroonOutline
import com.dhammameditate.ui.theme.MaroonSurfaceSubtle
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.WhiteMuted

@Composable
fun LanguageTogglePill(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaroonSurfaceSubtle,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaroonOutline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LanguageSegment(
                label = "EN",
                selected = currentLanguage == AppLanguage.ENGLISH,
                onClick = { onSelectLanguage(AppLanguage.ENGLISH) }
            )
            LanguageSegment(
                label = "မြန်မာ",
                selected = currentLanguage == AppLanguage.BURMESE,
                onClick = { onSelectLanguage(AppLanguage.BURMESE) }
            )
        }
    }
}

@Composable
private fun LanguageSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Saffron else Color.Transparent
    val textColor = if (selected) PureWhite else WhiteMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(backgroundColor)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = textColor,
            maxLines = 1
        )
    }
}
