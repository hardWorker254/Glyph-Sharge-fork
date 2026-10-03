package com.bleelblep.glyphsharge.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bleelblep.glyphsharge.ui.screens.FontSettingsScreen
import com.bleelblep.glyphsharge.ui.screens.LanguageSettingsScreen
import com.bleelblep.glyphsharge.ui.screens.SettingsScreen
import com.bleelblep.glyphsharge.ui.screens.ThemeSettingsScreen
import com.bleelblep.glyphsharge.ui.screens.home.HomeScreen
import com.bleelblep.glyphsharge.ui.theme.LocalFontState
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The whole navigation graph.
 *
 * Every destination is registered here next to the [Routes] constant it
 * answers to, so a route with no screen is obvious at the point of use.
 *
 * The host takes one dependency: [homeViewModel], the same instance
 * `MainActivity` drives. Everything else each screen resolves from the
 * composition — the store from `LocalSettingsRepository`, the animation list
 * from its own `hiltViewModel()` — which leaves those screens reachable from a
 * preview or a test without this host.
 *
 * The home screen is the exception because it cannot be. It reads the master
 * switch, the feature list and the music-capture channel that `MainActivity`
 * writes to; letting it resolve its own would put a second copy in between.
 *
 * The animation studio is deliberately absent: `CustomAnimationsActivity`
 * needs its own back stack and its own system file pickers, which a route in
 * this graph cannot give it.
 */
@Composable
fun GlyphNavHost(
    homeViewModel: HomeViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { MaterialSharedAxisZ.enterTransition() },
        exitTransition = { MaterialSharedAxisZ.exitTransition() },
        popEnterTransition = { MaterialSharedAxisZ.popEnterTransition() },
        popExitTransition = { MaterialSharedAxisZ.popExitTransition() },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                viewModel = homeViewModel,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onThemeSettingsClick = { navController.navigate(Routes.THEME_SETTINGS) },
                onFontSettingsClick = { navController.navigate(Routes.FONT_SETTINGS) },
            ) {
                navController.navigate(Routes.LANGUAGE_SETTINGS)
            }
        }

        composable(Routes.THEME_SETTINGS) {
            ThemeSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Routes.FONT_SETTINGS) {
            FontSettingsScreen(
                fontState = LocalFontState.current,
            ) {
                navController.popBackStack()
            }
        }

        composable(Routes.LANGUAGE_SETTINGS) {
            LanguageSettingsScreen(
                onBackClick = { navController.popBackStack() },
            ) {
                // `recreate()` is the only thing that applies the locale.
                //
                // The chosen code is read in `attachBaseContext`, which runs
                // on a fresh Activity and nowhere else. What this used to do
                // instead — `startActivity(activity.intent)` followed by
                // `finish()` — is defeated by the manifest's
                // `launchMode="singleTop"`: an Activity already at the top
                // of its own task is not recreated, it gets `onNewIntent`,
                // which touches no locale. The user saw the app close and
                // the language stay as it was.
                //
                // `recreate()` also keeps the ViewModels and the
                // NavBackStackEntry back stack, so the user stays on this
                // screen instead of being dropped back to Home.
                (context as? ComponentActivity)?.recreate()
            }
        }
    }
}
