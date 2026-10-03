package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that watches for a VPN transport coming up and plays the
 * user-chosen Glyph animation once per connection.
 *
 * Android broadcasts no "VPN connected" event, so the trigger is a
 * [ConnectivityManager.NetworkCallback] registered for
 * [NetworkCapabilities.TRANSPORT_VPN] rather than a receiver.
 */
@AndroidEntryPoint
class VpnConnectedService : FeatureService() {

    companion object {
        private const val TAG = "VpnConnectedService"
        private const val NOTIF_CHANNEL_ID = "VpnConnectedServiceChannel"
        private const val NOTIF_ID = 1015
        const val ACTION_START = "com.bleelblep.glyphsharge.VPN_CONNECTED_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.VPN_CONNECTED_STOP"
    }

    /**
     * This service's own registry entry, which owns the run gate — my switch
     * and the master Glyph switch. The VPN callback keeps running whatever the
     * toggles say, so `onStartCommand` and the trigger have to agree on when
     * this service is allowed to draw.
     */
    private val spec = FeatureSpecs.of(GlyphFeature.VPN_CONNECTED)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    /**
     * Set once the callback is registered, so [onFeatureDestroying] only tries
     * to unregister something that exists — a service that never got past
     * `onCreate` would otherwise throw on the way out.
     */
    private var callbackRegistered = false

    /**
     * Whether a VPN is up as far as this service is concerned.
     *
     * `registerNetworkCallback` immediately delivers `onAvailable` for a VPN
     * that is *already* connected, which is indistinguishable from a real
     * connect unless the state is known beforehand: seeded from
     * [vpnIsUp] before registering, this makes the handler fire only on a
     * genuine false -> true edge. A VPN that is already up at service start
     * therefore leaves the strip dark, which is what the user asked for — the
     * animation belongs to the connect event, not to the service being
     * started.
     */
    @Volatile
    private var wasConnected = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            if (wasConnected) {
                Log.d(TAG, "VPN already up – not a new connection")
                return
            }
            wasConnected = true
            Log.d(TAG, "VPN connected – starting VPN connected sequence")
            playVpnConnectedSequence()
        }

        override fun onLost(network: Network) {
            // Nothing is played on disconnect, but the latch has to be cleared:
            // the next `onAvailable` is then read as the connect event it is.
            wasConnected = false
            Log.d(TAG, "VPN lost")
        }

        override fun onUnavailable() {
            wasConnected = false
            Log.d(TAG, "VPN unavailable")
        }
    }

    // Identity

    override val isRunnable: Boolean
        get() = spec.isRunnable(settingsRepository)

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = NOTIF_CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.vpn_connected_channel

    /**
     * Never acquired here — a VPN connect is a foreground event and the
     * animation is capped well inside a foreground service's own window — but
     * the base creates one from this tag if anything ever asks.
     */
    override val wakeLockTag: String get() = "GlyphSharge:VpnConnectedAnimation"

    override val tag: String get() = TAG

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()
        registerNetworkCallback()
        Log.d(TAG, "VpnConnectedService created")
    }

    override fun onFeatureDestroying() {
        if (callbackRegistered) {
            runCatching {
                (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(networkCallback)
            }
            callbackRegistered = false
        }
        Log.d(TAG, "VpnConnectedService destroyed")
    }

    // Trigger registration

    /**
     * Asks for the current VPN state *before* registering, so the first
     * `onAvailable` can be classified as "already up" rather than as a connect
     * the user just made. The permission is declared in the manifest, so a
     * refusal here is a system decision rather than a packaging mistake — it
     * must not take the service down with it.
     */
    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        wasConnected = runCatching { vpnIsUp(cm) }
            .onFailure { Log.e(TAG, "Cannot read the current VPN state", it) }
            .getOrDefault(defaultValue = false)
        if (wasConnected) Log.d(TAG, "VPN is already connected at start – not triggering")

        // `NOT_VPN` must be removed, and this is the whole reason the feature can
        // otherwise fail silently forever.
        //
        // A default `NetworkCapabilities` — and so every `NetworkRequest.Builder`
        // that does not clear it — carries `NET_CAPABILITY_NOT_VPN`, which reads
        // as a *required* condition: the network must be something that is not a
        // VPN. A VPN network has that bit cleared, so a request built the obvious
        // way can never match one, and the callback is registered successfully
        // while `onAvailable` is never delivered. Nothing throws and nothing is
        // logged: the service just sits there, already watching a transport it
        // will never hear about.
        //
        // The other inherited defaults — TRUSTED, NOT_RESTRICTED, NOT_VCN_MANAGED —
        // are ones a VPN network does satisfy, so they are left in place.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        runCatching { cm.registerNetworkCallback(request, networkCallback) }
            .onSuccess { callbackRegistered = true }
            .onFailure {
                Log.e(TAG, "Cannot watch the VPN transport – feature stays dark", it)
            }
    }

    /**
     * Whether a VPN is up right now, per [cm].
     *
     * Read from [ConnectivityManager.getActiveNetwork] rather than from every
     * network: the active one is the transport that is actually carrying
     * traffic, so it is the VPN the user is "connected to", and it keeps the
     * answer single-valued where a [ConnectivityManager.NetworkCallback] is
     * multi-valued.
     */
    private fun vpnIsUp(cm: ConnectivityManager): Boolean {
        val active = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(active)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    // Core sequence

    private fun playVpnConnectedSequence() {
        animationScope.launch {
            if (!spec.isRunnable(settingsRepository)) {
                Log.d(TAG, "Feature disabled – skipping sequence")
                return@launch
            }

            try {
                // Tapping "Connect" is a thing the user just did, so it
                // interrupts a leftover animation rather than being skipped by
                // it — the same reasoning as the screen-off case.
                //
                // Foreground for the life of the feature switch, with nothing to
                // promote or demote around the strip. See the same note in
                // PulseLockService: dropping the foreground notification after
                // the first event turned this into an ordinary background
                // service that Android reclaimed, and the feature silently
                // worked once per boot.
                val played = featureCoordinator.withStrip(
                    owner = GlyphFeature.VPN_CONNECTED,
                    preempt = true,
                ) {
                    val animationId = settingsRepository.getVpnConnectedAnimationId()
                    val duration = glyphAnimationManager.runCapMs(
                        animationId,
                        settingsRepository.getVpnConnectedDuration(),
                    )

                    Log.d(TAG, "Sequence start – anim=$animationId duration=${duration}ms")

                    glyphAnimationManager.runCapped(
                        capMs = duration,
                        onTimeout = { Log.d(TAG, "Duration limit reached – stopping animation") },
                    ) {
                        glyphAnimationManager.playVpnConnectedAnimation()
                    }
                }
                if (played == null) {
                    Log.d(TAG, "Strip still busy after interrupt (owner: ${featureCoordinator.currentOwner.value}) – skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN Connected sequence", e)
            }
        }
    }

    // Notification

    override fun buildNotification(): Notification =
        buildNotification(getString(R.string.vpn_connected_notif_text))

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_connected_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}
