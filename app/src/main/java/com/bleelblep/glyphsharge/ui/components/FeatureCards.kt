package com.bleelblep.glyphsharge.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.cards.WideFeatureCardWithToggle
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository

/**
 * The six feature cards shown on the home screen.
 *
 * State arrives as `isEnabled` and leaves through [onEnabledChange], which both
 * persists the preference and starts or stops the backing service, so the
 * store is touched only by whoever owns the state.
 *
 * The store itself is read from [LocalSettingsRepository] by the configuration
 * dialog, which makes a card callable on its own.
 */

@Composable
fun PowerPeekCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestPowerPeek: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.power_peek_title),
    description: String = stringResource(id = R.string.power_peek_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.power_peek_toast)

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
        PowerPeekConfirmationDialog(
            onTestPowerPeek = { onTestPowerPeek(); showDialog = false },
            onEnablePowerPeek = { onEnabledChange(true); showDialog = false },
            onDisablePowerPeek = { onEnabledChange(false); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun PulseLockCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestPulseLock: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.pulse_lock_title),
    description: String = stringResource(id = R.string.pulse_lock_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.pulse_lock_toast)

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
        PulseLockConfirmationDialog(
            onTestPulseLock = { onTestPulseLock(); showDialog = false },
            onEnablePulseLock = { onEnabledChange(true); showDialog = false },
            onDisablePulseLock = { onEnabledChange(false); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun LowBatteryAlertCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestAlert: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.low_battery_alert_title),
    description: String = stringResource(id = R.string.low_battery_alert_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    val settingsRepository = LocalSettingsRepository.current
    var showDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.low_battery_alert_toast)

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
        LowBatteryAlertConfirmationDialog(
            onTestAlert = { onTestAlert(); showDialog = false },
            // Enabling closes the dialog and returns to the home screen; the
            // configuration dialog is reached through the confirmation's own
            // settings button.
            onEnableAlert = { config ->
                settingsRepository.saveLowBatteryEnabled(config.isEnabled)
                settingsRepository.saveLowBatteryThreshold(config.threshold)
                settingsRepository.saveLowBatteryAnimationId(config.animationId)
                settingsRepository.saveLowBatteryDuration(config.durationMs)
                onEnabledChange(config.isEnabled)
                showDialog = false
            },
            onDisableAlert = {
                onEnabledChange(false)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun ScreenOffCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestScreenOff: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.screen_off_title),
    description: String = stringResource(id = R.string.screen_off_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.screen_off_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = onEnabledChange,
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
                onEnabledChange(true)
                showConfirmDialog = false
            },
            onDisable = {
                onEnabledChange(false)
                showConfirmDialog = false
            }
        )
    }
}

@Composable
fun NfcGlyphCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestNfc: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.nfc_glyph_title),
    description: String = stringResource(id = R.string.nfc_glyph_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    val settingsRepository = LocalSettingsRepository.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.nfc_glyph_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = onEnabledChange,
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
                onEnabledChange(true)
                showConfirmDialog = false
            },
            onDisable = {
                onEnabledChange(false)
                showConfirmDialog = false
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

@Composable
fun ChargingAnimationCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestAnimation: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.charging_animation_title),
    description: String = stringResource(id = R.string.charging_animation_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    val settingsRepository = LocalSettingsRepository.current
    var showDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.charging_animation_toast)

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
        ChargingAnimationConfirmationDialog(
            onTestAnimation = { onTestAnimation(); showDialog = false },
            onEnableAnimation = { onEnabledChange(true); showDialog = false },
            onDisableAnimation = { onEnabledChange(false); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * The music visualiser card.
 *
 * Shaped like every other card, with one difference that is not cosmetic: the
 * permission is asked for by [HomeFeatures] before [onEnabledChange] is ever
 * called, because a visualiser that cannot read the audio is a card the user
 * switched on and nothing else.
 */
@Composable
fun MusicVisualizerCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestAnimation: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.music_viz_title),
    description: String = stringResource(id = R.string.music_viz_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    val settingsRepository = LocalSettingsRepository.current
    var showDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(id = R.string.music_viz_toast)

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
        MusicVisualizerConfirmationDialog(
            onTestAnimation = { onTestAnimation(); showDialog = false },
            onEnableAnimation = { onEnabledChange(true); showDialog = false },
            onDisableAnimation = { onEnabledChange(false); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}
