package com.bleelblep.glyphsharge.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.components.ChargingAnimationCard
import com.bleelblep.glyphsharge.ui.components.LowBatteryAlertCard
import com.bleelblep.glyphsharge.ui.components.NfcGlyphCard
import com.bleelblep.glyphsharge.ui.components.PowerPeekCard
import com.bleelblep.glyphsharge.ui.components.PulseLockCard
import com.bleelblep.glyphsharge.ui.components.ScreenOffCard
import androidx.core.content.ContextCompat
import android.widget.Toast

/**
 * The list of feature cards on the home screen.
 *
 * Kept in its own file so that adding a feature is a single self-contained
 * block: copy the pattern of any existing `item { ... }` below and the new
 * card appears in the right place with the right icon and service-active
 * flag, with no other file needing to change.
 */
internal fun LazyListScope.homeFeatureCards(
    glyphServiceEnabled: Boolean,
    settingsRepository: SettingsRepository,
    actions: HomeActions
) {
    item {
        ChargingAnimationCard(
            icon = rememberVectorPainter(image = Icons.Default.BatteryChargingFull),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            isServiceActive = glyphServiceEnabled,
            onTestAnimation = actions.onTestChargingAnimation,
            onEnableAnimation = actions.onEnableChargingAnimation,
            onDisableAnimation = actions.onDisableChargingAnimation,
            settingsRepository = settingsRepository
        )
    }

    item {
        val context = LocalContext.current
        PowerPeekCard(
            icon = painterResource(id = R.drawable._44),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            isServiceActive = glyphServiceEnabled,
            onTestPowerPeek = actions.onTestPowerPeek,
            onEnablePowerPeek = actions.onRequestEnablePowerPeek(context),
            onDisablePowerPeek = actions.onDisablePowerPeek,
            settingsRepository = settingsRepository
        )
    }

    item {
        PulseLockCard(
            icon = rememberVectorPainter(image = Icons.Default.Lock),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            isServiceActive = glyphServiceEnabled,
            onTestPulseLock = actions.onTestPulseLock,
            onEnablePulseLock = actions.onEnablePulseLock,
            onDisablePulseLock = actions.onDisablePulseLock,
            settingsRepository = settingsRepository
        )
    }

    item {
        ScreenOffCard(
            icon = rememberVectorPainter(image = Icons.Default.PowerSettingsNew),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            isServiceActive = glyphServiceEnabled,
            onTestScreenOff = actions.onTestScreenOff,
            onEnableScreenOff = actions.onEnableScreenOff,
            onDisableScreenOff = actions.onDisableScreenOff,
            settingsRepository = settingsRepository
        )
    }

    item {
        NfcGlyphCard(
            icon = rememberVectorPainter(image = Icons.Default.Nfc),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            isServiceActive = glyphServiceEnabled,
            onTestNfc = actions.onTestNfc,
            onEnableNfc = actions.onEnableNfc,
            onDisableNfc = actions.onDisableNfc,
            settingsRepository = settingsRepository
        )
    }

    item {
        LowBatteryAlertCard(
            icon = rememberVectorPainter(image = Icons.Default.BatteryAlert),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            isServiceActive = glyphServiceEnabled,
            onTestAlert = actions.onTestLowBattery,
            onEnableLowBattery = actions.onEnableLowBattery,
            onDisableLowBattery = actions.onDisableLowBattery,
            settingsRepository = settingsRepository
        )
    }
}

/**
 * Power Peek runs a `specialUse` foreground service, so it needs a runtime
 * permission. This requests it and only enables the feature once granted.
 */
@Composable
private fun HomeActions.onRequestEnablePowerPeek(
    context: Context
): () -> Unit {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onEnablePowerPeek()
        } else {
            Toast.makeText(
                context,
                "Permission denied. PowerPeek cannot run without it.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onEnablePowerPeek()
        } else {
            permissionLauncher.launch(
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
            )
        }
    }
}
