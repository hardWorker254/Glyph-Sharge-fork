package com.bleelblep.glyphsharge.glyph.audio

import java.util.Locale

/** Which third of the spectrum a reading came from. */
enum class AudioBand {
    BASS,
    MID,
    TREBLE
}

/**
 * One analysed slice of audio: what the glyphs should be showing right now.
 *
 * Every field is already normalised to `0..1`, so no animation ever touches a
 * raw magnitude or has to know what dB means. The visualiser reads
 * [AudioFrame] and nothing else, which is what lets a Lua script and a
 * built-in animation look at exactly the same numbers.
 *
 * Deliberately **not** a data class: [bands] is an array, and the generated
 * `equals` would compare it by identity, so two frames that happen to be
 * identical would compare unequal and every comparison would quietly mean
 * "different". Identity is also the cheaper check for a per-frame object that
 * is replaced rather than mutated.
 */
class AudioFrame(
    /** Monotonic, so a renderer can tell it has already drawn this frame. */
    val seq: Long,
    /** [BAND_COUNT] logarithmically spaced bands, each `0..1`. */
    val bands: FloatArray,
    /** Low band energy — the part a beat is detected from. */
    val bass: Float,
    val mid: Float,
    val treble: Float,
    /** Overall level, `0..1`. What `glyph.audio.level` reports. */
    val rms: Float,
    /** `true` on the frame where a beat was detected. */
    val beat: Boolean,
    /** `SystemClock.elapsedRealtime()` when this frame was produced. */
    val timestampMs: Long,
) {
    /** True when the audio is quiet enough that a bar should reach zero. */
    val isSilent: Boolean get() = rms < SILENCE_FLOOR

    /**
     * Resamples this frame's bands into [out], taking the peak of every group.
     *
     * Peak rather than mean, because with twenty segments and thirty-two bands
     * most groups cover one or two bands, and averaging a group that holds one
     * loud note and one quiet one throws the note away.
     *
     * Writes into a caller's array on purpose: the render loop calls this every
     * frame, and allocating a fresh one thirty times a second for no reason is
     * the sort of thing that shows up as jank on a busy phone.
     */
    fun bandsInto(out: FloatArray): FloatArray {
        if (out.isEmpty()) return out
        if (bands.isEmpty()) {
            out.fill(0f)
            return out
        }
        for (i in out.indices) {
            val from = (i.toLong() * bands.size / out.size).toInt()
            val to = (((i + 1).toLong() * bands.size) / out.size).toInt()
                .coerceIn(from + 1, bands.size)
            var peak = 0f
            for (j in from.toInt() until to) {
                if (bands[j] > peak) peak = bands[j]
            }
            out[i] = peak
        }
        return out
    }

    /**
     * A line for the studio console, read by a person and pasted into bug
     * reports — so it has to read the same on every device.
     *
     * [Locale.ROOT] is what makes that true. Kotlin's `format` otherwise uses
     * the default device locale, and on anything that writes a comma for the
     * decimal point this came out as `rms=0,250` — legible, but a string that
     * differs per phone and cannot be compared between reports.
     */
    override fun toString(): String =
        "AudioFrame(seq=$seq rms=%.3f bass=%.3f mid=%.3f treble=%.3f beat=$beat)".format(
            Locale.ROOT, rms, bass, mid, treble, beat
        )

    companion object {
        /** Bands per frame. Enough resolution for twenty segments, cheap to compute. */
        const val BAND_COUNT = 32

        /**
         * Below this the music is treated as stopped.
         *
         * Not zero: a real recording is never truly silent, and a floor at
         * exactly zero leaves the last frame's bars stuck on the strip.
         */
        const val SILENCE_FLOOR = 0.01f

        /**
         * The frame every reader sees before a capture has produced anything.
         *
         * Sharing one instance matters: the visualiser checks it by identity
         * to decide "no audio yet", and a fresh `AudioFrame` per call would
         * make that check always false.
         */
        val SILENT = AudioFrame(
            seq = 0L,
            bands = FloatArray(BAND_COUNT),
            bass = 0f,
            mid = 0f,
            treble = 0f,
            rms = 0f,
            beat = false,
            timestampMs = 0L
        )
    }
}
