package com.bleelblep.glyphsharge.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.components.ChargingAnimationCard
import com.bleelblep.glyphsharge.ui.components.LowBatteryAlertCard
import com.bleelblep.glyphsharge.ui.components.NfcGlyphCard
import com.bleelblep.glyphsharge.ui.components.PowerPeekCard
import com.bleelblep.glyphsharge.ui.components.PulseLockCard
import com.bleelblep.glyphsharge.ui.components.ScreenOffCard
import com.bleelblep.glyphsharge.ui.state.GlyphFeature
import com.bleelblep.glyphsharge.ui.state.HomeUiState
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The list of feature cards on the home screen.
 *
 * Adding a feature is one block here plus a case in [GlyphFeature] and
 * [com.bleelblep.glyphsharge.services.FeatureServiceController]; nothing else
 * in the app needs to change.
 */
internal fun LazyListScope.homeFeatureCards(
    uiState: HomeUiState,
    settingsRepository: SettingsRepository,
    viewModel: HomeViewModel
) {
    val glyphServiceEnabled = uiState.glyphServiceEnabled

    item {
        ChargingAnimationCard(
            isEnabled = uiState.stateOf(GlyphFeature.CHARGING_ANIMATION).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = { viewModel.setFeatureEnabled(GlyphFeature.CHARGING_ANIMATION, it) },
            onTestAnimation = { viewModel.testFeature(GlyphFeature.CHARGING_ANIMATION) },
            icon = rememberVectorPainter(image = Icons.Default.BatteryChargingFull),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            settingsRepository = settingsRepository
        )
    }

    item {
        val context = LocalContext.current
        val onPowerPeekToggle = rememberPowerPeekToggle(context, viewModel)
        PowerPeekCard(
            isEnabled = uiState.stateOf(GlyphFeature.POWER_PEEK).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = onPowerPeekToggle,
            onTestPowerPeek = { viewModel.testFeature(GlyphFeature.POWER_PEEK) },
            icon = painterResource(id = R.drawable._44),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            settingsRepository = settingsRepository
        )
    }

    item {
        PulseLockCard(
            isEnabled = uiState.stateOf(GlyphFeature.PULSE_LOCK).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = { viewModel.setFeatureEnabled(GlyphFeature.PULSE_LOCK, it) },
            onTestPulseLock = { viewModel.testFeature(GlyphFeature.PULSE_LOCK) },
            icon = rememberVectorPainter(image = Icons.Default.Lock),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            settingsRepository = settingsRepository
        )
    }

    item {
        ScreenOffCard(
            isEnabled = uiState.stateOf(GlyphFeature.SCREEN_OFF).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = { viewModel.setFeatureEnabled(GlyphFeature.SCREEN_OFF, it) },
            onTestScreenOff = { viewModel.testFeature(GlyphFeature.SCREEN_OFF) },
            icon = rememberVectorPainter(image = Icons.Default.PowerSettingsNew),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            settingsRepository = settingsRepository
        )
    }

    item {
        NfcGlyphCard(
            isEnabled = uiState.stateOf(GlyphFeature.NFC).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = { viewModel.setFeatureEnabled(GlyphFeature.NFC, it) },
            onTestNfc = { viewModel.testFeature(GlyphFeature.NFC) },
            icon = rememberVectorPainter(image = Icons.Default.Nfc),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            settingsRepository = settingsRepository
        )
    }

    item {
        LowBatteryAlertCard(
            isEnabled = uiState.stateOf(GlyphFeature.LOW_BATTERY).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = { viewModel.setFeatureEnabled(GlyphFeature.LOW_BATTERY, it) },
            onTestAlert = { viewModel.testFeature(GlyphFeature.LOW_BATTERY) },
            icon = rememberVectorPainter(image = Icons.Default.BatteryAlert),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
            settingsRepository = settingsRepository
        )
    }
}

/**
 * Power Peek runs a `specialUse` foreground service, so it needs a runtime
 * permission before it may be enabled. The service is only started once the
 * user grants it; if they decline, the toggle is put back.
 *
 * Must be called from a composable context so the launcher can be registered,
 * and returns the plain callback the card invokes.
 */
@Composable
private fun rememberPowerPeekToggle(
    context: Context,
    viewModel: HomeViewModel
): (Boolean) -> Unit {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, true)
        } else {
            Toast.makeText(
                context,
                "Permission denied. PowerPeek cannot run without it.",
                Toast.LENGTH_LONG
            ).show()
            // Put the toggle back where it was.
            viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, false)
        }
    }

    return { enabled ->
        if (!enabled) {
            viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, false)
        } else {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
            ) == PackageManager.PERMISSION_GRANTED

            if (alreadyGranted) {
                viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, true)
            } else {
                permissionLauncher.launch(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
            }
        }
    }
}
