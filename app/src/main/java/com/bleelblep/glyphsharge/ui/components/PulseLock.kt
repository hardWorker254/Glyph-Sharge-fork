package com.bleelblep.glyphsharge.ui.components

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.net.toUri
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.di.GlyphComponent
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class PulseLockConfig(
    val animationId: String,
    val durationMs: Long,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Confirmation Dialog  (uses FeatureConfirmationButtons)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PulseLockConfirmationDialog(
    onTestPulseLock: () -> Unit,
    onEnablePulseLock: () -> Unit,
    onDisablePulseLock: () -> Unit,
    onDismiss: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
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
                        text = stringResource(R.string.pulse_lock_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.pulse_lock_dialog_subtitle),
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
                            text = stringResource(R.string.pulse_lock_how_it_works_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.pulse_lock_how_it_works_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                FeatureConfirmationButtons(
                    primaryLabel = stringResource(R.string.pulse_lock_button_test),
                    onPrimary = onTestPulseLock,
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
        PulseLockEnableDialog(
            settingsRepository = settingsRepository,
            onConfirm = { _ ->
                onEnablePulseLock()
                showEnableDialog = false
                onDismiss()
            },
            onDisable = {
                onDisablePulseLock()
                showEnableDialog = false
                onDismiss()
            },
            onDismiss = { showEnableDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Enable / Settings Dialog  (uses FeatureSaveButtons + ThemedValueBadge)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PulseLockEnableDialog(
    onConfirm: (PulseLockConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val currentlyEnabled = remember { settingsRepository.isPulseLockEnabled() }
    var isSaving by remember { mutableStateOf(false) }

    // ── State ───────────────────────────────────────────────────────────
    var selectedAnim by remember {
        mutableStateOf(GlyphAnimations.getById(settingsRepository.getPulseLockAnimationId()))
    }
    var animationDuration by remember {
        mutableLongStateOf(settingsRepository.getPulseLockDuration())
    }


    // DI
    val glyphAnimationManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GlyphComponent::class.java
        ).glyphAnimationManager()
    }


    // Pre-resolve themed values
    val cardColor    = themeCardContainerColor()
    val accent       = themePrimaryActionColor()
    val secBtnColors = themeSecondaryButtonColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.pulse_lock_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.pulse_lock_configure_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Animation Picker ─────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pulse_lock_animation_title),
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
                                    selected = anim == selectedAnim,
                                    onClick = {
                                        selectedAnim = anim
                                        settingsRepository.savePulseLockAnimationId(anim.id)
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
                                        when (selectedAnim.id) {
                                            "SPIRAL"    -> glyphAnimationManager.runSpiralAnimation()
                                            "HEARTBEAT" -> glyphAnimationManager.runHeartbeatAnimation()
                                            "MATRIX"    -> glyphAnimationManager.runMatrixRainAnimation()
                                            "FIREWORKS" -> glyphAnimationManager.runFireworksAnimation()
                                            "DNA"       -> glyphAnimationManager.runDNAHelixAnimation()
                                            else        -> glyphAnimationManager.playPulseLockAnimation(selectedAnim.id)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PulseLock", "Error testing animation: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = secBtnColors,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pulse_lock_animation_test) + selectedAnim.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ── Animation Duration ───────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pulse_lock_duration_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.pulse_lock_duration_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ThemedValueBadge("${(animationDuration / 1000L)}" + stringResource(R.string.glyph_seconds))
                        }

                        Slider(
                            value = (animationDuration / 1000f).coerceIn(1f, 10f),
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                animationDuration = (it * 1000).toLong()
                                settingsRepository.savePulseLockDuration(animationDuration)
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.pulse_lock_duration_min),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.pulse_lock_duration_max),
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
                enableLabel = stringResource(R.string.pulse_lock_button_enable),
                onSave = {
                    isSaving = true
                    settingsRepository.savePulseLockAnimationId(selectedAnim.id)
                    settingsRepository.savePulseLockDuration(animationDuration)
                    onConfirm(
                        PulseLockConfig(
                            selectedAnim.id,
                            animationDuration
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