package com.bleelblep.glyphsharge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlin.math.roundToInt


/**
 * Toggle card with status text for font settings
 */
@Composable
fun ToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    statusText: @Composable (Boolean) -> String = { "" }
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusText(checked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NothingViolate,
                    checkedTrackColor = NothingViolate.copy(alpha = 0.5f)
                )
            )
        }
    }
}



/**
 * Simple Font Family Selector with clean button-based selection
 */
@Composable
fun SimpleFontSelector(
    currentVariant: FontVariant,
    onVariantSelected: (FontVariant) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(id = R.string.font_settings_section_family),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(id = R.string.font_settings_family_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Font options as simple buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleFontButton(
                    variant = FontVariant.HEADLINE,
                    title = stringResource(id = R.string.font_settings_family_ntype_title),
                    description = stringResource(id = R.string.font_settings_family_ntype_desc),
                    preview = "Typography",
                    isSelected = currentVariant == FontVariant.HEADLINE,
                    onClick = {
                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                        onVariantSelected(FontVariant.HEADLINE)
                    }
                )

                SimpleFontButton(
                    variant = FontVariant.NDOT,
                    title = stringResource(id = R.string.font_settings_family_ndot_title),
                    description = stringResource(id = R.string.font_settings_family_ndot_desc),
                    preview = "TYPOGRAPHY",
                    isSelected = currentVariant == FontVariant.NDOT,
                    onClick = {
                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                        onVariantSelected(FontVariant.NDOT)
                    }
                )

                SimpleFontButton(
                    variant = FontVariant.SYSTEM,
                    title = stringResource(id = R.string.font_settings_family_system_title),
                    description = stringResource(id = R.string.font_settings_family_system_desc),
                    preview = "Typography",
                    isSelected = currentVariant == FontVariant.SYSTEM,
                    onClick = {
                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                        onVariantSelected(FontVariant.SYSTEM)
                    }
                )
            }
        }
    }
}

/**
 * Simple font selection button with improved contrast
 */
@Composable
private fun SimpleFontButton(
    variant: FontVariant,
    title: String,
    description: String,
    preview: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            when (variant) {
                FontVariant.HEADLINE -> NothingViolate
                FontVariant.NDOT -> NothingViolate
                FontVariant.SYSTEM -> NothingRed
            }
        } else {
            Color.Transparent
        },
        animationSpec = tween(300),
        label = "fontButtonBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            when (variant) {
                FontVariant.HEADLINE -> NothingViolate
                FontVariant.NDOT -> NothingViolate
                FontVariant.SYSTEM -> NothingRed
            }
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300),
        label = "fontButtonBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Font info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Font preview
            Text(
                text = preview,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = when (variant) {
                        FontVariant.HEADLINE -> FontFamily(Font(R.font.ntype_82_headline))
                        FontVariant.NDOT -> FontFamily(Font(R.font.ndot55caps))
                        FontVariant.SYSTEM -> FontFamily.Default
                    }
                ),
                color = if (isSelected) {
                    when (variant) {
                        FontVariant.HEADLINE -> NothingViolate
                        FontVariant.NDOT -> NothingViolate
                        FontVariant.SYSTEM -> NothingRed
                    }
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(id = R.string.font_settings_family_selected),
                    tint = when (variant) {
                        FontVariant.HEADLINE -> NothingViolate
                        FontVariant.NDOT -> NothingViolate
                        FontVariant.SYSTEM -> NothingRed
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Font Size Controls with sliders for each category
 */
@Composable
fun FontSizeControls(
    fontSizeSettings: FontSizeSettings,
    onSizeChanged: (FontCategory, Float) -> Unit,
    onSizeChangeFinished: (FontCategory, Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.font_settings_card_sizes_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                TextButton(onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onReset()
                }) {
                    Text(stringResource(id = R.string.font_settings_button_reset_all))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Size controls for each category
            FontSizeSlider(
                label = stringResource(id = R.string.font_settings_size_display_label),
                description = stringResource(id = R.string.font_settings_size_display_desc),
                value = fontSizeSettings.displayScale,
                onValueChange = { onSizeChanged(FontCategory.DISPLAY, it) },
        onValueChangeFinished = { onSizeChangeFinished(FontCategory.DISPLAY, it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            FontSizeSlider(
                label = stringResource(id = R.string.font_settings_size_title_label),
                description = stringResource(id = R.string.font_settings_size_title_desc),
                value = fontSizeSettings.titleScale,
                onValueChange = { onSizeChanged(FontCategory.TITLE, it) },
        onValueChangeFinished = { onSizeChangeFinished(FontCategory.TITLE, it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            FontSizeSlider(
                label = stringResource(id = R.string.font_settings_size_body_label),
                description = stringResource(id = R.string.font_settings_size_body_desc),
                value = fontSizeSettings.bodyScale,
                onValueChange = { onSizeChanged(FontCategory.BODY, it) },
        onValueChangeFinished = { onSizeChangeFinished(FontCategory.BODY, it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            FontSizeSlider(
                label = stringResource(id = R.string.font_settings_size_label_label),
                description = stringResource(id = R.string.font_settings_size_label_desc),
                value = fontSizeSettings.labelScale,
                onValueChange = { onSizeChanged(FontCategory.LABEL, it) },
        onValueChangeFinished = { onSizeChangeFinished(FontCategory.LABEL, it) }
            )
        }
    }
}

/**
 * Individual font size slider
 */
@Composable
private fun FontSizeSlider(
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // The value the drag last reported, so the end-of-gesture callback has
    // something to hand on: Compose's `onValueChangeFinished` takes no
    // argument. Keyed on `value` so a change from elsewhere resets it.
    var draggedValue by remember(value) { mutableFloatStateOf(value) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = NothingViolate
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = value,
            // No haptic here. A drag reports a value for every pixel of travel,
            // so firing on each one buzzes continuously under the thumb and
            // buries the one that should land at the end of the gesture. The
            // caller's callback fired a second vibration per pixel too, which
            // is what made this slider feel like two buzzes for every step.
            onValueChange = { raw ->
                draggedValue = raw
                onValueChange(raw)
            },
            // Compose hands this one no argument, so the last value the drag
            // reported is captured above rather than passed in.
            onValueChangeFinished = { onValueChangeFinished(draggedValue) },
            valueRange = 0.5f..2.0f,
            steps = 29, // 0.5 to 2.0 in 0.05 increments
            colors = SliderDefaults.colors(
                thumbColor = NothingViolate,
                activeTrackColor = NothingViolate,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

/**
 * Font Preview Card showing current settings
 */
@Composable
fun FontPreview(
    fontState: FontState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(id = R.string.font_settings_section_preview),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Display text preview
            Text(
                text = stringResource(id = R.string.font_settings_preview_display),
                style = MaterialTheme.typography.displayLarge,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Title preview
            Text(
                text = stringResource(id = R.string.font_settings_preview_headline),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body text preview
            Text(
                text = stringResource(id = R.string.font_settings_preview_body),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Label preview
            Text(
                text = stringResource(id = R.string.font_settings_preview_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Current settings summary
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = NothingViolate.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.font_settings_preview_current),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = NothingViolate
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = buildString {
                            append(
                                stringResource(
                                    id = R.string.font_settings_preview_font,
                                    fontState.getFontDescription()
                                )
                            )
                            if (fontState.useCustomFonts && fontState.fontSizeSettings != FontSizeSettings()) {
                                append(stringResource(id = R.string.font_settings_preview_custom_sizing))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
} 