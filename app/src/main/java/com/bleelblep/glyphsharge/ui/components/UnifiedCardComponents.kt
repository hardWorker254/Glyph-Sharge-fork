package com.bleelblep.glyphsharge.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import androidx.compose.ui.platform.LocalHapticFeedback

// ─────────────────────────────────────────────────────────────────────────────
//  WideFeatureCardWithToggle
//
//  Replaces: PowerPeekCard, GlyphGuardCard, PulseLockCard,
//            LowBatteryAlertCard, ScreenOffCard, NfcGlyphCard
//
//  Each of those cards differs only in:
//    - title / description / icon
//    - enabled state persistence (settingsRepository call)
//    - which dialog to show on click
//
//  The caller supplies:
//    isFeatureEnabled  – current persisted state
//    onFeatureToggle   – persist + side-effects (start/stop service, etc.)
//    onCardClick       – show the feature's own dialog / gate behind service check
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WideFeatureCardWithToggle(
    title: String,
    description: String,
    icon: Painter,
    isServiceActive: Boolean,
    isFeatureEnabled: Boolean,
    onFeatureToggle: (Boolean) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 32,
    height: Int = 140
) {
    val resolvedTint = when {
        !isServiceActive -> NothingViolate
        isFeatureEnabled -> NothingGreen
        else             -> NothingRed
    }

    val alpha by animateFloatAsState(
        targetValue = if (isServiceActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "featureAlpha"
    )

    // Card layer
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .alpha(alpha)
    ) {
        FeatureCard(
            title = title,
            description = description,
            icon = icon,
            onClick = onCardClick,
            modifier = Modifier.fillMaxSize(),
            iconSize = iconSize,
            contentPadding = PaddingValues(16.dp),
            iconTint = resolvedTint
        )
    }

    // Toggle overlay — zero-height Box so it doesn't affect layout
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.dp)
    ) {
        MorphingToggleButton(
            checked = isFeatureEnabled,
            onCheckedChange = onFeatureToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-12).dp, y = 12.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  FeatureInfoDialog
//
//  Replaces: PowerPeekInformationDialog, GlyphGuardInformationDialog,
//            GlowGateInformationDialog, LowBatteryAlertInformationDialog,
//            BatteryStoryInformationDialog, QuietHoursInformationDialog
//
//  All six dialogs are structurally identical: title block + one card with
//  several labelled text sections + one "Got it!" button.
// ─────────────────────────────────────────────────────────────────────────────

data class InfoSection(
    val label: String,   // e.g. "🔋 What is Power Peek?"
    val body: String     // bullet or numbered text
)

@Composable
fun FeatureInfoDialog(
    titleEmoji: String,
    title: String,
    subtitle: String,
    sections: List<InfoSection>,
    buttonColor: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeState = LocalThemeState.current
    val haptic    = LocalHapticFeedback.current
    val context   = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$titleEmoji $title",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
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
                    .then(Modifier), // verticalScroll added below
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeCardContainerColor()
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        sections.forEach { section ->
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            ElevatedButton(
                onClick = {
                    HapticUtils.triggerMediumFeedback(haptic, context)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = buttonColor,
                    contentColor = NothingWhite
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "✓ Got it!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  FeatureConfirmationButtons
//
//  Replaces: the identical 3-button layout (Primary action / Settings / Cancel)
//  that appears in GlyphGuardConfirmationDialog, NfcGlyphConfirmationDialog,
//  ScreenOffConfirmationDialog, LowBatteryConfirmationDialog.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FeatureConfirmationButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSettings: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeState = LocalThemeState.current
    val haptic     = LocalHapticFeedback.current
    val context    = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedButton(
            onClick = {
                HapticUtils.triggerMediumFeedback(haptic, context)
                onPrimary()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = themePrimaryActionColor(),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onSettings()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeSettingsButtonColor(),
                    contentColor   = themeSettingsButtonContentColor()
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

            OutlinedButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onCancel()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
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
}

// ─────────────────────────────────────────────────────────────────────────────
//  FeatureSaveButtons
//
//  Replaces: the identical Save / Disable / Cancel button group that appears
//  in GlyphGuardSettingsDialog, NfcGlyphConfigDialog, ScreenOffConfigDialog,
//  LowBatteryConfigDialog.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FeatureSaveButtons(
    isSaving: Boolean,
    isCurrentlyEnabled: Boolean,
    enableLabel: String,
    onSave: () -> Unit,
    onDisable: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeState = LocalThemeState.current
    val haptic     = LocalHapticFeedback.current
    val context    = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedButton(
            onClick = {
                HapticUtils.triggerMediumFeedback(haptic, context)
                onSave()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = themePrimaryActionColor(),
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
                    color = NothingWhite,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = if (isCurrentlyEnabled) "💾 Save Settings" else enableLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    HapticUtils.triggerMediumFeedback(haptic, context)
                    onDisable()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingRed,
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

            OutlinedButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onCancel()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
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
}
