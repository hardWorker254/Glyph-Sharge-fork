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
import com.bleelblep.glyphsharge.R

data class ChargingAnimationConfig(
    val isEnabled: Boolean = false,
    val displayDuration: Long = 3000L,
)

@Composable
fun ChargingAnimationConfirmationDialog(
    onTestAnimation: () -> Unit,
    onEnableAnimation: () -> Unit,
    onDisableAnimation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeatureConfirmationFlow(
        title = stringResource(id = R.string.charging_animation_title),
        subtitle = stringResource(id = R.string.charging_animation_description),
        howItWorksTitle = stringResource(id = R.string.charging_animation_how_it_works_title),
        howItWorksDescription = stringResource(id = R.string.charging_animation_how_it_works_description),
        testLabel = stringResource(id = R.string.charging_animation_button_test),
        onTest = onTestAnimation,
        onEnable = onEnableAnimation,
        onDisable = onDisableAnimation,
        onDismiss = onDismiss,
        modifier = modifier,
        settings = { onConfirm, onDisable, onDismissSettings ->
            ChargingAnimationEnableDialog(
                onConfirm = { onConfirm() },
                onDismiss = onDismissSettings,
                onDisable = onDisable,
            )
        },
    )
}

@Composable
fun ChargingAnimationEnableDialog(
    onConfirm: (ChargingAnimationConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The store comes from the composition; the card has none to pass on.
    val settingsRepository = LocalSettingsRepository.current
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    val currentlyEnabled = remember { settingsRepository.isChargingAnimationEnabled() }
    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getChargingAnimationDuration() / 1000f).coerceIn(2f, 10f))
    }
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
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(id = R.string.charging_animation_configure_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            // The scroll container every other enable dialog has. See the note
            // in PowerPeek: without it the content is clipped at a non-default
            // font scale while the buttons stay reachable, which produces a
            // dialog the user cannot configure.
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
                                text = stringResource(id = R.string.charging_animation_duration_title),
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
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
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
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(id = R.string.charging_animation_button_enable),
                onSave = {
                    val newDuration = (durationSeconds * 1000).toLong()
                    settingsRepository.saveChargingAnimationDuration(newDuration)
                    onConfirm(ChargingAnimationConfig(isEnabled = true, displayDuration = newDuration))
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