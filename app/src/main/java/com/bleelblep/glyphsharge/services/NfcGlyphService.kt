package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that listens for NFC events (payments via HCE, tag discoveries, etc.)
 * and plays the user-chosen Glyph animation in response.
 *
 * Detected events:
 *  - HCE payment / card emulation transactions  (ACTION_TRANSACTION_DETECTED)
 *  - NFC tag discovered, NDEF discovered, tech discovered (standard NFC intents
 *    forwarded here by the app's Activity / NFC dispatch helper)
 *
 * NOTE: Android only delivers NFC tag intents to Activities via the foreground-dispatch
 * system. To forward those intents to this service, call
 *   [NfcGlyphService.forwardNfcIntent(context, intent)]
 * from your Activity's onNewIntent / onResume.
 *
 * HCE transactions (contactless payment) are delivered as a system broadcast and are
 * picked up automatically here without any Activity involvement.
 */
@AndroidEntryPoint
class NfcGlyphService : FeatureService() {

    companion object {
        private const val TAG = "NfcGlyphService"
        private const val NOTIF_CHANNEL_ID = "NfcGlyphServiceChannel"
        private const val NOTIF_ID = 1012

        const val ACTION_START = "com.bleelblep.glyphsharge.NFC_GLYPH_START"
        const val ACTION_STOP  = "com.bleelblep.glyphsharge.NFC_GLYPH_STOP"

        /**
         * Internal action used by [forwardNfcIntent] to relay NFC tag intents
         * from an Activity into this service.
         */
        private const val ACTION_NFC_TAG_FORWARDED =
            "com.bleelblep.glyphsharge.NFC_TAG_FORWARDED"

        // Extra key that carries the original NFC action string for logging
        private const val EXTRA_NFC_ACTION = "extra_nfc_action"

        /**
         * Call this from your Activity's `onNewIntent` / `onResume` so that tag
         * discoveries are forwarded to the running service.
         *
         * Example:
         * ```kotlin
         * override fun onNewIntent(intent: Intent) {
         *     super.onNewIntent(intent)
         *     NfcGlyphService.forwardNfcIntent(this, intent)
         * }
         * ```
         */
        fun forwardNfcIntent(context: Context, intent: Intent) {
            val nfcActions = setOf(
                NfcAdapter.ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED,
            )
            if (intent.action !in nfcActions) return

            val forward = Intent(context, NfcGlyphService::class.java).apply {
                action = ACTION_NFC_TAG_FORWARDED
                putExtra(EXTRA_NFC_ACTION, intent.action)
                // Forward the Tag parcelable so we can log / inspect it if needed
                IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)?.let {
                    putExtra(NfcAdapter.EXTRA_TAG, it)
                }
            }
            context.startService(forward)
        }
    }

    // Injected dependencies

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    /**
     * This service's own registry entry, which owns the run gate — my switch
     * and the master Glyph switch.
     */
    private val spec = FeatureSpecs.of(GlyphFeature.NFC)

    // Identity

    override val isRunnable: Boolean
        get() = spec.isRunnable(settingsRepository)

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = NOTIF_CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.nfc_glyph_channel

    override val wakeLockTag: String get() = "GlyphSharge:NfcAnimation"

    override val tag: String get() = TAG

    // BroadcastReceiver — HCE / contactless payment transactions

    /**
     * Receives [NfcAdapter.ACTION_TRANSACTION_DETECTED], which the system
     * broadcasts whenever the NFC controller processes a contactless
     * transaction (e.g. Google Pay / tap-to-pay). Requires
     * android.permission.NFC, already needed for NFC.
     */
    private val nfcTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NfcAdapter.ACTION_TRANSACTION_DETECTED -> {
                    val aid = intent.getByteArrayExtra(NfcAdapter.EXTRA_AID)
                        ?.joinToString("") { "%02X".format(it) }
                        ?: "unknown"
                    Log.d(TAG, "HCE transaction detected – AID: $aid")
                    triggerGlyphAnimation(eventLabel = "payment (AID=$aid)")
                }
            }
        }
    }

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()
        registerNfcReceiver()
        Log.d(TAG, "NfcGlyphService created")
    }

    override fun onStartCommandAfterGate(intent: Intent?): Int {
        // A tag the Activity forwarded arrives here, after the base's run gate
        // rather than before it. It used to be answered ahead of the gate, but
        // `triggerGlyphAnimation` re-checks the same gate before drawing
        // anything, so the observable outcome is unchanged: no animation either
        // way when the feature is off — the difference is only that the service
        // now shuts down cleanly instead of staying up.
        if (intent?.action == ACTION_NFC_TAG_FORWARDED) {
            val originalAction = intent.getStringExtra(EXTRA_NFC_ACTION) ?: "tag"
            val label = when (originalAction) {
                NfcAdapter.ACTION_TAG_DISCOVERED  -> "tag discovered"
                NfcAdapter.ACTION_NDEF_DISCOVERED -> "NDEF tag"
                NfcAdapter.ACTION_TECH_DISCOVERED -> "tech tag"
                else                              -> "NFC tag"
            }
            Log.d(TAG, "Forwarded NFC intent received – $label")
            triggerGlyphAnimation(eventLabel = label)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onFeatureDestroying() {
        // Guarded by the base class's own `runCatching` convention: `onCreate`
        // can fail before the registration completes — the Hilt graph, the
        // notification channel — and an unguarded `unregisterReceiver` then
        // throws out of `onDestroy`, which is an uncaught crash rather than a
        // cleanup failure.
        //
        // The animation scope and the WakeLock are the base's to cancel: both
        // used to be missed, because `animationJob` was a root job that
        // `serviceJob.cancel()` never reached, so a tag scan mid-animation kept
        // drawing, kept the strip and the WakeLock, and kept a destroyed
        // Service alive.
        runCatching { unregisterReceiver(nfcTransactionReceiver) }
        Log.d(TAG, "NfcGlyphService destroyed")
    }

    // NFC receiver registration

    private fun registerNfcReceiver() {
        val filter = IntentFilter().apply {
            addAction(NfcAdapter.ACTION_TRANSACTION_DETECTED)
        }
        ContextCompat.registerReceiver(
            this,
            nfcTransactionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    // Core animation sequence

    private fun triggerGlyphAnimation(eventLabel: String) {
        animationScope.launch {
            if (!spec.isRunnable(settingsRepository)) {
                Log.d(TAG, "Feature disabled – skipping NFC animation ($eventLabel)")
                return@launch
            }

            if (settingsRepository.isCurrentlyInQuietHours()) {
                Log.d(TAG, "Quiet hours active – skipping NFC animation ($eventLabel)")
                return@launch
            }

            try {
                // `preempt` stays false: this feature skips on a busy strip rather
                // than interrupting whoever holds it, and the default timeout is
                // the 500 ms the `acquire` call uses on its own.
                //
                // The WakeLock teardown hangs off `onRelease` so it fires after
                // `release`, and only when the strip was really taken. It is
                // never acquired before the block, so a busy strip still leaves
                // the WakeLock untouched.
                val played = featureCoordinator.withStrip(
                    owner = GlyphFeature.NFC,
                    onRelease = {
                        try {
                            if (wakeLock.isHeld) wakeLock.release()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to release WakeLock: ${e.message}")
                        }
                    },
                ) {
                    val animationId = settingsRepository.getNfcAnimationId()
                    val duration    = settingsRepository.getNfcAnimationDuration()

                    Log.d(TAG, "NFC sequence start – event=$eventLabel anim=$animationId duration=${duration}ms")

                    // +3s: the strip is still being blanked out when the cap hits.
                    try {
                        wakeLock.acquire(duration + 3000L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
                    }

                    glyphAnimationManager.runCapped(
                        capMs = duration,
                        onTimeout = {
                            Log.d(TAG, "Duration limit reached – stopping NFC animation")
                        },
                    ) {
                        glyphAnimationManager.playNfcAnimation()
                    }
                }
                if (played == null) {
                    Log.d(TAG, "LEDs busy – skipping ($eventLabel)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in NFC glyph sequence", e)
            }
        }
    }

    // Notification

    override fun buildNotification(): Notification =
        buildNotification(getString(R.string.nfc_glyph_notif_text))

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.nfc_glyph_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}
