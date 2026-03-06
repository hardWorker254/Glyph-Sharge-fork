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
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.ui.theme.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class PowerPeekService : Service(), SensorEventListener {

    companion object {
        private const val TAG        = "PowerPeekService"
        private const val NOTIF_ID   = 1337
        private const val CHANNEL_ID = "PowerPeekServiceChannel"
        private const val SHAKE_COOLDOWN_MS = 3_000L
    }

    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var powerManager: PowerManager? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastShakeTime = 0L
    // true while startForeground has been called and we haven't called stopForeground
    private var isForeground = false

    // ── Screen state receiver ─────────────────────────────────────────────────
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (settingsRepository.getGlyphServiceEnabled()) startListening()
                }
                Intent.ACTION_SCREEN_ON -> stopListening()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        sensorManager  = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        powerManager   = getSystemService(Context.POWER_SERVICE) as PowerManager

        createNotificationChannel()

        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })

        // Must enter foreground immediately on Android 14+ FGS rules
        ensureForeground()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If screen is already off, start listening right away
        if (powerManager?.isInteractive == false) startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        runCatching { unregisterReceiver(screenStateReceiver) }
        if (isForeground) stopForeground(true)
        scope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val i = Intent(this, PowerPeekService::class.java)
        startForegroundService(i)
    }

    // ── Sensor control ────────────────────────────────────────────────────────
    private fun startListening() {
        if (!settingsRepository.getGlyphServiceEnabled() || accelerometer == null) return
        ensureForeground()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Shake listening started")
    }

    private fun stopListening() {
        sensorManager.unregisterListener(this)
        if (isForeground) {
            stopForeground(true)
            isForeground = false
        }
        Log.d(TAG, "Shake listening stopped")
    }

    private fun ensureForeground() {
        if (!isForeground) {
            startForeground(NOTIF_ID, buildNotification())
            isForeground = true
        }
    }

    // ── SensorEventListener ───────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = event.values
        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        // Always read the latest threshold so slider changes apply instantly
        if (magnitude > settingsRepository.getShakeThreshold()) triggerVisualization()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── Visualization ─────────────────────────────────────────────────────────
    private fun triggerVisualization() {
        val now = System.currentTimeMillis()
        if (now - lastShakeTime < SHAKE_COOLDOWN_MS) return
        lastShakeTime = now

        if (settingsRepository.isCurrentlyInQuietHours()) {
            Log.d(TAG, "Trigger blocked by quiet hours")
            return
        }
        if (!glyphAnimationManager.ensureSessionActive()) {
            Log.w(TAG, "No Glyph session – aborting")
            return
        }

        scope.launch {
            if (!featureCoordinator.acquire(GlyphFeature.POWER_PEEK)) {
                Log.d(TAG, "LEDs busy by ${featureCoordinator.currentOwner.value}")
                return@launch
            }
            try {
                glyphAnimationManager.runBatteryPercentageVisualization(
                    applicationContext, settingsRepository.getDisplayDuration()
                ) {}
            } catch (e: Exception) {
                Log.e(TAG, "Visualization error", e)
            } finally {
                featureCoordinator.release(GlyphFeature.POWER_PEEK)
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PowerPeek Service", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📲 Power Peek Active")
            .setContentText("Shake to see battery when screen is off.")
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}