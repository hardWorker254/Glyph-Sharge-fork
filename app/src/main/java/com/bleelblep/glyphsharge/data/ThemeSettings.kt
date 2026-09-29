package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.di.GlyphPrefs
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slice 1: appearance — light/dark plus the palette variant.
 *
 * Split out of the old monolithic repository because these two keys have
 * exactly one consumer ([com.bleelblep.glyphsharge.ui.theme.ThemeState]) and
 * nothing else in the app writes them.
 */
@Singleton
class ThemeSettings @Inject constructor(
    @GlyphPrefs private val prefs: SharedPreferences
) {

    fun saveTheme(isDarkTheme: Boolean) = prefs.putSetting(KEY_IS_DARK_THEME, isDarkTheme)

    fun getTheme(): Boolean = prefs.getSetting(KEY_IS_DARK_THEME, false)

    fun saveThemeStyle(themeStyle: AppThemeStyle) =
        prefs.putSetting(KEY_THEME_STYLE, themeStyle.name)

    /**
     * An unrecognised style falls back to CLASSIC instead of throwing.
     *
     * The stored string is a raw enum name, so a phone that had a style this
     * build no longer knows about would crash on `valueOf` — at theme setup,
     * which is on the critical path of every cold start. A wrong palette is a
     * recoverable annoyance; a boot loop is not.
     */
    fun getThemeStyle(): AppThemeStyle {
        val styleName = prefs.getSetting(KEY_THEME_STYLE, AppThemeStyle.CLASSIC.name)
        return try {
            AppThemeStyle.valueOf(styleName ?: AppThemeStyle.CLASSIC.name)
        } catch (_: IllegalArgumentException) {
            AppThemeStyle.CLASSIC
        }
    }

    internal companion object {
        const val KEY_IS_DARK_THEME = "is_dark_theme"
        const val KEY_THEME_STYLE = "theme_style"
    }
}
