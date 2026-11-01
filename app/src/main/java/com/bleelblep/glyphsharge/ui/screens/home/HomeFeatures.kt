package com.bleelblep.glyphsharge.ui.screens.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.ui.components.MusicVisualizerCard
import com.bleelblep.glyphsharge.ui.state.HomeUiState
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The one feature card left on the home screen.
 *
 * Everything a card needs is already on [HomeUiState], so the block below is
 * pure wiring: read the toggle, name the feature for its test action, hand both
 * callbacks through.
 *
 * **One card, because one feature is one.** The seven that used to stand here
 * became rules, and their rules then went the way of every built-in rule the
 * user does not want: the app ships the user's own rules and the visualiser.
 *
 * The visualiser is what is left, and it is left because it cannot be a rule:
 * it captures other apps' audio through a `MediaProjection` token, and only an
 * Activity can obtain one — so its switch has to be driven from the screen that
 * asks the system for consent, and a row in a list has no Activity to ask with.
 */
internal fun LazyListScope.homeFeatureCards(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
) {
    item {
        // The switch asks the ViewModel for the feature rather than running
        // the capture grants itself: the Quick Settings tile asks for the same
        // thing, and the two grants can only be answered by an Activity. See
        // `MainActivity.watchMusicCaptureRequests`.
        //
        // Placed last even though it is the only one left: the visualiser holds
        // the Glyph strip for as long as music plays, so it is the one feature
        // that yields to all the others, and last is where "yields" reads.
        MusicVisualizerCard(
            isEnabled = uiState.stateOf(GlyphFeature.MUSIC_VISUALIZER).isEnabled,
            isServiceActive = uiState.glyphServiceEnabled,
            onEnabledChange = viewModel::toggleMusicVisualizer,
            onTestAnimation = { viewModel.testFeature(GlyphFeature.MUSIC_VISUALIZER) },
            icon = rememberVectorPainter(image = Icons.Default.LibraryMusic),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
        )
    }
}
