package com.dhammameditate.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TutorialPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _hasSeenTutorialOffer = MutableStateFlow(
        prefs.getBoolean(KEY_TUTORIAL_OFFER_SEEN, false)
    )
    val hasSeenTutorialOffer: StateFlow<Boolean> = _hasSeenTutorialOffer.asStateFlow()

    fun markOfferSeen() {
        if (_hasSeenTutorialOffer.value && prefs.getBoolean(KEY_TUTORIAL_OFFER_SEEN, false)) {
            return
        }
        prefs.edit().putBoolean(KEY_TUTORIAL_OFFER_SEEN, true).apply()
        _hasSeenTutorialOffer.value = true
    }

    companion object {
        private const val PREFS_NAME = "dhamma_meditate_prefs"
        private const val KEY_TUTORIAL_OFFER_SEEN = "tutorial_offer_seen"
    }
}
