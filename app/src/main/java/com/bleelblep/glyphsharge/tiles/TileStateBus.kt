package com.bleelblep.glyphsharge.tiles

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one signal that a switch a tile mirrors has moved.
 *
 * A tile reads the same preferences the cards do, but nothing makes it look
 * again on its own: the system binds it, it updates once, and then it is left
 * alone. Without this, switching the visualiser inside the app — which is
 * where every music permission is granted — would leave the tile claiming the
 * opposite, and the tile is what the user is looking at when they come back
 * to the shade.
 *
 * Two channels, because neither one alone is enough:
 *
 * - the broadcast, for a tile that is bound and listening right now;
 * - [TileService.requestListeningState], for one that is not. Asking the
 *   system to start the listening state is what actually produces an
 *   `onStartListening()`, and that callback is the only moment the platform
 *   guarantees a tile will read what changed.
 *
 * Subscribing is therefore a question for the *service* lifetime, not the
 * listening window: a state change lands while the shade is closed far more
 * often than while it is open, and a receiver that is only awake while the
 * user is looking is awake for the one case that needs no help.
 */
@Singleton
class TileStateBus @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Registers [receiver] for state changes, until [unsubscribe].
     *
     * `RECEIVER_NOT_EXPORTED` is the point: this is a private signal between
     * our own components, and an exported receiver would let any app on the
     * phone poke at our tiles.
     */
    fun subscribe(receiver: BroadcastReceiver) {
        context.registerReceiver(
            receiver,
            IntentFilter(ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unsubscribe(receiver: BroadcastReceiver) {
        // Unregistering a receiver that was never registered throws, and the
        // teardown path is exactly where a crash would arrive from.
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * Announces that every tile should read its state again.
     *
     * Cheap and unconditional on purpose: a tile that misses a signal shows a
     * stale switch, which is worse than one that looks again and finds
     * nothing. A tile the user has not added simply answers nothing.
     */
    fun notifyChanged() {
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))
        tiles().forEach { component ->
            runCatching { TileService.requestListeningState(context, component) }
        }
    }

    private fun tiles(): List<ComponentName> = listOf(
        ComponentName(context, GlyphServiceTileService::class.java),
        ComponentName(context, MusicVisualizerTileService::class.java),
    )

    companion object {
        const val ACTION = "com.bleelblep.glyphsharge.ACTION_TILE_STATE_CHANGED"
    }
}