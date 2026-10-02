package com.bleelblep.glyphsharge.services

import android.app.Activity
import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.glyph.audio.AudioAnalyzer
import com.bleelblep.glyphsharge.glyph.audio.AudioFrame
import com.bleelblep.glyphsharge.glyph.audio.AudioFrameFeed
import com.bleelblep.glyphsharge.glyph.audio.CaptureStatus
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import com.bleelblep.glyphsharge.glyph.audio.PlaybackAudioSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Draws the music that is playing on the Glyph strip, for as long as there is
 * any.
 *
 * The only service here with no trigger: the others wait for an event, this one
 * watches a stream. Everything else about it follows the same contract.
 *
 * ### Why the strip is handed over in slices
 *
 * Every other feature is a short burst — a blink, a sweep, a bar — and all of
 * them take the strip through [GlyphFeatureCoordinator.withStrip], which gives
 * up rather than fight for it. A visualiser that held the strip for a whole
 * track would therefore swallow charging animations, low-battery alerts and
 * screen-off effects, and those are exactly the ones a user does not want to
 * miss.
 *
 * So the music paints for [SLICE_MS], releases, waits [YIELD_GAP_MS] and takes
 * the strip again. A feature whose trigger lands in the gap — 4% of the time —
 * is served immediately, and the rest wait at most [SLICE_MS]. The cost is a
 * dark seam once every 1.5 s, which is the trade the plan settled on: the
 * alternative is a feature that silently never fires.
 *
 * ### What decides that music is playing
 *
 * [AudioManager.isMusicActive] is the fast signal, but it is a coarse one, and
 * a player that reports nothing would leave the visualiser permanently dark.
 * The analysed level is therefore the real gate, and the flag is only there to
 * start drawing a moment earlier. The capture itself runs for as long as the
 * service is enabled, which is what makes the level available at all.
 */
@AndroidEntryPoint
class MusicVisualizerService : FeatureService() {

    companion object {
        private const val TAG = "MusicVisualizerService"
        private const val NOTIF_CHANNEL_ID = "MusicVisualizerChannel"
        private const val NOTIF_ID = 1020

        const val ACTION_START = "com.bleelblep.glyphsharge.MUSIC_VIZ_START"

        /** The Activity result code, carried in with the consent. */
        const val EXTRA_CONSENT_RESULT_CODE = "com.bleelblep.glyphsharge.CONSENT_CODE"

        /** The consent `Intent` itself, nested inside the start intent. */
        const val EXTRA_CONSENT_DATA = "com.bleelblep.glyphsharge.CONSENT_DATA"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.MUSIC_VIZ_STOP"

        /** How long the visualiser owns the strip before yielding it. */
        const val SLICE_MS = 1500L

        /** The dark seam between two slices. Long enough to release, short enough to hide. */
        const val YIELD_GAP_MS = 60L

        /** How long to wait for the strip when another feature is holding it. */
        const val ACQUIRE_TIMEOUT_MS = 300L

        /** How often the loop re-checks music, quiet hours and the screen state. */
        const val IDLE_POLL_MS = 500L

        /** How long a `PARTIAL_WakeLock` is held for one slice. */
        const val WAKE_LOCK_MS = SLICE_MS + 2000L
    }

    /**
     * This service's own registry entry, which owns the run gate — my switch
     * and the master Glyph switch. Here the gate is polled rather than asked
     * once, so it must not be spelled out at each of the places the loop exits.
     */
    private val spec = FeatureSpecs.of(GlyphFeature.MUSIC_VISUALIZER)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    /** The real capture: a `MediaProjection` token the user approved. */
    @Inject lateinit var source: PlaybackAudioSource

    /** Only for devices that still open the global output mix. See its KDoc. */
    @Inject lateinit var legacyAnalyzer: AudioAnalyzer

    /**
     * Whichever of the two above is actually running, for reading.
     *
     * The service drives the captures — consent, start, stop — but every
     * *read* of a frame goes through this, so the gate below and the painters
     * behind it cannot end up looking at different streams.
     */
    @Inject lateinit var audioFeed: AudioFrameFeed

