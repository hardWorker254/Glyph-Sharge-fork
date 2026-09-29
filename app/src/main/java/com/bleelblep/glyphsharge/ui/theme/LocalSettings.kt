package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.bleelblep.glyphsharge.data.SettingsRepository

/**
 * The settings store, offered to the tree once by each Activity.
 *
 * A card, a dialog or a settings section reads the store it needs without
 * taking a parameter every caller would have to thread down, and without
 * having to know how the store is built.
 *
 * The default throws rather than returning a stub: a screen that read a
 * placeholder would quietly write the user's settings into nothing.
 */
val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository should be provided at the composition root")
}

/**
 * How hard the phone buzzes, on the 0..1 scale the setting is stored in.
 *
 * Kept apart from [LocalSettingsRepository] because
 * [com.bleelblep.glyphsharge.ui.utils.HapticUtils] is a plain object called from
 * click handlers, and a handler cannot read a repository. A float in the
 * composition is what lets the value be injected once at the root and passed
 * down as an ordinary argument.
 *
 * The default is the same medium step the settings store falls back to, so a
 * tree that never got a provider — a preview, a test — still buzzes like a
 * phone whose setting was never touched.
 */
val LocalVibrationIntensity = staticCompositionLocalOf { DEFAULT_VIBRATION_INTENSITY }

/** Medium. The value `GlyphServiceSettings` stores when nothing was ever saved. */
private const val DEFAULT_VIBRATION_INTENSITY = 0.66f
