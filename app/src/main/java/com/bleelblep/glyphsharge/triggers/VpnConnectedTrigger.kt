package com.bleelblep.glyphsharge.triggers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * Fires once per genuine VPN connection.
 *
 * Android broadcasts no "VPN connected" event, so this watches the VPN
 * transport through a [ConnectivityManager.NetworkCallback] — the same listener
 * `VpnConnectedService` open-coded, now behind [TriggerSource] so the rule host
 * can attach it like any other trigger.
 *
 * Two details are load-bearing and carried over verbatim from that service:
 *
 *  - `NET_CAPABILITY_NOT_VPN` must be *removed* from the request. A default
 *    [NetworkRequest.Builder] inherits it as a **required** condition — "a
 *    network that is not a VPN" — so a VPN network, which has the bit cleared,
 *    can never match. The callback registers successfully while `onAvailable`
 *    is never delivered: silent forever, nothing logged.
 *  - `registerNetworkCallback` immediately reports a VPN that is *already* up,
 *    indistinguishable from a fresh connect unless the state is seeded first.
 *    [wasConnected] is read from the live state before registering, so only a
 *    false -> true edge fires, and a VPN already up at attach leaves the strip
 *    dark — the animation belongs to the connect event, not to the listener
 *    starting.
 *
 * Disconnects are latched but not fired: [onLost] only clears [wasConnected]
 * so the next connect is read as the connect it is.
 */
class VpnConnectedTrigger(
    private val context: Context,
    private val type: TriggerType,
) : TriggerSource {

    private companion object {
        const val TAG = "VpnConnectedTrigger"
    }

    private var onEvent: ((TriggerEvent) -> Unit)? = null
    private var registered = false

    /** Set on a genuine connect, cleared on loss, so only the edge fires. */
    @Volatile
    private var wasConnected = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (wasConnected) return
            wasConnected = true
            onEvent?.invoke(TriggerEvent(type, "vpn connect"))
        }

        override fun onLost(network: Network) {
            wasConnected = false
        }

        override fun onUnavailable() {
            wasConnected = false
        }
    }

    override fun attach(onEvent: (TriggerEvent) -> Unit) {
        this.onEvent = onEvent
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Seed the edge-latch from the live state before registering, so an
        // already-connected VPN is read as "already up" rather than a connect.
        wasConnected = runCatching { vpnIsUp(cm) }
            .onFailure { Log.e(TAG, "Cannot read the current VPN state", it) }
            .getOrDefault(defaultValue = false)

        // `NOT_VPN` removed — see the class KDoc; without this the listener is
        // registered but can never fire.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        runCatching { cm.registerNetworkCallback(request, callback) }
            .onSuccess { registered = true }
            .onFailure { Log.e(TAG, "Cannot watch the VPN transport", it) }
    }

    override fun detach() {
        if (registered) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            runCatching { cm.unregisterNetworkCallback(callback) }
            registered = false
        }
        onEvent = null
    }

    private fun vpnIsUp(cm: ConnectivityManager): Boolean {
        val active = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(active)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}
