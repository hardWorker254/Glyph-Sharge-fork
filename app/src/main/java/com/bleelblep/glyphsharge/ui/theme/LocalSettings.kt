package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The values `MainActivity` hands to the Compose tree exactly once.
 *
 * Each is a thing the tree needs that it cannot build for itself: the settings
 * store, the home screen's state holder, and the haptic strength. Handing them
 * down as parameters would put a repository and a ViewModel into every
 * screen's signature; the composition locals let a card, a dialog or a
 * settings section read what it needs without threading anything through its
 * callers.
 */

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
 * The one [HomeViewModel] the whole tree shares.
 *
 * This exists because there were two, and having two is invisible in the code
 * and obvious on screen. `MainActivity` held one through `by viewModels()` and
 * drove it — the master switch, the session callback, the NFC hook, the music
 * capture — while `HomeScreen` asked `hiltViewModel()` for its own, resolved
 * against the `NavBackStackEntry` `NavHost` substitutes. Two `ViewModelStore`s,
 * two objects, both built from a `@HiltViewModel`, no error anywhere.
 *
 * What the split cost: everything `MainActivity` wrote went to an instance no
 * card could see, so `glyphServiceEnabled` stayed `false` on the copy the cards
 * read and all eight cards stayed disabled with a "service is off" toast on
 * every tap; `setNfcDispatchHook` was installed on the other instance and
 * foreground dispatch was never enabled; and the visualiser's switch raised a
 * request on a channel that only the Activity drained, so it filled and
 * nothing answered it — with no error, because a full channel is not an
 * exception.
 *
 * Handing the Activity's instance down is what makes those the same object
 * again. [LocalSettingsRepository] sits next to it because it is the same kind
 * of decision: one instance, provided at the root, read by whoever needs it.
 *
 * The default throws, as [LocalSettingsRepository] does. A tree that reached
 * this without a provider used to resolve its own ViewModel, which is exactly
 * how the second instance appeared.
 */
val LocalHomeViewModel = staticCompositionLocalOf<HomeViewModel> {
    error("HomeViewModel should be provided by MainActivity at the composition root")
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
