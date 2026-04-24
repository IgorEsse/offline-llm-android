package com.example.offlinellm.ui.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {
    fun applyLanguage(languageTag: String) {
        val locales = when (languageTag) {
            "ru" -> LocaleListCompat.forLanguageTags("ru")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
