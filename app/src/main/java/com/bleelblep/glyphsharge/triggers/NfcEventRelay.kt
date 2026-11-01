package com.bleelblep.glyphsharge.triggers

import android.nfc.NfcAdapter

/**
 * The in-process hand-off of forwarded NFC tag intents.
 *
 * Android delivers tag discoveries to an **Activity** through the
 * foreground-dispatch system and nowhere else: the Activity is the only
 * participant that can ask the adapter for them. The rule host is a service,
 * so what the Activity saw has to reach it another way — this relay, in the
 * one process both live in. The Activity publishes every intent it is handed;
 * [publish] filters, so the list of tag actions exists here once rather than
 * at every call site.
 *
 * A plain `object` with a copy-on-write listener list: publishes arrive on the
 * main thread from `onNewIntent`, subscriptions from a host reload, and a
 * listener that unsubscribes itself *during* a publish (a rule being torn down
 * while its event is in flight) must not corrupt the iteration — hence the
 * copied list rather than a synchronized set iterated in place.
 *
 * Deliberately not a Hilt singleton: there is no state worth injecting and no
 * lifecycle to manage, and both ends — `MainActivity` and [NfcTrigger] — are
 * in the same process by construction.
 */
object NfcEventRelay {

    /** Tag actions worth relaying; any other intent is another feature's business. */
    private val TAG_ACTIONS = setOf(
        NfcAdapter.ACTION_TAG_DISCOVERED,
        NfcAdapter.ACTION_NDEF_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
    )

    @Volatile
    private var listeners: List<(String) -> Unit> = emptyList()

    /** Hands [action] to every attached listener; silently ignored for non-tag actions. */
    fun publish(action: String?) {
        if (action == null || action !in TAG_ACTIONS) return
        listeners.forEach { it(action) }
    }

    /** Registers [listener]; the returned function unsubscribes it. */
    fun subscribe(listener: (String) -> Unit): () -> Unit {
        listeners = listeners + listener
        return { listeners = listeners - listener }
    }
}
