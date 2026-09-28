package com.bleelblep.glyphsharge.glyph.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.utils.LoggingManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy capture through `android.media.audiofx.Visualizer`, kept as a fallback
 * for devices that still open the global output mix.
 *
 * > [!WARNING]
 * > **Refused on Android 14+ for ordinary apps.** On a Nothing Phone 3a the
 * > constructor fails with `Cannot initialize Visualizer engine, error: -3`
 * > whatever the permission state, and the app's own
 * > `AudioPlaybackConfiguration` reports a redacted `sessionId: 0`, so there is
 * > no per-app session to bind to either.
 * >
 * > [PlaybackAudioSource] is the real path. This stays so a phone where the
 * > output mix *is* still open gets a working visualiser rather than a dead
 * > card, and the service only tries it after playback capture has failed
 * > outright.
 *
 * Shares [CaptureStatus] with the primary source, so the service reports one
 * vocabulary and never branches on which mechanism produced a frame.
 *
 * ### The API this is written against
 *
 * `Visualizer` was reworked in API 36: the two abstract listener classes are
 * gone in favour of the [Visualizer.OnDataCaptureListener] *interface*,
 * `getMaxCaptureSize()` is now the static `getCaptureSizeRange()`, and
 * `setEnabled` / `setCaptureSize` report a status code rather than a value —
 * so none of them can be used through Kotlin property syntax, and every one is
 * checked.
 */
