package com.bleelblep.glyphsharge.glyph.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** A snapshot of the battery taken at the moment an animation starts. */
data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
)

/**
 * Reads the current battery level from the sticky `ACTION_BATTERY_CHANGED`
 * broadcast.
 *
 * Reading the sticky intent is a plain system call: it never fails the way a
 * runtime-registered receiver would, which is why the battery animations can
 * ask for a snapshot without registering anything first.
 */
object BatteryStateReader {

    /** Shown when the system broadcast carries no level, so a bar still renders. */
    private const val FALLBACK_PERCENT = 50

    fun read(context: Context): BatteryState {
        // Sticky system broadcast read: null receiver, no exporter flag required.
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryState(FALLBACK_PERCENT, isCharging = false)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        val isPluggedIn = (plugged == BatteryManager.BATTERY_PLUGGED_AC) ||
            (plugged == BatteryManager.BATTERY_PLUGGED_USB) ||
            (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) ||
            (plugged == BatteryManager.BATTERY_PLUGGED_DOCK)

        val isCharging = isPluggedIn || (status == BatteryManager.BATTERY_STATUS_CHARGING)

        val percentage = if ((level != -1) && (scale != -1)) {
            ((level * 100) / scale.toFloat()).toInt().coerceIn(0, 100)
        } else {
            FALLBACK_PERCENT
        }

        return BatteryState(percentage, isCharging)
    }
}
