package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChargingAnimationService : FeatureService() {

    companion object {
        private const val TAG = "ChargingAnimService"
        private const val NOTIF_CHANNEL_ID = "ChargingAnimServiceChannel"
        private const val NOTIF_ID = 1014

        const val ACTION_START = "com.bleelblep.glyphsharge.CHARGING_ANIM_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.CHARGING_ANIM_STOP"
    }

    /**
     * This service's own registry entry.
     *
     * The run gate — my switch *and* the master Glyph switch — is the same
     * question everywhere it is asked, so it is answered by the entry rather
     * than re-derived at each call site.
     */
    private val spec = FeatureSpecs.of(GlyphFeature.CHARGING_ANIMATION)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    private val powerConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.d(TAG, "Power connected")
                    triggerChargingAnimation()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.d(TAG, "Power disconnected")
                    triggerChargingAnimation()
                }
            }
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
    override val channelNameRes: Int get() = R.string.charging_animation_channel

    @get:StringRes
    override val channelDescriptionRes: Int get() = R.string.charging_animation_channel_description

    override val wakeLockTag: String get() = "GlyphSharge:ChargingAnimation"

    override val tag: String get() = TAG

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()
        registerPowerReceiver()
    }

    override fun onFeatureDestroying() {
        // Guarded by the base class's own `runCatching` convention: `onCreate`
        // can fail before the registration completes, and an unguarded
        // `unregisterReceiver` throws out of `onDestroy`, which is an uncaught
        // crash rather than a cleanup failure.
        //
        // The animation scope and the WakeLock are the base's to cancel, in that
        // order: `animationJob` used to be a root job that `serviceJob.cancel()`
        // never reached, so an animation kept drawing after the service that
        // started it was gone, still holding the strip and a strong reference
        // to a destroyed Service.
        runCatching { unregisterReceiver(powerConnectionReceiver) }
    }

    private fun registerPowerReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            powerConnectionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun triggerChargingAnimation() {
        animationScope.launch {
            if (!spec.isRunnable(settingsRepository)) return@launch
            if (settingsRepository.isCurrentlyInQuietHours()) return@launch
            try {
                // `preempt` stays false: this feature skips on a busy strip rather
                // than interrupting whoever holds it, and the default timeout is
                // the 500 ms the `acquire` call used on its own.
                //
                // The WakeLock teardown hangs off `onRelease` so it keeps its old
                // position — after `release`, and only when the strip was really
                // taken. It is never acquired before the block, so a busy strip
                // still leaves the WakeLock untouched.
                featureCoordinator.withStrip(
                    owner = GlyphFeature.CHARGING_ANIMATION,
                    onRelease = {
                        try {
                            if (wakeLock.isHeld) wakeLock.release()
                        } catch (_: Exception) {}
                    },
                ) {
                    val duration = settingsRepository.getChargingAnimationDuration()

                    // +1s: the bar is still drawing its last frames after the cap.
                    try {
                        wakeLock.acquire(duration + 1000L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
                    }

                    glyphAnimationManager.runCapped(duration) {
                        glyphAnimationManager.playChargingAnimationAnimation(
                            this@ChargingAnimationService,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Charging animation sequence", e)
            }
        }
    }

    // Notification

    override fun buildNotification(): Notification =
        buildNotification(getString(R.string.charging_animation_notif_text))

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.charging_animation_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}
