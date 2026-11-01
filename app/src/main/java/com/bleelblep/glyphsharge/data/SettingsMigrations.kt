package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.bleelblep.glyphsharge.di.GlyphPrefs
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import com.bleelblep.glyphsharge.ui.theme.FontVariant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-run defaults, version migrations, and the one-time legacy fixup.
 *
 * This is the reason the settings were a `@Singleton` in the first place: the
 * class had to be *constructed* before anything could read a preference, so
 * that the defaults were on disk before the first read. Splitting the reads
 * out into slices would have quietly lost that guarantee if the migration pass
 * had stayed buried in whichever slice happened to be built first.
 *
 * ## Why both an `init` block and a public [applyMigrations]
 *
 * Both exist on purpose:
 *
 *  - The `init` block makes the guarantee structural. Hilt builds this the
 *    first time anything asks for it, so no matter which component that is, the
 *    defaults are written before that component's constructor body runs. This
 *    is what replaced the old `init` on `SettingsRepository`, and it is why
 *    the facade still takes this class as a constructor argument.
 *  - [applyMigrations] is the explicit call `GlyphShargeApplication` makes, so
 *    the ordering is stated in application code rather than left implied by an
 *    object graph, and so it stays visible to the next reader.
 *
 * [applied] makes the second path a no-op, so a process runs the pass exactly
 * once regardless of which path arrives first. Every step below is also
 * independently idempotent; the flag is belt and braces, not the mechanism.
 */
