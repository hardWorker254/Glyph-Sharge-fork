package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.BatteryManager
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

@AndroidEntryPoint
class LowBatteryAlertService : FeatureService() {

    companion object {
        private const val TAG         = "LowBatteryAlertService"
        private const val NOTIF_ID    = 1338
        private const val CHANNEL_ID  = "LowBatteryAlertServiceChannel"
        const val ACTION_START = "com.bleelblep.glyphsharge.START_LOW_BATTERY_ALERT"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.LOW_BATTERY_ALERT_STOP"
        const val ACTION_TEST_ALERT              = "com.bleelblep.glyphsharge.TEST_LOW_BATTERY_ALERT"
    }

    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    /**
     * This service's own registry entry, which owns the run gate — my switch
     * and the master Glyph switch.
     *
     * It used to be absent here while every sibling had one, and that absence
     * was the whole bug: the service started with the feature off, drew no
     * notification of its own state, and ignored the `ACTION_STOP` that
     * `FeatureServiceController.stop()` sends, so only the `stopService` that
     * follows saved it. A `START_STICKY` restart brought it back anyway, with
     * the card reading "off".
     */
    private val spec = FeatureSpecs.of(GlyphFeature.LOW_BATTERY)

    @Volatile private var lowBatteryTriggered = false
    private var mediaPlayer: MediaPlayer? = null

    // Identity

    override val isRunnable: Boolean
        get() = spec.isRunnable(settingsRepository)

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.low_battery_channel

    /** Unchanged from the tag this service used before the base class owned it. */
    override val wakeLockTag: String get() = "LowBatteryAlert::WakeLock"

    override val tag: String get() = TAG

    // Battery receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return

            val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val pct    = if ((level >= 0) && (scale > 0)) ((level * 100) / scale) else 0
            val charging = (status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                    (status == BatteryManager.BATTERY_STATUS_FULL)

            if (settingsRepository.isLowBatteryEnabled() && !charging) {
                val threshold = settingsRepository.getLowBatteryThreshold()
                if ((!lowBatteryTriggered) && (pct <= threshold)) {
                    lowBatteryTriggered = true
                    Log.d(TAG, "Low battery $pct% – triggering alert")
                    animationScope.launch { playLowBatterySequence() }
                }
                // Reset once battery recovers or charging resumes
                if (pct >= (threshold + 5)) lowBatteryTriggered = false
            } else {
                lowBatteryTriggered = false
            }
        }
    }

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()
        // `startForeground` is the base's business, in `onStartCommand`: this
        // used to call it here, which meant a service that was created but never
        // started sat in the foreground with nothing to show for it.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.d(TAG, "Service created")
    }

    /**
     * "Test" means "show me what this sounds like", not "turn it on", so it is
     * answered ahead of the run gate. Answered behind it instead — which is
     * where this used to be — and the button would refuse, asking the user to
     * switch the feature on first to hear what it does.
     *
     * Scoped to this one action on purpose: a wider bypass would let the
     * service draw with its switch off, which is the thing the gate is for.
     */
    override fun bypassesRunGate(intent: Intent?): Boolean =
        intent?.action == ACTION_TEST_ALERT

    override fun onStartCommandAfterGate(intent: Intent?): Int {
        if (intent?.action == ACTION_TEST_ALERT) {
            animationScope.launch { playLowBatterySequence() }
            return START_STICKY
        }

        // Held for the service's own life rather than per event. The battery
        // broadcast arrives with the phone idle and Dozing often enough that a
        // per-trigger lock would be a race it loses; this is bounded by the
        // base's `onDestroy`, which releases it unconditionally.
        runCatching { wakeLock.takeIf { !it.isHeld }?.acquire(10 * 60 * 1_000L) }
        return START_STICKY
    }

    override fun onFeatureDestroying() {
        runCatching { unregisterReceiver(batteryReceiver) }
        stopAudio()
        Log.d(TAG, "Service destroyed")
    }

    // Alert sequence
    private suspend fun playLowBatterySequence() {
        try {
            // `preempt` stays false: this feature skips on a busy strip rather
            // than interrupting whoever holds it, and the default timeout is
            // the 500 ms the `acquire` call uses on its own.
            val played = featureCoordinator.withStrip(owner = GlyphFeature.LOW_BATTERY) {
                val duration = settingsRepository.getLowBatteryDuration()

                glyphAnimationManager.runCapped(
                    capMs = duration,
                    // The manager knows nothing about the alert's own audio,
                    // so the sound is stopped here, after the strip.
                    onTimeout = { stopAudio() },
                ) {
                    glyphAnimationManager.playLowBatteryAnimation()
                }
            }
            if (played == null) {
                Log.d(TAG, "LEDs busy by ${featureCoordinator.currentOwner.value} – skipping")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in low-battery sequence", e)
        }
    }

    private fun stopAudio() {
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
    }

    // Notification

    override fun buildNotification(): Notification =
        buildNotification(getString(R.string.low_battery_notif_text))

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.low_battery_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}
