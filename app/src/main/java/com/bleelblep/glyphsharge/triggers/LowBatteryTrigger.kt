package com.bleelblep.glyphsharge.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/**
 * Fires once when the battery falls to or below a threshold while *not*
 * charging, then waits for it to recover before it can fire again.
 *
 * The listener is the one `LowBatteryAlertService` open-coded, reduced to the
 * event itself: the feature's own enable-check, its audio and its raw cap live
 * elsewhere (on the rule and the shared player), so all that remains here is
 * "read the battery broadcast, apply the threshold, latch an edge".
 *
 * ### Why an edge latch
 *
 * `ACTION_BATTERY_CHANGED` is chatty — it arrives on temperature and voltage
 * shifts, not just percentage steps. Without [triggered], a phone sitting at 19%
 * would fire on every one of them. So the first crossing fires, and [triggered]
 * holds until the battery climbs back to [threshold] + [RECOVER_MARGIN] or a
 * charger is plugged in — the same hysteresis the feature used, so a reading
 * hovering exactly on the threshold cannot machine-gun the strip.
 *
 * The threshold comes in as a constructor argument rather than read from a
 * repository: it is [com.bleelblep.glyphsharge.rules.GlyphRule.lowBatteryThreshold],
 * per-rule, which is what lets one rule warn at 30% and another at 10% off the
 * same listener. Keeping it a plain argument is also what lets this run from a
 * service or a `.servicelua` script host with no settings to inject.
 *
 * `ACTION_BATTERY_CHANGED` comes from the system power stack — the same place
 * the charger actions do — so the receiver must be `RECEIVER_EXPORTED` or the
 * platform drops it before it reaches us, silently.
 */
class LowBatteryTrigger(
    context: Context,
    private val type: TriggerType,
    private val threshold: Int,
) : TriggerSource {

    companion object {
        /** How far above the threshold the battery must recover before re-arming. */
        private const val RECOVER_MARGIN = 5

        /**
         * The battery point a low-battery rule falls back to when it names no
         * threshold of its own — mirrored by
         * [com.bleelblep.glyphsharge.rules.GlyphRule.DEFAULT_LOW_BATTERY_THRESHOLD],
         * which is what a newly-created rule actually stores.
         */
        const val DEFAULT_THRESHOLD = 20
    }

    private val appContext = context.applicationContext
    private var receiver: BroadcastReceiver? = null

    /** Edge latch: holds after a firing until the battery recovers or charges. */
    private var triggered = false

    override fun attach(onEvent: (TriggerEvent) -> Unit) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                if (charging) {
                    // A charging phone is not low on power; re-arm for later.
                    triggered = false
                    return
                }

                if (!triggered && pct <= threshold) {
                    triggered = true
                    onEvent(TriggerEvent(type, "battery $pct% (<= $threshold%)"))
                }

                // Reset once the battery recovers — the hysteresis that stops a
                // reading hovering on the threshold from refiring every broadcast.
                if (pct >= threshold + RECOVER_MARGIN) triggered = false
            }
        }
        receiver = r

        ContextCompat.registerReceiver(
            appContext,
            r,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun detach() {
        triggered = false
        val r = receiver ?: return
        receiver = null
        runCatching { appContext.unregisterReceiver(r) }
    }
}
