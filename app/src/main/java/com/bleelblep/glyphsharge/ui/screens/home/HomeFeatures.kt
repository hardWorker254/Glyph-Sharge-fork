package com.bleelblep.glyphsharge.ui.screens.home

import com.bleelblep.glyphsharge.glyph.audio.PlaybackAudioSource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            iconSize = 32
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
            iconSize = 32
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
            iconSize = 32
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
            iconSize = 32
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
            iconSize = 32
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
            iconSize = 32
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
            iconSize = 32
        )
    }

    item {
        // Placed last: the visualiser holds the Glyph strip for as long as
        // music plays, so it is the one feature that yields to all the others.
        val context = LocalContext.current
        // A LazyListScope cannot inject for itself, so the capture singleton
        // comes from the ViewModel the list is already given.
        val audioSource = viewModel.musicCaptureSource
        val onMusicVizToggle = rememberMusicVizToggle(context, viewModel, audioSource)
        MusicVisualizerCard(
            isEnabled = uiState.stateOf(GlyphFeature.MUSIC_VISUALIZER).isEnabled,
            isServiceActive = glyphServiceEnabled,
            onEnabledChange = onMusicVizToggle,
            onTestAnimation = { viewModel.testFeature(GlyphFeature.MUSIC_VISUALIZER) },
            icon = rememberVectorPainter(image = Icons.Default.LibraryMusic),
            modifier = Modifier.fillMaxWidth(),
            iconSize = 32
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

/**
 * The visualiser reads other apps' audio through a `MediaProjection` token, so
 * switching it on needs two grants in order: the microphone permission first,
 * then the system capture confirmation. Asking at the moment the user flips the
 * toggle is the only point where the reason is obvious, and the toggle is put
 * back if they decline — the card must never claim to be on while the service
 * cannot capture anything.
 */
@Composable
private fun rememberMusicVizToggle(
    context: Context,
    viewModel: HomeViewModel,
    audioSource: PlaybackAudioSource,
): (Boolean) -> Unit {
    // Armed while the microphone prompt is up, so the capture prompt can follow
    // it without the user having to flip the toggle a second time.
    var pendingConsent by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Straight on to the second grant: the user has agreed to
            // recording, and the capture confirmation is a separate question
            // that deserves its own prompt rather than a silent assumption.
            val armed = pendingConsent
            pendingConsent = null
            if (armed != null) {
                armed.invoke()
            } else {
                // No toggle armed this prompt, so there is nothing to continue
                // from. Leave the feature off rather than starting it without
                // a token behind it.
                Toast.makeText(
                    context,
                    context.getString(R.string.music_viz_consent_denied),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.setFeatureEnabled(GlyphFeature.MUSIC_VISUALIZER, false)
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.music_viz_permission_denied),
                Toast.LENGTH_LONG
            ).show()
            viewModel.setFeatureEnabled(GlyphFeature.MUSIC_VISUALIZER, false)
        }
    }

    // The projection token can only come from an Activity result, and a
    // ViewModel cannot register for one — hence here rather than there.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onMusicCaptureResult(result.resultCode, result.data)
    }

    return toggle@ { enabled ->
        if (!enabled) {
            viewModel.setFeatureEnabled(GlyphFeature.MUSIC_VISUALIZER, false)
            return@toggle
        }

        // Saving the settings dialog arrives here with the feature already on:
        // the dialog's Enable button and the card's switch are the same
        // callback. Asking for a second projection then is confusing to answer
        // and destructive to answer — the live token is replaced, so a capture
        // that fails during the swap leaves the card green over a dead strip.
        //
        // Nothing has to be restarted for a settings change either: the service
        // re-reads the mode, the sensitivity and the screen-off rule on every
        // pass, so the new value is live within a slice.
        if (audioSource.isCapturing) return@toggle

        val askForConsent = { consentLauncher.launch(audioSource.consentIntent()) }
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            askForConsent()
        } else {
            pendingConsent = askForConsent
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
