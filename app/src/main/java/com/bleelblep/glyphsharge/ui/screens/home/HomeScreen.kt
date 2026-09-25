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
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.components.GlyphControlCard
import com.bleelblep.glyphsharge.ui.components.HomeSectionHeader
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The landing screen: the master glyph switch plus one card per feature.
 *
 * State is observed from [HomeViewModel]; the screen itself holds nothing but
 * the layout.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    settingsRepository: SettingsRepository,
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
            settingsRepository = settingsRepository,
            viewModel = viewModel
        )
    }
}
