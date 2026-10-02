package com.bleelblep.glyphsharge.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.ui.components.cards.GlyphControlCard
import androidx.compose.ui.res.stringResource
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.layout.HomeSectionHeader
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The landing screen: the master glyph switch plus one card per feature.
 *
 * The settings store is read from `LocalSettingsRepository`, and the state
 * holder is passed in.
 *
 * [viewModel] has no default on purpose. It used to default to
 * `hiltViewModel()`, which resolved it against the `NavBackStackEntry` —
 * a *different* instance from the one `MainActivity` drives, so none of what
 * the Activity wrote ever reached these cards. It is a parameter so that being
 * the same object is something the compiler enforces at the call site.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = "Glyph Sharge",
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    ) {
        item {
            GlyphControlCard(
                enabled = uiState.glyphServiceEnabled,
                onEnabledChange = viewModel::toggleGlyphService,
            )
        }

        item { HomeSectionHeader(title = stringResource(R.string.home_section_features)) }

        homeFeatureCards(
            uiState = uiState,
            viewModel = viewModel,
        )
    }
}
