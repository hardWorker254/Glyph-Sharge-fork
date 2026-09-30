package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.di.GlyphPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slice 5: the UI language.
 *
 * A one-key slice, and deliberately so. This key is the reason the settings
 * store cannot simply be passed to every screen: `MainActivity.attachBaseContext`
 * has to read it *before* the Activity exists and therefore before Hilt can
 * build anything, straight off `Context`. The value below is the same string
 * that early read returns, and they must stay in step.
 */
@Singleton
class LanguageSettings @Inject constructor(
    @param:GlyphPrefs private val prefs: SharedPreferences
) {

    fun getAppLanguageCode(): String = prefs.getSetting(LANGUAGE, DEFAULT_LANGUAGE)

    fun saveAppLanguageCode(code: String) = prefs.putSetting(LANGUAGE, code)

    internal companion object {
        const val LANGUAGE = "language"
        const val DEFAULT_LANGUAGE = "system"
    }
}