    private var watchJob: Job? = null

    private lateinit var audioManager: AudioManager

    // Identity

    override val isRunnable: Boolean
        get() = spec.isRunnable(settingsRepository)

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = NOTIF_CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.music_viz_channel_name

    @get:StringRes
    override val channelDescriptionRes: Int get() = R.string.music_viz_description

    override val wakeLockTag: String get() = "GlyphSharge:MusicVisualizer"

    override val tag: String get() = TAG

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    /**
     * No restart when the app is swiped away.
     *
     * A `MediaProjection` token cannot be obtained from the background, so a
     * restart could only ever fail — and would leave the user with a card that
     * says "on" and a dark strip.
     */
    override val restartsOnTaskRemoval: Boolean get() = false

    override fun onStartCommandAfterGate(intent: Intent?): Int {

        // Order matters and is not obvious. `getMediaProjection` refuses to
        // return a token unless a foreground service of type mediaProjection is
        // already running, so the consent is adopted *here*, after the
        // `startForeground` the base has already done — not in the Activity
        // that received it.
        if (intent?.hasExtra(EXTRA_CONSENT_RESULT_CODE) == true) {
            val code = intent.getIntExtra(EXTRA_CONSENT_RESULT_CODE, Activity.RESULT_CANCELED)
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>(EXTRA_CONSENT_DATA)
            source.onConsent(code, data)
        }

        startWatching()
        return START_NOT_STICKY
    }

    override fun onFeatureDestroying() {
        // The WatchLock, both scopes and `stopForegroundCompat` are the base's.
        stopWatching()
    }

    // region The loop

    /**
     * The capture itself is not started here.
     *
     * A `MediaProjection` token can only be obtained from an Activity result,
     * so the Activity asks for consent and hands the token over; by the time
     * this service runs, the choice has already been made. What the service
     * owns is the reaction to it — drawing while frames arrive, and saying out
     * loud why it is not when they do not.
     */
    private fun startWatching() {
        if (watchJob?.isActive == true) return

        val sensitivity = settingsRepository.getMusicVizSensitivity()
        audioFeed.setGain(sensitivity)
        watchJob = serviceScope.launch { watch() }
    }

    /**
     * Falls back to the `Visualizer` path, once, if playback capture failed
     * outright and this device still opens the output mix.
     *
     * `FAILED` is the only status worth trying: a missing permission or a
     * revoked token is a user decision, and re-prompting for a microphone to
     * work around it would be worse than saying what went wrong.
     */
    private fun tryLegacyFallback(): Boolean {
        if (source.status.value != CaptureStatus.FAILED) return legacyAnalyzer.isCapturing
        Log.i(TAG, "Playback capture failed; trying the legacy Visualizer path")
        return legacyAnalyzer.start()
    }

    private suspend fun CoroutineScope.watch() {
        while (isActive) {
            when {
                shouldStop() -> return
                // `isSwapping` first, and it must be there at all: a re-granted
                // token is adopted asynchronously, and reading a not-yet-
                // capturing source as a failure ends this loop for good.
                source.isSwapping -> delay(IDLE_POLL_MS.milliseconds)
                !source.isCapturing && !tryLegacyFallback() -> {
                    // No capture, and none will appear on its own: a token
                    // cannot be fetched from here. Say why, and stop polling.
                    showNotification(getString(captureProblemText(source.status.value)))
                    return
                }
                !screenAllowsVisualization() -> {
                    showNotification(getString(R.string.music_viz_notif_screen_on))
                    delay(IDLE_POLL_MS.milliseconds)
                }
                isMusicPlaying() -> drawInSlices()
                else -> {
                    showNotification(getString(R.string.music_viz_notif_waiting))
                    delay(IDLE_POLL_MS.milliseconds)
                }
            }
        }
    }

