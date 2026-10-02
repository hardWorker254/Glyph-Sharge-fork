package com.bleelblep.glyphsharge.ui.state

import androidx.compose.runtime.Immutable
import com.bleelblep.glyphsharge.glyph.GlyphFeature

/**
 * Toggle state of a single feature.
 *
 * Cards stay visible when the glyph service is off but refuse to open:
 * tapping shows the service-off toast instead of the feature's dialog.
 */
@Immutable
data class FeatureUiState(
    val feature: GlyphFeature,
    val isEnabled: Boolean = false,
    val isServiceActive: Boolean = true,
)

/**
 * Everything the home screen renders.
 */
@Immutable
data class HomeUiState(
    val glyphServiceEnabled: Boolean = false,
    val features: Map<GlyphFeature, FeatureUiState> = emptyMap(),
) {
    fun stateOf(feature: GlyphFeature): FeatureUiState =
        features[feature] ?: FeatureUiState(feature)
}
