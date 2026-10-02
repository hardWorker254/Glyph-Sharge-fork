package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.bleelblep.glyphsharge.di.GlyphPrefs
import com.bleelblep.glyphsharge.ui.theme.FontSizeSettings
import com.bleelblep.glyphsharge.ui.theme.FontVariant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slice 2: typeface choice and the per-role size scales.
 *
 * Kept apart from the other settings because font size is the one setting with
 * a second, derived mode: [getFontSizeSettingsForFont] has to know whether the
 * user ever customised the scales at all, which is a fact only this slice owns.
 */
@Singleton
class FontSettings @Inject constructor(
    @param:GlyphPrefs private val prefs: SharedPreferences,
) {

    fun saveFontVariant(variant: FontVariant) = prefs.putSetting(KEY_FONT_VARIANT, variant.name)

    /** Same defensive `valueOf` as the theme style: an unknown name must not crash startup. */
    fun getFontVariant(): FontVariant {
        val variantName = prefs.getSetting(KEY_FONT_VARIANT, FontVariant.HEADLINE.name)
        return try {
            FontVariant.valueOf(variantName)
        } catch (_: IllegalArgumentException) {
            FontVariant.HEADLINE
        }
    }

    fun saveUseCustomFonts(useCustom: Boolean) =
        prefs.putSetting(KEY_USE_CUSTOM_FONTS, useCustom)

    fun getUseCustomFonts(): Boolean = prefs.getSetting(KEY_USE_CUSTOM_FONTS, default = true)

    fun saveFontSizeSettings(settings: FontSizeSettings) {
        prefs.edit {
            putFloat(KEY_FONT_SIZE_DISPLAY_SCALE, settings.displayScale)
            putFloat(KEY_FONT_SIZE_TITLE_SCALE, settings.titleScale)
            putFloat(KEY_FONT_SIZE_BODY_SCALE, settings.bodyScale)
            putFloat(KEY_FONT_SIZE_LABEL_SCALE, settings.labelScale)
            putBoolean(KEY_FONT_SIZE_CUSTOMIZED, true)
        }
    }

    fun getFontSizeSettings(): FontSizeSettings = FontSizeSettings(
        prefs.getSetting(KEY_FONT_SIZE_DISPLAY_SCALE, 1.0f),
        prefs.getSetting(KEY_FONT_SIZE_TITLE_SCALE, 1.0f),
        prefs.getSetting(KEY_FONT_SIZE_BODY_SCALE, 1.0f),
        prefs.getSetting(KEY_FONT_SIZE_LABEL_SCALE, 1.0f),
    )

    /**
     * Resets to "not customised" rather than writing 1.0f over the scales.
     *
     * The scales are removed, not overwritten, because the per-font defaults in
     * [FontSizeSettings.getDefaultForFont] differ per variant. Storing 1.0f
     * would read back as a user choice and pin every font to the same size.
     */
    fun clearFontSizeCustomization() {
        prefs.edit {
            remove(KEY_FONT_SIZE_DISPLAY_SCALE)
            remove(KEY_FONT_SIZE_TITLE_SCALE)
            remove(KEY_FONT_SIZE_BODY_SCALE)
            remove(KEY_FONT_SIZE_LABEL_SCALE)
            putBoolean(KEY_FONT_SIZE_CUSTOMIZED, false)
        }
    }

    fun getFontSizeSettingsForFont(fontVariant: FontVariant): FontSizeSettings =
        if (prefs.getSetting(KEY_FONT_SIZE_CUSTOMIZED, default = false)) {
            getFontSizeSettings()
        } else {
            FontSizeSettings.getDefaultForFont(fontVariant)
        }

    internal companion object {
        const val KEY_FONT_VARIANT = "font_variant"
        const val KEY_USE_CUSTOM_FONTS = "use_custom_fonts"
        const val KEY_FONT_SIZE_DISPLAY_SCALE = "font_size_display_scale"
        const val KEY_FONT_SIZE_TITLE_SCALE = "font_size_title_scale"
        const val KEY_FONT_SIZE_BODY_SCALE = "font_size_body_scale"
        const val KEY_FONT_SIZE_LABEL_SCALE = "font_size_label_scale"
        const val KEY_FONT_SIZE_CUSTOMIZED = "font_size_customized"
    }
}
