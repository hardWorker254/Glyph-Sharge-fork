package com.bleelblep.glyphsharge.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.cards.WideFeatureCardWithToggle
import com.bleelblep.glyphsharge.ui.components.dialogs.FeatureConfirmationFlow
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlinx.coroutines.launch

/**
 * The VPN Connected card and its configuration dialog.
 *
 * State arrives as `isEnabled` and leaves through [onEnabledChange], which both
 * persists the preference and starts or stops the backing service. The
 * configuration dialog reads the store from [LocalSettingsRepository], so the
 * card is callable on its own.
 */
@Composable
fun VpnConnectedCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.vpn_connected_title),
    description: String = stringResource(id = R.string.vpn_connected_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.vpn_connected_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = onEnabledChange,
        onCardClick = {
            if (isServiceActive) showDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize
    )

    if (showDialog && isServiceActive) {
        VpnConnectedConfirmationDialog(
            onTest = { onTest(); showDialog = false },
            onEnable = { onEnabledChange(true); showDialog = false },
            onDisable = { onEnabledChange(false); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun VpnConnectedConfirmationDialog(
    onTest: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    FeatureConfirmationFlow(
        title = stringResource(id = R.string.vpn_connected_title),
        subtitle = stringResource(id = R.string.vpn_connected_dialog_subtitle),
        howItWorksTitle = stringResource(id = R.string.vpn_connected_how_it_works_title),
        howItWorksDescription = stringResource(id = R.string.vpn_connected_how_it_works_description),
        testLabel = stringResource(id = R.string.vpn_connected_button_test),
        onTest = onTest,
        onEnable = onEnable,
        onDisable = onDisable,
        onDismiss = onDismiss,
        modifier = modifier,
        dismissible = true,
        settings = { onConfirm, onDisable, onDismissSettings ->
            VpnConnectedEnableDialog(
                onDismiss = onDismissSettings,
                onEnable = onConfirm,
                onDisable = onDisable,
                modifier = modifier
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VpnConnectedEnableDialog(
    onDismiss: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The store comes from the composition; the card has none to pass on.
    val settingsRepository = LocalSettingsRepository.current
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Built-ins plus whatever the studio currently holds.
    val animationOptions = rememberAnimationOptions()

    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getVpnConnectedDuration() / 1000f).coerceIn(1f, 10f))
    }
    var selectedAnimation by remember {
        mutableStateOf(
            GlyphAnimations.getById(
                settingsRepository.getVpnConnectedAnimationId(),
                animationOptions
            )
        )
    }
    val currentlyEnabled = remember { settingsRepository.isVpnConnectedEnabled() }
    var isSaving by remember { mutableStateOf(false) }

    val glyphAnimationManager = rememberGlyphAnimationManager()

    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()
    val secBtnColors = themeSecondaryButtonColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.vpn_connected_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.vpn_connected_configure_subtitle),
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
                            stringResource(id = R.string.vpn_connected_animation_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            animationOptions.forEach { anim ->
                                FilterChip(
                                    selected = anim == selectedAnimation,
                                    onClick = {
                                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                        selectedAnimation = anim
                                        settingsRepository.saveVpnConnectedAnimationId(anim.id)
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
                                HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                                scope.launch {
                                    try {
                                        when {
                                        selectedAnimation.isCustom ->
                                            glyphAnimationManager.playCustomAnimation(
                                                selectedAnimation.id
                                            )

                                        else -> when (selectedAnimation.id) {
                                            "SPIRAL"    -> glyphAnimationManager.runSpiralAnimation()
                                            "HEARTBEAT" -> glyphAnimationManager.runHeartbeatAnimation()
                                            "MATRIX"    -> glyphAnimationManager.runMatrixRainAnimation()
                                            "FIREWORKS" -> glyphAnimationManager.runFireworksAnimation()
                                            "DNA"       -> glyphAnimationManager.runDNAHelixAnimation()
                                            else        -> glyphAnimationManager.playVpnConnectedAnimation()
                                        }
                                    }
                                    } catch (e: Exception) {
                                        Log.e("VpnConnectedConfig", "Error testing animation: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = secBtnColors,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.vpn_connected_animation_test) + selectedAnimation.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
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
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.vpn_connected_duration_title),
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
                                    HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                                    durationSeconds = it
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = accent)
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(id = R.string.vpn_connected_duration_min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(id = R.string.vpn_connected_duration_max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isSaving = isSaving,
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(id = R.string.vpn_connected_button_enable),
                onSave = {
                    isSaving = true
                    settingsRepository.saveVpnConnectedAnimationId(selectedAnimation.id)
                    settingsRepository.saveVpnConnectedDuration((durationSeconds * 1000).toLong())
                    onEnable()
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
