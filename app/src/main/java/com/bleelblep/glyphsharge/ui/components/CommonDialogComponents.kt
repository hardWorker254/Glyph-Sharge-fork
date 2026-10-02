package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils

// Themed value badge

/**
 * Reusable badge component for displaying values in settings dialogs.
 * Automatically adapts to theme style (CLASSIC/EXPRESSIVE).
 */
@Composable
fun ThemedValueBadge(
    value: String,
    modifier: Modifier = Modifier,
) {
    val themeState = LocalThemeState.current
    
    val backgroundColor = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        modifier = modifier
            .wrapContentSize(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// Feature confirmation buttons

/**
 * Standardized 3-button layout for feature confirmation dialogs.
 * Used in: PowerPeek, PulseLock, ScreenOff, NfcGlyph, LowBattery confirmation dialogs.
 */
@Composable
fun FeatureConfirmationButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSettings: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedButton(
            onClick = {
                HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                onPrimary()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = themePrimaryActionColor(),
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onSettings()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeSettingsButtonColor(),
                    contentColor = themeSettingsButtonContentColor(),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.action_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onCancel()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.action_cancel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// Feature save buttons

/**
 * Standardized 3-button layout for feature settings/save dialogs.
 * Used in: PowerPeek, PulseLock, ScreenOff, NfcGlyph, LowBattery config dialogs.
 *
 * There is no "saving" state. Every one of these dialogs saves a handful of
 * synchronous `SharedPreferences` values and closes, so a spinner had nothing
 * to describe — and because the flag was set on click and never cleared, a
 * dialog whose `onConfirm` did not close it was left showing "working" for
 * good. Adding one back means adding something that genuinely takes time and
 * clearing it on both the success and the failure path.
 */
@Composable
fun FeatureSaveButtons(
    isCurrentlyEnabled: Boolean,
    enableLabel: String,
    onSave: () -> Unit,
    onDisable: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedButton(
            onClick = {
                HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                onSave()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = themePrimaryActionColor(),
                contentColor = NothingWhite,
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = if (isCurrentlyEnabled) stringResource(id = R.string.action_save)
                    else enableLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                    onDisable()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingRed,
                    contentColor = NothingWhite,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.action_disable),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            OutlinedButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onCancel()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.action_cancel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
