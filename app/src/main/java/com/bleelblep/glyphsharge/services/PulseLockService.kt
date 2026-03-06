package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.ui.theme.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import androidx.core.net.toUri

/**
 * Foreground service that listens for the device being unlocked and plays the
 * user-chosen Glow Gate glyph animation (and optional sound).
 */
@AndroidEntryPoint
class PulseLockService : Service() {

    companion object {
        private const val TAG = "PulseLockService"
        private const val NOTIF_CHANNEL_ID = "PulseLockServiceChannel"
        private const val NOTIF_ID = 1010
        const val ACTION_START = "com.bleelblep.glyphsharge.PULSE_LOCK_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.PULSE_LOCK_STOP"
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator

    // AtomicReference ensures thread-safe MediaPlayer swap without heavy synchronization
    private val mediaPlayerRef = AtomicReference<MediaPlayer?>(null)

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "ACTION_USER_PRESENT received – starting Glow Gate sequence")
                playPulseLockSequence()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        // Create the notification channel once here, not on every buildNotification() call
        createNotificationChannel()
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        Log.d(TAG, "PulseLockService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }

        if (!settingsRepository.isPulseLockEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) {
            shutDown()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(unlockReceiver)
        stopAudio()
        stopForegroundCompat()
        serviceJob.cancel()
        Log.d(TAG, "PulseLockService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Only restart if the feature is still supposed to be running
        if (!settingsRepository.isPulseLockEnabled() ||
            !settingsRepository.getGlyphServiceEnabled()
        ) return

        val restart = Intent(this, PulseLockService::class.java).apply { action = ACTION_START }
        startForegroundService(restart)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core sequence
    // ──────────────────────────────────────────────────────────────────────────

    private fun playPulseLockSequence() {
        scope.launch {
            // Guard: re-check settings before doing any work
            if (!settingsRepository.isPulseLockEnabled() ||
                !settingsRepository.getGlyphServiceEnabled()
            ) {
                Log.d(TAG, "Feature disabled – skipping sequence")
                return@launch
            }

            if (settingsRepository.isCurrentlyInQuietHours()) {
                Log.d(TAG, "Quiet hours active – skipping sequence")
                return@launch
            }

            // Try to acquire the LED lock; bail if another feature owns it
            if (!featureCoordinator.acquire(GlyphFeature.PULSE_LOCK)) {
                Log.d(TAG, "LEDs busy (owner: ${featureCoordinator.currentOwner.value}) – skipping")
                return@launch
            }

            val animationId  = settingsRepository.getPulseLockAnimationId()
            val audioEnabled = settingsRepository.isPulseLockAudioEnabled()
            val audioUriStr  = settingsRepository.getPulseLockAudioUri()
            val audioOffset  = settingsRepository.getPulseLockAudioOffset()
            val duration     = settingsRepository.getPulseLockDuration()

            Log.d(TAG, "Sequence start – anim=$animationId audio=$audioEnabled uri=$audioUriStr offset=${audioOffset}ms duration=${duration}ms")

            // Promote to foreground for the duration of the sequence
            startForeground(NOTIF_ID, buildNotification())

            try {
                val animJob = launch(Dispatchers.Default) {
                    glyphAnimationManager.playPulseLockAnimation(animationId)
                }

                // Hard-stop watchdog: cancels the animation job and kills audio
                val watchdogJob = launch {
                    delay(duration)
                    Log.d(TAG, "Duration limit reached – stopping animation & audio")
                    animJob.cancelAndJoin()          // cancel, then wait for cleanup
                    glyphAnimationManager.stopAnimations()
                    stopAudio()
                }

                // Handle audio with offset
                val effectiveUri = audioUriStr?.takeIf { audioEnabled && it.isNotEmpty() }
                if (effectiveUri != null && isUriAccessible(effectiveUri)) {
                    when {
                        audioOffset < 0 -> {
                            Log.d(TAG, "Audio leads animation by ${-audioOffset}ms")
                            playAudio(effectiveUri)
                            delay(-audioOffset)
                            animJob.join()
                        }
                        audioOffset > 0 -> {
                            Log.d(TAG, "Audio delayed by ${audioOffset}ms after animation start")
                            delay(audioOffset)
                            playAudio(effectiveUri)
                            animJob.join()
                        }
                        else -> {
                            Log.d(TAG, "Audio & animation simultaneous")
                            playAudio(effectiveUri)
                            animJob.join()
                        }
                    }
                } else {
                    if (effectiveUri != null) {
                        Log.w(TAG, "Audio URI not accessible – playing animation only: $effectiveUri")
                    }
                    animJob.join()
                }

                // Animation finished naturally before the watchdog fired – cancel it
                watchdogJob.cancel()

            } catch (e: Exception) {
                Log.e(TAG, "Error in Glow Gate sequence", e)
            } finally {
                featureCoordinator.release(GlyphFeature.PULSE_LOCK)
                stopAudio()
                // Demote from foreground but keep service alive for future unlocks
                stopForegroundCompat()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Audio helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Checks URI accessibility with a lightweight query instead of opening a full stream.
     */
    private fun isUriAccessible(uriStr: String): Boolean {
        return try {
            val uri = uriStr.toUri()
            applicationContext.contentResolver.query(uri, arrayOf("_id"), null, null, null)
                ?.use { it.count >= 0 } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "URI not accessible: $uriStr", e)
            false
        }
    }

    private fun playAudio(uriStr: String) {
        stopAudio() // Release any previous player

        val uri = uriStr.toUri()
        Log.d(TAG, "Starting audio playback: $uri")

        try {
            val player = MediaPlayer().apply {
                // AudioAttributes replaces deprecated setAudioStreamType()
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, uri)
                setOnPreparedListener { mp ->
                    Log.d(TAG, "Audio prepared – starting playback")
                    mp.start()
                }
                setOnCompletionListener {
                    Log.d(TAG, "Audio playback completed")
                    stopAudio()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stopAudio()
                    true // handled
                }
                prepareAsync()
            }
            // Atomically swap the reference; release any player that squeezed in concurrently
            mediaPlayerRef.getAndSet(player)?.releaseQuietly()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaPlayer for: $uriStr", e)
        }
    }

    private fun stopAudio() {
        mediaPlayerRef.getAndSet(null)?.releaseQuietly()
    }

    private fun MediaPlayer.releaseQuietly() {
        try { stop() } catch (_: Exception) {}
        try { release() } catch (_: Exception) {}
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Glow Gate Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("✨ Glow Gate Active")
            .setContentText("Unlocking will play your chosen animation.")
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    // ──────────────────────────────────────────────────────────────────────────
    // Compat helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun shutDown() {
        stopAudio()
        stopForegroundCompat()
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}