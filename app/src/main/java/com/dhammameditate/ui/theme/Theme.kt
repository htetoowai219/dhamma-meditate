package com.dhammameditate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DhammaColorScheme = lightColorScheme(
    primary = Saffron,
    onPrimary = PureWhite,
    primaryContainer = DeepSaffron,
    onPrimaryContainer = PureWhite,
    secondary = MonasticMaroon,
    onSecondary = PureWhite,
    tertiary = LotusGold,
    onTertiary = PureWhite,
    background = LightNeutral,
    onBackground = InkPrimary,
    surface = CardSurface,
    onSurface = InkPrimary,
    surfaceVariant = LightNeutral,
    onSurfaceVariant = InkSecondary,
    outline = OutlineNeutral,
    error = AlertRed,
    onError = PureWhite,
    errorContainer = AlertSurface,
    onErrorContainer = AlertRed
)

@Composable
fun DhammaMeditateTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DhammaColorScheme,
        typography = DhammaTypography,
        content = content
    )
}
