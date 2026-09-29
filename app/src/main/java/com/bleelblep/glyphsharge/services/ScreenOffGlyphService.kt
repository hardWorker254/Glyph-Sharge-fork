package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that listens for the device screen turning OFF and plays the
 * user-chosen Glyph animation.
 */
@AndroidEntryPoint
class ScreenOffGlyphService : Service() {

    companion object {
        private const val TAG = "ScreenOffGlyphService"
        private const val NOTIF_CHANNEL_ID = "ScreenOffServiceChannel"
        private const val NOTIF_ID = 1011
        const val ACTION_START = "com.bleelblep.glyphsharge.SCREEN_OFF_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.SCREEN_OFF_STOP"
    }

    /**
     * This service's own registry entry, which owns the run gate — my switch
     * and the master Glyph switch. The screen-off event arrives even while the
     * feature is off, so the handler and `onStartCommand` have to agree on when
     * this service is allowed to draw.
     */
    private val spec = FeatureSpecs.of(GlyphFeature.SCREEN_OFF)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "ACTION_SCREEN_OFF received – starting screen off sequence")
                playScreenOffSequence()
            }
        }
    }

    // Lifecycle

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "ScreenOffGlyphService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }

        if (!spec.isRunnable(settingsRepository)) {
            shutDown()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenOffReceiver)
        stopForegroundCompat()
        serviceJob.cancel()
        Log.d(TAG, "ScreenOffGlyphService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!spec.isRunnable(settingsRepository)) return

        val restart = Intent(this, ScreenOffGlyphService::class.java).apply { action = ACTION_START }
        startForegroundService(restart)
    }

    // Core sequence

    private fun playScreenOffSequence() {
        scope.launch {
            if (!spec.isRunnable(settingsRepository)) {
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
                // `onRelease` runs `stopForegroundCompat()` after the strip is
                // handed back, and on the throwing path as well as the normal
                // one. A failed acquisition returns before the block runs, so
                // the service stays foregrounded from `onStartCommand`.
                val played = featureCoordinator.withStrip(
                    owner = GlyphFeature.SCREEN_OFF,
                    preempt = true,
                    onRelease = { stopForegroundCompat() }
                ) {
                    val animationId = settingsRepository.getScreenOffAnimationId()
                    val duration = glyphAnimationManager.runCapMs(
                        animationId,
                        settingsRepository.getScreenOffDuration()
                    )

                    Log.d(TAG, "Sequence start – anim=$animationId duration=${duration}ms")

                    startForeground(NOTIF_ID, buildNotification())

                    glyphAnimationManager.runCapped(
                        capMs = duration,
                        onTimeout = { Log.d(TAG, "Duration limit reached – stopping animation") }
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

    // Notification helpers

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Screen Off Glyph Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("✨ Screen Off Animation Active")
            .setContentText("Turning off screen will play your chosen animation.")
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    // Compat helpers

    private fun shutDown() {
        stopForegroundCompat()
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}