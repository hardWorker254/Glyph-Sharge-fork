package com.bleelblep.glyphsharge.di

import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsRepositoryEntryPoint {
        fun getSettingsRepository(): SettingsRepository
    }
} 