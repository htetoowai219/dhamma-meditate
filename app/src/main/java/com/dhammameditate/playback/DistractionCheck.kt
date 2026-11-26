package com.dhammameditate.playback

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings

enum class RingerState {
    SILENT,
    VIBRATE,
    NORMAL,
    UNKNOWN
}

data class DistractionSnapshot(
    val isAirplaneModeOn: Boolean,
    val isDndActive: Boolean,
    val ringerState: RingerState
)

enum class DistractionStatusKind {
    AIRPLANE_ON,
    DND_ACTIVE,
    RINGER_SILENT,
    RINGER_NORMAL,
    RINGER_VIBRATE,
    UNKNOWN_UNSILENCED
}

data class DistractionEvaluation(
    val snapshot: DistractionSnapshot,
    val isReady: Boolean,
    val statusSummary: String,
    val guidanceText: String,
    val statusKind: DistractionStatusKind = DistractionStatusKind.UNKNOWN_UNSILENCED
)

object DistractionPolicy {
    const val GUIDANCE_NOT_READY: String =
        "Turn on Airplane Mode or silence alerts so calls and notifications do not break your stillness."
    const val GUIDANCE_READY: String =
        "Device is quiet and ready for meditation."

    fun evaluate(snapshot: DistractionSnapshot): DistractionEvaluation {
        return when {
            snapshot.isAirplaneModeOn -> DistractionEvaluation(
                snapshot = snapshot,
                isReady = true,
                statusSummary = "Airplane Mode is on. Ready for quiet meditation.",
                guidanceText = GUIDANCE_READY,
                statusKind = DistractionStatusKind.AIRPLANE_ON
            )

            snapshot.isDndActive -> DistractionEvaluation(
                snapshot = snapshot,
                isReady = true,
                statusSummary = "Do Not Disturb is active. Ready for quiet meditation.",
                guidanceText = GUIDANCE_READY,
                statusKind = DistractionStatusKind.DND_ACTIVE
            )

            snapshot.ringerState == RingerState.SILENT -> DistractionEvaluation(
                snapshot = snapshot,
                isReady = true,
                statusSummary = "Phone is silenced. Ready for quiet meditation.",
                guidanceText = GUIDANCE_READY,
                statusKind = DistractionStatusKind.RINGER_SILENT
            )

            snapshot.ringerState == RingerState.NORMAL -> DistractionEvaluation(
                snapshot = snapshot,
                isReady = false,
                statusSummary = "Airplane Mode is off and your ringer is on.",
                guidanceText = GUIDANCE_NOT_READY,
                statusKind = DistractionStatusKind.RINGER_NORMAL
            )

            snapshot.ringerState == RingerState.VIBRATE -> DistractionEvaluation(
                snapshot = snapshot,
                isReady = false,
                statusSummary = "Airplane Mode is off and vibration is on.",
                guidanceText = GUIDANCE_NOT_READY,
                statusKind = DistractionStatusKind.RINGER_VIBRATE
            )

            else -> DistractionEvaluation(
                snapshot = snapshot,
                isReady = false,
                statusSummary = "Airplane Mode is off and alerts may interrupt your session.",
                guidanceText = GUIDANCE_NOT_READY,
                statusKind = DistractionStatusKind.UNKNOWN_UNSILENCED
            )
        }
    }
}

interface DistractionChecker {
    fun evaluate(): DistractionEvaluation
}

/**
 * Zero-permission Android system state reader for pre-session distraction checks.
 * Each system read is wrapped in runCatching so OEM quirks never crash playback start.
 */
class AndroidDistractionChecker(context: Context) : DistractionChecker {
    private val appContext: Context = context.applicationContext

    override fun evaluate(): DistractionEvaluation {
        val snapshot = DistractionSnapshot(
            isAirplaneModeOn = readAirplaneMode(),
            isDndActive = readDndActive(),
            ringerState = readRingerState()
        )
        return DistractionPolicy.evaluate(snapshot)
    }

    private fun readAirplaneMode(): Boolean = runCatching {
        Settings.Global.getInt(
            appContext.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
    }.getOrDefault(false)

    private fun readDndActive(): Boolean = runCatching {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val filter = notificationManager?.currentInterruptionFilter
            ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(false)

    private fun readRingerState(): RingerState = runCatching {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerState.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerState.VIBRATE
            AudioManager.RINGER_MODE_NORMAL -> RingerState.NORMAL
            else -> RingerState.UNKNOWN
        }
    }.getOrDefault(RingerState.UNKNOWN)
}

object DistractionSettingsNavigator {
    private val SETTINGS_ACTIONS = listOf(
        Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        Settings.ACTION_WIRELESS_SETTINGS,
        Settings.ACTION_SETTINGS
    )

    fun openAirplaneOrDeviceSettings(context: Context): Boolean {
        val isActivityContext = context.findActivity() != null
        for (action in SETTINGS_ACTIONS) {
            val intent = Intent(action).apply {
                if (!isActivityContext) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val launched = runCatching {
                context.startActivity(intent)
            }.isSuccess
            if (launched) return true
        }
        return false
    }

    private tailrec fun Context?.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
