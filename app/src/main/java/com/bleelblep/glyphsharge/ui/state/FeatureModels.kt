package com.bleelblep.glyphsharge.ui.state

import androidx.compose.runtime.Immutable

/**
 * The glyph features the app can toggle.
 *
 * Previously each feature carried its own `is*Enabled()` / `save*Enabled()`
 * pair on `SettingsRepository` and its own hand-written card in
 * `FeatureCards.kt`, so the six of them were six near-copies that had to be
 * kept in sync by hand. Naming them once here means the feature list, the
 * service wiring and the UI all agree on a single enumeration.
 */
enum class GlyphFeature {
    CHARGING_ANIMATION,
    POWER_PEEK,
    PULSE_LOCK,
    SCREEN_OFF,
    NFC,
    LOW_BATTERY,
    MUSIC_VISUALIZER
}

/**
 * Toggle state of a single feature.
 *
 * @param isEnabled the user's preference, persisted by the repository.
 * @param isServiceActive whether the master glyph service is running. Cards
 *   stay visible but refuse to open while it is off, matching the previous
 *   behaviour where tapping showed a toast instead of a dialog.
 */
@Immutable
data class FeatureUiState(
    val feature: GlyphFeature,
    val isEnabled: Boolean = false,
    val isServiceActive: Boolean = true
)

/**
 * Everything the home screen renders.
 */
@Immutable
data class HomeUiState(
    val glyphServiceEnabled: Boolean = false,
    val features: Map<GlyphFeature, FeatureUiState> = emptyMap()
) {
    fun stateOf(feature: GlyphFeature): FeatureUiState =
        features[feature] ?: FeatureUiState(feature)
}

/**
 * Everything the settings screen renders.
 */
@Immutable
data class SettingsUiState(
    val isDarkTheme: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val languageCode: String = "system"
)
