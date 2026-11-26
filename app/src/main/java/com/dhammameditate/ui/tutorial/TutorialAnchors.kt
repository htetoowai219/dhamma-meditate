package com.dhammameditate.ui.tutorial

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

class TutorialAnchorRegistry {
    private data class AnchorEntry(
        val token: Any,
        val rect: Rect
    )

    private val anchors = mutableStateMapOf<TutorialTargetId, AnchorEntry>()

    fun update(targetId: TutorialTargetId, token: Any, bounds: Rect) {
        val current = anchors[targetId]
        if (current != null && current.token === token && current.rect == bounds) {
            return
        }
        anchors[targetId] = AnchorEntry(token = token, rect = bounds)
    }

    fun remove(targetId: TutorialTargetId, token: Any) {
        val current = anchors[targetId]
        if (current != null && current.token === token) {
            anchors.remove(targetId)
        }
    }

    operator fun get(targetId: TutorialTargetId): Rect? = anchors[targetId]?.rect
}

val LocalTutorialAnchorRegistry = staticCompositionLocalOf<TutorialAnchorRegistry?> { null }

fun Modifier.tutorialTarget(targetId: TutorialTargetId): Modifier = composed {
    val registry = LocalTutorialAnchorRegistry.current ?: return@composed this
    val token = remember(targetId) { Any() }

    DisposableEffect(registry, targetId, token) {
        onDispose {
            registry.remove(targetId, token)
        }
    }

    this.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val bounds = coordinates.boundsInRoot()
            if (bounds.width > 0f && bounds.height > 0f) {
                registry.update(targetId = targetId, token = token, bounds = bounds)
            } else {
                registry.remove(targetId = targetId, token = token)
            }
        }
    }
}
