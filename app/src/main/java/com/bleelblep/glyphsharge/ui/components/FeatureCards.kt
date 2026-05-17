package com.bleelblep.glyphsharge.ui.components

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import com.bleelblep.glyphsharge.R
import androidx.compose.ui.res.stringResource
import com.bleelblep.glyphsharge.data.SettingsRepository

// ─────────────────────────────────────────────────────────────────────────────
//  PowerPeekCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PowerPeekCard(
    icon: Painter,
    onTestPowerPeek: () -> Unit,
    onEnablePowerPeek: () -> Unit,
    onDisablePowerPeek: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.power_peek_title),
    description: String = stringResource(id = R.string.power_peek_description),
    iconSize: Int = 32,
    isServiceActive: Boolean = true
) {
    val context = LocalContext.current
    var showDialog    by remember { mutableStateOf(false) }
    var isEnabled     by remember { mutableStateOf(settingsRepository.isPowerPeekEnabled()) }
    val toastText = stringResource(id = R.string.power_peek_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = { enabled ->
            isEnabled = enabled
            settingsRepository.savePowerPeekEnabled(enabled)
            if (enabled) onEnablePowerPeek() else onDisablePowerPeek()
        },
        onCardClick = {
            if (isServiceActive) showDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize
    )

    if (showDialog && isServiceActive) {
        PowerPeekConfirmationDialog(
            onTestPowerPeek = { onTestPowerPeek(); showDialog = false },
            onEnablePowerPeek = { onEnablePowerPeek(); isEnabled = true; showDialog = false },
            onDisablePowerPeek = { onDisablePowerPeek(); isEnabled = false; showDialog = false },
            onDismiss = { showDialog = false },
            settingsRepository = settingsRepository
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PulseLockCard  (Glow Gate)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PulseLockCard(
    icon: Painter,
    onTestPulseLock: () -> Unit,
    onEnablePulseLock: () -> Unit,
    onDisablePulseLock: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.pulse_lock_title),
    description: String = stringResource(id = R.string.pulse_lock_description),
    iconSize: Int = 32,
    isServiceActive: Boolean = true
) {
    val context = LocalContext.current
    var showDialog  by remember { mutableStateOf(false) }
    var isEnabled   by remember { mutableStateOf(settingsRepository.isPulseLockEnabled()) }
    val toastText = stringResource(id = R.string.pulse_lock_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = { enabled ->
            isEnabled = enabled
            settingsRepository.savePulseLockEnabled(enabled)
            if (enabled) onEnablePulseLock() else onDisablePulseLock()
        },
        onCardClick = {
            if (isServiceActive) showDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize
    )

    if (showDialog && isServiceActive) {
        PulseLockConfirmationDialog(
            onTestPulseLock = onTestPulseLock,
            onEnablePulseLock = { onEnablePulseLock(); isEnabled = true; showDialog = false },
            onDisablePulseLock = { onDisablePulseLock(); isEnabled = false; showDialog = false },
            onDismiss = { showDialog = false },
            settingsRepository = settingsRepository
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LowBatteryAlertCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LowBatteryAlertCard(
    onTestAlert: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.low_battery_alert_title),
    description: String = stringResource(id = R.string.low_battery_alert_description),
    icon: Painter = rememberVectorPainter(Icons.Default.BatteryAlert),
    iconSize: Int = 32,
    isServiceActive: Boolean = true
) {
    val context = LocalContext.current
    var showDialog         by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isEnabled          by remember { mutableStateOf(settingsRepository.isLowBatteryEnabled()) }
    val toastText = stringResource(id = R.string.low_battery_alert_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = { enabled ->
            isEnabled = enabled
            settingsRepository.saveLowBatteryEnabled(enabled)
        },
        onCardClick = {
            if (isServiceActive) showDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize
    )

    if (showDialog && isServiceActive) {
        LowBatteryAlertConfirmationDialog(
            onTestAlert = { onTestAlert(); showDialog = false },
            onEnableAlert = { showDialog = false; showSettingsDialog = true },
            onDisableAlert = {
                isEnabled = false
                settingsRepository.saveLowBatteryEnabled(false)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            onConfirm = { config ->
                settingsRepository.saveLowBatteryEnabled(config.isEnabled)
                settingsRepository.saveLowBatteryThreshold(config.threshold)
                settingsRepository.saveLowBatteryAnimationId(config.animationId)
                isEnabled = config.isEnabled
                showDialog = false
            },
            settingsRepository = settingsRepository
        )
    }

    if (showSettingsDialog && isServiceActive) {
        LowBatteryAlertEnableDialog(
            onConfirm = { config ->
                settingsRepository.saveLowBatteryEnabled(config.isEnabled)
                settingsRepository.saveLowBatteryThreshold(config.threshold)
                settingsRepository.saveLowBatteryAnimationId(config.animationId)
                isEnabled = config.isEnabled
                showSettingsDialog = false
            },
            onDismiss = {
                showSettingsDialog = false
                isEnabled = settingsRepository.isLowBatteryEnabled()
            },
            onDisable = {
                isEnabled = false
                settingsRepository.saveLowBatteryEnabled(false)
                showSettingsDialog = false
            },
            settingsRepository = settingsRepository
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ScreenOffCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScreenOffCard(
    onTestScreenOff: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    onEnableScreenOff: () -> Unit,
    onDisableScreenOff: () -> Unit,
    title: String = stringResource(id = R.string.screen_off_title),
    description: String = stringResource(id = R.string.screen_off_description),
    icon: Painter = rememberVectorPainter(Icons.Default.PowerSettingsNew),
    iconSize: Int = 32,
    isServiceActive: Boolean = true
) {
    val context = LocalContext.current
    var showDialog         by remember { mutableStateOf(false) }
    var showConfirmDialog  by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isEnabled          by remember { mutableStateOf(settingsRepository.isScreenOffFeatureEnabled()) }
    val toastText = stringResource(id = R.string.screen_off_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = { enabled ->
            isEnabled = enabled
            settingsRepository.saveScreenOffFeatureEnabled(enabled)
        },
        onCardClick = {
            if (isServiceActive) showConfirmDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize
    )

    if (showConfirmDialog && isServiceActive) {
        ScreenOffConfirmationDialog(
            onTest = { onTestScreenOff(); showConfirmDialog = false },
            onDismiss = { showConfirmDialog = false },
            onEnable = { showDialog = false; showSettingsDialog = true },
            onDisable = {
                isEnabled = false
                settingsRepository.saveLowBatteryEnabled(false)
                showDialog = false
            },
            settingsRepository = settingsRepository
        )
    }

    if (showSettingsDialog && isServiceActive) {
        ScreenOffConfigDialog(
            onEnable = {
                isEnabled = true
                onEnableScreenOff()
                showSettingsDialog = false
            },
            onDisable = {
                isEnabled = false
                onDisableScreenOff()
                showSettingsDialog = false
            },
            onDismiss = {
                showSettingsDialog = false
                isEnabled = settingsRepository.isScreenOffFeatureEnabled()
            },
            settingsRepository = settingsRepository
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  NfcGlyphCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NfcGlyphCard(
    onTestNfc: () -> Unit,
    onEnableNfc: () -> Unit,
    onDisableNfc: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.nfc_glyph_title),
    description: String = stringResource(id = R.string.nfc_glyph_description),
    icon: Painter = rememberVectorPainter(Icons.Default.Nfc),
    iconSize: Int = 32,
    isServiceActive: Boolean = true
) {
    val context = LocalContext.current
    var showConfirmDialog  by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isEnabled          by remember { mutableStateOf(settingsRepository.isNfcFeatureEnabled()) }
    val toastText = stringResource(id = R.string.nfc_glyph_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = { enabled ->
            isEnabled = enabled
            settingsRepository.saveNfcFeatureEnabled(enabled)
            if (enabled) onEnableNfc() else onDisableNfc()
        },
        onCardClick = {
            if (isServiceActive) showConfirmDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize
    )
    if (showConfirmDialog && isServiceActive) {
        NfcGlyphConfirmationDialog(
            onTest = { onTestNfc(); showConfirmDialog = false },
            onEnable = {
                isEnabled = true
                settingsRepository.saveNfcFeatureEnabled(true)
                onEnableNfc()
                showConfirmDialog = false
            },
            onDisable = {
                isEnabled = false
                settingsRepository.saveNfcFeatureEnabled(false)
                onDisableNfc()
                showConfirmDialog = false
            },
            onDismiss = { showConfirmDialog = false },
            settingsRepository = settingsRepository
        )
    }

    if (showSettingsDialog) {
        NfcGlyphConfigDialog(
            onDismiss = {
                showSettingsDialog = false
                isEnabled = settingsRepository.isNfcFeatureEnabled()
            },
            onEnable = {
                isEnabled = true
                settingsRepository.saveNfcFeatureEnabled(true)
                onEnableNfc()
                showSettingsDialog = false
            },
            onDisable = {
                isEnabled = false
                settingsRepository.saveNfcFeatureEnabled(false)
                onDisableNfc()
                showSettingsDialog = false
            },
            settingsRepository = settingsRepository
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ChargingAnimationCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChargingAnimationCard(
    icon: Painter,
    onTestAnimation: () -> Unit,
    onEnableAnimation: () -> Unit,
    onDisableAnimation: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.charging_animation_title),
    description: String = stringResource(id = R.string.charging_animation_description),
    iconSize: Int = 32,
    isServiceActive: Boolean = true
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var isEnabled  by remember { mutableStateOf(settingsRepository.isChargingAnimationEnabled()) }
    val toastText = stringResource(id = R.string.charging_animation_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = { enabled ->
            isEnabled = enabled
            settingsRepository.saveChargingAnimationEnabled(enabled)
            if (enabled) onEnableAnimation() else onDisableAnimation()
        },
        onCardClick = {
            if (isServiceActive) {
                showDialog = true
            } else {
                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier,
        iconSize = iconSize
    )

    if (showDialog && isServiceActive) {
        ChargingAnimationConfirmationDialog(
            onTestAnimation = {
                onTestAnimation()
                showDialog = false
            },
            onEnableAnimation = {
                onEnableAnimation()
                isEnabled = true
                settingsRepository.saveChargingAnimationEnabled(true)
                showDialog = false
            },
            onDisableAnimation = {
                onDisableAnimation()
                isEnabled = false
                settingsRepository.saveChargingAnimationEnabled(false)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            settingsRepository = settingsRepository
        )
    }
}