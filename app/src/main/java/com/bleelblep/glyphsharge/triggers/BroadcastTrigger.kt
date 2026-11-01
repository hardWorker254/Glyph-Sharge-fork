package com.bleelblep.glyphsharge.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.utils.LoggingManager

/**
 * A [TriggerSource] backed by one or more system broadcasts.
 *
 * The whole of the screen-off, screen-on and charger triggers — and the base
 * the unlock trigger extends — used to be a hand-written `BroadcastReceiver`
 * inside each feature service. This is that receiver, written once and
 * parameterised by the actions it answers and how each firing is labelled.
 *
 * @param exported whether the platform may deliver broadcasts sent by other
 *   apps. Every action this listens for is **protected** — only the system can
 *   send it — so `true` opens no hole: nothing but the system can reach this
 *   receiver whatever the flag says.
 *
 *   It is `true` for all of them, and the reason is the one the charger
 *   actions were given first: a receiver registered as not-exported is
 *   dropped before the broadcast reaches it, silently, the trigger sitting
 *   registered and never firing. The screen actions came from the framework
 *   and were registered as not-exported on the strength of "the system sends
 *   them, so it does not matter" — and the rule that waits for the screen to
 *   come on never fired.
 */
class BroadcastTrigger(
    context: Context,
    private val type: TriggerType,
    private val actions: Set<String>,
    private val exported: Boolean,
    private val label: (Intent?) -> String,
) : TriggerSource {

    private val appContext = context.applicationContext
    private var receiver: BroadcastReceiver? = null

    private companion object {
        const val TAG = "Trigger"
    }

    override fun attach(onEvent: (TriggerEvent) -> Unit) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action in actions) {
                    // Into the app's own log file, not only into logcat: on a
                    // phone nobody can read logcat from, "the broadcast never
                    // arrived" and "the broadcast arrived and was ignored" look
                    // exactly the same from the outside.
                    LoggingManager.log(TAG, "${type.id}: received ${intent?.action}")
                    onEvent(TriggerEvent(type, label(intent)))
                }
            }
        }
        receiver = r

        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        ContextCompat.registerReceiver(
            appContext,
            r,
            filter,
            if (exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        LoggingManager.log(TAG, "${type.id}: listening for ${actions.joinToString()}")
    }

    override fun detach() {
        val r = receiver ?: return
        receiver = null
        runCatching { appContext.unregisterReceiver(r) }
    }
}
