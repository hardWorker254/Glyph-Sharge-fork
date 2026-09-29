package com.bleelblep.glyphsharge.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.ui.components.cards.GlyphControlCard
import com.bleelblep.glyphsharge.ui.components.layout.HomeSectionHeader
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The landing screen: the master glyph switch plus one card per feature.
 *
 * State is observed from [HomeViewModel] and the settings store is read from
 * `LocalSettingsRepository`, so the screen takes no dependencies at all and is
 * reachable from a preview or a test on its own.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                enabled = uiState.glyphServiceEnabled,
                onEnabledChange = viewModel::toggleGlyphService
            )
        }

        item { HomeSectionHeader(title = "Features") }

        homeFeatureCards(
            uiState = uiState,
            viewModel = viewModel
        )
    }
}
