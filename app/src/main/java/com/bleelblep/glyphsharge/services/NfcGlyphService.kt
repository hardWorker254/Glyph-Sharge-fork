package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.ui.theme.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
class NfcGlyphService : Service() {

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
         * Call this from your Activity's [onNewIntent] / [onResume] so that tag
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
                NfcAdapter.ACTION_TECH_DISCOVERED
            )
            if (intent.action !in nfcActions) return

            val forward = Intent(context, NfcGlyphService::class.java).apply {
                action = ACTION_NFC_TAG_FORWARDED
                putExtra(EXTRA_NFC_ACTION, intent.action)
                // Forward the Tag parcelable so we can log / inspect it if needed
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)?.let {
                    putExtra(NfcAdapter.EXTRA_TAG, it)
                }
            }
            context.startService(forward)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Injected dependencies
    // ──────────────────────────────────────────────────────────────────────────

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator

    // ──────────────────────────────────────────────────────────────────────────
    // Coroutine scope
    // ──────────────────────────────────────────────────────────────────────────

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    // ──────────────────────────────────────────────────────────────────────────
    // BroadcastReceiver — HCE / contactless payment transactions
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Receives [NfcAdapter.ACTION_TRANSACTION_DETECTED], which is broadcast by
     * the system whenever the NFC controller processes a contactless transaction
     * (e.g. Google Pay / tap-to-pay).
     *
     * Requires the android.permission.NFC permission (already needed for NFC).
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

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNfcReceiver()
        Log.d(TAG, "NfcGlyphService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }

            ACTION_NFC_TAG_FORWARDED -> {
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
        }

        if (!settingsRepository.isNfcFeatureEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) {
            shutDown()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(nfcTransactionReceiver)
        stopForegroundCompat()
        serviceJob.cancel()
        Log.d(TAG, "NfcGlyphService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!settingsRepository.isNfcFeatureEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) return

        val restart = Intent(this, NfcGlyphService::class.java).apply { action = ACTION_START }
        startForegroundService(restart)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // NFC receiver registration
    // ──────────────────────────────────────────────────────────────────────────

    private fun registerNfcReceiver() {
        val filter = IntentFilter().apply {
            addAction(NfcAdapter.ACTION_TRANSACTION_DETECTED)
        }
        ContextCompat.registerReceiver(
            this,
            nfcTransactionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core animation sequence
    // ──────────────────────────────────────────────────────────────────────────

    private fun triggerGlyphAnimation(eventLabel: String) {
        scope.launch {
            if (!settingsRepository.isNfcFeatureEnabled() ||
                !settingsRepository.getGlyphServiceEnabled()
            ) {
                Log.d(TAG, "Feature disabled – skipping NFC animation ($eventLabel)")
                return@launch
            }

            if (settingsRepository.isCurrentlyInQuietHours()) {
                Log.d(TAG, "Quiet hours active – skipping NFC animation ($eventLabel)")
                return@launch
            }

            if (!featureCoordinator.acquire(GlyphFeature.NFC)) {
                Log.d(TAG, "LEDs busy (owner: ${featureCoordinator.currentOwner.value}) – skipping ($eventLabel)")
                return@launch
            }

            val animationId = settingsRepository.getNfcAnimationId()
            val duration    = settingsRepository.getNfcAnimationDuration()

            Log.d(TAG, "NFC sequence start – event=$eventLabel anim=$animationId duration=${duration}ms")

            try {
                val animJob = launch(Dispatchers.Default) {
                    glyphAnimationManager.playNfcAnimation(animationId)
                }

                val watchdogJob = launch {
                    delay(duration)
                    Log.d(TAG, "Duration limit reached – stopping NFC animation")
                    animJob.cancelAndJoin()
                    glyphAnimationManager.stopAnimations()
                }

                animJob.join()
                watchdogJob.cancel()

            } catch (e: Exception) {
                Log.e(TAG, "Error in NFC glyph sequence", e)
            } finally {
                featureCoordinator.release(GlyphFeature.NFC)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "NFC Glyph Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("✨ NFC Animation Active")
            .setContentText("Tap to pay or scan an NFC tag to trigger an animation.")
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    // ──────────────────────────────────────────────────────────────────────────
    // Compat helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun shutDown() {
        stopForegroundCompat()
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}