package com.bleelblep.glyphsharge.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.utils.LoggingManager

/**
 * "The device became interactive" — the screen came on.
 *
 * Two paths to the same event, because the platform offers only one of them
 * and it does not always arrive:
 *
 *  * **`ACTION_SCREEN_ON`** — the broadcast. When it comes, the event is
 *    immediate, and the watcher is asked to look so that the broadcast and the
 *    watcher agree on whether the screen is on *now*.
 *  * **[InteractiveStateWatcher]** — the state. `PowerManager.isInteractive()`
 *    is the fact the broadcast is reporting, and it can be asked for rather
 *    than waited on. This is the path that works when the broadcast does not
 *    reach a background process, which on the phone this was written for is
 *    every time.
 *
 * The two are not two events: the watcher reports only the edge from "not
 * interactive" to "interactive", so whichever path notices first fires once
 * and the other sees nothing to report.
 *
 * The event is deliberately about the *device* becoming interactive rather
 * than about the screen lamp: that is what the platform itself reports, and
 * the two are the same thing on every phone this app runs on.
 */
class ScreenOnTrigger(
    context: Context,
    private val type: TriggerType,
) : TriggerSource {

    private val appContext = context.applicationContext
    private val watcher = InteractiveStateWatcher(appContext) {
        val listener = onEvent
        LoggingManager.log(
            TAG,
            "${type.id}: the device became interactive" +
                if (listener == null) " (no listener attached)" else "",
        )
        listener?.invoke(TriggerEvent(type, "screen on"))
    }
    private var receiver: BroadcastReceiver? = null
    private var onEvent: ((TriggerEvent) -> Unit)? = null

    private companion object {
        const val TAG = "Trigger"
    }

    override fun attach(onEvent: (TriggerEvent) -> Unit) {
        if (receiver != null) return
        this.onEvent = onEvent

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_ON) return
                // The fast path: the platform says the screen came on, and the
                // watcher turns that into the same single event the tick would
                // have produced a moment later.
                watcher.checkNow()
            }
        }
        receiver = r

        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(
            appContext,
            r,
            filter,
            // EXPORTED, and the reason is the same one the charger actions
            // have: a receiver registered as not-exported is dropped before the
            // broadcast reaches it — silently, the trigger sitting registered
            // and never firing, with nothing to say so.
            //
            // The hole this opens is not one: the action is protected, so only
            // the system can send it.
            ContextCompat.RECEIVER_EXPORTED,
        )
        watcher.start()
    }

    override fun detach() {
        watcher.stop()
        onEvent = null
        val r = receiver ?: return
        receiver = null
        runCatching { appContext.unregisterReceiver(r) }
    }
}
