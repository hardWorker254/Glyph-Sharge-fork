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
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that listens for the device screen turning OFF and plays the
 * user-chosen Glyph animation.
 */
@AndroidEntryPoint
class ScreenOffGlyphService : FeatureService() {

    companion object {
        private const val TAG = "ScreenOffGlyphService"
        private const val NOTIF_CHANNEL_ID = "ScreenOffServiceChannel"
        private const val NOTIF_ID = 1011
        const val ACTION_START = "com.bleelblep.glyphsharge.SCREEN_OFF_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.SCREEN_OFF_STOP"
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    private val spec = FeatureSpecs.of(GlyphFeature.SCREEN_OFF)

    /**
     * The screen-off event arrives even while the feature is off, so the
     * handler and [onStartCommand] have to agree on when this service may
     * draw — which is the whole reason this is one property rather than a
     * check each of them repeats.
     */
    override val isRunnable: Boolean
        get() = spec.isRunnable(settingsRepository)

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = NOTIF_CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.screen_off_channel

    override val wakeLockTag: String get() = "GlyphSharge:ScreenOffAnimation"
    override val tag: String get() = TAG

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "ACTION_SCREEN_OFF received – starting screen off sequence")
                playScreenOffSequence()
            }
        }
    }

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Log.d(TAG, "ScreenOffGlyphService created")
    }

    override fun onFeatureDestroying() {
        // Guarded by the base class's own `runCatching` convention: `onCreate`
        // can fail before the registration completes, and an unguarded
        // `unregisterReceiver` throws out of `onDestroy`, which is an uncaught
        // crash rather than a cleanup failure.
        runCatching { unregisterReceiver(screenOffReceiver) }
        Log.d(TAG, "ScreenOffGlyphService destroyed")
    }

    // Core sequence

    private fun playScreenOffSequence() {
        animationScope.launch {
            if (!isRunnable) {
                Log.d(TAG, "Feature disabled – skipping sequence")
                return@launch
            }

            if (settingsRepository.isCurrentlyInQuietHours()) {
                Log.d(TAG, "Quiet hours active – skipping sequence")
                return@launch
            }

            try {
                // Locking the phone is a thing the user just did, so it interrupts
                // a leftover animation rather than being skipped by it.
                //
                // Foreground for the life of the feature switch, with nothing to
                // promote or demote around the strip. See the same note in
                // PulseLockService: dropping the foreground notification after
                // the first event turned this into an ordinary background
                // service that Android reclaimed, and the feature silently
                // worked once per boot.
                val played = featureCoordinator.withStrip(
                    owner = GlyphFeature.SCREEN_OFF,
                    preempt = true,
                ) {
                    val animationId = settingsRepository.getScreenOffAnimationId()
                    val duration = glyphAnimationManager.runCapMs(
                        animationId,
                        settingsRepository.getScreenOffDuration(),
                    )

                    Log.d(TAG, "Sequence start – anim=$animationId duration=${duration}ms")

                    glyphAnimationManager.runCapped(
                        capMs = duration,
                        onTimeout = { Log.d(TAG, "Duration limit reached – stopping animation") },
                    ) {
                        glyphAnimationManager.playScreenOffAnimation()
                    }
                }
                if (played == null) {
                    Log.d(TAG, "Strip still busy after interrupt (owner: ${featureCoordinator.currentOwner.value}) – skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Screen Off sequence", e)
            }
        }
    }

    // Notification

    override fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_off_notif_title))
            .setContentText(getString(R.string.screen_off_notif_text))
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_off_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}
