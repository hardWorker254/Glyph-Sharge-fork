package com.bleelblep.glyphsharge.ui.navigation

/**
 * Every destination in the app.
 *
 * Routes are constants rather than bare string literals at the call site, so a
 * typo is a compile error instead of a crash at navigation time.
 */
object Routes {

    const val HOME = "home"
    const val SETTINGS = "settings"
    const val THEME_SETTINGS = "theme_settings"
    const val FONT_SETTINGS = "font_settings"
    const val QUIET_HOURS_SETTINGS = "quiet_hours_settings"
    const val LANGUAGE_SETTINGS = "language_settings"
}
