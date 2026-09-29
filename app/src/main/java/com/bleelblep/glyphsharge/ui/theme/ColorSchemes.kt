package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The twelve colour schemes, two per [AppThemeStyle].
 *
 * One theme per block, so adjusting a single palette is a local edit.
 */

// Classic theme — clean Material 3 design

internal val ClassicDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8C6DB), // Metallic silver-blue for better visibility
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF4A3D77), // Darker purple for containers
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFE3DCEC), // Light purple for accents
    onSecondary = Color.Black,
    background = Color(0xFF121212), // Material 3 dark background
    surface = Color(0xFF1E1E1E), // Slightly lighter than background
    onSurface = Color(0xFFE0E0E0), // Softer white for text
    surfaceVariant = Color(0xFF2D2D2D), // For elevated surfaces
    onSurfaceVariant = Color(0xFFB0B0B0), // Secondary text
    outline = Color(0xFF938F99),
    inverseOnSurface = Color(0xFF313033),
    inverseSurface = Color(0xFFE6E1E5),
    error = Color(0xFFFF5252), // Brighter red for better visibility
    onError = Color.White,
    errorContainer = Color(0xFF8B0000), // Darker red for containers
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF1E1E1E)
)

internal val ClassicLightColorScheme = lightColorScheme(
    primary = Color(0xFF0066CC), // Chrome blue for better visibility
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    background = Color(0xFFD9D0E0), // Original light background
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF79747E),
    inverseOnSurface = Color.White,
    inverseSurface = Color(0xFF313033),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.White
)

// Y2K theme — chrome, cyber, futuristic

internal val Y2KDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF000F1A),
    primaryContainer = Color(0xFF0077B5),
    onPrimaryContainer = Color(0xFFBBEBFF),

    secondary = Color(0xFFFF0099),
    onSecondary = Color(0xFF1A0014),
    secondaryContainer = Color(0xFFCC0077),
    onSecondaryContainer = Color(0xFFFFB3E0),

    tertiary = Color(0xFF00FF41),
    onTertiary = Color(0xFF001A0A),
    tertiaryContainer = Color(0xFF00CC33),
    onTertiaryContainer = Color(0xFFB3FFD1),

    background = Color(0xFF000B14),
    onBackground = Color(0xFF00D4FF),
    surface = Color(0xFF001629),
    onSurface = Color(0xFF00D4FF),
    surfaceVariant = Color(0xFF1A2B3D),
    onSurfaceVariant = Color(0xFF66B3E0),

    outline = Color(0xFF0099CC),
    outlineVariant = Color(0xFF003D5C),
    scrim = Color(0xFF000000),

    error = Color(0xFFFF0066),
    onError = Color(0xFF1A0014),
    errorContainer = Color(0xFFCC0052),
    onErrorContainer = Color(0xFFFFB3D1),

    surfaceTint = Color(0xFF00D4FF)
)

internal val Y2KLightColorScheme = lightColorScheme(
    primary = Color(0xFF0099CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F7FF),
    onPrimaryContainer = Color(0xFF001A2E),

    secondary = Color(0xFFCC0077),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE6F2),
    onSecondaryContainer = Color(0xFF1A0014),

    tertiary = Color(0xFF00B333),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6FFE6),
    onTertiaryContainer = Color(0xFF001A0A),

    background = Color(0xFFF0FBFF),
    onBackground = Color(0xFF001A2E),
    surface = Color(0xFFF0FBFF),
    onSurface = Color(0xFF001A2E),
    surfaceVariant = Color(0xFFE6F3FF),
    onSurfaceVariant = Color(0xFF004D70),

    outline = Color(0xFF0099CC),
    outlineVariant = Color(0xFFB3E0FF),
    scrim = Color(0xFF000000),

    error = Color(0xFFCC0052),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE6F2),
    onErrorContainer = Color(0xFF1A0014),

    surfaceTint = Color(0xFF0099CC)
)

// Neon theme — high contrast electric

internal val NeonDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FF00),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF00B300),
    onPrimaryContainer = Color(0xFF80FF80),

    secondary = Color(0xFFFF0080),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFB30060),
    onSecondaryContainer = Color(0xFFFF80C0),

    tertiary = Color(0xFF00FFFF),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF00B3B3),
    onTertiaryContainer = Color(0xFF80FFFF),

    background = Color(0xFF000000),
    onBackground = Color(0xFF00FF00),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFF00FF00),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF80FF80),

    outline = Color(0xFF00FF00),
    outlineVariant = Color(0xFF004D00),
    scrim = Color(0xFF000000),

    error = Color(0xFFFF0040),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFB30030),
    onErrorContainer = Color(0xFFFF8099),

    surfaceTint = Color(0xFF00FF00)
)

