package com.bleelblep.glyphsharge.glyph.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import java.io.File
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.utils.LoggingManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Why the capture is in the state it is in.
 *
 * Kept apart rather than collapsed into a boolean because each of these asks
 * the user for something different, and the service has to say which.
 */
enum class CaptureStatus {
    /** Not started, or stopped on purpose. */
    IDLE,

    /**
     * No projection token yet — the system confirmation dialog has to be
     * shown. The normal state after a reboot, because a token is good for one
     * session and nothing else.
     */
    NEEDS_CONSENT,

    /** Frames are arriving. */
    RUNNING,

    /**
     * The token was withdrawn: the Quick Settings chip, a screen lock, another
     * projection session, or the process dying.
     */
    TOKEN_REVOKED,

    /** `RECORD_AUDIO` has not been granted. */
    NO_PERMISSION,

    /** `AudioRecord` could not be created, or reading failed for good. */
    FAILED
}

/**
 * Reads the audio that *other* apps are playing, through a `MediaProjection`
 * token and an `AudioRecord` configured for playback capture.
 *
 * This is the only supported route on Android 14+. The obvious alternative,
 * `android.media.audiofx.Visualizer` on the global output mix, is closed to
 * ordinary apps: on a Nothing Phone 3a it fails with
 * `Cannot initialize Visualizer engine, error: -3` whatever the permission
 * state is, and a `Visualizer` bound to another app's session is no way out
 * either — `AudioPlaybackConfiguration` hands the app a redacted
 * `sessionId: 0`.
 *
 * Only `USAGE_MEDIA` is captured, so a notification chime or a ringtone never
 * reaches the buffer. The PCM becomes a spectrum in memory and is never stored
 * or sent.
 *
 * The surface is deliberately narrow — `stop`, `latest`, `frames`, `status`,
 * `isCapturing`, `setGain`, with `onConsent` in place of `start` — so the
 * visualisations, the Lua `glyph.audio` bindings and the service loop ask "what
 * is playing right now" without ever learning where the answer came from.
 */
