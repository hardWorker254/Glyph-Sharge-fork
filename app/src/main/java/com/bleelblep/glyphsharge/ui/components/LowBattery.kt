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
import androidx.compose.ui.window.DialogProperties
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.components.dialogs.FeatureConfirmationFlow
import com.bleelblep.glyphsharge.di.GlyphComponent
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

data class LowBatteryAlertConfig(
    val isEnabled: Boolean = false,
    val threshold: Int = 20,
    val animationId: String = "PULSE",
    val durationMs: Long = 10000L
)

@Composable
fun LowBatteryAlertConfirmationDialog(
    modifier: Modifier = Modifier,
    onTestAlert: () -> Unit,
    onEnableAlert: (LowBatteryAlertConfig) -> Unit,
    onDisableAlert: () -> Unit,
    onDismiss: () -> Unit,
    settingsRepository: SettingsRepository
) {
    // The configuration is threaded through the flow rather than dropped, so
    // the card receives the threshold, animation and duration the user picked.
    var pendingConfig by remember { mutableStateOf<LowBatteryAlertConfig?>(null) }

    FeatureConfirmationFlow(
        title = stringResource(R.string.low_battery_alert_title),
        subtitle = stringResource(R.string.low_battery_alert_description),
        howItWorksTitle = stringResource(R.string.low_battery_alert_how_it_works_title),
        howItWorksDescription = stringResource(R.string.low_battery_alert_how_it_works_description),
        testLabel = stringResource(R.string.low_battery_alert_button_test),
        onTest = onTestAlert,
        onEnable = { pendingConfig?.let(onEnableAlert) },
        onDisable = onDisableAlert,
        onDismiss = onDismiss,
        modifier = modifier,
        settings = { onConfirm, onDisable, onDismissSettings ->
            LowBatteryAlertEnableDialog(
                onConfirm = { config ->
                    pendingConfig = config
                    onConfirm()
                },
                onDisable = onDisable,
                onDismiss = onDismissSettings,
                settingsRepository = settingsRepository
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LowBatteryAlertEnableDialog(
    modifier: Modifier = Modifier,
    onConfirm: (LowBatteryAlertConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val currentlyEnabled = remember { settingsRepository.isLowBatteryEnabled() }
    var isSaving by remember { mutableStateOf(false) }

    var threshold by remember { mutableFloatStateOf(settingsRepository.getLowBatteryThreshold().toFloat()) }
    var selectedAnimation by remember {
        mutableStateOf(GlyphAnimations.getById(settingsRepository.getLowBatteryAnimationId()))
    }
    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getLowBatteryDuration() / 1000f).coerceIn(1f, 10f))
    }

    val cardColor = themeCardContainerColor()
    val secBtnColors = themeSecondaryButtonColors()
    val accent = themePrimaryActionColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.low_battery_alert_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.low_battery_alert_configure_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(id = R.string.low_battery_alert_threshold_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.low_battery_alert_threshold_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ThemedValueBadge("${threshold.toInt()}%")
                        }

                        Slider(
                            value = threshold,
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                threshold = it.coerceIn(5f, 50f)
                            },
                            valueRange = 5f..50f,
                            steps = 44,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(id = R.string.low_battery_alert_threshold_min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(id = R.string.low_battery_alert_threshold_max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    val glyphAnimationManager = remember {
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            GlyphComponent::class.java
                        ).glyphAnimationManager()
                    }
                    val scope = rememberCoroutineScope()

                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(id = R.string.low_battery_alert_animation_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            GlyphAnimations.list.forEach { anim ->
                                FilterChip(
                                    selected = anim == selectedAnimation,
                                    onClick = {
                                        HapticUtils.triggerLightFeedback(haptic, context)
                                        selectedAnimation = anim
                                    },
                                    label = { Text(anim.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = {
                                HapticUtils.triggerMediumFeedback(haptic, context)
                                scope.launch {
                                    try {
                                        when (selectedAnimation.id) {
                                            "SPIRAL"    -> glyphAnimationManager.runSpiralAnimation()
                                            "HEARTBEAT" -> glyphAnimationManager.runHeartbeatAnimation()
                                            "MATRIX"    -> glyphAnimationManager.runMatrixRainAnimation()
                                            "FIREWORKS" -> glyphAnimationManager.runFireworksAnimation()
                                            "DNA"       -> glyphAnimationManager.runDNAHelixAnimation()
                                            else        -> glyphAnimationManager.playLowBatteryAnimation()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("LowBatteryAlert", "Error testing animation: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = secBtnColors,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.low_battery_alert_animation_test) + selectedAnimation.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

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
                                text = stringResource(id = R.string.low_battery_alert_duration_title),
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
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(id = R.string.low_battery_alert_duration_min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(id = R.string.low_battery_alert_duration_max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isSaving = isSaving,
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(id = R.string.low_battery_alert_button_enable),
                onSave = {
                    isSaving = true
                    onConfirm(
                        LowBatteryAlertConfig(
                            isEnabled = true,
                            threshold = threshold.toInt(),
                            animationId = selectedAnimation.id,
                            durationMs = (durationSeconds * 1000).toLong()
                        )
                    )
                },
                onDisable = onDisable,
                onCancel = onDismiss
            )
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    )
}