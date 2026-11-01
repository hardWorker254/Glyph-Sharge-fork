package com.bleelblep.glyphsharge.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * The composite "the user got past the keyguard" listener.
 *
 * `ACTION_USER_PRESENT` is the honest signal — it means the keyguard was
 * actually dismissed — but it is only sent when there *is* a keyguard. On a
 * phone with no lock screen, or when the keyguard never comes up, the screen
 * simply turns on and that broadcast never arrives, so a listener waiting on it
 * alone does nothing, with no error and no way to tell why.
 *
 * So this listens for both, exactly as `PulseLockService` does:
 *
 *  * **`ACTION_USER_PRESENT`** fires immediately — a real unlock.
 *  * **The device becoming interactive** arms a short fallback that fires only
 *    if no real unlock followed. On a phone known never to send `USER_PRESENT`
 *    the window is just long enough for the screen-on animation to settle;
 *    otherwise it is a longer grace so someone who has to swipe or type is not
 *    made to watch it twice before the app learns this device sends the real
 *    thing.
 *
 *    The second signal is the platform's *state* rather than the screen-on
 *    broadcast, for the reason [ScreenOnTrigger] gives: the broadcast does not
 *    reach a background process on every phone, and this fallback is exactly
 *    the path that would have to work when it does not.
 *
 * Two deliberate differences from the feature it was extracted from, both
 * because this is a *palette* trigger reused by any rule rather than one
 * feature's private receiver:
 *
 *  * **The learn latch is in memory, not persisted.** `PulseLockService` learns
 *    once ever, across restarts, because it is the only thing that ever needs to
 *    know. Here the latch lives as long as this source is attached, which is a
 *    rule's lifetime on the host — so the first fire after a reload pays the
 *    grace and later ones are immediate. [UnlockTrigger] takes no repository,
 *    which is what lets it run from a service *or* a `.servicelua` script host.
 *  * **One fire per wake.** The extracted version can fire on the fallback and
 *    then again when a slow `USER_PRESENT` finally lands. For a single feature
 *    that is a rare edge; for a rule the user points at "unlock" it would be a
 *    visible double-flash, so a [firedThisCycle] latch makes the cycle fire once.
 *
 * [firedThisCycle] resets on the next [Intent.ACTION_SCREEN_ON], so each wake
 * starts clean.
 */
class UnlockTrigger(
    context: Context,
    private val type: TriggerType,
) : TriggerSource {

    companion object {
        /** Grace for a device that *may* send `USER_PRESENT`: long enough to swipe or type. */
        private const val GRACE_MS = 1_500L

        /** Settle for a device known never to send `USER_PRESENT`. */
        private const val SETTLE_MS = 150L
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null

    /**
     * The "the screen came on" signal this trigger's fallback is built on.
     *
     * A watcher rather than the broadcast, for the reason [ScreenOnTrigger]
     * gives: the screen-on broadcast does not reach a background process on
     * every phone, and the fallback is the path that has to work when it does
     * not.
     */
    private val interactiveWatcher = InteractiveStateWatcher(appContext) {
        onScreenOn()
    }

    /** The event sink, kept for the watcher, which outlives the receiver. */
    private var onEvent: ((TriggerEvent) -> Unit)? = null

    /** Whether this device has sent `USER_PRESENT` while attached — the learn latch. */
    private var seenUserPresent = false

    /** Whether the current wake has already fired, so a cycle fires once. */
    private var firedThisCycle = false

    private var fallback: Runnable? = null

    private fun cancelFallback() {
        fallback?.let { handler.removeCallbacks(it) }
        fallback = null
    }

    override fun attach(onEvent: (TriggerEvent) -> Unit) {
        if (receiver != null) return
        this.onEvent = onEvent

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        // The screen came on before the keyguard was dismissed,
                        // but the watcher that reports the screen may notice it
                        // only now — a tick late. Asking it to look *here*, before
                        // the unlock is handled, keeps the wake in one piece:
                        // otherwise the notice arrives after the unlock and
                        // starts a second cycle, and the rule fires twice for
                        // one wake.
                        interactiveWatcher.checkNow()

                        // A real unlock is the better signal: remember this
                        // device sends one, drop any pending fallback, and fire
                        // unless the fallback already did for this wake.
                        seenUserPresent = true
                        cancelFallback()
                        if (!firedThisCycle) {
                            firedThisCycle = true
                            onEvent?.invoke(TriggerEvent(type, "unlock"))
                        }
                    }

                }
            }
        }
        receiver = r

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            appContext,
            r,
            filter,
            // EXPORTED, and the reason is the same one the charger actions
            // have: a receiver registered as not-exported is dropped before the
            // broadcast reaches it — silently, the trigger sitting registered
            // and never firing.
            //
            // The hole this opens is not one: the action is protected, so only
            // the system can send it.
            ContextCompat.RECEIVER_EXPORTED,
        )
        interactiveWatcher.start()
    }

    /**
     * A wake: the screen came on, and a real unlock may or may not follow.
     *
     * Each wake is a fresh cycle. A device that has never sent USER_PRESENT is
     * not going to start now; waiting out the full grace only adds latency to
     * the moment the user is watching for. The first unlock pays the grace,
     * because that is the only way to find out — later ones are immediate.
     */
    private fun onScreenOn() {
        firedThisCycle = false
        cancelFallback()

        val expectUnlock = !seenUserPresent
        val run = Runnable {
            fallback = null
            if (!firedThisCycle) {
                firedThisCycle = true
                onEvent?.invoke(
                    TriggerEvent(
                        type,
                        if (expectUnlock) "screen-on, no USER_PRESENT within grace"
                        else "screen-on, device sends no USER_PRESENT",
                    ),
                )
            }
        }
        fallback = run
        handler.postDelayed(run, if (expectUnlock) GRACE_MS else SETTLE_MS)
    }

    override fun detach() {
        cancelFallback()
        interactiveWatcher.stop()
        onEvent = null
        seenUserPresent = false
        firedThisCycle = false
        val r = receiver ?: return
        receiver = null
        runCatching { appContext.unregisterReceiver(r) }
    }
}
