package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.di.GlyphPrefs
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seven optional Glyph features and their per-feature tuning.
 *
 * All seven are kept together rather than split seven ways because they are the
 * same shape: a switch, an animation id, a duration, and a few thresholds. One
 * class means adding a feature is adding methods here, not adding a class, and
 * the keys stay adjacent enough to be checked against each other.
 *
 * This class is deliberately unaware of services. It stores the user's intent;
 * `FeatureSpec` is what pairs it with a service.
 */
@Singleton
class FeatureSettings @Inject constructor(
    @param:GlyphPrefs private val prefs: SharedPreferences,
) {

    // Power Peek

    fun savePowerPeekEnabled(enabled: Boolean) = prefs.putSetting(KEY_POWER_PEEK_ENABLED, enabled)

    fun isPowerPeekEnabled(): Boolean = prefs.getSetting(KEY_POWER_PEEK_ENABLED, default = false)

    fun savePowerPeekThreshold(threshold: Float) =
        prefs.putSetting(KEY_POWER_PEEK_THRESHOLD, threshold)

    fun getPowerPeekThreshold(): Float =
        prefs.getSetting(KEY_POWER_PEEK_THRESHOLD, GlyphServiceSettings.SHAKE_MEDIUM)

    fun savePowerPeekDuration(duration: Long) = prefs.putSetting(KEY_POWER_PEEK_DURATION, duration)

    fun getPowerPeekDuration(): Long =
        prefs.getSetting(KEY_POWER_PEEK_DURATION, DEFAULT_POWER_PEEK_DURATION)

    // Glow Gate (Pulse Lock)

    fun savePulseLockEnabled(enabled: Boolean) = prefs.putSetting(KEY_PULSE_LOCK_ENABLED, enabled)

    fun isPulseLockEnabled(): Boolean = prefs.getSetting(KEY_PULSE_LOCK_ENABLED, default = false)

    fun savePulseLockAnimationId(id: String) = prefs.putSetting(KEY_PULSE_LOCK_ANIMATION_ID, id)

    fun getPulseLockAnimationId(): String =
        prefs.getSetting(KEY_PULSE_LOCK_ANIMATION_ID, DEFAULT_ANIMATION_ID)

    fun savePulseLockDuration(durationMs: Long) = prefs.putSetting(KEY_PULSE_LOCK_DURATION, durationMs)

    fun getPulseLockDuration(): Long =
        prefs.getSetting(KEY_PULSE_LOCK_DURATION, DEFAULT_PULSE_LOCK_DURATION)

    // Low battery

    fun saveLowBatteryEnabled(enabled: Boolean) = prefs.putSetting(KEY_LOW_BATTERY_ENABLED, enabled)

    fun isLowBatteryEnabled(): Boolean = prefs.getSetting(KEY_LOW_BATTERY_ENABLED, default = false)

    fun saveLowBatteryThreshold(pct: Int) = prefs.putSetting(KEY_LOW_BATTERY_THRESHOLD, pct)

    fun getLowBatteryThreshold(): Int =
        prefs.getSetting(KEY_LOW_BATTERY_THRESHOLD, DEFAULT_LOW_BATTERY_THRESHOLD)

    fun saveLowBatteryAnimationId(id: String) = prefs.putSetting(KEY_LOW_BATTERY_ANIMATION_ID, id)

    fun getLowBatteryAnimationId(): String =
        prefs.getSetting(KEY_LOW_BATTERY_ANIMATION_ID, DEFAULT_ANIMATION_ID)

    fun saveLowBatteryDuration(durationMs: Long) = prefs.putSetting(KEY_LOW_BATTERY_DURATION, durationMs)

    fun getLowBatteryDuration(): Long =
        prefs.getSetting(KEY_LOW_BATTERY_DURATION, DEFAULT_LOW_BATTERY_DURATION)

    // Screen off

    fun saveScreenOffFeatureEnabled(enabled: Boolean) =
        prefs.putSetting(KEY_SCREEN_OFF_ENABLED, enabled)

    fun isScreenOffFeatureEnabled(): Boolean = prefs.getSetting(KEY_SCREEN_OFF_ENABLED, default = false)

    fun saveScreenOffAnimationId(id: String) = prefs.putSetting(KEY_SCREEN_OFF_ANIMATION_ID, id)

    fun getScreenOffAnimationId(): String =
        prefs.getSetting(KEY_SCREEN_OFF_ANIMATION_ID, DEFAULT_ANIMATION_ID)

    fun saveScreenOffDuration(durationMs: Long) = prefs.putSetting(KEY_SCREEN_OFF_DURATION, durationMs)

    fun getScreenOffDuration(): Long =
        prefs.getSetting(KEY_SCREEN_OFF_DURATION, DEFAULT_SCREEN_OFF_DURATION)

    // NFC

    fun saveNfcFeatureEnabled(enabled: Boolean) =
        prefs.putSetting(KEY_NFC_FEATURE_ENABLED, enabled)

    fun isNfcFeatureEnabled(): Boolean = prefs.getSetting(KEY_NFC_FEATURE_ENABLED, default = false)

    fun saveNfcAnimationId(id: String) = prefs.putSetting(KEY_NFC_ANIMATION_ID, id)

    fun getNfcAnimationId(): String =
        prefs.getSetting(KEY_NFC_ANIMATION_ID, DEFAULT_ANIMATION_ID)

    fun saveNfcAnimationDuration(durationMs: Long) =
        prefs.putSetting(KEY_NFC_ANIMATION_DURATION, durationMs)

    fun getNfcAnimationDuration(): Long =
        prefs.getSetting(KEY_NFC_ANIMATION_DURATION, DEFAULT_NFC_ANIMATION_DURATION)

    // Charging animation

    fun saveChargingAnimationEnabled(enabled: Boolean) =
        prefs.putSetting(KEY_CHARGING_ANIMATION_ENABLED, enabled)

    fun isChargingAnimationEnabled(): Boolean =
        prefs.getSetting(KEY_CHARGING_ANIMATION_ENABLED, default = false)

    fun saveChargingAnimationDuration(durationMs: Long) =
        prefs.putSetting(KEY_CHARGING_ANIMATION_DURATION, durationMs)

    fun getChargingAnimationDuration(): Long =
        prefs.getSetting(KEY_CHARGING_ANIMATION_DURATION, DEFAULT_CHARGING_ANIMATION_DURATION)

    // VPN connected

    fun saveVpnConnectedEnabled(enabled: Boolean) =
        prefs.putSetting(KEY_VPN_CONNECTED_ENABLED, enabled)

    fun isVpnConnectedEnabled(): Boolean = prefs.getSetting(KEY_VPN_CONNECTED_ENABLED, default = false)

    fun saveVpnConnectedAnimationId(id: String) =
        prefs.putSetting(KEY_VPN_CONNECTED_ANIMATION_ID, id)

    fun getVpnConnectedAnimationId(): String =
        prefs.getSetting(KEY_VPN_CONNECTED_ANIMATION_ID, DEFAULT_ANIMATION_ID)

    fun saveVpnConnectedDuration(durationMs: Long) =
        prefs.putSetting(KEY_VPN_CONNECTED_DURATION, durationMs)

    fun getVpnConnectedDuration(): Long =
        prefs.getSetting(KEY_VPN_CONNECTED_DURATION, DEFAULT_VPN_CONNECTED_DURATION)

    // Music visualiser

    fun saveMusicVizEnabled(enabled: Boolean) = prefs.putSetting(KEY_MUSIC_VIZ_ENABLED, enabled)

    fun isMusicVizEnabled(): Boolean = prefs.getSetting(KEY_MUSIC_VIZ_ENABLED, default = false)

    /**
     * A built-in [MusicVisualizationMode] id or a `custom:<uuid>` script id.
     *
     * Shared key shape with the other features on purpose, so the picker, the
     * `isCustomId` branch and the stored value all work the same way here.
     */
    fun saveMusicVizAnimationId(id: String) = prefs.putSetting(KEY_MUSIC_VIZ_ANIMATION_ID, id)

    fun getMusicVizAnimationId(): String =
        prefs.getSetting<String?>(KEY_MUSIC_VIZ_ANIMATION_ID, null)
            ?: MusicVisualizationMode.DEFAULT.id

    fun saveMusicVizSensitivity(sensitivity: Float) =
        prefs.putSetting(KEY_MUSIC_VIZ_SENSITIVITY, sensitivity)

    fun getMusicVizSensitivity(): Float =
        prefs.getSetting(KEY_MUSIC_VIZ_SENSITIVITY, DEFAULT_MUSIC_VIZ_SENSITIVITY)

    fun saveMusicVizScreenOffOnly(onlyWhenScreenOff: Boolean) =
        prefs.putSetting(KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, onlyWhenScreenOff)

    fun getMusicVizScreenOffOnly(): Boolean = prefs.getSetting(KEY_MUSIC_VIZ_SCREEN_OFF_ONLY, default = false)

    internal companion object {
        // Power Peek. Threshold/duration keep their legacy storage names on
        // purpose: renaming the stored strings would silently reset the values
        // of existing users.
        const val KEY_POWER_PEEK_ENABLED = "power_peek_enabled"
        const val KEY_POWER_PEEK_THRESHOLD = "shake_threshold"
        const val KEY_POWER_PEEK_DURATION = "display_duration"

        const val KEY_PULSE_LOCK_ENABLED = "pulse_lock_enabled"
        const val KEY_PULSE_LOCK_ANIMATION_ID = "pulse_lock_animation_id"
        const val KEY_PULSE_LOCK_DURATION = "pulse_lock_duration"

        const val KEY_LOW_BATTERY_ENABLED = "low_battery_enabled"
        const val KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        const val KEY_LOW_BATTERY_ANIMATION_ID = "low_battery_animation_id"
        const val KEY_LOW_BATTERY_DURATION = "low_battery_duration"

        const val KEY_SCREEN_OFF_ENABLED = "screen_off_enabled"
        const val KEY_SCREEN_OFF_ANIMATION_ID = "screen_off_animation_id"
        const val KEY_SCREEN_OFF_DURATION = "screen_off_duration"

        const val KEY_NFC_FEATURE_ENABLED = "nfc_feature_enabled"
        const val KEY_NFC_ANIMATION_ID = "nfc_animation_id"
        const val KEY_NFC_ANIMATION_DURATION = "nfc_animation_duration"

        const val KEY_CHARGING_ANIMATION_ENABLED = "charging_animation_enabled"
        const val KEY_CHARGING_ANIMATION_DURATION = "charging_animation_duration"

        const val KEY_VPN_CONNECTED_ENABLED = "vpn_connected_enabled"
        const val KEY_VPN_CONNECTED_ANIMATION_ID = "vpn_connected_animation_id"
        const val KEY_VPN_CONNECTED_DURATION = "vpn_connected_duration"

        const val KEY_MUSIC_VIZ_ENABLED = "music_viz_enabled"
        const val KEY_MUSIC_VIZ_ANIMATION_ID = "music_viz_animation_id"
        const val KEY_MUSIC_VIZ_SENSITIVITY = "music_viz_sensitivity"
        const val KEY_MUSIC_VIZ_SCREEN_OFF_ONLY = "music_viz_screen_off_only"

        const val DEFAULT_POWER_PEEK_DURATION = 3000L
        const val DEFAULT_PULSE_LOCK_DURATION = 5000L
        const val DEFAULT_LOW_BATTERY_DURATION = 10000L
        const val DEFAULT_SCREEN_OFF_DURATION = 3000L
        const val DEFAULT_NFC_ANIMATION_DURATION = 3000L
        const val DEFAULT_CHARGING_ANIMATION_DURATION = 3000L
        const val DEFAULT_VPN_CONNECTED_DURATION = 3000L
        const val DEFAULT_LOW_BATTERY_THRESHOLD = 20
        const val DEFAULT_MUSIC_VIZ_SENSITIVITY = 1.0f

        /** Shared fallback for the six features that default to animation `C1`. */
        const val DEFAULT_ANIMATION_ID = "C1"
    }
}
