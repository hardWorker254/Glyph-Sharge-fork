package com.bleelblep.glyphsharge.glyph.audio

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The maths behind the visualiser, with no Android types in sight.
 *
 * Everything here is a pure function over a byte array, which is what makes the
 * spectrum testable without a phone, a `Visualizer` or a permission. The
 * analyser itself ([AudioAnalyzer]) only moves bytes in and publishes the
 * resulting [AudioFrame].
 *
 * ### The FFT layout
 *
 * `Visualizer`'s FFT callback delivers the transform the platform already
 * computed, as unsigned bytes with 128 standing for zero, real and imaginary
 * parts interleaved: component `k` of the spectrum is `(fft[2k], fft[2k + 1])`.
 * That is the layout every Android visualiser sample uses, and it is assumed
 * here rather than derived.
 *
 * Where a device sends something else the result is still a plausible-looking
 * spectrum that does not track pitch — so [maxComponentMagnitude] lets the
 * analyser notice a stream that is entirely zero and fall back to
 * [bandsFromWaveform], which reacts to loudness whatever the layout is.
 */
object AudioAnalysis {

    /** Bands per frame. Mirrors [AudioFrame.BAND_COUNT]. */
    const val BAND_COUNT = AudioFrame.BAND_COUNT

    /**
     * How fast a bar may rise.
     *
     * Fast, because a slow attack reads as lag: the peak has already passed by
     * the time the bar gets there.
     */
    const val ATTACK = 0.55f

    /**
     * How fast a bar may fall — roughly four times slower than it rose.
     *
     * A symmetric envelope makes a spectrum look like static. The slow release
     * is what makes it look like music.
     */
    const val RELEASE = 0.14f

    /** The largest magnitude a pair of 8-bit components can produce. */
    const val MAX_MAGNITUDE = 181f

    /** dB mapped to 0.0. Quieter than this is indistinguishable from nothing. */
    const val DB_FLOOR = -50f

    /** The largest value a 16-bit sample can hold. */
    const val PCM_FULL_SCALE = 32767f

    /** How much of the spectrum is swept, in Hz. Above this is mostly silence. */
    private const val TOP_HZ = 16_000f

    /** Below this there is no music left, only rumble. */
    private const val BOTTOM_HZ = 30f

    /** How much of Nyquist the top of the sweep is allowed to reach. */
    private const val NYQUIST_MARGIN = 0.95f

    /** Splits one byte at 128 into a signed component. */
    private fun component(byte: Byte): Float = ((byte.toInt() and 0xFF) - 128).toFloat()

    /**
     * The bin index each band starts at, `bandCount + 1` entries.
     *
     * Logarithmic, because a linear sweep spends four fifths of the strip on
     * frequencies no one can hear and squashes everything musical into the
     * first two segments.
     */
    fun bandEdges(
        binCount: Int,
        samplingRate: Int,
        bandCount: Int = BAND_COUNT,
    ): IntArray {
        if ((binCount <= 0) || (samplingRate <= 0) || (bandCount <= 0)) {
            return IntArray(bandCount + 1)
        }

        val nyquist = samplingRate / 2f
        val topHz = min(TOP_HZ, nyquist * NYQUIST_MARGIN).coerceAtLeast(BOTTOM_HZ * 2f)
        val hzPerBin = samplingRate.toFloat() / (binCount * 2f)
        val ratio = (topHz / BOTTOM_HZ).toDouble().pow(1.0 / bandCount)

        val edges = IntArray(bandCount + 1)
        var hz = BOTTOM_HZ.toDouble()
        for (i in 0..bandCount) {
            val bin = (hz / hzPerBin).toInt().coerceIn(0, binCount)
            // Bins must advance: two bands sharing one bin would read the same
            // number twice and leave a visible stripe on the strip.
            edges[i] = if (i > 0) bin.coerceAtLeast(edges[i - 1]) else bin
            hz *= ratio
        }
        edges[bandCount] = binCount
        return edges
    }

    /**
     * Fills [out] with the spectrum in [fft], `0..1` per band.
     *
     * The peak of each band is taken rather than its average: with 20 segments
     * and 32 bands most bands cover a handful of bins, and a narrow peak is
     * exactly what a viewer reads as a musical note.
     */
    fun bandsFromFft(fft: ByteArray, out: FloatArray, edges: IntArray) {
        val components = fft.size / 2
        if (components <= 0) {
            out.fill(0f)
            return
        }

        val magnitudes = FloatArray(components)
        for (bin in 0 until components) {
            val re = component(fft[bin * 2])
            val im = component(fft[(bin * 2) + 1])
            magnitudes[bin] = sqrt((re * re) + (im * im))
        }
        bandsFromMagnitudes(magnitudes, edges, out) { toUnitMagnitude(it) }
    }

