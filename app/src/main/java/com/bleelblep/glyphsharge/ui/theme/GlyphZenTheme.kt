package com.bleelblep.glyphsharge.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// CompositionLocal defaults that fail loudly if something reads them outside
// a GlyphZenTheme block, rather than silently handing back a stub.
private fun createPlaceholderFontState(): FontState {
    throw IllegalStateException("FontState should be provided via dependency injection")
}

private fun createPlaceholderThemeState(): ThemeState {
    throw IllegalStateException("ThemeState should be provided via dependency injection")
}

val LocalFontState = staticCompositionLocalOf { createPlaceholderFontState() }
val LocalThemeState = staticCompositionLocalOf { createPlaceholderThemeState() }

/**
 * Legacy theme composable for backward compatibility with existing FontState system
 */
@Composable
fun GlyphZenTheme(
    themeState: ThemeState,
    fontState: FontState,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = themeState.isDarkTheme
    val themeStyle = themeState.themeStyle

    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorScheme(themeStyle, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalFontState provides fontState,
        LocalThemeState provides themeState
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = getTypography(themeStyle, fontState),
            shapes = getShapes(themeStyle),
            content = content
        )
    }
}
