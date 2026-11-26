package com.dhammameditate.data.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(
    val languageTag: String,
    val locale: Locale
) {
    ENGLISH(languageTag = "en", locale = Locale.ENGLISH),
    BURMESE(languageTag = "my", locale = Locale.forLanguageTag("my"));

    companion object {
        fun resolveDefault(systemLocale: Locale): AppLanguage {
            return if (systemLocale.language.equals("my", ignoreCase = true)) {
                BURMESE
            } else {
                ENGLISH
            }
        }

        fun fromStorageTag(tag: String?): AppLanguage? {
            if (tag.isNullOrBlank()) return null
            val normalized = tag.trim().lowercase(Locale.US)
            return when {
                normalized == "my" || normalized.startsWith("my-") || normalized.startsWith("my_") -> BURMESE
                normalized == "en" || normalized.startsWith("en-") || normalized.startsWith("en_") -> ENGLISH
                else -> null
            }
        }

        fun resolveInitial(savedTag: String?, systemLocale: Locale): AppLanguage {
            return fromStorageTag(savedTag) ?: resolveDefault(systemLocale)
        }
    }
}

fun Context.withAppLanguage(language: AppLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(language.locale)
    val localized = createConfigurationContext(config)
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localized.resources
        override fun getAssets(): AssetManager = localized.assets
    }
}

class AppLanguageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _language = MutableStateFlow(
        AppLanguage.resolveInitial(
            savedTag = prefs.getString(KEY_LANGUAGE_TAG, null),
            systemLocale = Locale.getDefault()
        )
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        if (_language.value == language && prefs.contains(KEY_LANGUAGE_TAG)) return
        prefs.edit().putString(KEY_LANGUAGE_TAG, language.languageTag).apply()
        _language.value = language
    }

    fun localizedContext(base: Context): Context {
        return base.withAppLanguage(_language.value)
    }

    companion object {
        private const val PREFS_NAME = "dhamma_meditate_prefs"
        private const val KEY_LANGUAGE_TAG = "app_language_tag"
    }
}
