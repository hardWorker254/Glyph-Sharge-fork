package com.bleelblep.glyphsharge.ui.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.screens.FontSettingsScreen
import com.bleelblep.glyphsharge.ui.screens.LanguageSettingsScreen
import com.bleelblep.glyphsharge.ui.screens.QuietHoursSettingsScreen
import com.bleelblep.glyphsharge.ui.screens.SettingsScreen
import com.bleelblep.glyphsharge.ui.screens.ThemeSettingsScreen
import com.bleelblep.glyphsharge.ui.screens.home.HomeActions
import com.bleelblep.glyphsharge.ui.screens.home.HomeScreen
import com.bleelblep.glyphsharge.ui.theme.LocalFontState

/**
 * The whole navigation graph.
 *
 * Lifted out of `MainActivity` so the Activity is left with lifecycle duties
 * only. Every destination is registered here next to the [Routes] constant it
 * answers to, which is what makes an unregistered route like the old
 * `hidden_settings` obvious.
 */
@Composable
fun GlyphNavHost(
    glyphServiceEnabled: Boolean,
    onGlyphServiceToggle: (Boolean) -> Unit,
    actions: HomeActions,
    settingsRepository: SettingsRepository,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { MaterialSharedAxisZ.enterTransition() },
        exitTransition = { MaterialSharedAxisZ.exitTransition() },
        popEnterTransition = { MaterialSharedAxisZ.popEnterTransition() },
        popExitTransition = { MaterialSharedAxisZ.popExitTransition() }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                glyphServiceEnabled = glyphServiceEnabled,
                onGlyphServiceToggle = onGlyphServiceToggle,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                actions = actions,
                settingsRepository = settingsRepository
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onThemeSettingsClick = { navController.navigate(Routes.THEME_SETTINGS) },
                onFontSettingsClick = { navController.navigate(Routes.FONT_SETTINGS) },
                onQuietHoursSettingsClick = {
                    navController.navigate(Routes.QUIET_HOURS_SETTINGS)
                },
                onLanguageSettingsClick = {
                    navController.navigate(Routes.LANGUAGE_SETTINGS)
                },
                settingsRepository = settingsRepository
            )
        }

        composable(Routes.THEME_SETTINGS) {
            ThemeSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Routes.FONT_SETTINGS) {
            FontSettingsScreen(
                fontState = LocalFontState.current,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.QUIET_HOURS_SETTINGS) {
            QuietHoursSettingsScreen(
                onBackClick = { navController.popBackStack() },
                settingsRepository = settingsRepository
            )
        }

        composable(Routes.LANGUAGE_SETTINGS) {
            LanguageSettingsScreen(
                onBackClick = { navController.popBackStack() },
                settingsRepository = settingsRepository,
                onLanguageChanged = {
                    // A locale change only takes effect on a fresh Activity.
                    (context as? ComponentActivity)?.let { activity ->
                        activity.intent.apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }
                        activity.startActivity(activity.intent)
                        activity.finish()
                    }
                }
            )
        }
    }
}
