package com.bleelblep.glyphsharge.glyph.net

/**
 * One coherent reading of the device's network state.
 *
 * ### Why a snapshot and not four queries
 *
 * The obvious alternative is four independent lookups — "is there a network?",
 * "is it wifi?", "is it metered?", "is it a VPN?" — each asking
 * `ConnectivityManager` at the moment it is called. That is wrong in a way that
 * only shows up on a phone, mid-handover.
 *
 * The four answers are not independent, and nothing makes them change together.
 * A phone leaving a metered hotspot for home Wi-Fi walks through a moment where
 * the transport is already Wi-Fi but the metered flag has not been cleared, or
 * where the VPN has come up and the underlying network has not been torn down. A
 * script that reads the properties one after another can easily straddle that
 * window and be handed a combination that never existed for any measurable
 * period — "connected, wifi, and not metered" is a perfectly ordinary pair of
 * calls to make and a perfectly wrong thing to branch on. The script then
 * commits to the wrong animation for a frame it can never explain.
 *
 * Reading them together, once, and handing the script four fields that are
 * guaranteed to be true of the same instant, moves the race out of the script
 * and into a single binder round-trip. The cost is that a value can be up to
 * [NetworkSource]'s cache TTL stale, which is a far better failure: a script
 * reacts a second late rather than acting on a state that never was.
 *
 * The script sees these as `glyph.net`'s four live fields; it never sees the
 * type, because the module exposes booleans and nothing else.
 */
data class NetworkSnapshot(
    /** `true` when some network is carrying traffic. */
    val connected: Boolean,
    /** `true` when that network is Wi-Fi. */
    val wifi: Boolean,
    /** `true` when the user should be billed for the bytes, however they arrive. */
    val metered: Boolean,
    /** `true` when the active network is a VPN. */
    val vpn: Boolean,
) {
    companion object {
        /**
         * What a script sees when the state could not be read at all.
         *
         * Also the answer a session gives before anything has been wired to it.
         * A wrong-but-plausible value would be worse than an obviously absent
         * one: a script branching on "am I offline" is fine on this, because
         * offline is the safe answer to branch on — it is the state in which
         * the animation should not try to reach anything.
         */
        val DISCONNECTED = NetworkSnapshot(
            connected = false,
            wifi = false,
            metered = false,
            vpn = false,
        )
    }
}
