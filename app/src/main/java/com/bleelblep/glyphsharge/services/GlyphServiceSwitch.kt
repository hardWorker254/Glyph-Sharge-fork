package com.bleelblep.glyphsharge.services

import android.util.Log
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.tiles.TileStateBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the master Glyph switch actually did.
 *
 * The session, the flag and the services behind them move together, and which
 * of the three failed is the difference between "off" and "asked to be off but
 * still on". A caller that only wanted to know the new state would still need
 * all three, so they travel together.
 */
data class GlyphSwitchOutcome(
    /** Where the session ended up, which is not always what was asked for. */
    val isActive: Boolean,
    /** `false` when the session was already in the requested state. */
    val changed: Boolean,
    /** Why the switch could not be applied, or `null` when it could. */
    val error: String? = null,
)

/**
 * Turns the master Glyph switch on and off.
 *
 * The one place that does it, because it is the one place that knows the order
 * is not free: the SDK needs a bound service before a session will open, the
 * flag has to be written before any service is asked to start, and the
 * services have to be started or stopped to match. [com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel]
 * and the Quick Settings tile both go through here, so a switch flipped from
 * the shade reaches the same state as one flipped in the app.
 *
 * Blocking by design — [GlyphManager.forceEnsureSession]
 * waits for the system service to bind — which is why every call moves off the
 * main thread here rather than making each caller remember to.
 */
@Singleton
class GlyphServiceSwitch @Inject constructor(
    private val glyphManager: GlyphManager,
    private val settingsRepository: SettingsRepository,
    private val serviceController: FeatureServiceController,
    private val tileStateBus: TileStateBus,
) {
    private companion object {
        const val TAG = "GlyphServiceSwitch"
    }

    /**
     * Applies the master switch and reconciles everything behind it.
     *
     * Never throws: the callers are a Compose click and a tile tap, and both
     * have nothing useful to do with an exception.
     */
    suspend fun apply(enabled: Boolean): GlyphSwitchOutcome = withContext(Dispatchers.IO) {
        val wasActive = glyphManager.isSessionActive
        val error = if (enabled) turnOn() else turnOff()

        // Announced even when the switch failed: after a failure the truth is
        // not what was asked for, and the tiles mirror the truth.
        tileStateBus.notifyChanged()

        GlyphSwitchOutcome(
            isActive = glyphManager.isSessionActive,
            changed = glyphManager.isSessionActive != wasActive,
            error = error,
        )
    }

    private fun turnOn(): String? = try {
        // Wait for the SDK rather than opening optimistically: a session
        // opened before the service is bound and the device registered throws,
        // and neither a tile nor a later launch is in a position to retry.
        if (!glyphManager.isSessionActive && !glyphManager.forceEnsureSession()) {
            "The Glyph service could not be reached"
        } else {
            // Flag first. Every feature service reads it to decide whether it
            // may run at all, and GlyphForegroundService stops itself when it
            // is not set.
            settingsRepository.saveGlyphServiceEnabled(true)
            // What keeps the app listed in Quick Settings "Active apps", and
            // what every other feature service is then free to start behind.
            serviceController.startPersistentGlyphService()
            serviceController.startAllEnabled()
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Turning the Glyph service on failed", e)
        e.message ?: "The Glyph service could not be turned on"
    }

    private fun turnOff(): String? {
        // The same order as [turnOn], read downwards: the flag goes first so
        // nothing on the way down restarts itself, then the session, then the
        // services that were drawing behind it.
        settingsRepository.saveGlyphServiceEnabled(false)
        runCatching { glyphManager.closeSession() }
            .onFailure { Log.w(TAG, "Closing the Glyph session failed", it) }
        serviceController.stopAll()
        return null
    }
}