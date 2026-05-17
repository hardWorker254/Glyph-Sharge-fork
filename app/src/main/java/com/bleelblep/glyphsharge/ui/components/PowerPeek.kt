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
import kotlin.math.roundToInt
import com.bleelblep.glyphsharge.R

data class PowerPeekConfig(
    val isEnabled: Boolean = false,
    val shakeThreshold: Float = 12.0f,
    val displayDuration: Long = 3000L,
    val enableWhenScreenOff: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  Confirmation Dialog  (uses FeatureConfirmationButtons)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PowerPeekConfirmationDialog(
    onTestPowerPeek: () -> Unit,
    onEnablePowerPeek: () -> Unit,
    onDisablePowerPeek: () -> Unit,
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
                        text = stringResource(R.string.power_peek_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.power_peek_description),
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
                            text = stringResource(R.string.power_peek_how_it_works_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.power_peek_how_it_works_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                FeatureConfirmationButtons(
                    primaryLabel = stringResource(R.string.power_peek_button_test),
                    onPrimary = onTestPowerPeek,
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
        PowerPeekEnableDialog(
            onConfirm = { config ->
                onEnablePowerPeek()
                showEnableDialog = false
                onDismiss()
            },
            onDismiss = { showEnableDialog = false },
            onDisable = {
                onDisablePowerPeek()
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
fun PowerPeekEnableDialog(
    onConfirm: (PowerPeekConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository
) {
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current

    val currentlyEnabled = remember { settingsRepository.isPowerPeekEnabled() }
    var enableWhenScreenOff by remember { mutableStateOf(true) }
    var shakeThreshold by remember { mutableFloatStateOf(settingsRepository.getShakeThreshold()) }
    var displayDuration by remember { mutableFloatStateOf(settingsRepository.getDisplayDuration() / 1000f) }
    var isSaving by remember { mutableStateOf(false) }

    // Pre-resolve themed values
    val cardColor = themeCardContainerColor()
    val accent    = themePrimaryActionColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.power_peek_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.power_peek_configure_subtitle),
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
                // ── Shake Sensitivity ────────────────────────────────────
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
                                text = stringResource(R.string.power_peek_sensitivity_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            ThemedValueBadge(
                                settingsRepository.getShakeIntensityLevel(shakeThreshold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(stringResource(R.string.power_peek_sensitivity_soft),
                                stringResource(R.string.power_peek_sensitivity_easy),
                                stringResource(R.string.power_peek_sensitivity_medium),
                                stringResource(R.string.power_peek_sensitivity_hard),
                                stringResource(R.string.power_peek_sensitivity_hardest)).forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        var sliderStep by remember {
                            mutableFloatStateOf(
                                when (shakeThreshold) {
                                    SettingsRepository.SHAKE_EASY    -> 1f
                                    SettingsRepository.SHAKE_MEDIUM  -> 2f
                                    SettingsRepository.SHAKE_HARD    -> 3f
                                    SettingsRepository.SHAKE_HARDEST -> 4f
                                    else                             -> 0f
                                }
                            )
                        }

                        Slider(
                            value = sliderStep,
                            onValueChange = { raw ->
                                HapticUtils.triggerLightFeedback(haptic, context)
                                sliderStep = raw.coerceIn(0f, 4f)
                            },
                            onValueChangeFinished = {
                                val snapped = sliderStep.roundToInt().toFloat()
                                sliderStep = snapped
                                shakeThreshold = when (snapped.toInt()) {
                                    3    -> SettingsRepository.SHAKE_HARD
                                    4    -> SettingsRepository.SHAKE_HARDEST
                                    2    -> SettingsRepository.SHAKE_MEDIUM
                                    1    -> SettingsRepository.SHAKE_EASY
                                    else -> SettingsRepository.SHAKE_SOFT
                                }
                            },
                            valueRange = 0f..4f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent
                            )
                        )
                    }
                }

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
                                text = stringResource(R.string.power_peek_duration_title),
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
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.power_peek_duration_min),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.power_peek_duration_max),
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
                enableLabel = stringResource(R.string.power_peek_button_enable),
                onSave = {
                    isSaving = true
                    val newDuration = (displayDuration * 1000).toLong()
                    settingsRepository.saveShakeThreshold(shakeThreshold)
                    settingsRepository.saveDisplayDuration(newDuration)
                    onConfirm(
                        PowerPeekConfig(
                            isEnabled = true,
                            shakeThreshold = shakeThreshold,
                            displayDuration = newDuration,
                            enableWhenScreenOff = enableWhenScreenOff
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