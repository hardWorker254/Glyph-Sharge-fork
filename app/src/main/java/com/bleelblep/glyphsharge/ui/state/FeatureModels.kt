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
    /** The rules the home list shows, in repository order: the user's own. */
    val rules: List<RuleUiState> = emptyList(),
) {
    fun stateOf(feature: GlyphFeature): FeatureUiState =
        features[feature] ?: FeatureUiState(feature)
}

/**
 * One row of the constructor's rule list.
 *
 * Every rule in the app is one the user built, so every row is the same kind
 * of row: switchable, editable and deletable.
 *
 * @param description "trigger → animation", spelled out so the row is readable
 *   without opening it.
 * @param canExportScript whether the row's overflow menu can offer the rule's
 *   `.servicelua` file: the rule has to be scripted *and* the script still
 *   installed. The home list has no menu and simply ignores it — the fact
 *   belongs to the rule, so one mapper can answer it for both screens.
 */
@Immutable
data class RuleUiState(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val canExportScript: Boolean = false,
)

