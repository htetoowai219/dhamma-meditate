package com.dhammameditate.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun `resolveDefault selects Burmese for my and my-MM locales`() {
        assertEquals(AppLanguage.BURMESE, AppLanguage.resolveDefault(Locale.forLanguageTag("my")))
        assertEquals(AppLanguage.BURMESE, AppLanguage.resolveDefault(Locale.forLanguageTag("my-MM")))
    }

    @Test
    fun `resolveDefault selects English for English and other locales`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveDefault(Locale.ENGLISH))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveDefault(Locale.US))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveDefault(Locale.FRENCH))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveDefault(Locale.JAPANESE))
    }

    @Test
    fun `fromStorageTag parses valid tags and ignores blank or unknown tags`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageTag("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageTag("en-US"))
        assertEquals(AppLanguage.BURMESE, AppLanguage.fromStorageTag("my"))
        assertEquals(AppLanguage.BURMESE, AppLanguage.fromStorageTag("my-MM"))
        assertNull(AppLanguage.fromStorageTag(null))
        assertNull(AppLanguage.fromStorageTag(""))
        assertNull(AppLanguage.fromStorageTag("   "))
        assertNull(AppLanguage.fromStorageTag("fr"))
    }

    @Test
    fun `resolveInitial prefers saved preference over system locale`() {
        assertEquals(
            AppLanguage.ENGLISH,
            AppLanguage.resolveInitial(savedTag = "en", systemLocale = Locale.forLanguageTag("my-MM"))
        )
        assertEquals(
            AppLanguage.BURMESE,
            AppLanguage.resolveInitial(savedTag = "my", systemLocale = Locale.US)
        )
    }

    @Test
    fun `resolveInitial falls back to system locale when no preference is saved`() {
        assertEquals(
            AppLanguage.BURMESE,
            AppLanguage.resolveInitial(savedTag = null, systemLocale = Locale.forLanguageTag("my-MM"))
        )
        assertEquals(
            AppLanguage.ENGLISH,
            AppLanguage.resolveInitial(savedTag = null, systemLocale = Locale.US)
        )
    }
}
