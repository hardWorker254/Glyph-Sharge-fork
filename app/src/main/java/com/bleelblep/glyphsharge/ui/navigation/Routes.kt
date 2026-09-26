package com.bleelblep.glyphsharge.ui.navigation

/**
 * Every destination in the app.
 *
 * Routes used to be bare string literals scattered across `MainActivity`
 * (`navController.navigate("theme_settings")`), which meant a typo compiled
 * fine and simply crashed at runtime — that is exactly what had happened with
 * the `hidden_settings` route. Referencing a member of [Routes] instead makes
 * the compiler catch a bad route.
 */
object Routes {

    const val HOME = "home"
    const val SETTINGS = "settings"
    const val THEME_SETTINGS = "theme_settings"
    const val FONT_SETTINGS = "font_settings"
    const val QUIET_HOURS_SETTINGS = "quiet_hours_settings"
    const val LANGUAGE_SETTINGS = "language_settings"
}
