package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.di.GlyphPrefs
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The music visualiser's own settings.
 *
 * It is the last of the feature settings, and the last of the features: the
 * other seven were rules before they were features, and the user asked for the
 * app to ship their own rules and nothing else. What the removed features
 * owned — a switch, an animation, a duration, a threshold — is now carried by
 * the rule itself, so there is nothing left here for them.
 *
 * This class is deliberately unaware of services. It stores the user's intent;
 * `FeatureSpec` is what pairs it with a service.
 */
@Singleton
class FeatureSettings @Inject constructor(
    @param:GlyphPrefs private val prefs: SharedPreferences,
) {

    // Music visualiser

    fun saveMusicVizEnabled(enabled: Boolean) = prefs.putSetting(KEY_MUSIC_VIZ_ENABLED, enabled)

    fun isMusicVizEnabled(): Boolean = prefs.getSetting(KEY_MUSIC_VIZ_ENABLED, default = false)

    /**
     * A built-in [MusicVisualizationMode] id or a `custom:<uuid>` script id.
     *
     * Shared key shape with the rules on purpose, so the picker, the
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
        const val KEY_MUSIC_VIZ_ENABLED = "music_viz_enabled"
        const val KEY_MUSIC_VIZ_ANIMATION_ID = "music_viz_animation_id"
        const val KEY_MUSIC_VIZ_SENSITIVITY = "music_viz_sensitivity"
        const val KEY_MUSIC_VIZ_SCREEN_OFF_ONLY = "music_viz_screen_off_only"

        const val DEFAULT_MUSIC_VIZ_SENSITIVITY = 1.0f
    }
}