package com.bleelblep.glyphsharge.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.FontSizeSettings
import com.bleelblep.glyphsharge.ui.theme.FontVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persisting app settings using SharedPreferences
 */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    init {
        applyFirstRunDefaults()
        applyVersionMigrations()
        normalizeLegacyVibrationIntensity()
    }

    /**
     * Vibration intensity used to be persisted on a 1..255 scale. Normalising it once at
     * startup keeps [getVibrationIntensity] a side-effect free getter.
     */
    private fun normalizeLegacyVibrationIntensity() {
        val stored = prefs.getFloat(KEY_VIBRATION_INTENSITY, DEFAULT_VIBRATION_INTENSITY)
        if (stored <= 1.0f) return
        saveVibrationIntensity(((stored - 1f) / 254f).coerceIn(0.1f, 1.0f))
    }

    companion object {
        private const val TAG = "SettingsRepository"
        private const val PREFS_NAME = "glyphzen_settings"
        private const val LANGUAGE = "language"

        // Keys
        private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
        private const val KEY_LAST_MIGRATED_VERSION = "last_migrated_version"

        private const val KEY_FONT_VARIANT = "font_variant"
        private const val KEY_USE_CUSTOM_FONTS = "use_custom_fonts"
        private const val KEY_FONT_SIZE_DISPLAY_SCALE = "font_size_display_scale"
        private const val KEY_FONT_SIZE_TITLE_SCALE = "font_size_title_scale"
        private const val KEY_FONT_SIZE_BODY_SCALE = "font_size_body_scale"
        private const val KEY_FONT_SIZE_LABEL_SCALE = "font_size_label_scale"
        private const val KEY_FONT_SIZE_CUSTOMIZED = "font_size_customized"

        private const val KEY_IS_DARK_THEME = "is_dark_theme"
        private const val KEY_THEME_STYLE = "theme_style"
        private const val KEY_GLYPH_SERVICE_ENABLED = "glyph_service_enabled"
    private const val KEY_USER_PRESENT_EXPECTED = "user_present_expected"

        private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"

        // Power Peek. Threshold/duration keep their legacy storage names on purpose:
        // renaming the stored strings would silently reset the values of existing users.
        private const val KEY_POWER_PEEK_ENABLED = "power_peek_enabled"
        private const val KEY_POWER_PEEK_THRESHOLD = "shake_threshold"
        private const val KEY_POWER_PEEK_DURATION = "display_duration"

        // Pulse Lock keys
        private const val KEY_PULSE_LOCK_ENABLED = "pulse_lock_enabled"
        private const val KEY_PULSE_LOCK_ANIMATION_ID = "pulse_lock_animation_id"
        private const val KEY_PULSE_LOCK_DURATION = "pulse_lock_duration"

        // Low Battery keys
        private const val KEY_LOW_BATTERY_ENABLED = "low_battery_enabled"
        private const val KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        private const val KEY_LOW_BATTERY_ANIMATION_ID = "low_battery_animation_id"
        private const val KEY_LOW_BATTERY_DURATION = "low_battery_duration"

        // Screen Off keys
        private const val KEY_SCREEN_OFF_ENABLED = "screen_off_enabled"
        private const val KEY_SCREEN_OFF_ANIMATION_ID = "screen_off_animation_id"
        private const val KEY_SCREEN_OFF_DURATION = "screen_off_duration"

        // NFC keys
        private const val KEY_NFC_FEATURE_ENABLED = "nfc_feature_enabled"
        private const val KEY_NFC_ANIMATION_ID = "nfc_animation_id"
        private const val KEY_NFC_ANIMATION_DURATION = "nfc_animation_duration"

        // Quiet Hours keys
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START_HOUR = "quiet_hours_start_hour"
        private const val KEY_QUIET_HOURS_START_MINUTE = "quiet_hours_start_minute"
        private const val KEY_QUIET_HOURS_END_HOUR = "quiet_hours_end_hour"
        private const val KEY_QUIET_HOURS_END_MINUTE = "quiet_hours_end_minute"

        // Battery Charging
        private const val KEY_CHARGING_ANIMATION_ENABLED = "charging_animation_enabled"
        private const val KEY_CHARGING_ANIMATION_DURATION = "charging_animation_duration"

        // Music visualiser
        private const val KEY_MUSIC_VIZ_ENABLED = "music_viz_enabled"
        private const val KEY_MUSIC_VIZ_ANIMATION_ID = "music_viz_animation_id"
        private const val KEY_MUSIC_VIZ_SENSITIVITY = "music_viz_sensitivity"
        private const val KEY_MUSIC_VIZ_SCREEN_OFF_ONLY = "music_viz_screen_off_only"

        // Defaults
        private const val DEFAULT_VIBRATION_INTENSITY = 0.66f
        private const val DEFAULT_POWER_PEEK_DURATION = 3000L
        private const val DEFAULT_PULSE_LOCK_DURATION = 5000L
        private const val DEFAULT_LOW_BATTERY_DURATION = 10000L
        private const val DEFAULT_SCREEN_OFF_DURATION = 3000L
        private const val DEFAULT_NFC_ANIMATION_DURATION = 3000L
        private const val DEFAULT_CHARGING_ANIMATION_DURATION = 3000L
        private const val DEFAULT_LOW_BATTERY_THRESHOLD = 20
        private const val DEFAULT_MUSIC_VIZ_SENSITIVITY = 1.0f
        private const val DEFAULT_QUIET_HOURS_START_HOUR = 22
        private const val DEFAULT_QUIET_HOURS_START_MINUTE = 0
        private const val DEFAULT_QUIET_HOURS_END_HOUR = 7
        private const val DEFAULT_QUIET_HOURS_END_MINUTE = 0

        const val SHAKE_SOFT = 12.0f
        const val SHAKE_EASY = 15.0f
        const val SHAKE_MEDIUM = 18.0f
        const val SHAKE_HARD = 22.0f
        const val SHAKE_HARDEST = 28.0f
    }

    private fun applyFirstRunDefaults() {
        if (prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false)) return

        prefs.edit {
            putBoolean(KEY_POWER_PEEK_ENABLED, false)
            putBoolean(KEY_GLYPH_SERVICE_ENABLED, false)
            putBoolean(KEY_PULSE_LOCK_ENABLED, false)
            putBoolean(KEY_LOW_BATTERY_ENABLED, false)
            putBoolean(KEY_SCREEN_OFF_ENABLED, false)
            putBoolean(KEY_NFC_FEATURE_ENABLED, false)
            putBoolean(KEY_CHARGING_ANIMATION_ENABLED, false)
            putBoolean(KEY_MUSIC_VIZ_ENABLED, false)
            putString(KEY_MUSIC_VIZ_ANIMATION_ID, MusicVisualizationMode.DEFAULT.id)
            putFloat(KEY_MUSIC_VIZ_SENSITIVITY, DEFAULT_MUSIC_VIZ_SENSITIVITY)
            putBoolean(KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, false)
            putString(KEY_FONT_VARIANT, FontVariant.HEADLINE.name)
            putBoolean(KEY_USE_CUSTOM_FONTS, true)
            putFloat(KEY_FONT_SIZE_DISPLAY_SCALE, 1.0f)
            putFloat(KEY_FONT_SIZE_TITLE_SCALE, 1.0f)
            putFloat(KEY_FONT_SIZE_BODY_SCALE, 1.0f)
            putFloat(KEY_FONT_SIZE_LABEL_SCALE, 1.0f)
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
                if (!prefs.contains(KEY_POWER_PEEK_ENABLED)) putBoolean(KEY_POWER_PEEK_ENABLED, false)
                if (!prefs.contains(KEY_GLYPH_SERVICE_ENABLED)) putBoolean(KEY_GLYPH_SERVICE_ENABLED, false)
                if (!prefs.contains(KEY_PULSE_LOCK_ENABLED)) putBoolean(KEY_PULSE_LOCK_ENABLED, false)
                if (!prefs.contains(KEY_LOW_BATTERY_ENABLED)) putBoolean(KEY_LOW_BATTERY_ENABLED, false)
                if (!prefs.contains(KEY_FONT_VARIANT)) putString(KEY_FONT_VARIANT, FontVariant.HEADLINE.name)
                if (!prefs.contains(KEY_USE_CUSTOM_FONTS)) putBoolean(KEY_USE_CUSTOM_FONTS, true)
                if (!prefs.contains(KEY_FONT_SIZE_DISPLAY_SCALE)) putFloat(KEY_FONT_SIZE_DISPLAY_SCALE, 1.0f)
                if (!prefs.contains(KEY_FONT_SIZE_TITLE_SCALE)) putFloat(KEY_FONT_SIZE_TITLE_SCALE, 1.0f)
                if (!prefs.contains(KEY_FONT_SIZE_BODY_SCALE)) putFloat(KEY_FONT_SIZE_BODY_SCALE, 1.0f)
                if (!prefs.contains(KEY_FONT_SIZE_LABEL_SCALE)) putFloat(KEY_FONT_SIZE_LABEL_SCALE, 1.0f)
                putBoolean(KEY_FONT_SIZE_CUSTOMIZED, false)
                putInt(KEY_LAST_MIGRATED_VERSION, 109)
            }
            Log.i(TAG, "Migration to 109 applied")
        }

        if (lastMigrated < 110) {
            prefs.edit {
                if (!prefs.contains(KEY_SCREEN_OFF_ENABLED)) {
                    putBoolean(KEY_SCREEN_OFF_ENABLED, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 110)
            }
            Log.i(TAG, "Migration to 110 applied")
        }

        // NFC migration
        if (lastMigrated < 111) {
            prefs.edit {
                if (!prefs.contains(KEY_NFC_FEATURE_ENABLED)) {
                    putBoolean(KEY_NFC_FEATURE_ENABLED, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 111)
            }
            Log.i(TAG, "Migration to 111 applied")
        }

        // Charging migration
        if (lastMigrated < 112) {
            prefs.edit {
                if (!prefs.contains(KEY_CHARGING_ANIMATION_ENABLED)) {
                    putBoolean(KEY_CHARGING_ANIMATION_ENABLED, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 112)
            }
            Log.i(TAG, "Migration to 112 applied")
        }

        // Music visualiser migration
        if (lastMigrated < 113) {
            prefs.edit {
                if (!prefs.contains(KEY_MUSIC_VIZ_ENABLED)) {
                    putBoolean(KEY_MUSIC_VIZ_ENABLED, false)
                }
                if (!prefs.contains(KEY_MUSIC_VIZ_ANIMATION_ID)) {
                    putString(KEY_MUSIC_VIZ_ANIMATION_ID, MusicVisualizationMode.DEFAULT.id)
                }
                if (!prefs.contains(KEY_MUSIC_VIZ_SENSITIVITY)) {
                    putFloat(KEY_MUSIC_VIZ_SENSITIVITY, DEFAULT_MUSIC_VIZ_SENSITIVITY)
                }
                if (!prefs.contains(KEY_MUSIC_VIZ_SCREEN_OFF_ONLY)) {
                    putBoolean(KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, false)
                }
                putInt(KEY_LAST_MIGRATED_VERSION, 113)
            }
            Log.i(TAG, "Migration to 113 applied")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Font settings
    // ─────────────────────────────────────────────────────────────────────────

    fun saveFontVariant(variant: FontVariant) =
        prefs.edit { putString(KEY_FONT_VARIANT, variant.name) }

    fun getFontVariant(): FontVariant {
        val variantName = prefs.getString(KEY_FONT_VARIANT, FontVariant.HEADLINE.name)
        return try {
            FontVariant.valueOf(variantName ?: FontVariant.HEADLINE.name)
        } catch (_: IllegalArgumentException) {
            FontVariant.HEADLINE
        }
    }

    fun saveUseCustomFonts(useCustom: Boolean) =
        prefs.edit { putBoolean(KEY_USE_CUSTOM_FONTS, useCustom) }

    fun getUseCustomFonts(): Boolean = prefs.getBoolean(KEY_USE_CUSTOM_FONTS, true)

    fun saveFontSizeSettings(settings: FontSizeSettings) {
        prefs.edit {
            putFloat(KEY_FONT_SIZE_DISPLAY_SCALE, settings.displayScale)
            putFloat(KEY_FONT_SIZE_TITLE_SCALE, settings.titleScale)
            putFloat(KEY_FONT_SIZE_BODY_SCALE, settings.bodyScale)
            putFloat(KEY_FONT_SIZE_LABEL_SCALE, settings.labelScale)
            putBoolean(KEY_FONT_SIZE_CUSTOMIZED, true)
        }
    }

    fun getFontSizeSettings(): FontSizeSettings {
        return FontSizeSettings(
            prefs.getFloat(KEY_FONT_SIZE_DISPLAY_SCALE, 1.0f),
            prefs.getFloat(KEY_FONT_SIZE_TITLE_SCALE, 1.0f),
            prefs.getFloat(KEY_FONT_SIZE_BODY_SCALE, 1.0f),
            prefs.getFloat(KEY_FONT_SIZE_LABEL_SCALE, 1.0f)
        )
    }

    fun clearFontSizeCustomization() {
        prefs.edit {
            remove(KEY_FONT_SIZE_DISPLAY_SCALE)
            remove(KEY_FONT_SIZE_TITLE_SCALE)
            remove(KEY_FONT_SIZE_BODY_SCALE)
            remove(KEY_FONT_SIZE_LABEL_SCALE)
            putBoolean(KEY_FONT_SIZE_CUSTOMIZED, false)
        }
    }

    fun getFontSizeSettingsForFont(fontVariant: FontVariant): FontSizeSettings {
        return if (prefs.getBoolean(KEY_FONT_SIZE_CUSTOMIZED, false)) {
            getFontSizeSettings()
        } else {
            FontSizeSettings.getDefaultForFont(fontVariant)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Theme settings
    // ─────────────────────────────────────────────────────────────────────────

    fun saveTheme(isDarkTheme: Boolean) =
        prefs.edit { putBoolean(KEY_IS_DARK_THEME, isDarkTheme) }

    fun getTheme(): Boolean = prefs.getBoolean(KEY_IS_DARK_THEME, false)

    fun saveThemeStyle(themeStyle: AppThemeStyle) =
        prefs.edit { putString(KEY_THEME_STYLE, themeStyle.name) }

    fun getThemeStyle(): AppThemeStyle {
        val styleName = prefs.getString(KEY_THEME_STYLE, AppThemeStyle.CLASSIC.name)
        return try {
            AppThemeStyle.valueOf(styleName ?: AppThemeStyle.CLASSIC.name)
        } catch (_: IllegalArgumentException) {
            AppThemeStyle.CLASSIC
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Services
    // ─────────────────────────────────────────────────────────────────────────

    fun saveGlyphServiceEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_GLYPH_SERVICE_ENABLED, enabled) }

    fun getGlyphServiceEnabled(): Boolean =
        prefs.getBoolean(KEY_GLYPH_SERVICE_ENABLED, false)

    // ─────────────────────────────────────────────────────────────────────────
    // Glow Gate: what "unlocked" looks like on this phone
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Whether to wait for `ACTION_USER_PRESENT` before giving up on it.
     *
     * The honest trigger for Glow Gate is "the user got past the keyguard". On
     * some phones that broadcast simply never arrives — the keyguard never
     * presents, or the system does not send it — and then every unlock waits
     * out a grace period for an event that is never coming, which is latency
     * on the one thing the user is looking at.
     *
     * So the answer is learned rather than assumed. The first unlock waits,
     * which is the only way to find out; every unlock after that goes straight
     * to the animation. If a real unlock ever does show up, this flips back and
     * the wait resumes.
     */
    fun isUserPresentExpected(): Boolean = prefs.getBoolean(KEY_USER_PRESENT_EXPECTED, true)

    fun markUserPresentSeen() = prefs.edit { putBoolean(KEY_USER_PRESENT_EXPECTED, true) }

    fun markUserPresentMissing() = prefs.edit { putBoolean(KEY_USER_PRESENT_EXPECTED, false) }

    // ─────────────────────────────────────────────────────────────────────────
    // Power Peek
    // ─────────────────────────────────────────────────────────────────────────

    fun savePowerPeekEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_POWER_PEEK_ENABLED, enabled) }

    fun isPowerPeekEnabled(): Boolean =
        prefs.getBoolean(KEY_POWER_PEEK_ENABLED, false)

    fun savePowerPeekThreshold(threshold: Float) =
        prefs.edit { putFloat(KEY_POWER_PEEK_THRESHOLD, threshold) }

    fun getPowerPeekThreshold(): Float =
        prefs.getFloat(KEY_POWER_PEEK_THRESHOLD, SHAKE_MEDIUM)

    fun getShakeIntensityLevel(threshold: Float): Int = when (threshold) {
        SHAKE_SOFT -> R.string.power_peek_sensitivity_soft
        SHAKE_EASY -> R.string.power_peek_sensitivity_easy
        SHAKE_MEDIUM -> R.string.power_peek_sensitivity_medium
        SHAKE_HARD -> R.string.power_peek_sensitivity_hard
        SHAKE_HARDEST -> R.string.power_peek_sensitivity_hardest
        else -> R.string.power_peek_sensitivity_medium
    }

    fun savePowerPeekDuration(duration: Long) =
        prefs.edit { putLong(KEY_POWER_PEEK_DURATION, duration) }

    fun getPowerPeekDuration(): Long =
        prefs.getLong(KEY_POWER_PEEK_DURATION, DEFAULT_POWER_PEEK_DURATION)

    fun saveVibrationIntensity(intensity: Float) =
        prefs.edit { putFloat(KEY_VIBRATION_INTENSITY, intensity) }

    fun getVibrationIntensity(): Float =
        prefs.getFloat(KEY_VIBRATION_INTENSITY, DEFAULT_VIBRATION_INTENSITY)


    // ─────────────────────────────────────────────────────────────────────────
    // Glow Gate (Pulse Lock)
    // ─────────────────────────────────────────────────────────────────────────

    fun savePulseLockEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_PULSE_LOCK_ENABLED, enabled) }

    fun isPulseLockEnabled(): Boolean =
        prefs.getBoolean(KEY_PULSE_LOCK_ENABLED, false)

    fun savePulseLockAnimationId(id: String) =
        prefs.edit { putString(KEY_PULSE_LOCK_ANIMATION_ID, id) }

    fun getPulseLockAnimationId(): String =
        prefs.getString(KEY_PULSE_LOCK_ANIMATION_ID, "C1") ?: "C1"

    fun savePulseLockDuration(durationMs: Long) =
        prefs.edit { putLong(KEY_PULSE_LOCK_DURATION, durationMs) }

    fun getPulseLockDuration(): Long =
        prefs.getLong(KEY_PULSE_LOCK_DURATION, DEFAULT_PULSE_LOCK_DURATION)

    // ─────────────────────────────────────────────────────────────────────────
    // Low-Battery
    // ─────────────────────────────────────────────────────────────────────────

    fun saveLowBatteryEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_LOW_BATTERY_ENABLED, enabled) }

    fun isLowBatteryEnabled(): Boolean =
        prefs.getBoolean(KEY_LOW_BATTERY_ENABLED, false)

    fun saveLowBatteryThreshold(pct: Int) =
        prefs.edit { putInt(KEY_LOW_BATTERY_THRESHOLD, pct) }

    fun getLowBatteryThreshold(): Int =
        prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, DEFAULT_LOW_BATTERY_THRESHOLD)

    fun saveLowBatteryAnimationId(id: String) =
        prefs.edit { putString(KEY_LOW_BATTERY_ANIMATION_ID, id) }

    fun getLowBatteryAnimationId(): String =
        prefs.getString(KEY_LOW_BATTERY_ANIMATION_ID, "C1") ?: "C1"

    fun saveLowBatteryDuration(durationMs: Long) =
        prefs.edit { putLong(KEY_LOW_BATTERY_DURATION, durationMs) }

    fun getLowBatteryDuration(): Long =
        prefs.getLong(KEY_LOW_BATTERY_DURATION, DEFAULT_LOW_BATTERY_DURATION)

    // ─────────────────────────────────────────────────────────────────────────
    // Screen Off
    // ─────────────────────────────────────────────────────────────────────────

    fun saveScreenOffFeatureEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_SCREEN_OFF_ENABLED, enabled) }

    fun isScreenOffFeatureEnabled(): Boolean =
        prefs.getBoolean(KEY_SCREEN_OFF_ENABLED, false)

    fun saveScreenOffAnimationId(id: String) =
        prefs.edit { putString(KEY_SCREEN_OFF_ANIMATION_ID, id) }

    fun getScreenOffAnimationId(): String =
        prefs.getString(KEY_SCREEN_OFF_ANIMATION_ID, "C1") ?: "C1"

    fun saveScreenOffDuration(durationMs: Long) =
        prefs.edit { putLong(KEY_SCREEN_OFF_DURATION, durationMs) }

    fun getScreenOffDuration(): Long =
        prefs.getLong(KEY_SCREEN_OFF_DURATION, DEFAULT_SCREEN_OFF_DURATION)

    // ─────────────────────────────────────────────────────────────────────────
    // NFC
    // ─────────────────────────────────────────────────────────────────────────

    fun saveNfcFeatureEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_NFC_FEATURE_ENABLED, enabled) }

    fun isNfcFeatureEnabled(): Boolean =
        prefs.getBoolean(KEY_NFC_FEATURE_ENABLED, false)

    fun saveNfcAnimationId(id: String) =
        prefs.edit { putString(KEY_NFC_ANIMATION_ID, id) }

    fun getNfcAnimationId(): String =
        prefs.getString(KEY_NFC_ANIMATION_ID, "C1") ?: "C1"

    fun saveNfcAnimationDuration(durationMs: Long) =
        prefs.edit { putLong(KEY_NFC_ANIMATION_DURATION, durationMs) }

    fun getNfcAnimationDuration(): Long =
        prefs.getLong(KEY_NFC_ANIMATION_DURATION, DEFAULT_NFC_ANIMATION_DURATION)

    // ─────────────────────────────────────────────────────────────────────────
    // Charging animation
    // ─────────────────────────────────────────────────────────────────────────

    fun saveChargingAnimationEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_CHARGING_ANIMATION_ENABLED, enabled) }

    fun isChargingAnimationEnabled(): Boolean =
        prefs.getBoolean(KEY_CHARGING_ANIMATION_ENABLED, false)

    fun saveChargingAnimationDuration(durationMs: Long) =
        prefs.edit { putLong(KEY_CHARGING_ANIMATION_DURATION, durationMs) }

    fun getChargingAnimationDuration(): Long =
        prefs.getLong(KEY_CHARGING_ANIMATION_DURATION, DEFAULT_CHARGING_ANIMATION_DURATION)

    // ─────────────────────────────────────────────────────────────────────────
    // Music visualiser
    // ─────────────────────────────────────────────────────────────────────────

    fun saveMusicVizEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_MUSIC_VIZ_ENABLED, enabled) }

    fun isMusicVizEnabled(): Boolean =
        prefs.getBoolean(KEY_MUSIC_VIZ_ENABLED, false)

    /**
     * A built-in [MusicVisualizationMode] id or a `custom:<uuid>` script id.
     *
     * Shared key shape with the other features on purpose, so the picker, the
     * `isCustomId` branch and the stored value all work the same way here.
     */
    fun saveMusicVizAnimationId(id: String) =
        prefs.edit { putString(KEY_MUSIC_VIZ_ANIMATION_ID, id) }

    fun getMusicVizAnimationId(): String =
        prefs.getString(KEY_MUSIC_VIZ_ANIMATION_ID, null)
            ?: MusicVisualizationMode.DEFAULT.id

    fun saveMusicVizSensitivity(sensitivity: Float) =
        prefs.edit { putFloat(KEY_MUSIC_VIZ_SENSITIVITY, sensitivity) }

    fun getMusicVizSensitivity(): Float =
        prefs.getFloat(KEY_MUSIC_VIZ_SENSITIVITY, DEFAULT_MUSIC_VIZ_SENSITIVITY)

    fun saveMusicVizScreenOffOnly(onlyWhenScreenOff: Boolean) =
        prefs.edit { putBoolean(KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, onlyWhenScreenOff) }

    fun getMusicVizScreenOffOnly(): Boolean =
        prefs.getBoolean(KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, false)

    // ─────────────────────────────────────────────────────────────────────────
    // Quiet Hours
    // ─────────────────────────────────────────────────────────────────────────

    fun saveQuietHoursEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_QUIET_HOURS_ENABLED, enabled) }

    fun isQuietHoursEnabled(): Boolean =
        prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)

    fun saveQuietHoursStartHour(hour: Int) =
        prefs.edit { putInt(KEY_QUIET_HOURS_START_HOUR, hour) }

    fun getQuietHoursStartHour(): Int =
        prefs.getInt(KEY_QUIET_HOURS_START_HOUR, DEFAULT_QUIET_HOURS_START_HOUR)

    fun saveQuietHoursStartMinute(minute: Int) =
        prefs.edit { putInt(KEY_QUIET_HOURS_START_MINUTE, minute) }

    fun getQuietHoursStartMinute(): Int =
        prefs.getInt(KEY_QUIET_HOURS_START_MINUTE, DEFAULT_QUIET_HOURS_START_MINUTE)

    fun saveQuietHoursEndHour(hour: Int) =
        prefs.edit { putInt(KEY_QUIET_HOURS_END_HOUR, hour) }

    fun getQuietHoursEndHour(): Int =
        prefs.getInt(KEY_QUIET_HOURS_END_HOUR, DEFAULT_QUIET_HOURS_END_HOUR)

    fun saveQuietHoursEndMinute(minute: Int) =
        prefs.edit { putInt(KEY_QUIET_HOURS_END_MINUTE, minute) }

    fun getQuietHoursEndMinute(): Int =
        prefs.getInt(KEY_QUIET_HOURS_END_MINUTE, DEFAULT_QUIET_HOURS_END_MINUTE)

    fun isCurrentlyInQuietHours(): Boolean {
        if (!isQuietHoursEnabled()) return false

        val calendar = Calendar.getInstance()
        val currentTimeInMinutes =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startTimeInMinutes =
            getQuietHoursStartHour() * 60 + getQuietHoursStartMinute()
        val endTimeInMinutes =
            getQuietHoursEndHour() * 60 + getQuietHoursEndMinute()

        return if (startTimeInMinutes <= endTimeInMinutes) {
            currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
        } else {
            // Overnight
            currentTimeInMinutes !in (endTimeInMinutes + 1)..<startTimeInMinutes
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Language Settings
    // ─────────────────────────────────────────────────────────────────────────
    fun getAppLanguageCode(): String = prefs.getString(LANGUAGE, "system") ?: "system"
    fun saveAppLanguageCode(code: String) = prefs.edit { putString(LANGUAGE, code) }

    // ─────────────────────────────────────────────────────────────────────────
    // Debug
    // ─────────────────────────────────────────────────────────────────────────

    fun dumpAllSettings() {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val dump = """
            === All Settings ===
            Font Variant: ${getFontVariant()}
            Dark Theme: ${getTheme()}
            Theme Style: ${getThemeStyle()}
            Glyph Service: ${getGlyphServiceEnabled()}
            PowerPeek: ${isPowerPeekEnabled()}
            Shake Threshold: ${getPowerPeekThreshold()}
            Display Duration: ${getPowerPeekDuration()}
            Vibration Intensity: ${getVibrationIntensity()}
            Glow Gate enabled: ${isPulseLockEnabled()}
            Low-Battery Alert enabled: ${isLowBatteryEnabled()}
            Screen Off Anim enabled: ${isScreenOffFeatureEnabled()}
            NFC Feature enabled: ${isNfcFeatureEnabled()}
            NFC Animation ID: ${getNfcAnimationId()}
            NFC Animation Duration: ${getNfcAnimationDuration()}ms
            Quiet Hours enabled: ${isQuietHoursEnabled()}
            Quiet Hours start: ${getQuietHoursStartHour()}:${getQuietHoursStartMinute()}
            Quiet Hours end: ${getQuietHoursEndHour()}:${getQuietHoursEndMinute()}
            ===================
        """.trimIndent()
        Log.d(TAG, dump)
    }
}