@Singleton
class SettingsMigrations @Inject constructor(
    @param:GlyphPrefs private val prefs: SharedPreferences,
    private val glyphService: GlyphServiceSettings,
) {

    private var applied = false

    init {
        applyMigrations()
    }

    fun applyMigrations() {
        if (applied) return
        applied = true
        applyFirstRunDefaults()
        applyVersionMigrations()
        normalizeLegacyVibrationIntensity()
    }

    private fun applyFirstRunDefaults() {
        if (prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false)) return

        prefs.edit {
            putBoolean(KEY_RETIRED_POWER_PEEK_ENABLED, false)
            putBoolean(GlyphServiceSettings.KEY_GLYPH_SERVICE_ENABLED, false)
            putBoolean(KEY_RETIRED_PULSE_LOCK_ENABLED, false)
            putBoolean(KEY_RETIRED_LOW_BATTERY_ENABLED, false)
            putBoolean(KEY_RETIRED_SCREEN_OFF_ENABLED, false)
            putBoolean(KEY_RETIRED_NFC_FEATURE_ENABLED, false)
            putBoolean(KEY_RETIRED_CHARGING_ANIMATION_ENABLED, false)
            putBoolean(KEY_RETIRED_VPN_CONNECTED_ENABLED, false)
            putBoolean(FeatureSettings.KEY_MUSIC_VIZ_ENABLED, false)
            putString(
                FeatureSettings.KEY_MUSIC_VIZ_ANIMATION_ID,
                MusicVisualizationMode.DEFAULT.id,
            )
            putFloat(
                FeatureSettings.KEY_MUSIC_VIZ_SENSITIVITY,
                FeatureSettings.DEFAULT_MUSIC_VIZ_SENSITIVITY,
            )
            putBoolean(FeatureSettings.KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, false)
            putString(FontSettings.KEY_FONT_VARIANT, FontVariant.HEADLINE.name)
            putBoolean(FontSettings.KEY_USE_CUSTOM_FONTS, true)
            putFloat(FontSettings.KEY_FONT_SIZE_DISPLAY_SCALE, 1.0f)
            putFloat(FontSettings.KEY_FONT_SIZE_TITLE_SCALE, 1.0f)
            putFloat(FontSettings.KEY_FONT_SIZE_BODY_SCALE, 1.0f)
            putFloat(FontSettings.KEY_FONT_SIZE_LABEL_SCALE, 1.0f)
            putBoolean(KEY_FIRST_RUN_COMPLETED, true)
            putInt(KEY_LAST_MIGRATED_VERSION, 112)
        }
        Log.i(TAG, "First-run defaults applied")
    }

    private fun applyVersionMigrations() {
        val lastMigrated = prefs.getInt(KEY_LAST_MIGRATED_VERSION, 0)

        if (lastMigrated < 109) {
            // Guarded on purpose: a bare reset here wipes the toggles of every feature at once
            // whenever the version marker is lost or rolled back.
            prefs.edit {
                if (!prefs.contains(KEY_RETIRED_POWER_PEEK_ENABLED)) putBoolean(KEY_RETIRED_POWER_PEEK_ENABLED, false)
                if (!prefs.contains(GlyphServiceSettings.KEY_GLYPH_SERVICE_ENABLED)) putBoolean(GlyphServiceSettings.KEY_GLYPH_SERVICE_ENABLED, false)
                if (!prefs.contains(KEY_RETIRED_PULSE_LOCK_ENABLED)) putBoolean(KEY_RETIRED_PULSE_LOCK_ENABLED, false)
                if (!prefs.contains(KEY_RETIRED_LOW_BATTERY_ENABLED)) putBoolean(KEY_RETIRED_LOW_BATTERY_ENABLED, false)
                if (!prefs.contains(FontSettings.KEY_FONT_VARIANT)) putString(FontSettings.KEY_FONT_VARIANT, FontVariant.HEADLINE.name)
                if (!prefs.contains(FontSettings.KEY_USE_CUSTOM_FONTS)) putBoolean(FontSettings.KEY_USE_CUSTOM_FONTS, true)
                if (!prefs.contains(FontSettings.KEY_FONT_SIZE_DISPLAY_SCALE)) putFloat(FontSettings.KEY_FONT_SIZE_DISPLAY_SCALE, 1.0f)
                if (!prefs.contains(FontSettings.KEY_FONT_SIZE_TITLE_SCALE)) putFloat(FontSettings.KEY_FONT_SIZE_TITLE_SCALE, 1.0f)
                if (!prefs.contains(FontSettings.KEY_FONT_SIZE_BODY_SCALE)) putFloat(FontSettings.KEY_FONT_SIZE_BODY_SCALE, 1.0f)
                if (!prefs.contains(FontSettings.KEY_FONT_SIZE_LABEL_SCALE)) putFloat(FontSettings.KEY_FONT_SIZE_LABEL_SCALE, 1.0f)
                putBoolean(FontSettings.KEY_FONT_SIZE_CUSTOMIZED, false)
                putInt(KEY_LAST_MIGRATED_VERSION, 109)
            }
            Log.i(TAG, "Migration to 109 applied")
        }

        if (lastMigrated < 110) {
            prefs.edit {
                if (!prefs.contains(KEY_RETIRED_SCREEN_OFF_ENABLED)) {
                    putBoolean(KEY_RETIRED_SCREEN_OFF_ENABLED, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 110)
            }
            Log.i(TAG, "Migration to 110 applied")
        }

        // NFC migration
        if (lastMigrated < 111) {
            prefs.edit {
                if (!prefs.contains(KEY_RETIRED_NFC_FEATURE_ENABLED)) {
                    putBoolean(KEY_RETIRED_NFC_FEATURE_ENABLED, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 111)
            }
            Log.i(TAG, "Migration to 111 applied")
        }

        // Charging migration
        if (lastMigrated < 112) {
            prefs.edit {
                if (!prefs.contains(KEY_RETIRED_CHARGING_ANIMATION_ENABLED)) {
                    putBoolean(KEY_RETIRED_CHARGING_ANIMATION_ENABLED, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 112)
            }
            Log.i(TAG, "Migration to 112 applied")
        }

        // Music visualiser migration
        if (lastMigrated < 113) {
            prefs.edit {
                if (!prefs.contains(FeatureSettings.KEY_MUSIC_VIZ_ENABLED)) {
                    putBoolean(FeatureSettings.KEY_MUSIC_VIZ_ENABLED, false)
                }
                if (!prefs.contains(FeatureSettings.KEY_MUSIC_VIZ_ANIMATION_ID)) {
                    putString(
                        FeatureSettings.KEY_MUSIC_VIZ_ANIMATION_ID,
                        MusicVisualizationMode.DEFAULT.id,
                    )
                }
                if (!prefs.contains(FeatureSettings.KEY_MUSIC_VIZ_SENSITIVITY)) {
                    putFloat(
                        FeatureSettings.KEY_MUSIC_VIZ_SENSITIVITY,
                        FeatureSettings.DEFAULT_MUSIC_VIZ_SENSITIVITY,
                    )
                }
                if (!prefs.contains(FeatureSettings.KEY_MUSIC_VIZ_SCREEN_OFF_ONLY)) {
                    putBoolean(FeatureSettings.KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 113)
            }
            Log.i(TAG, "Migration to 113 applied")
        }

        // VPN connected migration
        if (lastMigrated < 114) {
            prefs.edit {
                if (!prefs.contains(KEY_RETIRED_VPN_CONNECTED_ENABLED)) {
                    putBoolean(KEY_RETIRED_VPN_CONNECTED_ENABLED, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 114)
            }
            Log.i(TAG, "Migration to 114 applied")
        }

        // The seven features that became rules, and then went away with their
        // rules: the app now ships the user's own rules and the visualiser, and
        // the settings those features owned are read by nothing. Removed rather
        // than left to rot — a preference file is the one place where "the app
        // does not have this any more" and "the app forgot this" look the same
        // from the outside.
        if (lastMigrated < 115) {
            prefs.edit {
                remove(KEY_RETIRED_POWER_PEEK_ENABLED)
                remove(KEY_RETIRED_POWER_PEEK_THRESHOLD)
                remove(KEY_RETIRED_POWER_PEEK_DURATION)
                remove(KEY_RETIRED_POWER_PEEK_BATTERY_ARCS)
                remove(KEY_RETIRED_PULSE_LOCK_ENABLED)
                remove(KEY_RETIRED_PULSE_LOCK_ANIMATION_ID)
                remove(KEY_RETIRED_PULSE_LOCK_DURATION)
                remove(KEY_RETIRED_LOW_BATTERY_ENABLED)
                remove(KEY_RETIRED_LOW_BATTERY_THRESHOLD)
                remove(KEY_RETIRED_LOW_BATTERY_ANIMATION_ID)
                remove(KEY_RETIRED_LOW_BATTERY_DURATION)
                remove(KEY_RETIRED_SCREEN_OFF_ENABLED)
                remove(KEY_RETIRED_SCREEN_OFF_ANIMATION_ID)
                remove(KEY_RETIRED_SCREEN_OFF_DURATION)
                remove(KEY_RETIRED_NFC_FEATURE_ENABLED)
                remove(KEY_RETIRED_NFC_ANIMATION_ID)
                remove(KEY_RETIRED_NFC_ANIMATION_DURATION)
                remove(KEY_RETIRED_CHARGING_ANIMATION_ENABLED)
                remove(KEY_RETIRED_CHARGING_ANIMATION_DURATION)
                remove(KEY_RETIRED_CHARGING_BATTERY_ARCS)
                remove(KEY_RETIRED_VPN_CONNECTED_ENABLED)
                remove(KEY_RETIRED_VPN_CONNECTED_ANIMATION_ID)
                remove(KEY_RETIRED_VPN_CONNECTED_DURATION)
                putInt(KEY_LAST_MIGRATED_VERSION, 115)
            }
            Log.i(TAG, "Migration to 115 applied: the retired features' settings removed")
        }
    }

    /**
     * Vibration intensity is stored on a 1..255 scale and normalised to 0..1
     * here once, at startup. That keeps
     * [GlyphServiceSettings.getVibrationIntensity] a side-effect free getter.
     */
    private fun normalizeLegacyVibrationIntensity() {
        val stored = prefs.getFloat(
            GlyphServiceSettings.KEY_VIBRATION_INTENSITY,
            GlyphServiceSettings.DEFAULT_VIBRATION_INTENSITY,
        )
        if (stored <= 1.0f) return
        glyphService.saveVibrationIntensity(((stored - 1f) / 254f).coerceIn(0.1f, 1.0f))
    }

    internal companion object {
        // The settings the removed features owned, by their stored names.
        //
        // They are history now: the seven features became rules, and their
        // rules went with the user's request for the app to ship its own rules
        // and nothing else. The names live on because the migration that
        // removes them has to name them, and a name written down once is a name
        // that cannot drift.
        const val KEY_RETIRED_POWER_PEEK_ENABLED = "power_peek_enabled"
        const val KEY_RETIRED_POWER_PEEK_THRESHOLD = "shake_threshold"
        const val KEY_RETIRED_POWER_PEEK_DURATION = "display_duration"
        const val KEY_RETIRED_POWER_PEEK_BATTERY_ARCS = "power_peek_battery_arcs"
        const val KEY_RETIRED_PULSE_LOCK_ENABLED = "pulse_lock_enabled"
        const val KEY_RETIRED_PULSE_LOCK_ANIMATION_ID = "pulse_lock_animation_id"
        const val KEY_RETIRED_PULSE_LOCK_DURATION = "pulse_lock_duration"
        const val KEY_RETIRED_LOW_BATTERY_ENABLED = "low_battery_enabled"
        const val KEY_RETIRED_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        const val KEY_RETIRED_LOW_BATTERY_ANIMATION_ID = "low_battery_animation_id"
        const val KEY_RETIRED_LOW_BATTERY_DURATION = "low_battery_duration"
        const val KEY_RETIRED_SCREEN_OFF_ENABLED = "screen_off_enabled"
        const val KEY_RETIRED_SCREEN_OFF_ANIMATION_ID = "screen_off_animation_id"
        const val KEY_RETIRED_SCREEN_OFF_DURATION = "screen_off_duration"
        const val KEY_RETIRED_NFC_FEATURE_ENABLED = "nfc_feature_enabled"
        const val KEY_RETIRED_NFC_ANIMATION_ID = "nfc_animation_id"
        const val KEY_RETIRED_NFC_ANIMATION_DURATION = "nfc_animation_duration"
        const val KEY_RETIRED_CHARGING_ANIMATION_ENABLED = "charging_animation_enabled"
        const val KEY_RETIRED_CHARGING_ANIMATION_DURATION = "charging_animation_duration"
        const val KEY_RETIRED_CHARGING_BATTERY_ARCS = "charging_battery_arcs"
        const val KEY_RETIRED_VPN_CONNECTED_ENABLED = "vpn_connected_enabled"
        const val KEY_RETIRED_VPN_CONNECTED_ANIMATION_ID = "vpn_connected_animation_id"
        const val KEY_RETIRED_VPN_CONNECTED_DURATION = "vpn_connected_duration"
        /**
         * The tag is not a stored value, but it *is* part of what a bug report
         * carries, and re-tagging these lines would make existing log filters
         * stop matching this startup work.
         */
        private const val TAG = "SettingsRepository"

        const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
        const val KEY_LAST_MIGRATED_VERSION = "last_migrated_version"
    }
}
