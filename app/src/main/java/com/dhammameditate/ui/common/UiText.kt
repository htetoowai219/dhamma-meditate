package com.dhammameditate.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dhammameditate.R
import com.dhammameditate.data.importer.ImportFailureReason
import com.dhammameditate.playback.TimerCapNotice
import com.dhammameditate.playback.TimerCapScope
import com.dhammameditate.playback.TimerValidation

sealed interface UiText {
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText

    fun asString(context: Context): String = when (this) {
        is Dynamic -> value
        is Resource -> {
            if (args.isEmpty()) {
                context.getString(resId)
            } else {
                val resolvedArgs = args.map { arg ->
                    if (arg is UiText) arg.asString(context) else arg
                }.toTypedArray()
                context.getString(resId, *resolvedArgs)
            }
        }
    }
}

@Composable
fun UiText.asString(): String {
    return asString(LocalContext.current)
}

fun TimerCapNotice.toUiText(): UiText {
    val formatted = TimerValidation.formatDuration(cappedDurationMs)
    return when (scope) {
        TimerCapScope.TRACK -> UiText.Resource(R.string.timer_adjusted_track, listOf(formatted))
        TimerCapScope.MAIN_QUEUE -> UiText.Resource(R.string.timer_adjusted_main_queue, listOf(formatted))
    }
}

fun ImportFailureReason.toUiText(): UiText = when (this) {
    ImportFailureReason.UNSUPPORTED_FORMAT -> UiText.Resource(R.string.import_error_unsupported_format)
    ImportFailureReason.STORAGE_UNAVAILABLE -> UiText.Resource(R.string.import_error_storage)
    ImportFailureReason.EMPTY_OR_UNREADABLE -> UiText.Resource(R.string.import_error_empty_file)
    ImportFailureReason.CORRUPT_OR_DRM -> UiText.Resource(R.string.import_error_corrupt_or_drm)
    ImportFailureReason.OPEN_FAILED -> UiText.Resource(R.string.import_error_open_failed)
    ImportFailureReason.DRM_UNSUPPORTED -> UiText.Resource(R.string.import_error_drm)
    ImportFailureReason.VIDEO_UNSUPPORTED -> UiText.Resource(R.string.import_error_video)
    ImportFailureReason.NO_AUDIO_STREAM -> UiText.Resource(R.string.import_error_no_audio_stream)
    ImportFailureReason.ZERO_DURATION -> UiText.Resource(R.string.import_error_zero_duration)
    ImportFailureReason.GENERIC -> UiText.Resource(R.string.import_error_generic)
}
