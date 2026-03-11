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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.di.GlyphComponent
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class LowBatteryAlertConfig(
    val isEnabled: Boolean = false,
    val threshold: Int = 20,
    val animationId: String = "PULSE",
    val audioEnabled: Boolean = false,
    val audioUri: String? = null,
    val audioOffset: Long = 0L
)

// ─────────────────────────────────────────────────────────────────────────────
//  Private theme helpers – eliminate repeated `when (themeStyle)` blocks
// ─────────────────────────────────────────────────────────────────────────────

/** Card container tinted with a feature-specific accent (e.g. [NothingRed]). */
@Composable
private fun accentCardColor(accent: Color): Color {
    val ts = LocalThemeState.current
    return when (ts.themeStyle) {
        AppThemeStyle.AMOLED -> accent
        AppThemeStyle.CLASSIC ->
            if (ts.isDarkTheme) MaterialTheme.colorScheme.surfaceContainer
            else NothingWhite
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
}

/** Colors for secondary / test action buttons. */
@Composable
private fun secondaryButtonColors(): ButtonColors {
    val ts = LocalThemeState.current
    return ButtonDefaults.buttonColors(
        containerColor = when (ts.themeStyle) {
            AppThemeStyle.AMOLED  -> NothingGray
            AppThemeStyle.CLASSIC -> NothingWhite
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when (ts.themeStyle) {
            AppThemeStyle.AMOLED  -> NothingWhite
            AppThemeStyle.CLASSIC -> NothingViolate
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

/** Themed badge / pill showing a value (threshold %, duration, etc.). */
@Composable
private fun ValueBadge(text: String) {
    val ts = LocalThemeState.current
    Surface(
        color = when (ts.themeStyle) {
            AppThemeStyle.AMOLED  -> NothingGray
            AppThemeStyle.CLASSIC -> NothingWhite
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = when (ts.themeStyle) {
                AppThemeStyle.AMOLED  -> NothingWhite
                AppThemeStyle.CLASSIC -> NothingViolate
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Confirmation Dialog
//
//  Uses FeatureConfirmationButtons from UnifiedFeatureCards.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LowBatteryAlertConfirmationDialog(
    onTestAlert: () -> Unit,
    onEnableAlert: () -> Unit,
    onDisableAlert: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (LowBatteryAlertConfig) -> Unit,
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository
) {
    var showEnableDialog by remember { mutableStateOf(false) }

    // Only show confirmation when settings dialog is NOT open
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
                        text = "🔋 Low Battery Alert",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Alert when battery is low",
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
                            text = "📱 How it works:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Monitors battery level continuously\n" +
                                    "• Triggers glyph animation when level drops below threshold\n" +
                                    "• Works even when screen is off\n" +
                                    "• Customizable threshold and animation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                FeatureConfirmationButtons(
                    primaryLabel = "🧪 Test Low Alert",
                    onPrimary = onTestAlert,
                    onSettings = { showEnableDialog = true },
                    onCancel = onDismiss
                )
            },
            dismissButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Settings dialog
    if (showEnableDialog) {
        LowBatteryAlertEnableDialog(
            onConfirm = { config ->
                onConfirm(config)
                showEnableDialog = false
                onDismiss()
            },
            onDisable = {
                onDisableAlert()
                showEnableDialog = false
                onDismiss()
            },
            onDismiss = { showEnableDialog = false },
            settingsRepository = settingsRepository
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Enable / Settings Dialog
//
//  Uses FeatureSaveButtons from UnifiedFeatureCards.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LowBatteryAlertEnableDialog(
    onConfirm: (LowBatteryAlertConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current

    val currentlyEnabled = remember { settingsRepository.isLowBatteryEnabled() }
    var isSaving by remember { mutableStateOf(false) }

    // Persisted preferences
    var threshold by remember {
        mutableFloatStateOf(settingsRepository.getLowBatteryThreshold().toFloat())
    }
    var selectedAnim by remember {
        mutableStateOf(GlyphAnimations.getById(settingsRepository.getLowBatteryAnimationId()))
    }
    var soundEnabled by remember {
        mutableStateOf(settingsRepository.isLowBatteryAudioEnabled())
    }
    var soundUri by remember {
        mutableStateOf(settingsRepository.getLowBatteryAudioUri())
    }
    var soundOffset by remember {
        mutableFloatStateOf(maxOf(0f, settingsRepository.getLowBatteryAudioOffset().toFloat()))
    }
    var animationDuration by remember {
        mutableLongStateOf(settingsRepository.getLowBatteryDuration().coerceIn(1000L, 10000L))
    }

    // Derived sound display name
    val soundName = remember(soundUri) {
        soundUri?.let { uriString ->
            try {
                val uri = uriString.toUri()
                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                    ?: uri.lastPathSegment
                        ?.substringBeforeLast('.')
                        ?.replace("%20", " ")
                    ?: "Custom audio file"
            } catch (e: Exception) {
                Log.w("LowBatteryAlert", "Error getting sound name", e)
                "Custom audio file"
            }
        } ?: "No sound selected"
    }

    // Pickers
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            soundUri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                ?.toString()
        }
    }

    val customFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val internalUri = copyFileToInternalStorage(context, uri, "lowbattery_custom_audio")
            if (internalUri != null) {
                soundUri = internalUri.toString()
            } else {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                soundUri = uri.toString()
            }
        } catch (e: Exception) {
            Log.e("LowBatteryAlert", "Error processing custom file", e)
        }
    }

    // Pre-resolve themed values
    val accentCard     = accentCardColor(NothingRed)
    val secBtnColors   = secondaryButtonColors()
    val accent         = themePrimaryActionColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Configure",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Customize low battery alert",
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
                // ── Battery Threshold ────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = accentCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🔋 Battery Threshold",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Alert when battery drops to:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ValueBadge("${threshold.toInt()}%")
                        }

                        Slider(
                            value = threshold,
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                threshold = it.coerceIn(5f, 50f)
                            },
                            valueRange = 5f..50f,
                            steps = 45,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Low (5%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("High (50%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── Animation Picker ─────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = accentCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val glyphAnimationManager = remember {
                            EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                GlyphComponent::class.java
                            ).glyphAnimationManager()
                        }
                        val scope = rememberCoroutineScope()

                        Text(
                            text = "🎞️ Animation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        @OptIn(ExperimentalLayoutApi::class)
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
                                            else        -> glyphAnimationManager.playLowBatteryAnimation(selectedAnim.id)
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
                                text = "🧪 Test \"${selectedAnim.displayName}\"",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ── Animation Duration ───────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = accentCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "⏱️ Display Duration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Duration:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ValueBadge("${(animationDuration / 1000f).toInt()}s")
                        }

                        Slider(
                            value = animationDuration.toFloat(),
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                animationDuration = it.toLong()
                                settingsRepository.saveLowBatteryDuration(animationDuration)
                            },
                            valueRange = 1000f..10000f,
                            steps = 17, // 0.5s increments between 1s and 10s
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("10s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Sync with sound duration (only when sound is enabled + selected)
                        if (soundEnabled && soundUri != null) {
                            Button(
                                onClick = {
                                    HapticUtils.triggerMediumFeedback(haptic, context)
                                    try {
                                        val mp = android.media.MediaPlayer()
                                        mp.setDataSource(context, Uri.parse(soundUri))
                                        mp.prepare()
                                        val dur = mp.duration.toLong().coerceIn(1000L, 10000L)
                                        mp.release()
                                        animationDuration = dur
                                        settingsRepository.saveLowBatteryDuration(dur)
                                    } catch (e: Exception) {
                                        Log.e("LowBatteryAlert", "Error getting sound duration: ${e.message}")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = secBtnColors,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "🎵 Sync with Sound Duration",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // ── Sound Alerts ─────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeCardContainerColor()
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔊 Sound Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = {
                                    HapticUtils.triggerLightFeedback(haptic, context)
                                    soundEnabled = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accent,
                                    checkedTrackColor = accent.copy(alpha = 0.5f)
                                )
                            )
                        }

                        if (soundEnabled) {
                            // Current sound name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Selected Sound:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = soundName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Sound picker buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        HapticUtils.triggerLightFeedback(haptic, context)
                                        ringtonePickerLauncher.launch(
                                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alert Sound")
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                                soundUri?.let {
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = secBtnColors,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("🎵 System", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        HapticUtils.triggerLightFeedback(haptic, context)
                                        customFilePickerLauncher.launch("audio/*")
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = secBtnColors,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("📁 Custom", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            // Audio offset slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Audio Offset:")
                                Text("${soundOffset.toInt()}ms")
                            }
                            Slider(
                                value = soundOffset,
                                onValueChange = {
                                    HapticUtils.triggerLightFeedback(haptic, context)
                                    soundOffset = it
                                },
                                valueRange = 0f..1500f,
                                steps = 29,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = accent)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isSaving = isSaving,
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = "⚡ Enable Low Alert",
                onSave = {
                    isSaving = true
                    onConfirm(
                        LowBatteryAlertConfig(
                            isEnabled = true,
                            threshold = threshold.toInt(),
                            animationId = selectedAnim.id,
                            audioEnabled = soundEnabled,
                            audioUri = soundUri,
                            audioOffset = soundOffset.toLong()
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