internal val NeonLightColorScheme = lightColorScheme(
    primary = Color(0xFF00CC00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6FFE6),
    onPrimaryContainer = Color(0xFF003300),

    secondary = Color(0xFFCC0066),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE6F2),
    onSecondaryContainer = Color(0xFF330019),

    tertiary = Color(0xFF00CCCC),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6FFFF),
    onTertiaryContainer = Color(0xFF003333),

    background = Color(0xFFE6FFE6),
    onBackground = Color(0xFF003300),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF003300),
    surfaceVariant = Color(0xFFF0FFF0),
    onSurfaceVariant = Color(0xFF006600),

    outline = Color(0xFF00CC00),
    outlineVariant = Color(0xFF80FF80),
    scrim = Color(0xFF000000),

    error = Color(0xFFCC0033),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE6E6),
    onErrorContainer = Color(0xFF330008),

    surfaceTint = Color(0xFF00CC00)
)

// AMOLED theme — true black minimal

internal val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF5555),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFCC2222),
    onPrimaryContainer = Color(0xFFFFAAAA),

    secondary = Color(0xFFCCCCCC),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFEEEEEE),

    tertiary = Color(0xFFAAAAAA),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF222222),
    onTertiaryContainer = Color(0xFFDDDDDD),

    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFFCCCCCC),

    outline = Color(0xFF444444),
    outlineVariant = Color(0xFF222222),
    scrim = Color(0xFF000000),

    error = Color(0xFFFF5555),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFCC2222),
    onErrorContainer = Color(0xFFFFAAAA),

    surfaceTint = Color(0xFFFF5555)
)

internal val AmoledLightColorScheme = lightColorScheme(
    primary = Color(0xFFDD2222),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE6E6),
    onPrimaryContainer = Color(0xFF440000),

    secondary = Color(0xFF777777),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5F5F5),
    onSecondaryContainer = Color(0xFF111111),

    tertiary = Color(0xFF555555),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEEEEE),
    onTertiaryContainer = Color(0xFF000000),

    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF333333),

    outline = Color(0xFFBBBBBB),
    outlineVariant = Color(0xFFDDDDDD),
    scrim = Color(0xFF000000),

    error = Color(0xFFDD2222),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE6E6),
    onErrorContainer = Color(0xFF440000),

    surfaceTint = Color(0xFFDD2222)
)

// Pastel theme — soft dreamy colors

internal val PastelDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4A5FF),
    onPrimary = Color(0xFF2A1A3D),
    primaryContainer = Color(0xFF8B5FBD),
    onPrimaryContainer = Color(0xFFEDD5FF),

    secondary = Color(0xFFFFB3D9),
    onSecondary = Color(0xFF3D1A2E),
    secondaryContainer = Color(0xFFBD5F8F),
    onSecondaryContainer = Color(0xFFFFD5ED),

    tertiary = Color(0xFFB3E6FF),
    onTertiary = Color(0xFF1A2E3D),
    tertiaryContainer = Color(0xFF5F8FBD),
    onTertiaryContainer = Color(0xFFD5EDFF),

    background = Color(0xFF1A0F26),
    onBackground = Color(0xFFF0E6FF),
    surface = Color(0xFF2A1F3D),
    onSurface = Color(0xFFF0E6FF),
    surfaceVariant = Color(0xFF3D2F52),
    onSurfaceVariant = Color(0xFFD4C5E6),

    outline = Color(0xFF9B7FB3),
    outlineVariant = Color(0xFF52335F),
    scrim = Color(0xFF000000),

    error = Color(0xFFFF99B3),
    onError = Color(0xFF3D1A22),
    errorContainer = Color(0xFFBD5F75),
    onErrorContainer = Color(0xFFFFD5E0),

    surfaceTint = Color(0xFFD4A5FF)
)

internal val PastelLightColorScheme = lightColorScheme(
    primary = Color(0xFF8B5FBD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0E6FF),
    onPrimaryContainer = Color(0xFF2A1A3D),

    secondary = Color(0xFFBD5F8F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE6F2),
    onSecondaryContainer = Color(0xFF3D1A2E),

    tertiary = Color(0xFF5F8FBD),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6F2FF),
    onTertiaryContainer = Color(0xFF1A2E3D),

    background = Color(0xFFFAF0FF),
    onBackground = Color(0xFF2A1A3D),
    surface = Color(0xFFFAF0FF),
    onSurface = Color(0xFF2A1A3D),
    surfaceVariant = Color(0xFFF5E6FF),
    onSurfaceVariant = Color(0xFF524F5C),

    outline = Color(0xFF9B7FB3),
    outlineVariant = Color(0xFFD4C5E6),
    scrim = Color(0xFF000000),

    error = Color(0xFFBD2F52),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE6ED),
    onErrorContainer = Color(0xFF3D0F1A),

    surfaceTint = Color(0xFF8B5FBD)
)