    /**
     * The real input path: interleaved 16-bit PCM straight out of `AudioRecord`.
     *
     * Downmixed to mono, windowed, transformed, then banded. [window] is
     * supplied by the caller because this runs dozens of times a second and
     * allocating two arrays per call is exactly the kind of churn that shows
     * up as jank on a busy phone.
     *
     * @param length how many samples of [pcm] are valid; an `AudioRecord` read
     *   may return fewer than the buffer holds
     * @param channels samples per frame — 2 for interleaved stereo
     * @param edges from [bandEdges], computed for the *actual* sample rate and
     *   for the same window size
     * @param window scratch; its size *is* the transform length
     */
    fun bandsFromPcm(
        pcm: ShortArray,
        length: Int,
        channels: Int,
        out: FloatArray,
        edges: IntArray,
        window: FloatArray,
    ) {
        val size = window.size
        val usable = minOf(length, pcm.size)
        val frames = if (channels > 0) usable / channels else usable

        // Cleared rather than partially overwritten: the array is reused, and a
        // short read would otherwise leave the previous frame's tail in place.
        window.fill(0f)
        val count = minOf(frames, size)
        for (i in 0 until count) {
            if (channels <= 1) {
                window[i] = pcm[i] / PCM_FULL_SCALE
                continue
            }
            // Interleaved: frame `i` occupies `i * channels .. i * channels + channels`.
            // Averaged rather than taken from one channel: a visualiser wants
            // the music, not the difference between left and right, and an
            // average keeps a mono source from reading as twice as loud.
            var sum = 0
            for (c in 0 until channels) sum += pcm[(i * channels) + c]
            window[i] = (sum.toFloat() / channels) / PCM_FULL_SCALE
        }

        bandsFromMagnitudes(Fft.magnitudeSpectrum(window, size), edges, out) {
            pcmMagnitudeToUnit(it)
        }
    }

    /** Overall level of a PCM buffer, `0..1`, on a **linear** scale. */
    fun rmsFromPcm(pcm: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0f
        val usable = minOf(length, pcm.size)
        for (i in 0 until usable) {
            val value = pcm[i] / PCM_FULL_SCALE
            sum += value * value
        }
        // Linear on purpose, where [pcmMagnitudeToUnit] is not: this is what
        // [AudioFrame.isSilent] measures, and a dB curve reports a quiet room
        // as -30 dB over the floor — permanently "playing", with the idle
        // animation never reached.
        return (sqrt(sum / usable) * 1.8f).coerceIn(0f, 1f)
    }

    /**
     * Folds a magnitude spectrum into [out], one value per band.
     *
     * The peak of each band is taken rather than its average: with 20 segments
     * and 32 bands most bands cover a handful of bins, and a narrow peak is
     * exactly what a viewer reads as a musical note.
     *
     * @param unit the curve from raw magnitude to `0..1`; the two sources
     *   normalise differently, so the caller says which one it has
     */
    private inline fun bandsFromMagnitudes(
        magnitudes: FloatArray,
        edges: IntArray,
        out: FloatArray,
        unit: (Float) -> Float,
    ) {
        val bins = magnitudes.size
        if (bins <= 0) {
            out.fill(0f)
            return
        }

        var last = 0
        for (band in out.indices) {
            val to = edges.getOrElse(band + 1) { bins }.coerceIn(0, bins)
            val from = edges.getOrElse(band) { last }.coerceIn(0, to)
            last = to

            var peak = 0f
            for (bin in from until to) {
                if (magnitudes[bin] > peak) peak = magnitudes[bin]
            }
            out[band] = unit(peak)
        }
    }

    /**
     * Normalises a transform magnitude to `0..1`.
     *
     * dB over [DB_FLOOR], for the same reason [toUnitMagnitude] is: a linear
     * scale pegs every loud band at full brightness and loses the difference
     * between a kick and a hiss.
     *
     * A magnitude from [Fft.magnitudeSpectrum] is already normalised to `0..1`
     * — a full-scale sine at a bin centre is `0.5`, half of which the window
     * takes — so there is no [MAX_MAGNITUDE] to divide out, and the curve is
     * the plain `20·log10`. A full-scale note therefore lands at 0.88 here
     * rather than at the ceiling, which is the intended reading: the top of
     * the scale is reserved for a band that is louder than a sine at full
     * scale, and a strip that pins every band bright has nothing to show.
     *
     * > [!IMPORTANT]
     * > This is where the sensitivity of the whole visualiser is decided. The
     * > earlier exponential treated a typical band's `0.03` as `0.07`, so an
     * > equaliser drew two segments at 3% brightness — indistinguishable from
     * > a dead strip — and the only modes that showed anything were the three
     * > with an explicit brightness floor. On the dB scale that same band
     * > lands at `0.4`.
     */
    fun pcmMagnitudeToUnit(magnitude: Float): Float {
        if (magnitude <= 0.0001f) return 0f
        val db = 20f * log10(magnitude)
        return ((db - DB_FLOOR) / -DB_FLOOR).coerceIn(0f, 1f)
    }

