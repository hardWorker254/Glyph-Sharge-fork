package com.bleelblep.glyphsharge.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import androidx.core.content.ContextCompat

/**
 * The composite NFC listener: a contactless payment *or* a tag the Activity read.
 *
 * Android has two NFC channels, and this trigger takes both:
 *
 *  * **`ACTION_TRANSACTION_DETECTED`** — broadcast by the system NFC stack
 *    whenever the controller processes a contactless transaction (Google Pay,
 *    tap-to-pay). It arrives as a broadcast, so this trigger registers its own
 *    receiver — EXPORTED, because the sender (`com.android.nfc`) is another UID
 *    and a NOT_EXPORTED receiver is dropped before it reaches us, silently.
 *  * **Tag discoveries** — delivered to an Activity via foreground dispatch and
 *    nowhere else. The Activity publishes what it was handed to
 *    [NfcEventRelay]; this trigger subscribes. The service itself cannot ask
 *    the adapter for tags, and does not need to: the participant that must be
 *    on screen already is, and the relay is the rest of the path.
 *
 * Extracted from `NfcGlyphService` together with the relay, the same way every
 * other palette trigger was extracted from the service that open-coded it —
 * and like the others, it takes no repository, which is what lets it run from
 * the rule host *or* from a `.servicelua` subscription.
 */
class NfcTrigger(
    context: Context,
    private val type: TriggerType,
) : TriggerSource {

    private val appContext = context.applicationContext
    private var receiver: BroadcastReceiver? = null
    private var unsubscribeRelay: (() -> Unit)? = null

    override fun attach(onEvent: (TriggerEvent) -> Unit) {
        if (receiver != null) return

        // The Activity's half: a tag read while some screen of this app was up.
        unsubscribeRelay = NfcEventRelay.subscribe {
            onEvent(TriggerEvent(type, "NFC tag"))
        }

        // The system's half: a payment going through, no Activity involved.
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != NfcAdapter.ACTION_TRANSACTION_DETECTED) return
                val aid = intent.getByteArrayExtra(NfcAdapter.EXTRA_AID)
                    ?.joinToString("") { "%02X".format(it) }
                    ?: "unknown"
                onEvent(TriggerEvent(type, "payment (AID=$aid)"))
            }
        }
        receiver = r
        ContextCompat.registerReceiver(
            appContext,
            r,
            IntentFilter(NfcAdapter.ACTION_TRANSACTION_DETECTED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun detach() {
        unsubscribeRelay?.invoke()
        unsubscribeRelay = null
        val r = receiver ?: return
        receiver = null
        runCatching { appContext.unregisterReceiver(r) }
    }
}
