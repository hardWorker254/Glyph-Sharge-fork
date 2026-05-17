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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.di.GlyphComponent
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Confirmation Dialog  (uses FeatureConfirmationButtons)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScreenOffConfirmationDialog(
    onTest: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    var showConfigDialog by remember { mutableStateOf(false) }

    if (!showConfigDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.screen_off_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(id = R.string.screen_off_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = themeCardContainerColor()
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.screen_off_how_it_works_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.screen_off_how_it_works_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                FeatureConfirmationButtons(
                    primaryLabel = stringResource(id = R.string.screen_off_button_test),
                    onPrimary = onTest,
                    onSettings = { showConfigDialog = true },
                    onCancel = onDismiss
                )
            },
            dismissButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = modifier
        )
    }

    if (showConfigDialog) {
        ScreenOffConfigDialog(
            onDismiss = { showConfigDialog = false },
            onEnable = {
                onEnable()
                showConfigDialog = false
                onDismiss()
            },
            onDisable = {
                onDisable()
                showConfigDialog = false
                onDismiss()
            },
            settingsRepository = settingsRepository,
            modifier = modifier
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Config / Settings Dialog  (uses FeatureSaveButtons + ThemedValueBadge)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenOffConfigDialog(
    onDismiss: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── State ───────────────────────────────────────────────────────────
    var durationMs by remember {
        mutableStateOf(settingsRepository.getScreenOffDuration().toFloat())
    }
    var selectedAnim by remember {
        mutableStateOf(GlyphAnimations.getById(settingsRepository.getScreenOffAnimationId()))
    }
    val currentlyEnabled = remember { settingsRepository.isScreenOffFeatureEnabled() }
    var isSaving by remember { mutableStateOf(false) }

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
                    text = stringResource(id = R.string.screen_off_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.screen_off_configure_subtitle),
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
                            stringResource(id = R.string.screen_off_animation_title),
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
                                        HapticUtils.triggerLightFeedback(haptic, context)
                                        selectedAnim = anim
                                        settingsRepository.saveScreenOffAnimationId(anim.id)
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
                                        glyphAnimationManager.playScreenOffAnimation(selectedAnim.id)
                                    } catch (e: Exception) {
                                        Log.e("ScreenOffConfig", "Error testing animation: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = secBtnColors,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.screen_off_animation_test) + selectedAnim.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ── Duration Slider ──────────────────────────────────────
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
                                text = stringResource(id = R.string.screen_off_duration_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            ThemedValueBadge("${(durationMs / 1000f).toInt()}" + stringResource(id = R.string.glyph_seconds))
                        }

                        Slider(
                            value = durationMs,
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                durationMs = it
                                settingsRepository.saveScreenOffDuration(it.toLong())
                            },
                            valueRange = 1000f..10000f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(id = R.string.screen_off_duration_min),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(id = R.string.screen_off_duration_max),
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
                enableLabel = stringResource(id = R.string.screen_off_button_enable),
                onSave = {
                    isSaving = true
                    settingsRepository.saveScreenOffAnimationId(selectedAnim.id)
                    settingsRepository.saveScreenOffDuration(durationMs.toLong())
                    settingsRepository.saveScreenOffFeatureEnabled(true)
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