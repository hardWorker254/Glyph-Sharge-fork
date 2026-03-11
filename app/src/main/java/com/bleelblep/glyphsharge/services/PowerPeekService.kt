package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@AndroidEntryPoint
class PowerPeekService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "PowerPeekService"
        private const val NOTIF_CHANNEL_ID = "PowerPeekServiceChannel"
        private const val NOTIF_ID = 1013

        const val ACTION_START = "com.bleelblep.glyphsharge.POWER_PEEK_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.POWER_PEEK_STOP"

        private const val TRIGGER_COOLDOWN_MS = 5000L
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
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var isSensorRegistered = false
    private var lastTriggerTime = 0L

    private var isRestingOnTable = false
    private var stableStartTime = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    startListeningToSensors()
                }
                Intent.ACTION_SCREEN_ON -> {
                    stopListeningToSensors()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GlyphSharge:PowerPeekAnimation"
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }
        }

        if (!settingsRepository.isPowerPeekEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) {
            shutDown()
            return START_NOT_STICKY
        }

        @Suppress("DEPRECATION")
        if (!powerManager.isInteractive) {
            startListeningToSensors()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenStateReceiver)
        stopListeningToSensors()
        stopForegroundCompat()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!settingsRepository.isPowerPeekEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) return

        val restart = Intent(this, PowerPeekService::class.java).apply { action = ACTION_START }
        startForegroundService(restart)
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun startListeningToSensors() {
        if (!isSensorRegistered && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isSensorRegistered = true
            isRestingOnTable = false
            stableStartTime = 0L
        }
    }

    private fun stopListeningToSensors() {
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val zAbs = abs(z)
        val now = System.currentTimeMillis()

        val isStillAndFlat = zAbs in 9.0f..10.6f && abs(x) < 1.5f && abs(y) < 1.5f

        if (isStillAndFlat) {
            if (stableStartTime == 0L) {
                stableStartTime = now
            } else if (now - stableStartTime > 500L) {
                isRestingOnTable = true
            }
        } else {
            if (zAbs < 8.0f || zAbs > 11.5f) {
                isRestingOnTable = false
                stableStartTime = 0L
            }
        }
        if (!isRestingOnTable) return
        val horizontalAcceleration = sqrt((x * x + y * y).toDouble()).toFloat()
        val baseThreshold = settingsRepository.getShakeThreshold()
        val horizontalThreshold = max(3.0f, baseThreshold - SensorManager.STANDARD_GRAVITY)

        if (horizontalAcceleration > horizontalThreshold) {
            isRestingOnTable = false
            stableStartTime = 0L
            triggerPowerPeekAnimation()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ──────────────────────────────────────────────────────────────────────────
    // Animation
    // ──────────────────────────────────────────────────────────────────────────

    private fun triggerPowerPeekAnimation() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) return

        animationScope.launch {
            if (!settingsRepository.isPowerPeekEnabled() ||
                !settingsRepository.getGlyphServiceEnabled()
            ) return@launch

            if (settingsRepository.isCurrentlyInQuietHours()) return@launch

            if (!featureCoordinator.acquire(GlyphFeature.POWER_PEEK)) return@launch

            lastTriggerTime = System.currentTimeMillis()
            val duration = settingsRepository.getDisplayDuration()

            Log.d(TAG, "Power Peek: Horizontal shake on table detected! Duration=${duration}ms")

            try {
                wakeLock.acquire(duration + 2000L)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
            }

            try {
                val animJob = launch(Dispatchers.Default) {
                    glyphAnimationManager.playBatteryPercentageVisualization(
                        this@PowerPeekService,
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
                Log.e(TAG, "Error in Power Peek glyph sequence", e)
            } finally {
                featureCoordinator.release(GlyphFeature.POWER_PEEK)
                try {
                    if (wakeLock.isHeld) wakeLock.release()
                } catch (e: Exception) {}
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Power Peek Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("🔋 Power Peek Active")
            .setContentText("Wiggle phone horizontally on a table to check battery.")
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