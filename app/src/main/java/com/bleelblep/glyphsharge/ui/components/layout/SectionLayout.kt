package com.bleelblep.glyphsharge.ui.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState

/**
 * Section header for grouping cards.
 */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(start = 8.dp, bottom = 8.dp, top = 8.dp)
    )
}

/**
 * Feature grid layout for organizing multiple feature cards.
 */
@Composable
fun FeatureGrid(
    modifier: Modifier = Modifier,
    spacing: Int = 16,
    content: @Composable RowScope.() -> Unit
) {
    val themeState = LocalThemeState.current

    val enhancedSpacing = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        when (spacing) {
            8 -> 8
            16 -> 16
            24 -> 24
            32 -> 32
            else -> 16
        }
    } else {
        spacing
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(enhancedSpacing.dp),
        content = content
    )
}
