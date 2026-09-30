package com.bleelblep.glyphsharge.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.services.GlyphServiceSwitch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The master Glyph switch, in the shade.
 *
 * Everything it does goes through [GlyphServiceSwitch], the same path the home
 * screen's switch takes, so the tile cannot open a session the app does not
 * know about or leave a feature service running that the app believes is off.
 *
 * Bound from [TileStateBus] rather than only refreshed when the shade opens:
 * a tile that only updates on open would sit there claiming the old state for
 * as long as the user left the panel down, which is the one thing a switch
 * must never do.
 */
@AndroidEntryPoint
class GlyphServiceTileService : TileService() {

    @Inject lateinit var glyphManager: GlyphManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphServiceSwitch: GlyphServiceSwitch
    @Inject lateinit var tileStateBus: TileStateBus

    /**
     * Asking the SDK whether this is a Nothing phone binds it, so the answer
     * is asked once: it is read on every refresh, and a refresh happens every
     * time the shade opens.
     */
    private val supported: Boolean by lazy { glyphManager.isNothingPhone() }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    /**
     * Owned here rather than borrowed from `lifecycleScope`: a `TileService` is
     * a plain [android.app.Service] and not a `LifecycleOwner`, and the toggle
     * is worth more than the convenience.
     */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /**
     * One tap at a time. Opening a session waits for the system service to
     * bind — up to two seconds — and a second tap landing inside that window
     * would ask for the state the first one is still deciding.
     */
    private var switching = false

    /**
     * Subscribed for as long as the service is bound, not for the listening
     * window: the master switch moves most often from somewhere else, and a
     * receiver that is only awake while the user is looking at the shade is
     * awake for the one case that needs no help.
     */
    override fun onCreate() {
        super.onCreate()
        tileStateBus.subscribe(stateReceiver)
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Safe to perform while locked: it is the user's own switch, and it
        // reveals nothing — which is why this does not ask to be unlocked.
        if (!supported || switching) return

        switching = true
        scope.launch {
            val outcome = glyphServiceSwitch.apply(!settingsRepository.getGlyphServiceEnabled())
            switching = false
            outcome.error?.let { Toast.makeText(this@GlyphServiceTileService, it, Toast.LENGTH_LONG).show() }
            refresh()
        }
    }

    override fun onDestroy() {
        // Unsubscribing here rather than in onStopListening: the system can
        // destroy a tile that was never listening, and a receiver left behind
        // would hold this service alive for nothing.
        tileStateBus.unsubscribe(stateReceiver)
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION") // getTile() is not public API; getQsTile() is still the way in.
    private fun refresh() {
        val tile: Tile = qsTile ?: return

        if (!supported) {
            // Not something that can be turned off and on again, so not
            // something to offer a switch for.
            tile.state = Tile.STATE_UNAVAILABLE
            tile.stateDescription = getString(R.string.tile_unsupported_device)
            tile.updateTile()
            return
        }

        // The flag, not the session: the session is closed whenever the app
        // stops with the master switch off, and a tile that read it would go
        // inactive behind a switch that is very much still on.
        val enabled = settingsRepository.getGlyphServiceEnabled()
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.stateDescription = getString(
            if (enabled) R.string.tile_state_on else R.string.tile_state_off
        )
        tile.updateTile()
    }
}