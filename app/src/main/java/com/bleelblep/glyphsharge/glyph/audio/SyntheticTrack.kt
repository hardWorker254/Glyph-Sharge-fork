package com.bleelblep.glyphsharge.glyph.audio

import kotlin.math.exp
import kotlin.math.sin

/**
 * A plausible spectrum for the settings preview.
 *
 * Built from two sine sweeps and a low bump travelling across the bands, so
 * every mode has something to react to. A flat frame would make six modes
 * look identical, and a real capture is not available in a dialog.
 *
 * It depends on nothing but [AudioFrame] and [AudioAnalysis], which is what
 * lets it stand on its own here rather than hanging off the animation
 * machinery: a preview track is audio data, not a way of drawing one.
 */
internal class SyntheticTrack {
    private val bands = FloatArray(AudioFrame.BAND_COUNT)
    private var seq = 0L

    fun next(): AudioFrame {
        val t = seq / 30f
        for (i in bands.indices) {
            val x = i / (bands.size - 1f)
            val sweep = 0.5f + 0.5f * sin(TWO_PI * (x * 1.5f - t * 0.7f))
            val kick = exp(-((x - (t * 0.35f % 1f)) * (x - (t * 0.35f % 1f))) / 0.01f)
            bands[i] = (0.15f + 0.6f * sweep * sweep + 0.45f * kick).coerceIn(0f, 1f)
        }
        seq++
        return AudioFrame(
            seq = seq,
            bands = bands.copyOf(),
            bass = AudioAnalysis.bassOf(bands),
            mid = AudioAnalysis.midOf(bands),
            treble = AudioAnalysis.trebleOf(bands),
            rms = 0.55f,
            beat = seq % 30 == 0L,
            timestampMs = 0L
        )
    }

    private companion object {
        const val TWO_PI = 2f * Math.PI.toFloat()
    }
}