    /**
     * The time-domain fallback: loudness per slice of the waveform.
     *
     * It carries no pitch, so `BARS` on it looks like a VU meter rather than a
     * spectrum. That is the point — it keeps every mode reacting to the music
     * on a device whose FFT stream cannot be read.
     */
    fun bandsFromWaveform(wave: ByteArray, out: FloatArray) {
        if (wave.isEmpty()) {
            out.fill(0f)
            return
        }
        val perBand = (wave.size / out.size).coerceAtLeast(1)
        for (band in out.indices) {
            val from = band * perBand
            if (from >= wave.size) {
                out[band] = 0f
                continue
            }
            val to = min(from + perBand, wave.size)
            var sum = 0f
            for (i in from until to) {
                val value = component(wave[i])
                sum += value * value
            }
            out[band] = toUnitMagnitude(sqrt(sum / (to - from)))
        }
    }

    /** The loudest component pair in [fft]; `0f` when the stream carries nothing. */
    fun maxComponentMagnitude(fft: ByteArray): Float {
        val components = fft.size / 2
        var peak = 0f
        for (bin in 0 until components) {
            val re = component(fft[bin * 2])
            val im = component(fft[(bin * 2) + 1])
            val magnitude = sqrt((re * re) + (im * im))
            if (magnitude > peak) peak = magnitude
        }
        return peak
    }

    /** Overall level of [fft], `0..1`. */
    fun rmsFromFft(fft: ByteArray): Float {
        val components = fft.size / 2
        if (components <= 0) return 0f
        var sum = 0f
        for (bin in 0 until components) {
            val re = component(fft[bin * 2])
            val im = component(fft[(bin * 2) + 1])
            sum += (re * re) + (im * im)
        }
        return toUnitMagnitude(sqrt(sum / components))
    }

    /** Overall level of a time-domain buffer, `0..1`. */
    fun rmsFromWaveform(wave: ByteArray): Float {
        if (wave.isEmpty()) return 0f
        var sum = 0f
        for (byte in wave) {
            val value = component(byte)
            sum += value * value
        }
        return toUnitMagnitude(sqrt(sum / wave.size))
    }

    /**
     * Turns a raw magnitude into `0..1` on a dB scale.
     *
     * Linear scaling would peg every loud band at full brightness and leave the
     * difference between a kick and a hiss invisible.
     */
    fun toUnitMagnitude(magnitude: Float): Float {
        if (magnitude <= 0.001f) return 0f
        val db = 20f * log10(magnitude / MAX_MAGNITUDE)
        return ((db - DB_FLOOR) / -DB_FLOOR).coerceIn(0f, 1f)
    }

    /**
     * Folds [raw] into [bands] in place, with a fast attack and a slow release.
     *
     * @param gain the user's sensitivity, applied before the envelope so that
     *   raising it makes peaks taller rather than faster
     */
    fun applyEnvelope(bands: FloatArray, raw: FloatArray, gain: Float) {
        val last = min(bands.size, raw.size)
        for (i in 0 until last) {
            val target = (raw[i] * gain).coerceIn(0f, 1f)
            val current = bands[i]
            val coefficient = if (target > current) ATTACK else RELEASE
            bands[i] = current + ((target - current) * coefficient)
        }
    }

    /** Energy of the lowest third of [bands] — the part a beat lives in. */
    fun bassOf(bands: FloatArray): Float = energyIn(bands, 0f, 0.33f)

    /** Energy of the middle third. */
    fun midOf(bands: FloatArray): Float = energyIn(bands, 0.33f, 0.66f)

    /** Energy of the top third. */
    fun trebleOf(bands: FloatArray): Float = energyIn(bands, 0.66f, 1f)

    /** Mean of [bands] over the `[from, to)` share of the array. */
    fun energyIn(bands: FloatArray, from: Float, to: Float): Float {
        if (bands.isEmpty()) return 0f
        val start = (from * bands.size).toInt().coerceIn(0, bands.size - 1)
        val end = (to * bands.size).toInt().coerceIn(start + 1, bands.size)
        var sum = 0f
        for (i in start until end) sum += bands[i]
        return sum / (end - start)
    }
}

/**
 * Decides when a kick drum has happened.
 *
 * Energy-based rather than interval-based on purpose: a fixed tempo is wrong for
 * every other track and every other recording, whereas "the low end just
 * jumped" is right for all of them. The rolling average is what makes "just
 * jumped" mean anything — without it the first loud note of a track counts as a
 * beat and every note after it does not.
 */
class BeatDetector(
    private val minLevel: Float = 0.18f,
    private val ratio: Float = 1.35f,
    private val cooldownMs: Long = 180L,
) {
    private companion object {
        /** ~600 ms of history at 30 fps, which covers a slow tempo. */
        const val AVERAGE_ALPHA = 0.08f
    }

    private var average = 0f
    private var lastBeatMs = 0L
    private var started = false

    /** Call when the music stops, so the next track is not measured against this one. */
    fun reset() {
        average = 0f
        lastBeatMs = 0L
        started = false
    }

    /** `true` on the single frame where a beat is detected. */
    fun update(bass: Float, nowMs: Long): Boolean {
        average += (bass - average) * AVERAGE_ALPHA
        if (!started) {
            started = true
            return false
        }
        if ((nowMs - lastBeatMs) < cooldownMs) return false
        if ((bass < minLevel) || (average <= 0.001f)) return false
        if (bass > (average * ratio)) {
            lastBeatMs = nowMs
            return true
        }
        return false
    }
}
