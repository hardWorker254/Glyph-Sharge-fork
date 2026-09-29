package com.bleelblep.glyphsharge.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the app-settings [SharedPreferences].
 *
 * The qualifier exists so that providing it is an explicit, single decision
 * rather than something every settings slice repeats for itself. `Context`
 * alone can back many preference files, and a slice that asked for a
 * `SharedPreferences` with no qualifier could silently be handed a different
 * one.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GlyphPrefs

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Name of the settings file.
     *
     * This is the name every phone already has on disk. Renaming it — even
     * "just" to something more descriptive — would start every user from empty
     * settings, so it is asserted in one place and never derived.
     *
     * `MainActivity.attachBaseContext` and `CustomAnimationsActivity` read this
     * same file before any injection exists, which is why the string is
     * duplicated there and cannot be helped from here.
     */
    private const val PREFS_NAME = "glyphzen_settings"

    /**
     * The one settings store, provided once for every slice.
     *
     * It is `@Singleton` because `SharedPreferences` keeps an in-process cache
     * of the file: handing out a second wrapper would mean a second cache and
     * a second writer for the same bytes.
     */
    @Provides
    @Singleton
    @GlyphPrefs
    fun provideGlyphPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
