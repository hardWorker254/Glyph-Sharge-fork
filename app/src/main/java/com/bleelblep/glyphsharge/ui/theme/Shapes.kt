package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes as M3Shapes

/**
 * Corner treatment per theme style.
 *
 * Y2K and Neon deliberately use tight corners, Expressive uses asymmetric
 * ones, and everything else falls back to the Material 3 defaults.
 */
internal fun getShapes(themeStyle: AppThemeStyle): M3Shapes = when (themeStyle) {
    AppThemeStyle.EXPRESSIVE -> M3Shapes(
        // Expressive asymmetric shapes for more playful appearance
        extraSmall = RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 12.dp,
            bottomStart = 12.dp,
            bottomEnd = 8.dp
        ),
        small = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 16.dp
        ),
        medium = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 28.dp,
            bottomStart = 28.dp,
            bottomEnd = 24.dp
        ),
        large = RoundedCornerShape(
            topStart = 32.dp,
            topEnd = 36.dp,
            bottomStart = 36.dp,
            bottomEnd = 32.dp
        ),
        extraLarge = RoundedCornerShape(
            topStart = 40.dp,
            topEnd = 44.dp,
            bottomStart = 44.dp,
            bottomEnd = 40.dp
        )
    )
    AppThemeStyle.Y2K -> M3Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(12.dp)
    )
    AppThemeStyle.NEON -> M3Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(12.dp)
    )
    else -> M3Shapes() // Default Material 3 shapes for other themes
}
