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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.ui.components.ChargingAnimationCard
import com.bleelblep.glyphsharge.ui.components.LowBatteryAlertCard
import com.bleelblep.glyphsharge.ui.components.MusicVisualizerCard
import com.bleelblep.glyphsharge.ui.components.NfcGlyphCard
import com.bleelblep.glyphsharge.ui.components.PowerPeekCard
import com.bleelblep.glyphsharge.ui.components.PulseLockCard
import com.bleelblep.glyphsharge.ui.components.ScreenOffCard
import com.bleelblep.glyphsharge.ui.components.VpnConnectedCard
import com.bleelblep.glyphsharge.ui.state.HomeUiState
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The list of feature cards on the home screen.
 *
 * Everything a card needs is already on [HomeUiState], so a block here is pure
 * wiring: read the toggle, name the feature for its test action, hand both
 * callbacks through.
 */
internal fun LazyListScope.homeFeatureCards(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
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
        )
    }

    item {
        VpnConnectedCard(
            isEnabled = uiState.stateOf(GlyphFeature.VPN_CONNECTED).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = { viewModel.setFeatureEnabled(GlyphFeature.VPN_CONNECTED, it) },
            onTest = { viewModel.testFeature(GlyphFeature.VPN_CONNECTED) },
            icon = rememberVectorPainter(image = Icons.Default.Shield),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
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
        )
    }

    item {
        // Placed last: the visualiser holds the Glyph strip for as long as
        // music plays, so it is the one feature that yields to all the others.
        //
        // The switch asks the ViewModel for the feature rather than running
        // the capture grants itself: the Quick Settings tile asks for the same
        // thing, and the two grants can only be answered by an Activity. See
        // `MainActivity.watchMusicCaptureRequests`.
        MusicVisualizerCard(
            isEnabled = uiState.stateOf(GlyphFeature.MUSIC_VISUALIZER).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = viewModel::toggleMusicVisualizer,
            onTestAnimation = { viewModel.testFeature(GlyphFeature.MUSIC_VISUALIZER) },
            icon = rememberVectorPainter(image = Icons.Default.LibraryMusic),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32,
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
    viewModel: HomeViewModel,
): (Boolean) -> Unit {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, enabled = true)
        } else {
            Toast.makeText(
                context,
                "Permission denied. PowerPeek cannot run without it.",
                Toast.LENGTH_LONG,
            ).show()
            // Put the toggle back where it was.
            viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, enabled = false)
        }
    }

    return { enabled ->
        if (!enabled) {
            viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, enabled = false)
        } else {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            ) == PackageManager.PERMISSION_GRANTED

            if (alreadyGranted) {
                viewModel.setFeatureEnabled(GlyphFeature.POWER_PEEK, enabled = true)
            } else {
                permissionLauncher.launch(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
            }
        }
    }
}
