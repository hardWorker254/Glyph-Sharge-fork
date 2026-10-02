package com.bleelblep.glyphsharge.tiles

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.bleelblep.glyphsharge.MainActivity
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.audio.PlaybackAudioSource
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.FeatureSpec
import com.bleelblep.glyphsharge.services.FeatureSpecs
import com.bleelblep.glyphsharge.services.GlyphServiceSwitch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The music visualiser, in the shade.
 *
 * Switching it off is the same thing the card's switch does. Switching it on
 * usually is too — the capture outlives the service, so a token granted a
 * moment ago is still held and the visualiser starts drawing straight away.
 *
 * Two things the card handles on screen and the tile has to answer for:
 *
 * - a visualiser with no Glyph session behind it draws nothing, so this turns
 *   the master switch on rather than refusing. A tile is a one-press request,
 *   and a switch that does nothing is not a switch;
 * - a `MediaProjection` token can only come back from an Activity, so the
 *   first tap on a phone that has no live token collapses the shade and lets
 *   [MainActivity] run the very same two grants the card runs. A tile cannot
 *   ask for the microphone either, which is the other half of why that hop
 *   exists.
 */
@AndroidEntryPoint
class MusicVisualizerTileService : TileService() {

    @Inject lateinit var glyphManager: GlyphManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var serviceController: FeatureServiceController
    @Inject lateinit var glyphServiceSwitch: GlyphServiceSwitch
    @Inject lateinit var audioSource: PlaybackAudioSource
    @Inject lateinit var tileStateBus: TileStateBus

    private val supported: Boolean by lazy { glyphManager.isNothingPhone() }

    /**
     * The same registry entry the service reads its run gate from, so the tile
     * and the service cannot answer "is this running?" differently.
     */
    private val spec: FeatureSpec = FeatureSpecs.of(GlyphFeature.MUSIC_VISUALIZER)

    /**
     * Owned here rather than borrowed from `lifecycleScope`: a `TileService` is
     * a plain [android.app.Service] and not a `LifecycleOwner`, and the toggle
     * is worth more than the convenience.
     */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /** One tap at a time, for the same reason as the Glyph tile. */
    private var switching = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    /**
     * Subscribed for as long as the service is bound, not for the listening
     * window. This is the tile that most needs it: almost every change to the
     * visualiser happens inside the app, while the shade is closed.
     */
    override fun onCreate() {
        super.onCreate()
        tileStateBus.subscribe(stateReceiver)
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onDestroy() {
        // Unsubscribing here rather than in onStopListening: the system can
        // destroy a tile that was never listening, and a receiver left behind
        // would hold this service alive for nothing.
        tileStateBus.unsubscribe(stateReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (!supported || switching) return

        // From what the tile shows, not from the flag alone: a feature left
        // switched on behind a master switch that is off is a setting, not a
        // running visualiser, and a tap on a tile that says "off" has to turn
        // things on.
        val enabling = !isRunning()
        // Off the main thread: turning the master service on waits for the
        // SDK to bind, and a tile that blocks the shade is a frozen phone.
        //
        // The try/catch is the point. `applyToggle` reaches
        // `startForegroundService`, which throws `ForegroundServiceStartNotAllowedException`
        // when the app is in the background without an exemption — and a
        // Quick Settings tap is exactly that. An uncaught exception out of a
        // `launch` on `Main` goes to the thread's default handler and takes
        // the process with it, which also left `switching` stuck `true` and the
        // tile dead for the rest of the process's life.
        switching = true
        scope.launch {
            try {
                if (enabling && !ensureGlyphService()) return@launch
                when {
                    !enabling -> applyToggle(enabled = false)
                    // Nothing the tile can do about this one: it needs an Activity.
                    !audioSource.hasToken -> openAppForConsent()
                    else -> applyToggle(enabled = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Music visualiser tile toggle failed", e)
                Toast.makeText(
                    this@MusicVisualizerTileService,
                    R.string.music_viz_notif_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                switching = false
            }
        }
    }

    /**
     * Makes sure there is a Glyph session behind the visualiser.
     *
     * Turns the master switch on rather than refusing, because a tile is a
     * one-press request: refusing it leaves the user with a switch that does
     * nothing, and the master switch is the thing the visualiser cannot run
     * without. The card still refuses — it can explain itself on screen, and
     * that is the right place for a rule the user may not have meant.
     *
     * @return `true` when the master service is on.
     */
    private suspend fun ensureGlyphService(): Boolean {
        if (settingsRepository.getGlyphServiceEnabled()) return true
        val outcome = glyphServiceSwitch.apply(enabled = true)
        outcome.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }
        return outcome.isActive
    }

    /**
     * Whether the visualiser is actually running, which is what the tile
     * claims and what a tap acts on.
     *
     * The feature switch and the master switch are two separate preferences,
     * and the service checks both before it draws anything. Turning the master
     * off therefore leaves a feature switched on and its service stopped — a
     * setting the app keeps for next time, not something working. Reading only
     * the feature flag is what put a lit tile over a strip that had gone dark.
     *
     * Asked of [FeatureSpec] rather than spelled out again. It used to be
     * `isMusicVizEnabled() && getGlyphServiceEnabled()` written longhand here,
     * which is the same pair `MusicVisualizerService` asks the registry about —
     * so a third condition added to the gate would have reached the service and
     * not the tile, and the tile would claim a capture the service had stopped.
     */
    private fun isRunning(): Boolean = spec.isRunnable(settingsRepository)

    /**
     * Persists the switch and brings the service in line with it, then tells
     * the tiles to look again — including this one, which is why [refresh]
     * is called here rather than left to the broadcast that is about to arrive.
     */
    private fun applyToggle(enabled: Boolean) {
        serviceController.apply(GlyphFeature.MUSIC_VISUALIZER, enabled)
        tileStateBus.notifyChanged()
        refresh()
    }

    /**
     * Collapses the shade and hands the request to the app.
     *
     * `startActivityAndCollapse` rather than a plain `startActivity`: a tile
     * has no background activity start privileges from Android 14 on, and the
     * panel staying open over the app it just opened is the visible half of
     * the same problem.
     */
    private fun openAppForConsent() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_ENABLE_MUSIC
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    @Suppress("DEPRECATION") // getTile() is not public API; getQsTile() is still the way in.
    private fun refresh() {
        val tile: Tile = qsTile ?: return

        if (!supported) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.stateDescription = getString(R.string.tile_unsupported_device)
            tile.updateTile()
            return
        }

        // [isRunning], not the feature flag: what the shade says is what the
        // phone is doing.
        tile.state = if (isRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.stateDescription = getString(
            when {
                !settingsRepository.isMusicVizEnabled() -> R.string.tile_state_off
                // Configured but blocked, which is worth saying out loud: a
                // tile that goes dark with no explanation is the same problem
                // as one that stays lit over nothing.
                !settingsRepository.getGlyphServiceEnabled() -> R.string.tile_music_blocked
                else -> R.string.tile_state_on
            },
        )
        tile.updateTile()
    }

    companion object {
        private const val TAG = "MusicVizTile"

        /**
         * Asks [MainActivity] for the visualiser's capture consent.
         *
         * Public because it is half of a hand-off across two components: the
         * tile sends it, the Activity has to recognise it.
         */
        const val ACTION_ENABLE_MUSIC = "com.bleelblep.glyphsharge.action.ENABLE_MUSIC_VISUALIZER"

        /** PendingIntent identity is (requestCode, action). */
        private const val REQUEST_CODE = 0
    }
}