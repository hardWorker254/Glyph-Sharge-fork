package com.bleelblep.glyphsharge.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.FontPreview
import com.bleelblep.glyphsharge.ui.components.FontSizeControls
import com.bleelblep.glyphsharge.ui.components.SimpleFontSelector
import com.bleelblep.glyphsharge.ui.components.ToggleCard
import com.bleelblep.glyphsharge.ui.components.layout.HomeSectionHeader
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.utils.HapticUtils

/**
 * Font Settings Screen with comprehensive customization options
 * Updated to follow consistent UI patterns throughout the app
 */
@Composable
fun FontSettingsScreen(
    fontState: FontState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    SettingsScaffold(
        title = stringResource(id = R.string.settings_card_typography),
        modifier = modifier,
        onBackClick = onNavigateBack
    ) {
        // Custom Fonts Toggle Card
        item {
            ToggleCard(
                title = stringResource(id = R.string.font_settings_toggle_custom_fonts),
                checked = fontState.useCustomFonts,
                onCheckedChange = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    fontState.toggleCustomFonts()
                },
                statusText = { isChecked ->
                    if (isChecked)
                        stringResource(id = R.string.font_settings_toggle_status_on)
                    else
                        stringResource(id = R.string.font_settings_toggle_status_off)
                }
            )
        }

        // Font Family Selection (only show if custom fonts enabled)
        if (fontState.useCustomFonts) {
            item {
                HomeSectionHeader(
                    title = stringResource(id = R.string.font_settings_section_family),
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                SimpleFontSelector(
                    currentVariant = fontState.currentVariant,
                    onVariantSelected = { variant ->
                        HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                        fontState.setFontVariant(variant)
                    }
                )
            }
        }

        // Font Size Section Header
        item {
            HomeSectionHeader(
                title = stringResource(id = R.string.font_settings_section_size),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        // Font Size Controls
        item {
            FontSizeControls(
                fontSizeSettings = fontState.fontSizeSettings,
                onSizeChanged = { category, scale ->
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    fontState.updateFontSize(category, scale)
                },
                onReset = {
                    HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                    fontState.resetFontSizes()
                }
            )
        }

        // Preview Section Header
        item {
            HomeSectionHeader(
                title = stringResource(id = R.string.font_settings_section_preview),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        // Preview Section
        item {
            FontPreview(fontState = fontState)
        }
    }
}
