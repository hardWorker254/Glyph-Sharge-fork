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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

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

        if (!settingsRepository.isScreenOffFeatureEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) {
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
        if (!settingsRepository.isScreenOffFeatureEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) return

        val restart = Intent(this, ScreenOffGlyphService::class.java).apply { action = ACTION_START }
        startForegroundService(restart)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core sequence
    // ──────────────────────────────────────────────────────────────────────────

    private fun playScreenOffSequence() {
        scope.launch {
            if (!settingsRepository.isScreenOffFeatureEnabled() ||
                !settingsRepository.getGlyphServiceEnabled()
            ) {
                Log.d(TAG, "Feature disabled – skipping sequence")
                return@launch
            }

            if (settingsRepository.isCurrentlyInQuietHours()) {
                Log.d(TAG, "Quiet hours active – skipping sequence")
                return@launch
            }

            if (!featureCoordinator.acquire(GlyphFeature.SCREEN_OFF)) {
                Log.d(TAG, "LEDs busy (owner: ${featureCoordinator.currentOwner.value}) – skipping")
                return@launch
            }

            val animationId  = settingsRepository.getScreenOffAnimationId()
            val duration     = settingsRepository.getScreenOffDuration()

            Log.d(TAG, "Sequence start – anim=$animationId duration=${duration}ms")


            startForeground(NOTIF_ID, buildNotification())

            try {
                val animJob = launch(Dispatchers.Default) {
                    glyphAnimationManager.playScreenOffAnimation(animationId)
                }

                val watchdogJob = launch {
                    delay(duration)
                    Log.d(TAG, "Duration limit reached – stopping animation")
                    animJob.cancelAndJoin()
                    glyphAnimationManager.stopAnimations()
                }

                animJob.join()
                watchdogJob.cancel()

            } catch (e: Exception) {
                Log.e(TAG, "Error in Screen Off sequence", e)
            } finally {
                featureCoordinator.release(GlyphFeature.SCREEN_OFF)
                stopForegroundCompat()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ──────────────────────────────────────────────────────────────────────────

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