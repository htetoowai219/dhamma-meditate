package com.dhammameditate.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhammameditate.R
import com.dhammameditate.playback.PlaybackSnapshot
import com.dhammameditate.playback.PlaybackStatus
import com.dhammameditate.playback.TimerValidation
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.PureWhite
import com.dhammameditate.ui.theme.Saffron
import com.dhammameditate.ui.theme.WhiteMuted

@Composable
fun ActiveSessionBanner(
    playback: PlaybackSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = playback.activeSessionName
        ?: playback.activeTrackTitle
        ?: stringResource(R.string.banner_default_title)
    val phaseLabel = when (playback.status) {
        PlaybackStatus.INTRO_PLAYING -> stringResource(R.string.banner_phase_intro)
        PlaybackStatus.INTRO_PAUSED -> stringResource(R.string.banner_phase_intro_paused)
        PlaybackStatus.PLAYING -> if (playback.activeSessionId != null) {
            stringResource(R.string.banner_phase_main)
        } else {
            stringResource(R.string.banner_phase_playing)
        }
        PlaybackStatus.PAUSED -> stringResource(R.string.banner_phase_paused)
        PlaybackStatus.FADING -> stringResource(R.string.banner_phase_fading)
        PlaybackStatus.IDLE, PlaybackStatus.COMPLETED -> stringResource(R.string.banner_phase_active)
    }
    val remaining = TimerValidation.formatDuration(playback.remainingMs)

    Surface(
        color = MonasticMaroon,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Saffron),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = stringResource(R.string.banner_icon_cd),
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$phaseLabel · $remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = WhiteMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.banner_return_cd),
                tint = WhiteMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