// Expressive theme — bold Material 3 Expressive

internal val ExpressiveDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD60A),
    onPrimary = Color(0xFF3D2F00),
    primaryContainer = Color(0xFFB8A000),
    onPrimaryContainer = Color(0xFFFFF566),

    secondary = Color(0xFFFF453A),
    onSecondary = Color(0xFF3D0F0A),
    secondaryContainer = Color(0xFFBD2217),
    onSecondaryContainer = Color(0xFFFF9980),

    tertiary = Color(0xFF30D158),
    onTertiary = Color(0xFF0A3D17),
    tertiaryContainer = Color(0xFF17BD3B),
    onTertiaryContainer = Color(0xFF80FF99),

    background = Color(0xFF0F0A00),
    onBackground = Color(0xFFFFF566),
    surface = Color(0xFF1F1A0A),
    onSurface = Color(0xFFFFF566),
    surfaceVariant = Color(0xFF3D331A),
    onSurfaceVariant = Color(0xFFE6CC80),

    // Material 3 Surface Container Colors for depth hierarchy
    surfaceContainer = Color(0xFF2A2410),
    surfaceContainerLow = Color(0xFF1F1A0A),
    surfaceContainerHigh = Color(0xFF3D331A),
    surfaceContainerHighest = Color(0xFF4A3F20),

    outline = Color(0xFFB8A000),
    outlineVariant = Color(0xFF5C4D1A),
    scrim = Color(0xFF000000),

    error = Color(0xFFFF453A),
    onError = Color(0xFF3D0F0A),
    errorContainer = Color(0xFFBD2217),
    onErrorContainer = Color(0xFFFF9980),

    surfaceTint = Color(0xFFFFD60A)
)

internal val ExpressiveLightColorScheme = lightColorScheme(
    primary = Color(0xFFB8A000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF566),
    onPrimaryContainer = Color(0xFF3D2F00),

    secondary = Color(0xFFBD2217),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE6E6),
    onSecondaryContainer = Color(0xFF3D0F0A),

    tertiary = Color(0xFF17BD3B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6FFE6),
    onTertiaryContainer = Color(0xFF0A3D17),

    background = Color(0xFFFFFCC7),
    onBackground = Color(0xFF3D2F00),
    surface = Color(0xFFFFFCC7),
    onSurface = Color(0xFF3D2F00),
    surfaceVariant = Color(0xFFFFF9B3),
    onSurfaceVariant = Color(0xFF5C4D1A),

    // Material 3 Surface Container Colors for depth hierarchy
    surfaceContainer = Color(0xFFF5F0B3),
    surfaceContainerLow = Color(0xFFFFFCC7),
    surfaceContainerHigh = Color(0xFFF0EBA0),
    surfaceContainerHighest = Color(0xFFEAE58D),

    outline = Color(0xFFB8A000),
    outlineVariant = Color(0xFFE6CC80),
    scrim = Color(0xFF000000),

    error = Color(0xFFBD2217),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE6E6),
    onErrorContainer = Color(0xFF3D0F0A),

    surfaceTint = Color(0xFFB8A000)
)

/**
 * Resolves the colour scheme for a theme style in light or dark mode.
 */
internal fun getColorScheme(themeStyle: AppThemeStyle, isDark: Boolean): ColorScheme =
    when (themeStyle) {
        AppThemeStyle.CLASSIC -> if (isDark) ClassicDarkColorScheme else ClassicLightColorScheme
        AppThemeStyle.Y2K -> if (isDark) Y2KDarkColorScheme else Y2KLightColorScheme
        AppThemeStyle.NEON -> if (isDark) NeonDarkColorScheme else NeonLightColorScheme
        AppThemeStyle.AMOLED -> if (isDark) AmoledDarkColorScheme else AmoledLightColorScheme
        AppThemeStyle.PASTEL -> if (isDark) PastelDarkColorScheme else PastelLightColorScheme
        AppThemeStyle.EXPRESSIVE ->
            if (isDark) ExpressiveDarkColorScheme else ExpressiveLightColorScheme
    }
