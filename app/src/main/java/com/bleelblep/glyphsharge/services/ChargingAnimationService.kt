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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChargingAnimationService : Service() {

    companion object {
        private const val TAG = "ChargingAnimService"
        private const val NOTIF_CHANNEL_ID = "ChargingAnimServiceChannel"
        private const val NOTIF_ID = 1014

        const val ACTION_START = "com.bleelblep.glyphsharge.CHARGING_ANIM_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.CHARGING_ANIM_STOP"
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val animationJob = SupervisorJob()
    private val animationScope = CoroutineScope(Dispatchers.Main + animationJob)

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var powerManager: PowerManager

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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GlyphSharge:ChargingAnimation"
        )

        registerPowerReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }

        if (!settingsRepository.getGlyphServiceEnabled()
            || !settingsRepository.isChargingAnimationEnabled()) {
            shutDown()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(powerConnectionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Receiver not registered", e)
        }
        stopForegroundCompat()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!settingsRepository.getGlyphServiceEnabled()
            || !settingsRepository.isChargingAnimationEnabled()) return

        val restart = Intent(this, ChargingAnimationService::class.java).apply { action = ACTION_START }
        startForegroundService(restart)
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
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun triggerChargingAnimation() {
        animationScope.launch {
            if (!settingsRepository.getGlyphServiceEnabled()) return@launch
            if (settingsRepository.isCurrentlyInQuietHours()) return@launch
            if (!settingsRepository.isChargingAnimationEnabled()) return@launch
            if (!featureCoordinator.acquire(GlyphFeature.CHARGING_ANIMATION)) return@launch

            val duration = settingsRepository.getChargingAnimationDuration()

            try {
                wakeLock.acquire(duration + 1000L)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
            }

            try {
                val animJob = launch(Dispatchers.Default) {
                    glyphAnimationManager.playBatteryStatusAnimation(
                        this@ChargingAnimationService,
                        duration
                    )
                }

                val watchdogJob = launch {
                    delay(duration)
                    animJob.cancelAndJoin()
                    glyphAnimationManager.stopAnimations()
                }

                animJob.join()
                watchdogJob.cancel()

            } catch (e: Exception) {
                Log.e(TAG, "Error in Charging animation sequence", e)
            } finally {
                featureCoordinator.release(GlyphFeature.CHARGING_ANIMATION)
                try {
                    if (wakeLock.isHeld) wakeLock.release()
                } catch (e: Exception) {}
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Charging Animation Service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Shows Glyph animation when charger is connected or disconnected"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("⚡ Charging Animation Active")
            .setContentText("Listening for charger connection/disconnection.")
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    private fun shutDown() {
        stopForegroundCompat()
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}