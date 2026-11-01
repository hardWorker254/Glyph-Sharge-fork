package com.bleelblep.glyphsharge.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.utils.LoggingManager

/**
 * Watches the platform's own answer to "is the device interactive".
 *
 * `ACTION_SCREEN_ON` and `ACTION_SCREEN_OFF` are the broadcasts that report
 * that state; asking for it directly is the same fact with one difference —
 * a state can be *asked* for, a broadcast can only be *waited* for.
 *
 * **Why this exists.** The screen-on broadcast reaches a receiver registered by
 * a background process only when the platform decides to deliver it, and on
 * the phone this app was written for it does not: the rule host is a foreground
 * *service*, not a foreground app, and the event that matters most — the screen
 * coming on — is the one that never arrived. Nothing said so. The receiver was
 * registered, the rule was attached, the host was running, and the event
 * simply never came, so a rule that waited for it waited forever with no
 * error anywhere.
 *
 * **What it costs.** A tick, which sounds like a poll and is not one:
 *
 *  * while the screen is on, the CPU is awake anyway, so a tick costs nothing;
 *  * while the screen is off and the device is asleep, the tick does not run
 *    at all — the CPU is off — and it runs the moment the device wakes, which
 *    is exactly when the screen comes on.
 *
 * So the tick is a wake-up detector wearing a poll's clothes.
 *
 * **Why it also listens for `ACTION_SCREEN_OFF`.** The tick is the only thing
 * that would otherwise notice the screen going *off*, and the device can
 * suspend before the next tick. The latch then stays at "interactive" for the
 * whole sleep, and the screen coming on is no longer an edge: the tick reads
 * `true`, the latch already says `true`, and the rule that waited for the
 * screen never fires. The screen-off broadcast is delivered before the
 * suspend, so it is what clears the latch; the tick is then free to be the
 * wake-up detector it was meant to be.
 *
 * **One event per transition.** The watcher remembers the last state it read
 * and reports only the edge from "not interactive" to "interactive". A caller
 * that also listens for the broadcast gets one event for one screen-on however
 * many paths noticed it: the broadcast path asks the watcher to look, and the
 * watcher says "already interactive" the second time.
 */
class InteractiveStateWatcher(
    context: Context,
    private val onBecameInteractive: () -> Unit,
) {

    companion object {
        private const val TAG = "Trigger"

        /**
         * How often the state is read while the watcher runs.
         *
         * A second, because it is not the interval that decides when the event
         * is noticed: while the device is asleep the tick cannot run at all,
         * and it runs the moment the device wakes.
         */
        const val POLL_MS = 1_000L
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val powerManager: PowerManager? =
        appContext.getSystemService(PowerManager::class.java)

    /** The last state that was read. `true` means "the device is interactive". */
    private var interactive: Boolean = readInteractive()

    /** Whether the tick is scheduled. */
    private var running = false

    /** The screen-off receiver, while [start] has it registered. */
    private var receiver: BroadcastReceiver? = null

    /**
     * Clears the latch before the device suspends.
     *
     * `ACTION_SCREEN_OFF` is protected, so only the system can send it; the
     * exported registration opens no hole. It has to be exported all the same:
     * a not-exported receiver is dropped before the broadcast reaches it.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                interactive = false
                LoggingManager.log(TAG, "screen_off: latch cleared")
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            checkNow()
            handler.postDelayed(this, POLL_MS)
        }
    }

    /**
     * Reads the state and reports a transition into "interactive".
     *
     * Called by the tick, and by a caller that has just been told the screen
     * came on: the broadcast is the fast path, and asking the state is what
     * turns it into the same single event the tick would have produced.
     */
    fun checkNow() {
        val now = readInteractive()
        val becameInteractive = now && !interactive
        interactive = now
        if (becameInteractive) onBecameInteractive()
    }

    /**
     * Starts watching.
     *
     * The state is read once here, so a watcher that starts while the screen
     * is already on does not report a transition that never happened.
     */
    fun start() {
        if (running) return
        running = true

        // Registered before the state is read and before the first tick is
        // posted: a screen that goes off in either window must still clear the
        // latch. Registering after the read leaves a gap in which the
        // screen-off broadcast can be missed, and the stale latch is exactly
        // the bug this receiver exists to prevent.
        val r = screenOffReceiver
        receiver = r
        ContextCompat.registerReceiver(
            appContext,
            r,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_EXPORTED,
        )

        interactive = readInteractive()
        LoggingManager.log(TAG, "watcher started, interactive=$interactive")
        handler.postDelayed(tick, POLL_MS)
    }

    /** Stops watching. Safe to call on a watcher that was never started. */
    fun stop() {
        val wasRunning = running
        running = false
        handler.removeCallbacks(tick)
        receiver?.let { r ->
            receiver = null
            runCatching { appContext.unregisterReceiver(r) }
        }
        if (wasRunning) LoggingManager.log(TAG, "watcher stopped")
    }

    /**
     * The platform's answer, read defensively.
     *
     * `isInteractive` talks to the power manager, and a phone that is in the
     * middle of a suspend can refuse the question rather than answer it. A
     * watcher that threw would take the host's trigger with it, so a refusal
     * is read as "not interactive" — the state before the screen comes on,
     * which is the state that can only cause a late event, never a missing one.
     */
    private fun readInteractive(): Boolean = try {
        powerManager?.isInteractive == true
    } catch (e: Exception) {
        false
    }
}
