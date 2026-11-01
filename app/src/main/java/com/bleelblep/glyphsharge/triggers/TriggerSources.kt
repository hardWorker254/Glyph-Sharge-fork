package com.bleelblep.glyphsharge.triggers

import android.content.Context
import android.content.Intent

/**
 * Builds a live [TriggerSource] for a [TriggerType].
 *
 * The one place that answers "how do I actually hear this event on Android",
 * so neither the rule host nor a `.servicelua` subscription has to know that
 * a charger is a `RECEIVER_EXPORTED` broadcast while the screen is a
 * `NOT_EXPORTED` one.
 *
 * A plain `object` rather than an injected class: the sources it builds take
 * the [Context] they need as an argument, so there is nothing to inject and no
 * state to hold. The host passes its own service context; a script host passes
 * the application context.
 */
object TriggerSources {

    /**
     * A source for [type], or `null` for a trigger whose dedicated listener has
     * not been migrated onto this library yet.
     *
     * [lowBatteryThreshold] is threaded through because a low-battery rule's
     * threshold is per-rule (see
     * [com.bleelblep.glyphsharge.rules.GlyphRule.lowBatteryThreshold]), and
     * [shakeThreshold] for the same reason on the shake trigger; every other
     * trigger ignores both, and [available] passes the defaults purely so it
     * can construct a source to prove one exists.
     *
     * Every type in [TriggerType] builds — the palette is complete, and with
     * the last bespoke event service gone (what `NfcGlyphService` open-coded
     * is now `NfcTrigger` plus the Activity's relay) the `?` on the return
     * type is kept for the shape, not for any current `null`.
     */
    fun create(
        context: Context,
        type: TriggerType,
        lowBatteryThreshold: Int = LowBatteryTrigger.DEFAULT_THRESHOLD,
        shakeThreshold: Float = com.bleelblep.glyphsharge.rules.GlyphRule.DEFAULT_SHAKE_THRESHOLD,
    ): TriggerSource? = when (type) {
        TriggerType.SCREEN_OFF -> BroadcastTrigger(
            context = context,
            type = type,
            actions = setOf(Intent.ACTION_SCREEN_OFF),
            // EXPORTED, and the reason is the same one the charger actions
            // have: a receiver that is registered as not-exported is dropped
            // before the broadcast reaches it — silently, the service sitting
            // registered and never firing.
            //
            // The hole this opens is not one: the action is protected, so only
            // the system can send it. Nothing else can reach this receiver
            // whatever the flag says.
            exported = true,
        ) { "screen off" }

        // Not a plain broadcast listener: the screen-on broadcast does not
        // reach a background process on every phone, and a rule that waits for
        // it waits forever with nothing to say so. `ScreenOnTrigger` asks the
        // platform's own state as well, which always answers.
        TriggerType.SCREEN_ON -> ScreenOnTrigger(context, type)

        TriggerType.POWER_CONNECTED -> BroadcastTrigger(
            context = context,
            type = type,
            actions = setOf(Intent.ACTION_POWER_CONNECTED),
            // From the system power stack (another UID): must be EXPORTED or the
            // platform drops it before it reaches us, silently.
            exported = true,
        ) { "power connected" }

        TriggerType.POWER_DISCONNECTED -> BroadcastTrigger(
            context = context,
            type = type,
            actions = setOf(Intent.ACTION_POWER_DISCONNECTED),
            exported = true,
        ) { "power disconnected" }

        TriggerType.POWER_CHANGED -> BroadcastTrigger(
            context = context,
            type = type,
            actions = setOf(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED),
            exported = true,
        ) { intent ->
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) "power connected"
            else "power disconnected"
        }

        // `USER_PRESENT` alone is not enough — see `UnlockTrigger` for the
        // screen-on fallback that covers phones which never send one.
        TriggerType.UNLOCK -> UnlockTrigger(context, type)

        // Android has no broadcast for a VPN coming up, so this watches the
        // VPN transport through a NetworkCallback with an edge-latch; see
        // `VpnConnectedTrigger`.
        TriggerType.VPN_CONNECTED -> VpnConnectedTrigger(context, type)

        // Threshold is per-rule, so it is handed in rather than read from a
        // repository; see `LowBatteryTrigger` for the edge latch.
        TriggerType.LOW_BATTERY -> LowBatteryTrigger(context, type, lowBatteryThreshold)

        // The accelerometer gate, extracted from `PowerPeekService` — same
        // story: the jolt threshold is per-rule data.
        TriggerType.SHAKE -> ShakeTrigger(context, type, shakeThreshold)

        // Two channels in one: the HCE payment broadcast, and the tags the
        // Activity forwards through `NfcEventRelay`; see `NfcTrigger`.
        TriggerType.NFC -> NfcTrigger(context, type)
    }

    /**
     * Every type this factory can currently build.
     *
     * The palette a UI should offer *today* — the `create` result filtered of
     * its `null`s, so a rule can only be built around a trigger that will
     * actually fire.
     */
    fun available(context: Context): List<TriggerType> =
        TriggerType.entries.filter { create(context, it) != null }
}
