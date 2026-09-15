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

@Composable
fun PowerPeekCard(
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.power_peek_title),
    description: String = stringResource(id = R.string.power_peek_description),
    icon: Painter,
    iconSize: Int = 32,
    isServiceActive: Boolean = true,
    onTestPowerPeek: () -> Unit,
    onEnablePowerPeek: () -> Unit,
    onDisablePowerPeek: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(settingsRepository.isPowerPeekEnabled()) }
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

@Composable
fun PulseLockCard(
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.pulse_lock_title),
    description: String = stringResource(id = R.string.pulse_lock_description),
    icon: Painter,
    iconSize: Int = 32,
    isServiceActive: Boolean = true,
    onTestPulseLock: () -> Unit,
    onEnablePulseLock: () -> Unit,
    onDisablePulseLock: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(settingsRepository.isPulseLockEnabled()) }
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
            onTestPulseLock = { onTestPulseLock(); showDialog = false },
            onEnablePulseLock = { onEnablePulseLock(); isEnabled = true; showDialog = false },
            onDisablePulseLock = { onDisablePulseLock(); isEnabled = false; showDialog = false },
            onDismiss = { showDialog = false },
            settingsRepository = settingsRepository
        )
    }
}

@Composable
fun LowBatteryAlertCard(
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.low_battery_alert_title),
    description: String = stringResource(id = R.string.low_battery_alert_description),
    icon: Painter,
    iconSize: Int = 32,
    isServiceActive: Boolean = true,
    onTestAlert: () -> Unit,
    onEnableLowBattery: () -> Unit,
    onDisableLowBattery: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(settingsRepository.isLowBatteryEnabled()) }
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
            onEnableAlert = {
                showDialog = false
                settingsRepository.saveLowBatteryEnabled(true)
                onEnableLowBattery()
                showSettingsDialog = true
            },
            onDisableAlert = {
                isEnabled = false
                settingsRepository.saveLowBatteryEnabled(false)
                onDisableLowBattery()
                showDialog = false
            },
            onDismiss = { showDialog = false },
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

@Composable
fun ScreenOffCard(
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.screen_off_title),
    description: String = stringResource(id = R.string.screen_off_description),
    icon: Painter,
    iconSize: Int = 32,
    isServiceActive: Boolean = true,
    onTestScreenOff: () -> Unit,
    onEnableScreenOff: () -> Unit,
    onDisableScreenOff: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(settingsRepository.isScreenOffFeatureEnabled()) }
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
            onEnable = {
                isEnabled = true
                settingsRepository.saveScreenOffFeatureEnabled(true)
                onEnableScreenOff()
                showConfirmDialog = false
            },
            onDisable = {
                isEnabled = false
                settingsRepository.saveScreenOffFeatureEnabled(false)
                onDisableScreenOff()
                showConfirmDialog = false
            },
            settingsRepository = settingsRepository
        )
    }

    if (showSettingsDialog && isServiceActive) {
        ScreenOffEnableDialog(
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

@Composable
fun NfcGlyphCard(
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.nfc_glyph_title),
    description: String = stringResource(id = R.string.nfc_glyph_description),
    icon: Painter,
    iconSize: Int = 32,
    isServiceActive: Boolean = true,
    onTestNfc: () -> Unit,
    onEnableNfc: () -> Unit,
    onDisableNfc: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(settingsRepository.isNfcFeatureEnabled()) }
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
        NfcGlyphEnableDialog(
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

@Composable
fun ChargingAnimationCard(
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.charging_animation_title),
    description: String = stringResource(id = R.string.charging_animation_description),
    icon: Painter,
    iconSize: Int = 32,
    isServiceActive: Boolean = true,
    onTestAnimation: () -> Unit,
    onEnableAnimation: () -> Unit,
    onDisableAnimation: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(settingsRepository.isChargingAnimationEnabled()) }
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