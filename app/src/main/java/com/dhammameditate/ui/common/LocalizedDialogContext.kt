package com.dhammameditate.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

val LocalAppLocalizedContext = staticCompositionLocalOf<Context?> { null }

/**
 * Re-applies the active in-app localized Context & Configuration inside Compose
 * Dialog / AlertDialog / ModalBottomSheet windows, whose own AndroidComposeView
 * otherwise shadows the outer LocalContext with the unlocalized Activity context.
 */
@Composable
fun ProvideLocalizedDialogContext(
    content: @Composable () -> Unit
) {
    val localizedContext = LocalAppLocalizedContext.current
    if (localizedContext == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration,
            content = content
        )
    }
}
