package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  Theme color helpers
//
//  Читаем LocalThemeState внутри каждой функции напрямую — не нужно знать
//  точное имя класса состояния темы.
//
//  Использование:
//    containerColor = themeCardContainerColor()
//    containerColor = themePrimaryActionColor()
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun themeCardContainerColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color(0xFF1A1A1A)
        AppThemeStyle.CLASSIC -> if (t.isDarkTheme) MaterialTheme.colorScheme.surfaceContainer
        else Color(0xFFF8F5FF)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
}

@Composable
fun themePrimaryActionColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color(0xFF4CAF50)
        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
fun themeCancelButtonColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color(0xFF38393B)
        AppThemeStyle.CLASSIC -> Color(0xFFFFDBD7)
        AppThemeStyle.NEON    -> Color(0xFF00FF00)
        else -> MaterialTheme.colorScheme.errorContainer
    }
}

@Composable
fun themeCancelButtonContentColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color.White
        AppThemeStyle.NEON    -> Color.Black
        AppThemeStyle.CLASSIC -> Color.Black
        else -> Color.Black
    }
}

@Composable
fun themeSettingsButtonColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color(0xFF2D4A3E)
        AppThemeStyle.CLASSIC -> Color(0xFF8D7BA5)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
}

@Composable
fun themeSettingsButtonContentColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color.White
        AppThemeStyle.CLASSIC -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
}

@Composable
fun themeBadgeContainerColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color(0xFF2D2D2D)
        AppThemeStyle.CLASSIC -> if (t.isDarkTheme) MaterialTheme.colorScheme.primaryContainer
        else Color(0xFFE8DEF8)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
}

@Composable
fun themeBadgeContentColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color.White
        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
}

@Composable
fun themeSliderThumbColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color(0xFF4CAF50)
        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
fun themeSurfaceVariantButtonColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color(0xFF2D2D2D)
        AppThemeStyle.CLASSIC -> Color(0xFFE8E1F5)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
fun themeSurfaceVariantButtonContentColor(): Color {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED  -> Color.White
        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}