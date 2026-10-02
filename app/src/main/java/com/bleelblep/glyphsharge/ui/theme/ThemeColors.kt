package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
fun themeSecondaryButtonColors(): androidx.compose.material3.ButtonColors {
    val t = LocalThemeState.current
    return when (t.themeStyle) {
        AppThemeStyle.AMOLED -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2D2D2D),
            contentColor = Color.White,
        )
        AppThemeStyle.CLASSIC -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE8E1F5),
            contentColor = Color(0xFF674FA3),
        )
        else -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}