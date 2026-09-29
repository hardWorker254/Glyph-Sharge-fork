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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.dialogs.FeatureConfirmationFlow
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import kotlinx.coroutines.launch

private suspend fun testAnimation(
    animId: String,
    manager: com.bleelblep.glyphsharge.glyph.GlyphAnimationManager,
    fallback: suspend (String) -> Unit
) {
    try {
        when {
            // A custom script goes through the same path a feature uses, so the
            // test button exercises the real dispatch rather than a shortcut.
            animId.startsWith(ScriptAnimation.ID_PREFIX) ->
                manager.playCustomAnimation(animId)

            else -> when (animId) {
                "SPIRAL"    -> manager.runSpiralAnimation()
                "HEARTBEAT" -> manager.runHeartbeatAnimation()
                "MATRIX"    -> manager.runMatrixRainAnimation()
                "FIREWORKS" -> manager.runFireworksAnimation()
                "DNA"       -> manager.runDNAHelixAnimation()
                else        -> fallback(animId)
            }
        }
    } catch (e: Exception) {
        Log.e("NfcGlyphCard", "Error testing animation: ${e.message}")
    }
}

@Composable
fun NfcGlyphConfirmationDialog(
    onTest: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    FeatureConfirmationFlow(
        title = stringResource(R.string.nfc_glyph_title),
        subtitle = stringResource(R.string.nfc_glyph_description),
        howItWorksTitle = stringResource(R.string.nfc_glyph_how_it_works_title),
        howItWorksDescription = stringResource(R.string.nfc_glyph_how_it_works_description),
        testLabel = stringResource(R.string.nfc_glyph_button_test),
        onTest = onTest,
        onEnable = onEnable,
        onDisable = onDisable,
        onDismiss = onDismiss,
        modifier = modifier,
        settings = { onConfirm, onDisable, onDismissSettings ->
            NfcGlyphEnableDialog(
                onDismiss = onDismissSettings,
                onEnable = onConfirm,
                onDisable = onDisable
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NfcGlyphEnableDialog(
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

    var selectedAnimation by remember {
        mutableStateOf(
            GlyphAnimations.getById(
                settingsRepository.getNfcAnimationId(),
                animationOptions
            )
        )
    }
    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getNfcAnimationDuration() / 1000f).coerceIn(1f, 10f))
    }
    val currentlyEnabled = remember { settingsRepository.isNfcFeatureEnabled() }
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
                    text = stringResource(R.string.nfc_glyph_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.nfc_glyph_configure_subtitle),
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
                            stringResource(R.string.nfc_glyph_animation_title),
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
                                        settingsRepository.saveNfcAnimationId(anim.id)
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
                                    testAnimation(
                        selectedAnimation.id,
                        glyphAnimationManager
                    ) {
                                        glyphAnimationManager.playNfcAnimation()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = secBtnColors,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.nfc_glyph_animation_test) + selectedAnimation.displayName,
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
                                    text = stringResource(R.string.nfc_glyph_duration_title),
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
                                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                    durationSeconds = it
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = accent)
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.nfc_glyph_duration_min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.nfc_glyph_duration_max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.nfc_glyph_note_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.nfc_glyph_note_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isSaving = isSaving,
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(R.string.nfc_glyph_button_enable),
                onSave = {
                    isSaving = true
                    settingsRepository.saveNfcAnimationId(selectedAnimation.id)
                    settingsRepository.saveNfcAnimationDuration((durationSeconds * 1000).toLong())
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