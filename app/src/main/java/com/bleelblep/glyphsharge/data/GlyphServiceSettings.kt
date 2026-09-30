package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.di.GlyphPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slice 3: the master Glyph switch, and how hard the phone buzzes.
 *
 * These two live together because they are the app-wide gate and the app-wide
 * feedback channel: every feature service asks the switch, and the single
 * haptic helper in the app asks the intensity. Neither has any relation to the
 * per-feature tuning in [FeatureSettings].
 */
@Singleton
class GlyphServiceSettings @Inject constructor(
    @param:GlyphPrefs private val prefs: SharedPreferences
) {

    fun saveGlyphServiceEnabled(enabled: Boolean) =
        prefs.putSetting(KEY_GLYPH_SERVICE_ENABLED, enabled)

    fun getGlyphServiceEnabled(): Boolean = prefs.getSetting(KEY_GLYPH_SERVICE_ENABLED, false)

    fun saveVibrationIntensity(intensity: Float) =
        prefs.putSetting(KEY_VIBRATION_INTENSITY, intensity)

    fun getVibrationIntensity(): Float =
        prefs.getSetting(KEY_VIBRATION_INTENSITY, DEFAULT_VIBRATION_INTENSITY)

    /**
     * Maps a stored Power Peek threshold onto its label.
     *
     * Lives here because the thresholds are the vibration scale's named steps.
     * Unknown values fall through to "medium" so a threshold saved by a build
     * with different steps still shows a sane label instead of crashing the
     * settings screen.
     */
    fun getShakeIntensityLevel(threshold: Float): Int = when (threshold) {
        SHAKE_SOFT -> R.string.power_peek_sensitivity_soft
        SHAKE_EASY -> R.string.power_peek_sensitivity_easy
        SHAKE_MEDIUM -> R.string.power_peek_sensitivity_medium
        SHAKE_HARD -> R.string.power_peek_sensitivity_hard
        SHAKE_HARDEST -> R.string.power_peek_sensitivity_hardest
        else -> R.string.power_peek_sensitivity_medium
    }

    companion object {
        /**
         * Named steps of the shake scale.
         *
         * Public because the Power Peek slider snaps to these values; the
         * threshold itself is stored by [FeatureSettings] under its legacy key.
         */
        const val SHAKE_SOFT = 12.0f
        const val SHAKE_EASY = 15.0f
        const val SHAKE_MEDIUM = 18.0f
        const val SHAKE_HARD = 22.0f
        const val SHAKE_HARDEST = 28.0f

        internal const val DEFAULT_VIBRATION_INTENSITY = 0.66f
        internal const val KEY_GLYPH_SERVICE_ENABLED = "glyph_service_enabled"
        internal const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
    }
}
