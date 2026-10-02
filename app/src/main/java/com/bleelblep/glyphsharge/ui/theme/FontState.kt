package com.bleelblep.glyphsharge.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class FontVariant {
    HEADLINE,    // NType Headline 
    NDOT,       // NDot 57 Caps
    SYSTEM      // Default system font
}

enum class FontCategory {
    DISPLAY,    // Display text (large headlines)
    TITLE,      // Titles and subtitles
    BODY,       // Body text
    LABEL       // Labels and small text
}

data class FontSizeSettings(
    val displayScale: Float = 1.0f,    // Scale for display text
    val titleScale: Float = 1.0f,      // Scale for title text
    val bodyScale: Float = 1.0f,       // Scale for body text
    val labelScale: Float = 1.0f,      // Scale for label text
) {
    companion object {
        fun getDefaultForFont(fontVariant: FontVariant): FontSizeSettings {
            return when (fontVariant) {
                FontVariant.SYSTEM -> FontSizeSettings(
                    displayScale = 0.8f,
                    titleScale = 0.8f,
                    bodyScale = 0.8f,
                    labelScale = 0.8f,
                )
                FontVariant.HEADLINE, FontVariant.NDOT -> FontSizeSettings(
                    displayScale = 1.0f,
                    titleScale = 1.0f,
                    bodyScale = 1.0f,
                    labelScale = 1.0f,
                )
            }
        }
    }
}

@Singleton
class FontState @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    var currentVariant by mutableStateOf(settingsRepository.getFontVariant())
        private set

    var useCustomFonts by mutableStateOf(settingsRepository.getUseCustomFonts())
        private set

    var fontSizeSettings by mutableStateOf(settingsRepository.getFontSizeSettingsForFont(currentVariant))
        private set

    // Custom Nothing fonts
    val headlineFont = FontFamily(
        Font(R.font.ntype_82_headline, FontWeight.Normal),
        Font(R.font.ntype_82_headline, FontWeight.Medium),
        Font(R.font.ntype_82_headline, FontWeight.Bold),
    )

    val ndotFont = FontFamily(
        Font(R.font.ndot55caps, FontWeight.Normal),
        Font(R.font.ndot55caps, FontWeight.Medium),
        Font(R.font.ndot55caps, FontWeight.Bold),
    )

    val regularFont = FontFamily(
        Font(R.font.ntype_82_regular, FontWeight.Normal),
        Font(R.font.ntype_82_regular, FontWeight.Medium),
        Font(R.font.ntype_82_regular, FontWeight.Bold),
    )

    // System font fallback
    val systemFont = FontFamily.Default

    fun setFontVariant(variant: FontVariant) {
        val oldVariant = currentVariant
        currentVariant = variant
        settingsRepository.saveFontVariant(variant)
        // Update font size settings for the new font variant
        val newFontSizeSettings = settingsRepository.getFontSizeSettingsForFont(variant)
        fontSizeSettings = newFontSizeSettings
        
        android.util.Log.d("FontState", "Font changed from ${oldVariant.name} to ${variant.name}, font sizes: $newFontSizeSettings")
    }

    fun toggleCustomFonts() {
        useCustomFonts = !useCustomFonts
        settingsRepository.saveUseCustomFonts(useCustomFonts)
    }

    /**
     * Moves one size slider, and optionally writes it out.
     *
     * Split from the write on purpose. A drag reports a value for every pixel
     * of travel, and this used to persist each one — a `SharedPreferences`
     * write, and a full recomposition of every screen in the app, tens of times
     * for a single gesture. The live update is kept, because watching the text
     * grow under the thumb is the point of a live preview; only the write is
     * deferred, to the drag's end.
     *
     * @param persist `false` while the thumb is moving, `true` once it stops.
     */
    fun updateFontSize(category: FontCategory, scale: Float, persist: Boolean = true) {
        fontSizeSettings = when (category) {
            FontCategory.DISPLAY -> fontSizeSettings.copy(displayScale = scale)
            FontCategory.TITLE -> fontSizeSettings.copy(titleScale = scale)
            FontCategory.BODY -> fontSizeSettings.copy(bodyScale = scale)
            FontCategory.LABEL -> fontSizeSettings.copy(labelScale = scale)
        }
        if (persist) settingsRepository.saveFontSizeSettings(fontSizeSettings)
    }

    fun resetFontSizes() {
        fontSizeSettings = FontSizeSettings.getDefaultForFont(currentVariant)
        settingsRepository.clearFontSizeCustomization()
    }

    fun getTitleFont(): FontFamily {
        if (!useCustomFonts) return systemFont
        
        return when (currentVariant) {
            FontVariant.HEADLINE -> headlineFont
            FontVariant.NDOT -> ndotFont
            FontVariant.SYSTEM -> systemFont
        }
    }

    fun getBodyFont(): FontFamily {
        if (!useCustomFonts) return systemFont
        
        return when (currentVariant) {
            FontVariant.HEADLINE, FontVariant.NDOT -> regularFont
            FontVariant.SYSTEM -> systemFont
        }
    }

    fun getFontDescription(): String {
        if (!useCustomFonts) return "System Default"
        
        return when (currentVariant) {
            FontVariant.HEADLINE -> "NType Headline"
            FontVariant.NDOT -> "NDot 57 Caps"
            FontVariant.SYSTEM -> "System Default"
        }
    }

    // Legacy support
    @Deprecated("Use setFontVariant instead")
    fun toggleFontVariant() {
        currentVariant = if (currentVariant == FontVariant.HEADLINE) {
            FontVariant.NDOT
        } else {
            FontVariant.HEADLINE
        }
        settingsRepository.saveFontVariant(currentVariant)
    }
} 