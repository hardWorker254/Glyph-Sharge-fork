package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.glyph.RunTrace
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject


/**
 * Foreground service that listens for the device being unlocked and plays the
 * user-chosen Glow Gate glyph animation (and optional sound).
 */
@AndroidEntryPoint
class PulseLockService : FeatureService() {

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

    /**
     * This service's own registry entry, which owns the run gate — my switch
     * and the master Glyph switch. `onStartCommand`, the OS-driven restart and
     * the unlock handler all had to answer the same pair of questions, and
     * only the first two could disagree without it being visible.
     */
    private val spec = FeatureSpecs.of(GlyphFeature.PULSE_LOCK)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator
    @Inject lateinit var runTrace: RunTrace

    /**
     * One property for the pair of questions `onStartCommand`, the OS-driven
     * restart and the unlock handler all have to answer the same way. They
     * used to answer it separately, and `onTaskRemoved` could disagree with
     * the other two without anything showing it.
     */
    override val isRunnable: Boolean
        get() = spec.isRunnable(settingsRepository)

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = NOTIF_CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.pulse_lock_notification_channel

    override val wakeLockTag: String get() = "GlyphSharge:PulseLockAnimation"
    override val tag: String get() = TAG

    // AtomicReference ensures thread-safe MediaPlayer swap without heavy synchronization

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

                    screenOnFallback = serviceScope.launch {
                        // A phone that has never sent USER_PRESENT is not going
                        // to start now, and waiting out the grace period only
                        // adds latency to the moment the user is watching for.
                        // The first unlock waits, because that is the only way
                        // to find out; from the second one it is immediate.
                        delay((if (expectUnlock) SCREEN_ON_GRACE_MS else SCREEN_ON_SETTLE_MS).milliseconds)
                        if (expectUnlock) settingsRepository.markUserPresentMissing()
                        runTrace.record(
                            FEATURE,
                            "unlock",
                            if (expectUnlock) "screen-on, no USER_PRESENT within the grace"
                            else "screen-on, this device sends no USER_PRESENT",
                        )
                        playPulseLockSequence()
                    }
                }
            }
        }
    }

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()

        val filters = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            filters,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        runTrace.record(FEATURE, "service", "created, listening for unlock and screen-on")
        Log.d(TAG, "PulseLockService created")
    }

    override fun onFeatureDestroying() {
        // Guarded: `onCreate` can fail before the registration completes — the
        // Hilt graph, the notification channel — and an unguarded
        // `unregisterReceiver` then throws out of `onDestroy`, which is an
        // uncaught crash rather than a cleanup failure.
        runCatching { unregisterReceiver(unlockReceiver) }
        screenOnFallback?.cancel()
        Log.d(TAG, "PulseLockService destroyed")
    }

    // Core sequence

    private fun playPulseLockSequence() {
        animationScope.launch {
            // Guard: re-check settings before doing any work
            if (!spec.isRunnable(settingsRepository)) {
                Log.d(TAG, "Feature disabled – skipping sequence")
                runTrace.record(FEATURE, "skip", "feature or glyph service disabled")
                return@launch
            }

            if (settingsRepository.isCurrentlyInQuietHours()) {
                Log.d(TAG, "Quiet hours active – skipping sequence")
                runTrace.record(FEATURE, "skip", "quiet hours")
                return@launch
            }

            try {
                // An unlock is something the user just did, so it interrupts
                // whatever is still playing — usually the screen-off animation,
                // seconds old. Hence `preempt = true`.
                //
                // The service stays foregrounded for as long as the feature is
                // on, so there is nothing to promote or demote around the strip.
                // This used to pass `onRelease = { stopForegroundCompat() }`,
                // and that was not the "keep it alive between events" it looked
                // like: a service whose foreground notification has been removed
                // is an ordinary background service, and every one of these
                // events arrives with the app in the background, so Android
                // reclaimed the service shortly after the first unlock. That is
                // a graceful `stopService`, not a process kill, so `START_STICKY`
                // never brought it back and the card kept saying "on" while
                // nothing happened. Teardown is `FeatureServiceController`'s
                // job and does not depend on the demotion.
                val played = featureCoordinator.withStrip(
                    owner = GlyphFeature.PULSE_LOCK,
                    preempt = true,
                ) {
                    val animationId = settingsRepository.getPulseLockAnimationId()
                    val duration = glyphAnimationManager.runCapMs(
                        animationId,
                        settingsRepository.getPulseLockDuration(),
                    )

                    Log.d(TAG, "Sequence start – anim=$animationId duration=${duration}ms")
                    runTrace.record(FEATURE, "start", "anim=$animationId cap=${duration}ms")

                    // `runCapped` joins the animation before returning, which is what
                    // keeps the lock from being released — and the strip
                    // blanked — while frames are still being drawn. A `finally`
                    // in a coroutine body runs when the *body* exits, not when
                    // the coroutine does. Charging, Low Battery, NFC, Screen
                    // Off and Power Peek all join here.
                    glyphAnimationManager.runCapped(
                        capMs = duration,
                        onTimeout = {
                            Log.d(TAG, "Duration limit reached – stopping animation & audio")
                        },
                    ) {
                        glyphAnimationManager.playPulseLockAnimation()
                    }
                }
                if (played == null) {
                    Log.d(TAG, "Strip still busy after interrupt (owner: ${featureCoordinator.currentOwner.value}) – skipping")
                    runTrace.record(
                        FEATURE,
                        "skip",
                        "strip busy, owner=${featureCoordinator.currentOwner.value}",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Glow Gate sequence", e)
                runTrace.record(FEATURE, "error", e.toString())
            }
        }
    }

    // Notification

    override fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.pulse_lock_notification_title))
            .setContentText(getString(R.string.pulse_lock_notification_text))
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.pulse_lock_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}