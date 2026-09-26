package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bleelblep.glyphsharge.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Available theme styles for the app
 */
enum class AppThemeStyle {
    CLASSIC,    // Clean, standard Material 3 theme
    Y2K,        // Chrome, cyber, futuristic aesthetic
    NEON,       // High contrast electric colors
    AMOLED,     // True black with minimal design
    PASTEL,     // Soft, dreamy colors
    EXPRESSIVE  // Vibrant, bold Material 3 expressive
}

/**
 * Theme state management for the app
 * Handles dark/light theme switching and theme style selection
 */
@Singleton
class ThemeState @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private var _isDarkTheme by mutableStateOf(settingsRepository.getTheme())
    val isDarkTheme: Boolean get() = _isDarkTheme

    private var _themeStyle by mutableStateOf(settingsRepository.getThemeStyle())
    val themeStyle: AppThemeStyle get() = _themeStyle

    fun toggleTheme() {
        _isDarkTheme = !_isDarkTheme
        settingsRepository.saveTheme(_isDarkTheme)
    }

    fun setDarkTheme(darkMode: Boolean) {
        _isDarkTheme = darkMode
        settingsRepository.saveTheme(_isDarkTheme)
    }

    fun setThemeStyle(style: AppThemeStyle) {
        _themeStyle = style
        settingsRepository.saveThemeStyle(style)
    }
}
