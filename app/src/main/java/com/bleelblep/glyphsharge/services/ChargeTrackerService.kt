package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.repository.ChargingSessionRepository
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that tracks battery state while the device is plugged in
 * and maintains an ongoing charging session in the repository.
 */
@AndroidEntryPoint
class ChargeTrackerService : Service() {

    @Inject lateinit var repository: ChargingSessionRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Battery data extracted directly from the sticky ACTION_BATTERY_CHANGED intent ──
    private data class BatteryState(
        val percentage: Int,
        val temperatureC: Float,
        val isCharging: Boolean,
        val isFull: Boolean
    )

    /**
     * Extract battery state from the intent that ACTION_BATTERY_CHANGED delivers.
     * We never need to call registerReceiver(null, …) here because the receiver
     * already receives the intent as its parameter.
     */
    private fun Intent.toBatteryState(): BatteryState {
        val level  = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale  = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct    = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val tempC  = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        return BatteryState(
            percentage   = pct,
            temperatureC = tempC,
            isCharging   = status == BatteryManager.BATTERY_STATUS_CHARGING,
            isFull       = status == BatteryManager.BATTERY_STATUS_FULL
        )
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            val state = intent.toBatteryState()

            scope.launch {
                when {
                    state.isCharging -> {
                        if (repository.getOpenSession() == null) {
                            repository.startSession(state.percentage, state.temperatureC)
                            Log.d(TAG, "Session started at ${state.percentage}%")
                        } else {
                            repository.updateOngoingSession(state.percentage, state.temperatureC)
                        }
                    }
                    // isFull and disconnected both end the session
                    else -> {
                        val reason = if (state.isFull) "full" else "disconnected"
                        repository.finishSession(state.percentage, state.temperatureC)
                        Log.d(TAG, "Session finished at ${state.percentage}% ($reason)")
                    }
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        if (!settingsRepository.isBatteryStoryEnabled()) {
            Log.d(TAG, "Battery Story disabled – stopping")
            stopSelf()
            return
        }
        // Create the notification channel once, not on every buildNotification() call
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.d(TAG, "Service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        scope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Charge Tracker", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tracks battery charging sessions"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                setAllowBubbles(false)
            }
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔋 Battery Story Active")
            .setContentText("Monitoring battery level and temperature")
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

    companion object {
        const val NOTIF_ID  = 42
        private const val TAG        = "ChargeTracker"
        private const val CHANNEL_ID = "charge_tracker"
    }
}