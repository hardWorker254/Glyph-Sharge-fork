package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.bleelblep.glyphsharge.ui.components.dialogs.FeatureConfirmationFlow
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlin.math.roundToInt
import com.bleelblep.glyphsharge.R
// Still needed for the named shake steps; the store itself comes from the
// composition.
import com.bleelblep.glyphsharge.data.SettingsRepository

data class PowerPeekConfig(
    val isEnabled: Boolean = false,
    val shakeThreshold: Float = 12.0f,
    val displayDuration: Long = 3000L,
    val enableWhenScreenOff: Boolean = false,
)

@Composable
fun PowerPeekConfirmationDialog(
    modifier: Modifier = Modifier,
    onTestPowerPeek: () -> Unit,
    onEnablePowerPeek: () -> Unit,
    onDisablePowerPeek: () -> Unit,
    onDismiss: () -> Unit,
) {
    FeatureConfirmationFlow(
        title = stringResource(R.string.power_peek_title),
        subtitle = stringResource(R.string.power_peek_description),
        howItWorksTitle = stringResource(R.string.power_peek_how_it_works_title),
        howItWorksDescription = stringResource(R.string.power_peek_how_it_works_description),
        testLabel = stringResource(R.string.power_peek_button_test),
        onTest = onTestPowerPeek,
        onEnable = onEnablePowerPeek,
        onDisable = onDisablePowerPeek,
        onDismiss = onDismiss,
        modifier = modifier,
        settings = { onConfirm, onDisable, onDismissSettings ->
            PowerPeekEnableDialog(
                onConfirm = { onConfirm() },
                onDisable = onDisable,
                onDismiss = onDismissSettings,
            )
        },
    )
}

@Composable
fun PowerPeekEnableDialog(
    modifier: Modifier = Modifier,
    onConfirm: (PowerPeekConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
) {
    // The store comes from the composition; the card has none to pass on.
    val settingsRepository = LocalSettingsRepository.current
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    val currentlyEnabled = remember { settingsRepository.isPowerPeekEnabled() }
var enableWhenScreenOff by remember { mutableStateOf(value = true) }

    var shakeThreshold by remember { mutableFloatStateOf(settingsRepository.getPowerPeekThreshold()) }
    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getPowerPeekDuration() / 1000f).coerceIn(2f, 10f))
    }

    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.power_peek_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.power_peek_configure_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            // The scroll container every other enable dialog has. This one and
            // ChargingAnimation's were the two that lacked it, and Power Peek
            // has the tallest content of the eight. At a non-default font
            // scale — which this app itself offers sliders for — the second
            // card and its slider end up outside the dialog with no gesture
            // that reaches them, while the Save button (outside `text`)
            // survives. The result is a dialog that cannot be configured
            // rather than one that merely looks clipped.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.power_peek_sensitivity_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            ThemedValueBadge(stringResource(settingsRepository.getShakeIntensityLevel(shakeThreshold)))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf(
                                stringResource(R.string.power_peek_sensitivity_soft),
                                stringResource(R.string.power_peek_sensitivity_easy),
                                stringResource(R.string.power_peek_sensitivity_medium),
                                stringResource(R.string.power_peek_sensitivity_hard),
                                stringResource(R.string.power_peek_sensitivity_hardest),
                            ).forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
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
                                },
                            )
                        }

                        Slider(
                            value = sliderStep,
                            // No haptic per pixel: a drag reports a value for every pixel of travel,
                            // so firing on each one buzzes continuously under the thumb and buries the
                            // one that should land at the end. Here that lands next to the step snap,
                            // because the snapped step is what the drag actually committed to.
                            onValueChange = { raw ->
                                sliderStep = raw.coerceIn(0f, 4f)
                            },
                            onValueChangeFinished = {
                                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
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
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.power_peek_duration_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ThemedValueBadge(durationSeconds.toInt().toString() + stringResource(id = R.string.glyph_seconds))
                        }

                        Slider(
                            value = durationSeconds,
                            // No haptic per pixel: a drag reports a value for every pixel of travel,
                            // so firing on each one buzzes continuously under the thumb and buries the
                            // one that should land at the end. One buzz, at the end.
                            onValueChange = { durationSeconds = it },
                            onValueChangeFinished = {
                                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                            },
                            valueRange = 2f..10f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent),
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.power_peek_duration_min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.power_peek_duration_max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(R.string.power_peek_button_enable),
                onSave = {
                    val newDuration = (durationSeconds * 1000).toLong()
                    settingsRepository.savePowerPeekThreshold(shakeThreshold)
                    settingsRepository.savePowerPeekDuration(newDuration)
                    onConfirm(
                        PowerPeekConfig(
                            isEnabled = true,
                            shakeThreshold = shakeThreshold,
                            displayDuration = newDuration,
                            enableWhenScreenOff = enableWhenScreenOff,
                        ),
                    )
                },
                onDisable = {
                    onDisable()
                    onDismiss()
                },
                onCancel = onDismiss,
            )
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    )
}