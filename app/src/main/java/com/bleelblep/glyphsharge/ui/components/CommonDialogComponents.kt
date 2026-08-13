package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

// ─────────────────────────────────────────────────────────────────────────────
//  Themed Value Badge
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reusable badge component for displaying values in settings dialogs.
 * Automatically adapts to theme style (CLASSIC/EXPRESSIVE).
 */
@Composable
fun ThemedValueBadge(
    value: String,
    modifier: Modifier = Modifier
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
        contentColor = contentColor
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Feature Confirmation Buttons
// ─────────────────────────────────────────────────────────────────────────────

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
    modifier: Modifier = Modifier
) {
    val themeState = LocalThemeState.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedButton(
            onClick = {
                HapticUtils.triggerMediumFeedback(haptic, context)
                onPrimary()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = themePrimaryActionColor(),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onSettings()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeSettingsButtonColor(),
                    contentColor = themeSettingsButtonContentColor()
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.action_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onCancel()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.action_cancel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Feature Save Buttons
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Standardized 3-button layout for feature settings/save dialogs.
 * Used in: PowerPeek, PulseLock, ScreenOff, NfcGlyph, LowBattery config dialogs.
 */
@Composable
fun FeatureSaveButtons(
    isSaving: Boolean,
    isCurrentlyEnabled: Boolean,
    enableLabel: String,
    onSave: () -> Unit,
    onDisable: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedButton(
            onClick = {
                HapticUtils.triggerMediumFeedback(haptic, context)
                onSave()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = themePrimaryActionColor(),
                contentColor = NothingWhite
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = NothingWhite,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = if (isCurrentlyEnabled) stringResource(id = R.string.action_save)
                        else enableLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    HapticUtils.triggerMediumFeedback(haptic, context)
                    onDisable()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingRed,
                    contentColor = NothingWhite
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.action_disable),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onCancel()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.action_cancel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
