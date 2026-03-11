package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Permanent foreground service that keeps the app listed in Quick Settings "Active apps".
 * Owns no LED logic — animations are triggered by other components via [GlyphFeatureCoordinator].
 */
@AndroidEntryPoint
class GlyphForegroundService : Service() {

    // coordinator is injected but used by other components through DI; kept here so Hilt
    // maintains the binding while this service is alive.
    @Inject lateinit var coordinator: GlyphFeatureCoordinator
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settingsRepository.getGlyphServiceEnabled()) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Self-restart only if the user still has Glyph Service enabled
        if (settingsRepository.getGlyphServiceEnabled()) {
            val intent = Intent(this, GlyphForegroundService::class.java)
            startForegroundService(intent)
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Glyph Foreground Service", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glyph features ready")
            .setContentText("LED features will activate automatically")
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 4242
        private const val CHANNEL_ID      = "GlyphForegroundServiceChannel"
    }
}