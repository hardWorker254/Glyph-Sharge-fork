package com.bleelblep.glyphsharge.ui.components

import android.util.Log
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
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.dialogs.FeatureConfirmationFlow
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlinx.coroutines.launch

data class PulseLockConfig(
    val animationId: String,
    val durationMs: Long,
)

@Composable
fun PulseLockConfirmationDialog(
    onTestPulseLock: () -> Unit,
    onEnablePulseLock: () -> Unit,
    onDisablePulseLock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeatureConfirmationFlow(
        title = stringResource(R.string.pulse_lock_title),
        subtitle = stringResource(R.string.pulse_lock_dialog_subtitle),
        howItWorksTitle = stringResource(R.string.pulse_lock_how_it_works_title),
        howItWorksDescription = stringResource(R.string.pulse_lock_how_it_works_description),
        testLabel = stringResource(R.string.pulse_lock_button_test),
        onTest = onTestPulseLock,
        onEnable = onEnablePulseLock,
        onDisable = onDisablePulseLock,
        onDismiss = onDismiss,
        modifier = modifier,
        settings = { onConfirm, onDisable, onDismissSettings ->
            PulseLockEnableDialog(
                onConfirm = { onConfirm() },
                onDisable = onDisable,
                onDismiss = onDismissSettings,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PulseLockEnableDialog(
    onConfirm: (PulseLockConfig) -> Unit,
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
    val scope = rememberCoroutineScope()

    val currentlyEnabled = remember { settingsRepository.isPulseLockEnabled() }
// Built-ins plus whatever the studio currently holds, so a script saved
    // while this dialog is open shows up in the chip row.
    val animationOptions = rememberAnimationOptions()

    var selectedAnimation by remember {
        mutableStateOf(
            GlyphAnimations.getById(
                settingsRepository.getPulseLockAnimationId(),
                animationOptions,
            ),
        )
    }
    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getPulseLockDuration() / 1000f).coerceIn(1f, 10f))
    }

    val glyphAnimationManager = rememberGlyphAnimationManager()

    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()
    val secBtnColors = themeSecondaryButtonColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.pulse_lock_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.pulse_lock_configure_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.pulse_lock_animation_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            animationOptions.forEach { anim ->
                                FilterChip(
                                    selected = anim == selectedAnimation,
                                    onClick = {
                                        selectedAnimation = anim
                                        settingsRepository.savePulseLockAnimationId(anim.id)
                                    },
                                    label = { Text(anim.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }

                        Button(
                            onClick = {
                                HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                                scope.launch {
                                    try {
                                        when {
                                        selectedAnimation.isCustom ->
                                            glyphAnimationManager.playCustomAnimation(
                                                selectedAnimation.id,
                                            )

                                        else -> when (selectedAnimation.id) {
                                            "SPIRAL"    -> glyphAnimationManager.runSpiralAnimation()
                                            "HEARTBEAT" -> glyphAnimationManager.runHeartbeatAnimation()
                                            "MATRIX"    -> glyphAnimationManager.runMatrixRainAnimation()
                                            "FIREWORKS" -> glyphAnimationManager.runFireworksAnimation()
                                            "DNA"       -> glyphAnimationManager.runDNAHelixAnimation()
                                            else        -> glyphAnimationManager.playPulseLockAnimation()
                                        }
                                    }
                                    } catch (e: Exception) {
                                        Log.e("PulseLock", "Error testing animation: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = secBtnColors,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.pulse_lock_animation_test) + selectedAnimation.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Not shown for a user's script: a script lasts exactly as long as
                // its own code, so a duration here would be a control that
                // changes nothing.
                if (!selectedAnimation.isCustom) {
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
                                    text = stringResource(R.string.pulse_lock_duration_title),
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
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = accent),
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.pulse_lock_duration_min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.pulse_lock_duration_max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(R.string.pulse_lock_button_enable),
                onSave = {
                    settingsRepository.savePulseLockAnimationId(selectedAnimation.id)
                    settingsRepository.savePulseLockDuration((durationSeconds * 1000).toLong())
                    onConfirm(PulseLockConfig(selectedAnimation.id, (durationSeconds * 1000).toLong()))
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