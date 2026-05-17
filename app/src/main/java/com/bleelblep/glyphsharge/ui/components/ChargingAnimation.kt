package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import com.bleelblep.glyphsharge.R

data class ChargingAnimationConfig(
    val isEnabled: Boolean = false,
    val displayDuration: Long = 3000L
)

// ─────────────────────────────────────────────────────────────────────────────
//  Confirmation Dialog  (uses FeatureConfirmationButtons)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChargingAnimationConfirmationDialog(
    onTestAnimation: () -> Unit,
    onEnableAnimation: () -> Unit,
    onDisableAnimation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository
) {
    var showEnableDialog by remember { mutableStateOf(false) }

    if (!showEnableDialog) {
        AlertDialog(
            onDismissRequest = { /* non-dismissible */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.charging_animation_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(id = R.string.charging_animation_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeCardContainerColor()
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.charging_animation_how_it_works_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.charging_animation_how_it_works_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                FeatureConfirmationButtons(
                    primaryLabel = stringResource(id = R.string.charging_animation_button_test),
                    onPrimary = onTestAnimation,
                    onSettings = { showEnableDialog = true },
                    onCancel = onDismiss
                )
            },
            dismissButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = modifier
        )
    }

    if (showEnableDialog) {
        ChargingAnimationEnableDialog(
            onConfirm = { config ->
                onEnableAnimation()
                showEnableDialog = false
                onDismiss()
            },
            onDismiss = { showEnableDialog = false },
            onDisable = {
                onDisableAnimation()
                showEnableDialog = false
                onDismiss()
            },
            settingsRepository = settingsRepository
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Enable / Settings Dialog  (uses FeatureSaveButtons + ThemedValueBadge)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChargingAnimationEnableDialog(
    onConfirm: (ChargingAnimationConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val currentlyEnabled = remember { settingsRepository.isChargingAnimationEnabled() }

    var displayDuration by remember { mutableFloatStateOf(settingsRepository.getChargingAnimationDuration() / 1000f) }
    var isSaving by remember { mutableStateOf(false) }

    // Pre-resolve themed values
    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.charging_animation_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.charging_animation_configure_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Display Duration ─────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.charging_animation_duration_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            ThemedValueBadge("${displayDuration.toInt()}" + stringResource(id = R.string.glyph_seconds))
                        }

                        Slider(
                            value = displayDuration,
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                displayDuration = it
                            },
                            valueRange = 2f..10f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent
                            )
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(id = R.string.charging_animation_duration_min),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(id = R.string.charging_animation_duration_max),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isSaving = isSaving,
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(id = R.string.charging_animation_button_enable),
                onSave = {
                    isSaving = true
                    val newDuration = (displayDuration * 1000).toLong()

                    settingsRepository.saveChargingAnimationDuration(newDuration)

                    onConfirm(
                        ChargingAnimationConfig(
                            isEnabled = true,
                            displayDuration = newDuration
                        )
                    )
                },
                onDisable = {
                    onDisable()
                    onDismiss()
                },
                onCancel = onDismiss
            )
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    )
}