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
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.RunTrace
import com.bleelblep.glyphsharge.data.SettingsRepository
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Foreground service that listens for the device being unlocked and plays the
 * user-chosen Glow Gate glyph animation (and optional sound).
 */
@AndroidEntryPoint
class PulseLockService : Service() {

    companion object {
        private const val TAG = "PulseLockService"
        private const val FEATURE = "glow-gate"

        /**
         * How long to wait for a real unlock after the screen comes on, before
         * concluding this phone does not send one.
         *
         * Only the first unlock pays this. Long enough that someone who has to
         * swipe or type a code is not made to watch it twice.
         */
        private const val SCREEN_ON_GRACE_MS = 1_500L

        /**
         * The delay for a phone already known not to send `USER_PRESENT` —
         * only long enough for the screen-on animation to settle.
         */
        private const val SCREEN_ON_SETTLE_MS = 150L
        private const val NOTIF_CHANNEL_ID = "PulseLockServiceChannel"
        private const val NOTIF_ID = 1010
        const val ACTION_START = "com.bleelblep.glyphsharge.PULSE_LOCK_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.PULSE_LOCK_STOP"
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
    @Inject lateinit var runTrace: RunTrace

    // AtomicReference ensures thread-safe MediaPlayer swap without heavy synchronization

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var screenOnFallback: Job? = null

    /**
     * The unlock trigger.
     *
     * `ACTION_USER_PRESENT` is the honest one — it means the user got past the
     * keyguard. But it is only sent when there *is* a keyguard to get past: on
     * a phone with no lock screen, or when the keyguard never comes up, the
     * screen simply turns on and this broadcast never arrives. Glow Gate then
     * does nothing at all, with no error and no way for the user to tell why.
     *
     * So both are listened for. `ACTION_SCREEN_ON` arms a fallback that fires
     * only if no real unlock arrived shortly after — which on a locked phone
     * means "the screen came on and nothing has happened since", and on an
     * unlocked one means "turning the screen on is the unlocking".
     */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "ACTION_USER_PRESENT received – starting Glow Gate sequence")
                    // A real unlock is the better signal, whatever we assumed
                    // before: wait for it again next time.
                    settingsRepository.markUserPresentSeen()
                    runTrace.record(FEATURE, "unlock", "ACTION_USER_PRESENT")
                    screenOnFallback?.cancel()
                    playPulseLockSequence()
                }

                Intent.ACTION_SCREEN_ON -> {
                    screenOnFallback?.cancel()
                    val expectUnlock = settingsRepository.isUserPresentExpected()

                    screenOnFallback = scope.launch {
                        // A phone that has never sent USER_PRESENT is not going
                        // to start now, and waiting out the grace period only
                        // adds latency to the moment the user is watching for.
                        // The first unlock waits, because that is the only way
                        // to find out; from the second one it is immediate.
                        delay(if (expectUnlock) SCREEN_ON_GRACE_MS else SCREEN_ON_SETTLE_MS)
                        if (expectUnlock) settingsRepository.markUserPresentMissing()
                        runTrace.record(
                            FEATURE,
                            "unlock",
                            if (expectUnlock) "screen-on, no USER_PRESENT within the grace"
                            else "screen-on, this device sends no USER_PRESENT"
                        )
                        playPulseLockSequence()
                    }
                }
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

        val filters = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            filters,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        runTrace.record(FEATURE, "service", "created, listening for unlock and screen-on")
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
                runTrace.record(FEATURE, "skip", "feature or glyph service disabled")
                return@launch
            }

            if (settingsRepository.isCurrentlyInQuietHours()) {
                Log.d(TAG, "Quiet hours active – skipping sequence")
                runTrace.record(FEATURE, "skip", "quiet hours")
                return@launch
            }

            // An unlock is something the user just did, so it interrupts whatever
            // is still playing — usually the screen-off animation, seconds old.
            if (!featureCoordinator.acquireNow(GlyphFeature.PULSE_LOCK)) {
                Log.d(TAG, "Strip still busy after interrupt (owner: ${featureCoordinator.currentOwner.value}) – skipping")
                runTrace.record(
                    FEATURE,
                    "skip",
                    "strip busy, owner=${featureCoordinator.currentOwner.value}"
                )
                return@launch
            }

            val animationId = settingsRepository.getPulseLockAnimationId()
            val duration = glyphAnimationManager.runCapMs(
                animationId,
                settingsRepository.getPulseLockDuration()
            )

            Log.d(TAG, "Sequence start – anim=$animationId duration=${duration}ms")
            runTrace.record(FEATURE, "start", "anim=$animationId cap=${duration}ms")

            // Promote to foreground for the duration of the sequence
            startForeground(NOTIF_ID, buildNotification())

            try {
                val animJob = launch(Dispatchers.Default) {
                    glyphAnimationManager.playPulseLockAnimation()
                }

                // Hard-stop watchdog: cancels the animation job and kills audio
                val watchdogJob = launch {
                    delay(duration.milliseconds)
                    Log.d(TAG, "Duration limit reached – stopping animation & audio")
                    animJob.cancelAndJoin()          // cancel, then wait for cleanup
                    glyphAnimationManager.stopAnimations()
                }

                // Animation finished naturally before the watchdog fired – cancel it
                watchdogJob.cancel()

                // Wait for the animation before leaving the try block.
                //
                // `finally` in a coroutine body runs when the *body* exits, not
                // when the coroutine does, so without this join the lock is
                // released — and `release()` blanks the strip — while the
                // animation is still running. Charging, Low Battery, NFC, Screen
                // Off and Power Peek all join here; Glow Gate did not, which is
                // why a scripted animation died at its first frame.
                animJob.join()

            } catch (e: Exception) {
                Log.e(TAG, "Error in Glow Gate sequence", e)
                runTrace.record(FEATURE, "error", e.toString())
            } finally {
                featureCoordinator.release(GlyphFeature.PULSE_LOCK)
                // Demote from foreground but keep service alive for future unlocks
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
            getString(R.string.pulse_lock_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.pulse_lock_notification_title))
            .setContentText(getString(R.string.pulse_lock_notification_text))
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