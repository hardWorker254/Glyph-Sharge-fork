package com.bleelblep.glyphsharge.di

import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The escape hatch for the screens that are not ViewModels.
 *
 * Hilt injects into Activities, Views and ViewModels, but the feature dialogs
 * are plain Composables that only have a `Context`. They reach the animation
 * manager and the custom-animation list through this entry point rather than
 * threading a repository through every call site.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GlyphComponent {
    fun glyphAnimationManager(): GlyphAnimationManager

    fun customAnimationRepository(): CustomAnimationRepository
}
