package com.bleelblep.glyphsharge.di

import android.content.Context
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.theme.ThemeState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsRepositoryEntryPoint {
        fun getSettingsRepository(): SettingsRepository
    }
} 