    /** Paints until something stops us, handing the strip over between slices. */
    private suspend fun CoroutineScope.drawInSlices() {
        showNotification(getString(R.string.music_viz_notif_playing, currentModeLabel()))

        while (isActive && !shouldStop() && screenAllowsVisualization() && isMusicPlaying()) {
            // A busy strip — `withStrip` returns null without running the block —
            // is treated exactly like a finished slice: either way the visualiser
            // hands the strip back, waits [YIELD_GAP_MS] and tries again, rather
            // than spinning on tryLock.
            try {
                featureCoordinator.withStrip(
                    owner = GlyphFeature.MUSIC_VISUALIZER,
                    timeoutMs = ACQUIRE_TIMEOUT_MS,
                    // The WakeLock is only ever taken inside the block, so
                    // releasing it here can only undo something that happened.
                    onRelease = { runCatching { if (wakeLock.isHeld) wakeLock.release() } },
                ) {
                    runCatching { wakeLock.acquire(WAKE_LOCK_MS) }
                    // The slice cap. `runCapped` stops the painter at SLICE_MS
                    // the way the old `delay` + `cancelAndJoin()` did, and leaves
                    // the strip blank — the dark seam the design below trades for
                    // not swallowing charging, low battery and screen-off.
                    glyphAnimationManager.runCapped(SLICE_MS) {
                        glyphAnimationManager.playMusicVisualizerAnimation()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Music visualiser slice failed", e)
            }

            if (isActive) delay(YIELD_GAP_MS.milliseconds)
        }
    }

    /**
     * Every reason to stop, in one place so the loop and the slice agree.
     *
     * Quiet hours count for the same reason as in the other services: a
     * visualiser is exactly what a quiet-hours schedule is meant to silence.
     */
    private fun shouldStop(): Boolean =
        !spec.isRunnable(settingsRepository) ||
            settingsRepository.isCurrentlyInQuietHours()

    @Suppress("DEPRECATION")
    private fun screenAllowsVisualization(): Boolean {
        if (!settingsRepository.getMusicVizScreenOffOnly()) return true
        return !(getSystemService(POWER_SERVICE) as PowerManager).isInteractive
    }

    private fun isMusicPlaying(): Boolean {
        val systemSaysMusic =
            runCatching { audioManager.isMusicActive }.getOrDefault(defaultValue = false)
        // Through the feed, not `source`: on a phone where the legacy path is
        // the one that opened, the primary stream is silent by construction and
        // this would then wait on `isMusicActive` alone.
        return (systemSaysMusic) || (audioFeed.latest().rms > AudioFrame.SILENCE_FLOOR)
    }

    private fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        source.stop()
        legacyAnalyzer.stop()
    }

    // endregion

    // region Notification

    /**
     * The message for a capture that is not running.
     *
     * Each state asks for something different, so each gets its own words.
     * [CaptureStatus.FAILED] deliberately does not guess: "could not be
     * started" is the truth, and a message that claimed to know why would be
     * wrong at least as often as right.
     */
    private fun captureProblemText(status: CaptureStatus): Int = when (status) {
        CaptureStatus.NO_PERMISSION -> R.string.music_viz_notif_no_permission
        CaptureStatus.NEEDS_CONSENT -> R.string.music_viz_notif_needs_consent
        CaptureStatus.TOKEN_REVOKED -> R.string.music_viz_notif_token_revoked
        else -> R.string.music_viz_notif_failed
    }

    /** The mode name, or the feature name when a script is selected. */
    private fun currentModeLabel(): String {
        val mode = MusicVisualizationMode.of(settingsRepository.getMusicVizAnimationId())
            ?: return getString(R.string.music_viz_title)
        return mode.displayName
    }

    override fun buildNotification(): Notification =
        buildNotification(getString(R.string.music_viz_notif_waiting))

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.music_viz_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .setSilent(true)
            .build()

    /**
     * `startForeground` with the types the manifest declares.
     *
     * `mediaProjection` is the part that matters: it is the only type that
     * lets the app read other apps' audio, and a foreground service that
     * claims a type it cannot back is refused. The call is run-caught because
     * a service that throws here dies silently — the feature simply stops and
     * the card still claims it is on.
     */
    override fun startForegroundCompat(notification: Notification) {
        runCatching {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.onFailure { Log.e(tag, "startForeground refused", it) }
    }

    // endregion
}
