package com.bleelblep.glyphsharge.ui.components

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The animation manager behind every "Test" button on a feature card.
 *
 * The configuration dialogs are plain Composables, so they cannot inject for
 * themselves. [HomeViewModel] already holds the manager because it plays these
 * features' animations, and `hiltViewModel()` resolves from inside a dialog as
 * well as from a screen — so this hands back the very instance the home screen
 * uses.
 */
@Composable
fun rememberGlyphAnimationManager(): GlyphAnimationManager =
    hiltViewModel<HomeViewModel>().glyphAnimationManager
