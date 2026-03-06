package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.ui.theme.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import androidx.core.net.toUri

@AndroidEntryPoint
class GlyphGuardService : Service() {

    companion object {
        private const val TAG = "GlyphGuardService"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "GlyphGuardServiceChannel"
        const val ACTION_START_GLYPH_GUARD = "com.bleelblep.glyphsharge.START_GLYPH_GUARD"
        const val ACTION_STOP_GLYPH_GUARD  = "com.bleelblep.glyphsharge.STOP_GLYPH_GUARD"
    }

    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var glyphManager: GlyphManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var powerManager: PowerManager

    private val scope       = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isGuardActive = false
    @Volatile private var wasCharging = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null

    // ── Channel ranges per device model ──────────────────────────────────────
    // Each entry is a list of IntRanges to illuminate during the alert blink.
    private fun alertChannels(): List<IntRange> = when {
        com.nothing.ketchum.Common.is20111()                                              -> listOf(0..0, 1..1, 2..5, 6..6, 7..14)
        com.nothing.ketchum.Common.is22111()                                              -> listOf(0..32)
        com.nothing.ketchum.Common.is23111() || com.nothing.ketchum.Common.is23113()     -> listOf(0..25)
        com.nothing.ketchum.Common.is24111()                                              -> listOf(20..30, 31..35, 0..19)
        else                                                                               -> listOf(0..9)
    }

    // ── Broadcast receiver ────────────────────────────────────────────────────
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.d(TAG, "ACTION_POWER_DISCONNECTED")
                    if (isGuardActive) triggerAlert()
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.d(TAG, "ACTION_POWER_CONNECTED")
                    stopAlert()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    if (wasCharging && !isCharging && isGuardActive) {
                        Log.d(TAG, "Battery state: charging stopped → triggering alert")
                        triggerAlert()
                    }
                    wasCharging = isCharging
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlyphGuard::WakeLock")

        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(powerReceiver, filter)

        // Seed initial charging state
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            wasCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settingsRepository.getGlyphServiceEnabled()) {
            stopSelf()
            return START_STICKY
        }
        when (intent?.action) {
            ACTION_START_GLYPH_GUARD -> startGuard()
            ACTION_STOP_GLYPH_GUARD  -> stopGuard()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(powerReceiver) }
        stopAlert()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        startSelf(ACTION_START_GLYPH_GUARD)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun startSelf(action: String) {
        val intent = Intent(this, GlyphGuardService::class.java).apply { this.action = action }
        startForegroundService(intent)
    }

    // ── Guard control ─────────────────────────────────────────────────────────
    private fun startGuard() {
        isGuardActive = true
        acquireWakeLock()
        glyphManager.forceEnsureSession()
        startForeground(NOTIFICATION_ID, buildNotification("🛡️ Glyph Guard Active", "Monitoring for USB disconnection…"))
        schedulePeriodicCheck()
    }

    private fun stopGuard() {
        isGuardActive = false
        stopAlert()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Alert ─────────────────────────────────────────────────────────────────
    private fun triggerAlert() {
        if (settingsRepository.isCurrentlyInQuietHours()) {
            Log.d(TAG, "Alert blocked by quiet hours")
            return
        }
        if (!glyphManager.isSessionActive) glyphManager.forceEnsureSession()

        scope.launch {
            updateNotification("⚠️ USB DISCONNECTED!", "Glyph Guard detected charger removal")
            val duration = settingsRepository.getGlyphGuardDuration()
            val glyphJob = launch { runAlertBlink(duration) }
            val soundJob = if (settingsRepository.isGlyphGuardSoundEnabled()) {
                launch { playAlarmSound(duration) }
            } else {
                launch { delay(duration) }
            }
            glyphJob.join()
            soundJob.join()
            Log.d(TAG, "Alert completed")
        }
    }

    private fun stopAlert() {
        scope.launch { glyphCleanup() }
        stopAudio()
        if (isGuardActive) {
            updateNotification("🛡️ Glyph Guard Active", "Monitoring for USB disconnection…")
        }
    }

    // ── Glyph animation ───────────────────────────────────────────────────────
    /**
     * Blinks all device-appropriate glyph channels for [durationMillis] at 100 ms intervals.
     * A single function replaces the five near-identical per-device methods.
     */
    private suspend fun runAlertBlink(durationMillis: Long) {
        if (!glyphManager.isNothingPhone()) return
        val channels = alertChannels()
        val deadline = System.currentTimeMillis() + durationMillis
        var step = 0
        try {
            while (isGuardActive && System.currentTimeMillis() < deadline) {
                val brightness = if (step % 2 == 0) 4095 else 0
                glyphManager.mGM?.getGlyphFrameBuilder()?.let { builder ->
                    channels.forEach { range -> range.forEach { i -> builder.buildChannel(i, brightness) } }
                    glyphManager.mGM?.toggle(builder.build())
                }
                delay(100L)
                step++
            }
        } finally {
            glyphCleanup()
        }
    }

    private suspend fun glyphCleanup() {
        runCatching {
            glyphManager.mGM?.turnOff()
            delay(100L)
        }
    }

    // ── Audio ─────────────────────────────────────────────────────────────────
    private suspend fun playAlarmSound(durationMillis: Long) {
        val uri = settingsRepository.getGlyphGuardCustomRingtoneUri()
            ?.runCatching { this.toUri() }?.getOrNull()
            ?: getDefaultSoundUri()

        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@GlyphGuardService, uri)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.e(TAG, "Failed to start alarm sound", it) }

        delay(durationMillis)
        stopAudio()
    }

    private fun stopAudio() {
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
    }

    private fun getDefaultSoundUri(): Uri {
        val type = when (settingsRepository.getGlyphGuardSoundType()) {
            "NOTIFICATION" -> RingtoneManager.TYPE_NOTIFICATION
            "RINGTONE"     -> RingtoneManager.TYPE_RINGTONE
            else           -> RingtoneManager.TYPE_ALARM
        }
        return RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    // ── Periodic fallback check ───────────────────────────────────────────────
    private fun schedulePeriodicCheck() {
        scope.launch {
            while (isGuardActive) {
                delay(2_000L)
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
                    val status  = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    if (wasCharging && !charging && plugged == 0) triggerAlert()
                    wasCharging = charging
                }
            }
        }
    }

    // ── Wake lock helpers ─────────────────────────────────────────────────────
    private fun acquireWakeLock() {
        runCatching {
            wakeLock?.takeIf { !it.isHeld }?.acquire(10 * 60 * 1_000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Glyph Guard Service", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }
}