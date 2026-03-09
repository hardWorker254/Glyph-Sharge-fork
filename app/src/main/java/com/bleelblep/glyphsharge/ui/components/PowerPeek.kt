package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalHapticFeedback
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState
import com.bleelblep.glyphsharge.ui.theme.SettingsRepository
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import com.bleelblep.glyphsharge.ui.theme.NothingGray
import com.bleelblep.glyphsharge.ui.theme.NothingGreen
import com.bleelblep.glyphsharge.ui.theme.NothingRed
import com.bleelblep.glyphsharge.ui.theme.NothingViolate
import com.bleelblep.glyphsharge.ui.theme.NothingWhite
import kotlin.math.roundToInt

/**
 * PowerPeek Configuration Data Class
 */
data class PowerPeekConfig(
    val isEnabled: Boolean = false,
    val shakeThreshold: Float = 12.0f,
    val displayDuration: Long = 3000L, // 3 seconds
    val enableWhenScreenOff: Boolean = false
)

/**
 * Redesigned PowerPeek Confirmation Dialog with improved layout and button placement
 */
@Composable
fun PowerPeekConfirmationDialog(
    onTestPowerPeek: () -> Unit,
    onEnablePowerPeek: () -> Unit,
    onDisablePowerPeek: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository
) {
    var showEnableDialog by remember { mutableStateOf(false) }
    val themeState = LocalThemeState.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = { /* Prevent dismissal */ },
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
                    text = "⚡ PowerPeek",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Battery peek on shake",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Feature description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (themeState.themeStyle) {
                            AppThemeStyle.AMOLED -> NothingRed
                            AppThemeStyle.CLASSIC -> if (themeState.isDarkTheme) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                NothingWhite
                            }
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
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
                            text = "• Shake your device to view battery percentage\n• Works even when screen is off\n• Customizable sensitivity and display duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Action - Test PowerPeek
                ElevatedButton(
                    onClick = { 
                        HapticUtils.triggerMediumFeedback(haptic, context)
                        onTestPowerPeek() 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = when (themeState.themeStyle) {
                            AppThemeStyle.AMOLED -> NothingGreen
                            AppThemeStyle.CLASSIC -> NothingViolate
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentColor = NothingWhite
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 12.dp
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧪 Test Power Peek",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Secondary Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Enable PowerPeek Button
                    Button(
                        onClick = { 
                            HapticUtils.triggerLightFeedback(haptic, context)
                            showEnableDialog = true 
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (themeState.themeStyle) {
                                AppThemeStyle.AMOLED -> NothingGray
                                AppThemeStyle.CLASSIC -> NothingViolate
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = when (themeState.themeStyle) {
                                AppThemeStyle.AMOLED -> NothingWhite
                                AppThemeStyle.CLASSIC -> NothingWhite
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "⚙️ Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Cancel Button
                    OutlinedButton(
                        onClick = { 
                            HapticUtils.triggerLightFeedback(haptic, context)
                            onDismiss() 
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "✕ Cancel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    )
    
    // Enable PowerPeek Settings Dialog
    if (showEnableDialog) {
        PowerPeekEnableDialog(
            onConfirm = { config ->
                onEnablePowerPeek()
                showEnableDialog = false
                onDismiss()
            },
            onDismiss = {
                showEnableDialog = false
            },
            onDisable = {
                onDisablePowerPeek()
                showEnableDialog = false
                onDismiss()
            },
            settingsRepository = settingsRepository
        )
    }
}

/**
 * Redesigned PowerPeek Enable Dialog with modern layout and better controls
 */
@Composable
fun PowerPeekEnableDialog(
    onConfirm: (PowerPeekConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository
) {
    val initialThreshold = remember { settingsRepository.getShakeThreshold() }
    val initialDuration = remember { settingsRepository.getDisplayDuration() }

    var enableWhenScreenOff by remember { mutableStateOf(true) }
    var shakeThreshold by remember { mutableFloatStateOf(initialThreshold) }
    var displayDuration by remember { mutableFloatStateOf(initialDuration / 1000f) } // in seconds
    val themeState = LocalThemeState.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
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
                    text = "Customize your shake detection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Sensitivity Control Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (themeState.themeStyle) {
                            AppThemeStyle.AMOLED -> NothingRed
                            AppThemeStyle.CLASSIC -> if (themeState.isDarkTheme) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                NothingWhite
                            }
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
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
                                text = "📳 Shake Sensitivity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = when (themeState.themeStyle) {
                                    AppThemeStyle.AMOLED -> NothingGray
                                    AppThemeStyle.CLASSIC -> if (themeState.isDarkTheme) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        NothingWhite
                                    }
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = settingsRepository.getShakeIntensityLevel(shakeThreshold),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (themeState.themeStyle) {
                                        AppThemeStyle.AMOLED -> NothingWhite
                                        AppThemeStyle.CLASSIC -> NothingViolate
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }

                        // Simple discrete slider with exactly three stops (0,1,2)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("Medium", "Hard", "Harder").forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        // Backing value: 0-Medium, 1-Hard, 2-Harder
                        var sliderStep by remember {
                            mutableFloatStateOf(
                                when (shakeThreshold) {
                                    SettingsRepository.SHAKE_HARD      -> 1f
                                    SettingsRepository.SHAKE_HARDEST   -> 2f
                                    else                              -> 0f
                                }
                            )
                        }

                        Slider(
                            value = sliderStep,
                            onValueChange = { raw ->
                                HapticUtils.triggerLightFeedback(haptic, context)
                                sliderStep = raw.coerceIn(0f,2f)
                            },
                            onValueChangeFinished = {
                                val snapped = sliderStep.roundToInt().toFloat()
                                sliderStep = snapped
                                shakeThreshold = when (snapped.toInt()) {
                                    1 -> SettingsRepository.SHAKE_HARD
                                    2 -> SettingsRepository.SHAKE_HARDEST
                                    else -> SettingsRepository.SHAKE_MEDIUM
                                }
                            },
                            valueRange = 0f..2f,
                            steps = 0, // continuous track, no tick marks
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = when (themeState.themeStyle) {
                                    AppThemeStyle.AMOLED -> NothingGreen
                                    AppThemeStyle.CLASSIC -> NothingViolate
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                activeTrackColor = when (themeState.themeStyle) {
                                    AppThemeStyle.AMOLED -> NothingGreen
                                    AppThemeStyle.CLASSIC -> NothingViolate
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        )
                    }
                }
                
                // Duration Control Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (themeState.themeStyle) {
                            AppThemeStyle.AMOLED -> NothingRed
                            AppThemeStyle.CLASSIC -> if (themeState.isDarkTheme) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                NothingWhite
                            }
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
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
                                text = "⏱️ Display Duration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = when (themeState.themeStyle) {
                                    AppThemeStyle.AMOLED -> NothingGray
                                    AppThemeStyle.CLASSIC -> if (themeState.isDarkTheme) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        NothingWhite
                                    }
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${displayDuration.toInt()}s",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (themeState.themeStyle) {
                                        AppThemeStyle.AMOLED -> NothingWhite
                                        AppThemeStyle.CLASSIC -> NothingViolate
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                        
                        Slider(
                            value = displayDuration,
                            onValueChange = { 
                                HapticUtils.triggerLightFeedback(haptic, context)
                                displayDuration = it 
                            },
                            valueRange = 2f..10f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = when (themeState.themeStyle) {
                                    AppThemeStyle.AMOLED -> NothingGreen
                                    AppThemeStyle.CLASSIC -> NothingViolate
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Quick",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Long",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Enable Button
                var isSaving by remember { mutableStateOf(false) }
                val currentlyEnabled = remember { settingsRepository.isPowerPeekEnabled() }

                ElevatedButton(
                    onClick = {
                        HapticUtils.triggerMediumFeedback(haptic, context)
                        isSaving = true
                        val newDuration = (displayDuration * 1000).toLong()
                        settingsRepository.saveShakeThreshold(shakeThreshold)
                        settingsRepository.saveDisplayDuration(newDuration)

                        val config = PowerPeekConfig(
                            isEnabled = true,
                            shakeThreshold = shakeThreshold,
                            displayDuration = newDuration,
                            enableWhenScreenOff = enableWhenScreenOff
                        )
                        onConfirm(config)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = when (themeState.themeStyle) {
                            AppThemeStyle.AMOLED -> NothingGreen
                            AppThemeStyle.CLASSIC -> NothingViolate
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentColor = NothingWhite
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 12.dp
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = if (currentlyEnabled) "💾 Save Settings" else "✅ Enable Power Peek",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Secondary Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Disable Button
                    Button(
                        onClick = {
                            HapticUtils.triggerMediumFeedback(haptic, context)
                            onDisable()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (themeState.themeStyle) {
                                AppThemeStyle.AMOLED -> NothingRed
                                AppThemeStyle.CLASSIC -> NothingRed
                                else -> MaterialTheme.colorScheme.error
                            },
                            contentColor = NothingWhite
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "✖️ Disable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Cancel Button
                    OutlinedButton(
                        onClick = { 
                            HapticUtils.triggerLightFeedback(haptic, context)
                            onDismiss() 
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "✕ Cancel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    )
}

