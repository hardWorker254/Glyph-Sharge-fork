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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
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
    val soundEnabled: Boolean,
    val soundUri: String?,
    val soundOffsetMs: Long,
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
                        text = "✨ Glow Gate",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Unlock animation",
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
                            text = "✨ How it works:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Plays the selected Glyph animation after unlock\n" +
                                    "• Optional sound with adjustable delay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                FeatureConfirmationButtons(
                    primaryLabel = "🧪 Test Glow Gate",
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
    var soundEnabled by remember { mutableStateOf(settingsRepository.isPulseLockAudioEnabled()) }
    var soundUri by remember { mutableStateOf(settingsRepository.getPulseLockAudioUri()) }
    var soundOffset by remember {
        mutableFloatStateOf(maxOf(0f, settingsRepository.getPulseLockAudioOffset().toFloat()))
    }
    var animationDuration by remember {
        mutableLongStateOf(settingsRepository.getPulseLockDuration())
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
                Log.w("PulseLock", "Error getting sound name", e)
                "Custom audio file"
            }
        } ?: "No sound selected"
    }

    // DI
    val glyphAnimationManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GlyphComponent::class.java
        ).glyphAnimationManager()
    }

    // Pickers
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            soundUri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                ?.toString()
            settingsRepository.savePulseLockAudioUri(soundUri)
        }
    }

    val customFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val internalUri = copyFileToInternalStorage(context, uri, "pulselock_custom_audio")
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
            settingsRepository.savePulseLockAudioUri(soundUri)
        } catch (e: Exception) {
            Log.e("PulseLock", "Error processing custom file", e)
        }
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
                    text = "Configure",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Customize unlock animation",
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
                            text = "🎞️ Animation",
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
                                text = "🧪 Test \"${selectedAnim.displayName}\"",
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
                            text = "⏱️ Animation Duration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Duration:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ThemedValueBadge("${(animationDuration / 1000f).toInt()}s")
                        }

                        Slider(
                            value = animationDuration.toFloat(),
                            onValueChange = {
                                HapticUtils.triggerLightFeedback(haptic, context)
                                animationDuration = it.toLong()
                                settingsRepository.savePulseLockDuration(animationDuration)
                            },
                            valueRange = 1000f..10000f,
                            steps = 17,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent)
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Short (1s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Long (10s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Sync with sound duration
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
                                        settingsRepository.savePulseLockDuration(dur)
                                    } catch (e: Exception) {
                                        Log.e("PulseLock", "Error getting sound duration: ${e.message}")
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

                // ── Sound Alerts ─────────────────────────────────────────
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
                                text = "🔊 Sound Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = {
                                    HapticUtils.triggerLightFeedback(haptic, context)
                                    soundEnabled = it
                                    settingsRepository.savePulseLockAudioEnabled(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accent,
                                    checkedTrackColor = accent.copy(alpha = 0.5f)
                                )
                            )
                        }

                        if (soundEnabled) {
                            // Selected sound name
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
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select PulseLock Sound")
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                                soundUri?.let {
                                                    runCatching {
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                                    }
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

                            // Audio offset
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Audio Offset:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ThemedValueBadge("${soundOffset.toInt()}ms")
                            }

                            Slider(
                                value = soundOffset,
                                onValueChange = {
                                    HapticUtils.triggerLightFeedback(haptic, context)
                                    soundOffset = it
                                    settingsRepository.savePulseLockAudioOffset(it.toLong())
                                },
                                valueRange = 0f..1500f,
                                steps = 29,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = accent)
                            )

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Simultaneous (0ms)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("After (+1.5s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                enableLabel = "✨ Enable Glow Gate",
                onSave = {
                    isSaving = true
                    settingsRepository.savePulseLockAnimationId(selectedAnim.id)
                    settingsRepository.savePulseLockDuration(animationDuration)
                    settingsRepository.savePulseLockAudioEnabled(soundEnabled)
                    settingsRepository.savePulseLockAudioUri(soundUri)
                    settingsRepository.savePulseLockAudioOffset(soundOffset.toLong())
                    onConfirm(
                        PulseLockConfig(
                            selectedAnim.id,
                            soundEnabled,
                            soundUri,
                            soundOffset.toLong(),
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

// ─────────────────────────────────────────────────────────────────────────────
//  Utility: copy external URI to internal storage
// ─────────────────────────────────────────────────────────────────────────────

fun copyFileToInternalStorage(
    context: android.content.Context,
    sourceUri: Uri,
    fileName: String
): Uri? {
    return try {
        val inputStream: InputStream = context.contentResolver.openInputStream(sourceUri)
            ?: run {
                Log.e("FileUtils", "Could not open input stream for URI: $sourceUri")
                return null
            }

        val originalName = sourceUri.lastPathSegment ?: "audio"
        val extension = listOf(".mp3", ".wav", ".m4a", ".aac", ".ogg")
            .firstOrNull { originalName.contains(it, ignoreCase = true) }
            ?: ".mp3"

        val internalFile = File(context.filesDir, "$fileName$extension")

        inputStream.use { input ->
            FileOutputStream(internalFile).use { output ->
                input.copyTo(output)
            }
        }

        Uri.fromFile(internalFile)
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to copy file to internal storage", e)
        null
    }
}