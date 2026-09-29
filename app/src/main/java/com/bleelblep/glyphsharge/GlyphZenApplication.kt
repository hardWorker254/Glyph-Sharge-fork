package com.bleelblep.glyphsharge

import android.app.Application
import com.bleelblep.glyphsharge.data.SettingsMigrations
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.utils.LoggingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GlyphShargeApplication : Application() {

    @Inject
    lateinit var glyphManager: GlyphManager

    // Touched in onCreate so first-run defaults and version migrations reach
    // disk before any component can read preferences. `SettingsMigrations` also
    // runs them from its own `init` block, so the guarantee holds either way;
    // both entry points share one idempotent pass.
    @Inject
    lateinit var settingsMigrations: SettingsMigrations

    // Needed for the startup settings dump below.
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        LoggingManager.initialize(this)

        if (glyphManager.isNothingPhone()) {
            glyphManager.initialize()
        }

        settingsMigrations.applyMigrations()
        settingsRepository.dumpAllSettings()
    }

    override fun onTerminate() {
        super.onTerminate()

        if (glyphManager.isNothingPhone()) {
            glyphManager.cleanup()
        }
    }
} 