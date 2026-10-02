package com.bleelblep.glyphsharge.ui.components

import androidx.compose.runtime.Composable
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.ui.theme.LocalHomeViewModel

/**
 * The animation manager behind every "Test" button on a feature card.
 *
 * The configuration dialogs are plain Composables that reach their state from
 * the composition, so they read the manager the same way — through
 * [LocalHomeViewModel] — rather than injecting for themselves.
 *
 * That indirection was once a `hiltViewModel<HomeViewModel>()` call, justified
 * by "it resolves from inside a dialog as well as from a screen". That is
 * true and it is the problem: the dialog resolved a different instance from the
 * one `MainActivity` drives, so "Test" ran animations on a manager nothing else
 * was using. [LocalHomeViewModel] hands back the same object the home screen
 * has, and the dialog can only be reached through a composition that has it.
 */
@Composable
fun rememberGlyphAnimationManager(): GlyphAnimationManager =
    LocalHomeViewModel.current.glyphAnimationManager
