package com.bleelblep.glyphsharge.glyph.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's network state, read on demand and cached for a moment.
 *
 * Needs no runtime permission: `ACCESS_NETWORK_STATE` is a normal permission
 * and is already declared for the VPN feature, so the only cost of asking is a
 * binder round-trip to `ConnectivityManager` — which is exactly the cost this
 * class exists to avoid paying too often.
 *
 * ### Why the TTL cache
 *
 * A script reads these four values from a draw loop, which means as often as
 * every frame. `activeNetwork` and `getActiveNetworkCapabilities` are binder
 * calls, and at 60fps that is a burst of IPC per second, fired from a
 * foreground service whose entire job is to push pixels at LEDs. The glyph has
 * no business making the user pay for it.
 *
 * A second is far shorter than any network handover takes to settle and far
 * longer than one frame, so nothing a script can observe at that granularity is
 * lost — while the steady state is one binder call per second per device rather
 * than sixty.
 *
 * ### Why there is no `registerNetworkCallback`
 *
 * A callback would be the tidier design in an app with a lifecycle: push the
 * change in, never poll. There is no such owner here. These reads come from
 * animation services, which come and go with their own lifecycles, and nothing
 * in the VM's own design says which of them is current — so a callback
 * registered here could only be unregistered by a component that may never
 * exist. A leaked `NetworkCallback` holds its registration against a system
 * service for the life of the process, and outliving the service that wanted
 * the answer is precisely the failure the platform's `unregister` call exists
 * to prevent. Polling a cheap cached value has no such failure mode.
 */
@Singleton
class NetworkSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "NetworkSource"

        /**
         * How long a reading is reused before the system is asked again.
         *
         * See the class KDoc: long enough to make a per-frame read free, short
         * enough that a script reacts to the network coming back well before
         * the user thinks the animation has hung.
         */
        const val CACHE_TTL_MS = 1_000L
    }

    /** The last reading, or `null` before the first one. */
    private var cached: NetworkSnapshot? = null

    /** [SystemClock.elapsedRealtime] when [cached] was taken. */
    private var cachedAtMs = 0L

    /**
     * The current state, re-read from the system at most once per
     * [CACHE_TTL_MS].
     *
     * Synchronised because the value is read from whatever thread is running a
     * script and the studio preview can be going at the same time; the body is
     * a field read in the common case, so the lock is only ever held across
     * the binder call itself.
     */
    @Synchronized
    fun snapshot(): NetworkSnapshot {
        val now = SystemClock.elapsedRealtime()
        val previous = cached
        if (previous != null && (now - cachedAtMs < CACHE_TTL_MS)) return previous

        val read = readNow()
        cached = read
        cachedAtMs = now
        return read
    }

    /**
     * Asks the system once, and never throws.
     *
     * Every call is wrapped because the alternative is a script being killed
     * mid-animation by something it has no way to handle: a device with no
     * `ConnectivityManager` at all, or one that refuses the query. Neither is
     * worth a crash in a foreground service, and [NetworkSnapshot.DISCONNECTED]
     * is a state a script can already reason about, so it is the honest answer
     * to "I could not tell" — indistinguishable, deliberately, from a phone
     * that really is offline.
     */
    private fun readNow(): NetworkSnapshot = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching NetworkSnapshot.DISCONNECTED

        // A null active network is the only definition of disconnected, and it
        // is read *once* here: everything below is derived from that same
        // `Network` handle, so the four fields are one instant rather than
        // four queries that can straddle a handover.
        val active = manager.activeNetwork
        val capabilities = active?.let { manager.getNetworkCapabilities(it) }

        NetworkSnapshot(
            connected = active != null,
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            // `isActiveNetworkMetered` answers "should the user be charged",
            // so `true` there is `metered = false` here. The name reads
            // backwards against its own meaning and that is the platform's,
            // not this class's — the comment is here so the next reader does
            // not "fix" it.
            metered = !manager.isActiveNetworkMetered,
            vpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        )
    }.getOrElse { failure ->
        Log.w(TAG, "Cannot read the network state; reporting disconnected", failure)
        NetworkSnapshot.DISCONNECTED
    }
}