@Singleton
class PlaybackAudioSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "PlaybackCapture"

        /** Requested rate; the real one is read back from the recorder. */
        const val REQUESTED_SAMPLE_RATE = 44_100

        /** Interleaved stereo, which is what the recorder is built for. */
        const val CHANNEL_COUNT = 2

        /**
         * *Frames* per read, not samples: the buffer holds this many interleaved
         * stereo frames, and the FFT window is sized from the frame count. Two
         * per frame, so the numbers below are halved again.
         */
        const val BUFFER_FRAMES = 1024

        /** How long a failing read is retried before the recorder is rebuilt. */
        const val READ_RETRY_LIMIT = 3

        /** Pause between read failures, so a dead object is not spun on. */
        const val READ_RETRY_DELAY_MS = 250L

        /** Delay between reads that returned nothing — nothing is playing. */
        const val IDLE_POLL_MS = 250L

        const val DEFAULT_GAIN = 1.0f
        const val MIN_GAIN = 0.5f
        const val MAX_GAIN = 3.0f
    }

    private val _frames = MutableStateFlow(AudioFrame.SILENT)
    val frames: StateFlow<AudioFrame> = _frames.asStateFlow()

    private val _status = MutableStateFlow(CaptureStatus.IDLE)
    val status: StateFlow<CaptureStatus> = _status.asStateFlow()

    val isCapturing: Boolean get() = _status.value == CaptureStatus.RUNNING

    /**
     * `true` while a granted token is being swapped in.
     *
     * Not a capture failure, and that is the whole reason it is exposed: a
     * caller that polls for "is it capturing" would otherwise conclude the
     * session is dead during the few milliseconds the old teardown takes, stop
     * its watch loop, and never come back — leaving the card claiming to work
     * over a strip that never lights again.
     */
    val isSwapping: Boolean get() = swapping

    /** `true` while a projection token is held, which is what enables drawing. */
    val hasToken: Boolean get() = projection != null

    @Volatile
    private var gain: Float = DEFAULT_GAIN

    @Volatile
    private var swapping = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var projection: MediaProjection? = null
    private var callback: MediaProjection.Callback? = null
    private var callbackThread: HandlerThread? = null
    private var recorder: AudioRecord? = null
    private var readerJob: Job? = null

    private var edges: IntArray = IntArray(0)
    private var seq = 0L
    private var readFailures = 0

    // Sized from the frame count so the transform is always a whole number of
    // windows; a size that varies with the read length would need the band
    // edges recomputed on every partial read.
    private val window = FloatArray(BUFFER_FRAMES)
    private val raw = FloatArray(AudioFrame.BAND_COUNT)
    private val envelope = FloatArray(AudioFrame.BAND_COUNT)
    private val beatDetector = BeatDetector()

    private var pcm = ShortArray(BUFFER_FRAMES * CHANNEL_COUNT)

    /**
     * The user's sensitivity.
     *
     * Behind a volatile because the setter comes from a dialog and the reader
     * is an I/O thread: a plain field would be an unsynchronised cross-thread
     * read on every frame.
     */
    fun setGain(value: Float) {
        gain = value.coerceIn(MIN_GAIN, MAX_GAIN)
    }

    fun gain(): Float = gain

    /**
     * Whether the app may capture playback at all.
     *
     * Checked before the consent dialog so the user is not walked through a
     * system prompt that then fails anyway.
     */
    fun canCapture(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The `Intent` that shows the system confirmation.
     *
     * Returned rather than launched from here: a projection can only be
     * obtained from an Activity result.
     *
     * Built with [MediaProjectionConfig.createConfigForDefaultDisplay] so the
     * prompt does not ask which app to share. That question comes from app
     * screen sharing, and this feature never creates a `VirtualDisplay` — it
     * only wants the audio that other apps are playing. Answering it would be
     * both a pointless extra step and misleading: choosing a single app would
     * read as "only this one", which is not what the capture does.
     */
    fun consentIntent(): Intent {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        return runCatching {
            manager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        }.getOrElse {
            // Pre-14 devices, or a manufacturer that rejects the config: the
            // plain intent still works, it just asks the extra question.
            manager.createScreenCaptureIntent()
        }
    }

    /**
     * Adopts a token the user just approved and starts reading.
     *
     * @return `true` when the token was accepted and its adoption has started.
     *   A `false` leaves the reason in [status] so the caller can say it out
     *   loud rather than guess. `true` is not "recording": the recorder is
     *   built a moment later, and [isSwapping] is `true` until it is.
     */
    fun onConsent(resultCode: Int, data: Intent?): Boolean {
        if (!canCapture()) {
            report("RECORD_AUDIO has not been granted")
            _status.value = CaptureStatus.NO_PERMISSION
            return false
        }
        if (data == null) {
            report("Consent returned no data")
            _status.value = CaptureStatus.NEEDS_CONSENT
            return false
        }

        val manager = context.getSystemService(MediaProjectionManager::class.java)
        val granted = runCatching { manager.getMediaProjection(resultCode, data) }
            .onFailure { report("getMediaProjection refused: ${it.message}") }
            .getOrNull()
        if (granted == null) {
            _status.value = CaptureStatus.FAILED
            return false
        }

        // A token is good for one use, and two live recorders would double the
        // audio and the battery. The previous session is therefore torn down
        // and *awaited* before the new recorder is built — see [adopt].
        adopt(granted)
        return true
    }

    /**
     * Swaps in a freshly granted projection.
     *
     * Suspending, and the whole reason this is not done inline: a playback
     * capture cannot be built while the previous `AudioRecord` is still open.
     * Releasing the old one from a side coroutine and building immediately is
     * a race the platform resolves by refusing the new recorder, which leaves
     * the service reporting that it was drawing with a dead capture behind it,
     * and re-consenting can never recover it.
     *
     * The teardown therefore finishes before [startRecorder], and [isSwapping]
     * covers the gap so a caller polling the status knows to wait rather than
     * to give up.
     */
    private fun adopt(granted: MediaProjection) {
        swapping = true
        scope.launch {
            try {
                val stale = detachSession()
                runCatching { stale.job?.cancelAndJoin() }
                releaseNow(stale)
                if (!isActive) return@launch
                projection = granted
                watchToken(granted)
                startRecorder(granted)
            } finally {
                swapping = false
            }
        }
    }

    /** Ends the session and releases the token, the recorder and the callback. */
    fun stop() {
        val stale = detachSession()
        releaseSession(stale)
        resetFrameState()
        _status.value = CaptureStatus.IDLE
    }

    /**
     * The newest frame, or [AudioFrame.SILENT] when there is none.
     *
     * Convenience over [frames] so a caller cannot forget the identity check
     * and animate a frame from three minutes ago.
     */
    fun latest(): AudioFrame = _frames.value

    // region Session

    /**
     * Watches for a revoked token.
     *
     * Not optional: the system stops the capture when the user taps the Quick
     * Settings chip, locks the screen, or starts another projection. Without
     * this the recorder keeps returning zeros, and the visualiser would look
     * alive while lying.
     */
    private fun watchToken(projection: MediaProjection) {
        val thread = HandlerThread("glyph-capture", Process.THREAD_PRIORITY_AUDIO)
            .also { it.start() }
        callbackThread = thread

        val handler = object : MediaProjection.Callback() {
            override fun onStop() {
                report("Projection token revoked")
                stop()
                // `stop` leaves the status at IDLE. The distinction matters: the
                // user withdrew something, rather than switching it off, and
                // the two want different words.
                _status.value = CaptureStatus.TOKEN_REVOKED
            }
        }
        callback = handler
        projection.registerCallback(handler, Handler(thread.looper))
    }

    private fun startRecorder(projection: MediaProjection): Boolean {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(REQUESTED_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        // Re-checked here rather than trusted from the caller: this is the call
        // the lint flags, and the answer can change between the consent dialog
        // and this line.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            report("RECORD_AUDIO was revoked before the recorder was built")
            _status.value = CaptureStatus.NO_PERMISSION
            return false
        }

        val created = runCatching {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .build()
        }.onFailure { report("AudioRecord could not be built: ${it.message}") }.getOrNull()

        if (created == null) {
            _status.value = CaptureStatus.FAILED
            return false
        }

        val state = runCatching { created.state }.getOrDefault(AudioRecord.STATE_UNINITIALIZED)
        if (state != AudioRecord.STATE_INITIALIZED) {
            report("AudioRecord did not initialise (state=$state)")
            runCatching { created.release() }
            _status.value = CaptureStatus.FAILED
            return false
        }

        // `startRecording()` reports nothing — in API 36 it returns `Unit`, the
        // same rework that made `Visualizer`'s setters return a status — so
        // the only way to know it took is to ask the recorder afterwards.
        runCatching { created.startRecording() }
            .onFailure { report("startRecording threw: ${it.message}") }

        val recording = runCatching { created.recordingState }
            .getOrDefault(AudioRecord.RECORDSTATE_STOPPED)
        if (recording != AudioRecord.RECORDSTATE_RECORDING) {
            report("recordingState is $recording after startRecording")
            runCatching { created.release() }
            _status.value = CaptureStatus.FAILED
            return false
        }

        // The real rate is read back rather than assumed: a player at 48 kHz
        // would otherwise come out sharp and every band edge would be wrong.
        val rate = runCatching { created.sampleRate }.getOrNull()?.takeIf { it > 0 }
            ?: REQUESTED_SAMPLE_RATE
        edges = AudioAnalysis.bandEdges(BUFFER_FRAMES / 2, rate)
        pcm = ShortArray(BUFFER_FRAMES * CHANNEL_COUNT)

        recorder = created
        readFailures = 0
        beatDetector.reset()
        _status.value = CaptureStatus.RUNNING
        report("Capture started: rate=$rate window=$BUFFER_FRAMES bands=${edges.size - 1}")

        readerJob = scope.launch { readLoop(created) }
        return true
    }

    private suspend fun CoroutineScope.readLoop(active: AudioRecord) {
        while (isActive && recorder === active) {
            val read = withContext(Dispatchers.IO) { active.read(pcm, 0, pcm.size) }

            when {
                read < 0 -> if (!surviveReadError(active, read)) return
                // Nothing playing. What the strip shows is the service's
                // business — it watches the frame level — and here there is
                // simply nothing to do.
                read == 0 -> delay(IDLE_POLL_MS.milliseconds)
                else -> {
                    readFailures = 0
                    publish(read)
                }
            }
        }
    }

    /**
     * Reacts to a failed read.
     *
     * `ERROR_DEAD_OBJECT` means the underlying audio object is gone, which no
     * retry can fix. Anything else is worth a few attempts, because a busy
     * device drops reads without dropping the session.
     *
     * @return `true` to keep going
     */
    private suspend fun CoroutineScope.surviveReadError(active: AudioRecord, code: Int): Boolean {
        readFailures++
        report("read() returned $code (failure $readFailures)")

        if (code == AudioRecord.ERROR_DEAD_OBJECT || readFailures > READ_RETRY_LIMIT) {
            val token = projection ?: run {
                resetFrameState()
                _status.value = CaptureStatus.TOKEN_REVOKED
                return false
            }
            // The recorder is rebuilt in place and the token kept: it is still
            // valid, and asking the user to consent again over a transient
            // failure would be the more annoying outcome.
            val stale = detachSession()
            releaseSession(stale)
            return startRecorder(token)
        }

        delay(READ_RETRY_DELAY_MS.milliseconds)
        return isActive && this@PlaybackAudioSource.recorder === active
    }

    private fun publish(length: Int) {
        val now = SystemClock.elapsedRealtime()

        AudioAnalysis.bandsFromPcm(pcm, length, CHANNEL_COUNT, raw, edges, window)
        AudioAnalysis.applyEnvelope(envelope, raw, gain)

        val bass = AudioAnalysis.bassOf(envelope)
        val level = AudioAnalysis.rmsFromPcm(pcm, length)
        if (level > AudioFrame.SILENCE_FLOOR && seq % 40 == 0L) {
            report("SIGNAL rms=$level peak=${envelope.max()} bass=$bass")
        }
        _frames.value = AudioFrame(
            seq = ++seq,
            bands = envelope.copyOf(),
            bass = bass,
            mid = AudioAnalysis.midOf(envelope),
            treble = AudioAnalysis.trebleOf(envelope),
            rms = level,
            beat = beatDetector.update(bass, now),
            timestampMs = now,
        )
    }

    /**
     * Clears the session fields and hands back what they held.
     *
     * Returning the pieces is the point: teardown is slow — it waits for the
     * reader — and must not run against fields a *new* session has already
     * refilled.
     */
    private fun detachSession(): Session = Session(
        recorder = recorder.also { recorder = null },
        projection = projection.also { projection = null },
        callback = callback.also { callback = null },
        thread = callbackThread.also { callbackThread = null },
        job = readerJob.also { readerJob = null },
    )

    private class Session(
        val recorder: AudioRecord?,
        val projection: MediaProjection?,
        val callback: MediaProjection.Callback?,
        val thread: HandlerThread?,
        val job: Job?,
    )

    /**
     * Releases a detached session.
     *
     * The reader is cancelled and *awaited* before the recorder is touched:
     * releasing an `AudioRecord` out from under a thread that is inside
     * `read` is a native crash, not an exception.
     */
    private fun releaseSession(session: Session) {
        if (scope.isActive) {
            scope.launch {
                runCatching { session.job?.cancelAndJoin() }
                releaseNow(session)
            }
        } else {
            // Nothing left to await on, so release synchronously rather than
            // leak the audio objects.
            releaseNow(session)
        }
    }

    private fun releaseNow(session: Session) {
        session.recorder?.let { current ->
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        session.projection?.let { current ->
            session.callback?.let { handler ->
                runCatching { current.unregisterCallback(handler) }
            }
            runCatching { current.stop() }
        }
        session.thread?.let { thread -> runCatching { thread.quitSafely() } }
    }

    private fun resetFrameState() {
        envelope.fill(0f)
        beatDetector.reset()
        _frames.value = AudioFrame.SILENT
    }

    // endregion

    /**
     * The same line to logcat and to the app's own log file.
     *
     * Two channels on purpose: logcat is gone the moment the process is killed
     * or the buffer wraps, and the file is the one a user can actually share
     * when they report "the visualiser does nothing".
     */
    private fun report(message: String) {
        Log.w(TAG, message)
        LoggingManager.log("AUDIO", message)
        runCatching { File(context.cacheDir, "viz_diag.txt").appendText("$message\n") }
    }
}
