package com.bleelblep.glyphsharge.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.components.GlyphControlCard
import com.bleelblep.glyphsharge.ui.components.HomeSectionHeader
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold

/**
 * The landing screen: the master glyph switch plus one card per feature.
 */
@Composable
fun HomeScreen(
    glyphServiceEnabled: Boolean,
    onGlyphServiceToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    actions: HomeActions,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    SettingsScaffold(
        title = "Glyph Sharge",
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    ) {
        item {
            GlyphControlCard(
                enabled = glyphServiceEnabled,
                onEnabledChange = onGlyphServiceToggle
            )
        }

        item { HomeSectionHeader(title = "Features") }

        homeFeatureCards(
            glyphServiceEnabled = glyphServiceEnabled,
            settingsRepository = settingsRepository,
            actions = actions
        )
    }
}
