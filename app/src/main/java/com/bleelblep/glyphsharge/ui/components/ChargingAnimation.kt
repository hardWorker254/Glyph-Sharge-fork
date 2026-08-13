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
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun ChargingAnimationConfirmationDialog(
    onTestAnimation: () -> Unit,
    onEnableAnimation: () -> Unit,
    onDisableAnimation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (!showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { /* non-dismissible */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
                    colors = CardDefaults.cardColors(containerColor = themeCardContainerColor()),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    onSettings = { showSettingsDialog = true },
                    onCancel = onDismiss
                )
            },
            dismissButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = modifier
        )
    }

    if (showSettingsDialog) {
        ChargingAnimationEnableDialog(
            onConfirm = { config ->
                onEnableAnimation()
                showSettingsDialog = false
                onDismiss()
            },
            onDismiss = { showSettingsDialog = false },
            onDisable = {
                onDisableAnimation()
                showSettingsDialog = false
                onDismiss()
            },
            settingsRepository = settingsRepository
        )
    }
}

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
    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getChargingAnimationDuration() / 1000f).coerceIn(2f, 10f))
    }
    var isSaving by remember { mutableStateOf(false) }

    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.charging_animation_duration_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ThemedValueBadge("${durationSeconds.toInt()}" + stringResource(id = R.string.glyph_seconds))
                        }

                        Slider(
                            value = durationSeconds,
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                durationSeconds = it
                            },
                            valueRange = 2f..10f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(id = R.string.charging_animation_duration_min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(id = R.string.charging_animation_duration_max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val newDuration = (durationSeconds * 1000).toLong()
                    settingsRepository.saveChargingAnimationDuration(newDuration)
                    onConfirm(ChargingAnimationConfig(isEnabled = true, displayDuration = newDuration))
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