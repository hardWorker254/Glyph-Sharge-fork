package com.bleelblep.glyphsharge.triggers

import android.content.Context
import androidx.annotation.StringRes
import com.bleelblep.glyphsharge.R

/**
 * The vocabulary of things that can make a rule fire.
 *
 * This is the palette the constructor's "simple mode" offers and the set of
 * names a `.servicelua` script subscribes to, so its [id] is a contract: it is
 * written into a saved rule and into a script's `glyph.on("...")` call, and
 * must keep meaning the same event across releases.
 *
 * [labelRes] is the opposite kind of field — the *display* name, free to be
 * reworded or translated. The constructor chips and rule rows show it so the
 * user reads "VPN connected", never the raw `vpn_connected` id.
 *
 * A trigger is deliberately *just* the event. Whether the rule reacts, which
 * animation it plays, and any cooldown or threshold live on the rule, not here
 * — so the same `SCREEN_OFF` can drive a stock animation, a user's Lua logic,
 * or nothing at all.
 */
enum class TriggerType(val id: String, @StringRes val labelRes: Int) {

    /** The screen turned off. */
    SCREEN_OFF("screen_off", R.string.trigger_screen_off),

    /** The screen turned on. */
    SCREEN_ON("screen_on", R.string.trigger_screen_on),

    /**
     * The user got past the keyguard.
     *
     * A composite on purpose: `USER_PRESENT` only fires when there is a
     * keyguard, so this also arms a short screen-on fallback for phones that
     * never send one. See `UnlockTrigger`.
     */
    UNLOCK("unlock", R.string.trigger_unlock),

    /** A charger was plugged in. */
    POWER_CONNECTED("power_connected", R.string.trigger_power_connected),

    /** A charger was unplugged. */
    POWER_DISCONNECTED("power_disconnected", R.string.trigger_power_disconnected),

    /** Either edge of the charger — plugged *or* unplugged. */
    POWER_CHANGED("power_changed", R.string.trigger_power_changed),

    /** Battery fell to or below a rule-supplied threshold while not charging. */
    LOW_BATTERY("low_battery", R.string.trigger_low_battery),

    /** An NFC tag was read or a contactless payment went through. */
    NFC("nfc", R.string.trigger_nfc),

    /** A VPN transport came up (once per connection). */
    VPN_CONNECTED("vpn_connected", R.string.trigger_vpn_connected),

    /**
     * The phone, lying flat, was shaken.
     *
     * Like [UNLOCK] this is more than one platform signal folded together —
     * the accelerometer plus the "is it resting on a table" gate. See
     * `ShakeTrigger`.
     */
    SHAKE("shake", R.string.trigger_shake);

    companion object {
        /** The trigger named [id], or `null` when this build does not know it. */
        fun fromId(id: String): TriggerType? =
            entries.firstOrNull { it.id == id }
    }
}

/**
 * One firing of a [TriggerType], with enough context to log or trace it.
 *
 * @param label a short human description of *this* firing — "screen off",
 *   "payment (AID=…)" — so a busy-strip skip or a `RunTrace` line can be read
 *   back to the exact event, not just its type.
 */
data class TriggerEvent(
    val type: TriggerType,
    val label: String = type.id,
)

/**
 * A live listener for one [TriggerType].
 *
 * The abstraction the eight bespoke services used to *be*: each had its own
 * `BroadcastReceiver` / `NetworkCallback` / `SensorEventListener` open-coded
 * inside its own class. Pulling them behind this interface is what lets one
 * generic host attach any trigger a rule needs, and what lets a `.servicelua`
 * script subscribe to `SCREEN_OFF` without the app knowing in advance which
 * rules exist.
 *
 * Lifecycle is the caller's: [attach] starts listening, [detach] stops it. A
 * source is single-use-per-attach — attach, detach, attach again is fine;
 * two concurrent attaches are not a state this has to support because the host
 * owns exactly one attachment per enabled rule.
 */
interface TriggerSource {

    /** Starts listening; [onEvent] is invoked on the main thread for each firing. */
    fun attach(onEvent: (TriggerEvent) -> Unit)

    /** Stops listening. Safe to call when not attached, or twice. */
    fun detach()
}