@Singleton
class AudioAnalyzer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        /** `Visualizer`'s magic session id for the whole output mix. */
        const val SESSION_GLOBAL_MIX = 0

        const val DEFAULT_GAIN = 1.0f
        const val MIN_GAIN = 0.5f
        const val MAX_GAIN = 3.0f

        private const val TAG = "AudioAnalyzer"

        /**
         * How often the platform is asked for a buffer, in Hz.
         *
         * Above the ~30 fps the strip is drawn at, so the envelope always has a
         * newer sample to work from than the last one it displayed. Asking for
         * `getMaxCaptureRate()` instead would flood a binder thread with data
         * nothing ever draws.
         */
        const val CAPTURE_RATE_HZ = 50

        /** Preferred FFT size. Reduced if the device cannot go that high. */
        const val PREFERRED_CAPTURE_SIZE = 1024

        /** Used when the device does not report a rate, as some do not. */
        const val FALLBACK_SAMPLING_RATE = 44_100

        /**
         * Consecutive loud waveforms seen beside an empty FFT before the FFT is
         * declared unreadable and the waveform takes over.
         *
         * Only ever reached while the music is audibly loud, see
         * [captureListener]: silence produces a flat FFT on every device, so a
         * flat FFT on its own says nothing about whether the stream works.
         */
        const val FLAT_FRAMES_BEFORE_FALLBACK = 12

        /** Waveform level above which "the FFT is empty" is a real defect. */
        const val LOUD_ENOUGH = 0.05f
    }

    private val _frames = MutableStateFlow(AudioFrame.SILENT)
    val frames: StateFlow<AudioFrame> = _frames.asStateFlow()

    private val _status = MutableStateFlow(CaptureStatus.IDLE)
    val status: StateFlow<CaptureStatus> = _status.asStateFlow()

    val isCapturing: Boolean get() = _status.value == CaptureStatus.RUNNING

    /**
     * `false` once the FFT stream has been shown to be unusable and the
     * waveform is being banded instead. The spectrum then tracks loudness
     * rather than pitch, which still reacts to music.
     */
    val isFftUsable: Boolean get() = usingFft

    /** `true` while a projection token is held. Always `false` for this path. */
    val hasToken: Boolean get() = false

    @Volatile
    private var gain: Float = DEFAULT_GAIN

    @Volatile
    private var usingFft = true

    private var visualizer: Visualizer? = null
    private var edges: IntArray = IntArray(0)
    private var seq = 0L
    private var flatFftFrames = 0

    private val envelope = FloatArray(AudioFrame.BAND_COUNT)
    private val raw = FloatArray(AudioFrame.BAND_COUNT)
    private val beatDetector = BeatDetector()

    /**
     * The user's sensitivity.
     *
     * Behind a volatile rather than passed per frame because the setter comes
     * from a dialog and the reader is a binder thread: a plain field would be
     * an unsynchronised cross-thread read on every frame.
     */
    fun setGain(value: Float) {
        gain = value.coerceIn(MIN_GAIN, MAX_GAIN)
    }

    fun gain(): Float = gain

    /**
     * Opens the capture. Idempotent, and never throws: a visualiser that takes
     * down the service it lives in is worse than one that does nothing.
     */
    fun start(): Boolean {
        if (visualizer != null) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            report("RECORD_AUDIO has not been granted")
            _status.value = CaptureStatus.NO_PERMISSION
            return false
        }

        val created = runCatching { Visualizer(SESSION_GLOBAL_MIX) }
            .onFailure { report("Visualizer could not be created: ${it.message}") }
            .getOrNull()
        if (created == null) {
            _status.value = CaptureStatus.FAILED
            return false
        }

        val size = applyCaptureSize(created)
        if (size == null) {
            release(created)
            _status.value = CaptureStatus.FAILED
            return false
        }

        // Both buffers: the waveform is what the fallback bands when the FFT
        // cannot be read, and asking for it up front is cheaper than attaching
        // a second listener later.
        val attached = runCatching {
            created.setDataCaptureListener(captureListener, CAPTURE_RATE_HZ, true, true)
        }.onFailure { report("setDataCaptureListener rejected: ${it.message}") }
            .getOrDefault(Visualizer.ERROR)
        if (attached != Visualizer.SUCCESS) {
            report("setDataCaptureListener returned $attached")
            release(created)
            _status.value = CaptureStatus.FAILED
            return false
        }

        val enabled = runCatching { created.setEnabled(true) }
            .onFailure { report("Capture could not be enabled: ${it.message}") }
            .getOrDefault(Visualizer.ERROR)
        if (enabled != Visualizer.SUCCESS) {
            report("setEnabled returned $enabled")
            release(created)
            _status.value = CaptureStatus.FAILED
            return false
        }

        val rate = samplingRateOf(created)
        edges = AudioAnalysis.bandEdges(size / 2, rate)
        visualizer = created
        usingFft = true
        flatFftFrames = 0
        beatDetector.reset()
        _status.value = CaptureStatus.RUNNING
        report("Capture started: size=$size rate=$rate bands=${edges.size - 1}")
        return true
    }

    /** Closes the capture and puts [AudioFrame.SILENT] back on the stream. */
    fun stop() {
        val current = visualizer ?: return
        visualizer = null
        release(current)
        envelope.fill(0f)
        beatDetector.reset()
        _frames.value = AudioFrame.SILENT
        _status.value = CaptureStatus.IDLE
        Log.i(TAG, "Capture stopped")
    }

    /**
     * The newest frame, or [AudioFrame.SILENT] when there is none.
     *
     * Convenience over [frames] so a caller cannot forget the identity check
     * and animate a frame from three minutes ago.
     */
    fun latest(): AudioFrame = _frames.value

    /**
     * Picks a capture size the device accepts, or `null` when it accepts none.
     *
     * `setCaptureSize` reports a status rather than throwing, so the chosen
     * value is only trusted once the call has said so; otherwise the device's
     * own current size is read back.
     */
    private fun applyCaptureSize(visualizer: Visualizer): Int? {
        val range = runCatching { Visualizer.getCaptureSizeRange() }.getOrNull()
        if (range == null || range.size < 2) {
            report("Capture size range unavailable")
            return null
        }

        val (minSize, maxSize) = range[0] to range[1]
        if (maxSize <= 0 || minSize > maxSize) {
            report("Nonsensical capture size range: $minSize..$maxSize")
            return null
        }

        val wanted = PREFERRED_CAPTURE_SIZE.coerceIn(minSize, maxSize)
        val status = runCatching { visualizer.setCaptureSize(wanted) }
            .getOrDefault(Visualizer.ERROR)

        return if (status == Visualizer.SUCCESS) {
            wanted
        } else {
            // The device refused our size; whatever it is already set to may
            // still be usable, so ask it rather than giving up.
            val actual = runCatching { visualizer.captureSize }.getOrNull() ?: 0
            report("setCaptureSize($wanted) returned $status, device reports $actual")
            actual.takeIf { it >= minSize }
        }
    }

    private fun samplingRateOf(visualizer: Visualizer): Int =
        runCatching { visualizer.samplingRate }.getOrNull()?.takeIf { it > 0 }
            ?: FALLBACK_SAMPLING_RATE

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
    }

    private fun release(visualizer: Visualizer) {
        runCatching { visualizer.setDataCaptureListener(null, CAPTURE_RATE_HZ, true, true) }
        runCatching { visualizer.setEnabled(false) }
        runCatching { visualizer.release() }
    }

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int,
        ) {
            val buffer = waveform ?: return
            val level = AudioAnalysis.rmsFromWaveform(buffer)

            if (usingFft) {
                // The only place the FFT is judged: a loud waveform beside an
                // empty spectrum is a stream that cannot be read, whereas an
                // empty spectrum on its own is only silence.
                if (level > LOUD_ENOUGH && ++flatFftFrames >= FLAT_FRAMES_BEFORE_FALLBACK) {
                    Log.w(TAG, "FFT is empty while the waveform is loud; banding the waveform instead")
                    usingFft = false
                    beatDetector.reset()
                }
                return
            }

            publish(buffer, isFft = false, levelOverride = level)
        }

        override fun onFftDataCapture(
            visualizer: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int,
        ) {
            val buffer = fft ?: return
            if (!usingFft) return

            if (AudioAnalysis.maxComponentMagnitude(buffer) > 0.01f) flatFftFrames = 0
            publish(buffer, isFft = true)
        }
    }

    private fun publish(buffer: ByteArray, isFft: Boolean, levelOverride: Float? = null) {
        val now = SystemClock.elapsedRealtime()

        if (isFft) {
            if (edges.size < 2) return
            AudioAnalysis.bandsFromFft(buffer, raw, edges)
        } else {
            AudioAnalysis.bandsFromWaveform(buffer, raw)
        }
        AudioAnalysis.applyEnvelope(envelope, raw, gain)

        val bass = AudioAnalysis.bassOf(envelope)
        _frames.value = AudioFrame(
            seq = ++seq,
            bands = envelope.copyOf(),
            bass = bass,
            mid = AudioAnalysis.midOf(envelope),
            treble = AudioAnalysis.trebleOf(envelope),
            rms = levelOverride ?: AudioAnalysis.rmsFromFft(buffer),
            beat = beatDetector.update(bass, now),
            timestampMs = now
        )
    }
}
