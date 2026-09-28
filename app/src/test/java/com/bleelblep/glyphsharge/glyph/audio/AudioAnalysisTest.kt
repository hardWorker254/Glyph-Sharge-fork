package com.bleelblep.glyphsharge.glyph.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * The calibration every mode's brightness rests on.
 *
 * These exist because it has been wrong, and because when it is wrong the
 * symptom is a strip that draws almost nothing — indistinguishable, to a user
 * or to logcat, from a visualiser that is not running at all. The numbers
 * pinned here are what a real track produces through
 * [PlaybackAudioSource]'s own block size, so they are the ones that matter.
 */
class AudioAnalysisTest {

    private val sampleRate = 44_100

    /** [PlaybackAudioSource]'s `BUFFER_FRAMES`; the transform is sized from it. */
    private val frames = 1_024

    private val channels = 2

    /** Interleaved stereo [frames] of a sine at [hz] and [amplitude] full scale. */
    private fun sine(hz: Float, amplitude: Float): ShortArray {
        val pcm = ShortArray(frames * channels)
        for (i in 0 until frames) {
            val value = amplitude * sin(2.0 * PI * hz * i / sampleRate)
            val sample = (value * AudioAnalysis.PCM_FULL_SCALE).toInt().toShort()
            pcm[i * channels] = sample
            pcm[i * channels + 1] = sample
        }
        return pcm
    }

    private fun bandsOf(pcm: ShortArray): FloatArray {
        val out = FloatArray(AudioAnalysis.BAND_COUNT)
        val edges = AudioAnalysis.bandEdges(frames / 2, sampleRate)
        AudioAnalysis.bandsFromPcm(pcm, pcm.size, channels, out, edges, FloatArray(frames))
        return out
    }

    @Test
    fun `a note at half scale fills the top of the scale`() {
        val bands = bandsOf(sine(1_000f, 0.5f))
        val peak = bands.max()
        assertTrue("half-scale 1 kHz read as $peak", peak > 0.6f)
    }

    @Test
    fun `a quiet note is dim, not invisible`() {
        val quiet = bandsOf(sine(1_000f, 0.05f)).max()
        assertTrue("a quiet note read as $quiet", quiet > 0.2f)
    }

    @Test
    fun `silence produces no bands at all`() {
        assertEquals(0f, bandsOf(ShortArray(frames * channels)).max(), 0.0001f)
    }

    @Test
    fun `a note lands in the band for its frequency`() {
        // 1 kHz is bin 23 of 512, and the edges are logarithmic, so it belongs
        // to band 17. A linear sweep put it in band 1 and gave the other 30
        // bands the strip to themselves.
        val bands = bandsOf(sine(1_000f, 0.5f))
        val peak = bands.indices.maxByOrNull { bands[it] } ?: -1
        assertTrue("1 kHz peaked in band $peak", peak in 15..21)
    }

    @Test
    fun `the unit curve lifts a typical band out of the dark`() {
        // 0.03 is an ordinary peak for a band's worth of a real track. The
        // curve this replaced read it as 0.07, which an equaliser draws as a
        // single segment at 3% brightness — a dead strip.
        assertTrue(AudioAnalysis.pcmMagnitudeToUnit(0.03f) > 0.3f)
        assertEquals(0f, AudioAnalysis.pcmMagnitudeToUnit(0f), 0.0001f)
        assertEquals(1f, AudioAnalysis.pcmMagnitudeToUnit(1f), 0.0001f)
    }

    @Test
    fun `the level stays linear, so a quiet room is still silence`() {
        // The dB curve belongs on the bands, not here: [AudioFrame.isSilent]
        // is measured against this, and a quiet room would read as -26 dB over
        // the floor — permanently "playing", with the idle animation never
        // reached.
        assertEquals(0f, AudioAnalysis.rmsFromPcm(ShortArray(frames * channels), 0), 0.0001f)
        val room = AudioAnalysis.rmsFromPcm(sine(1_000f, 0.005f), frames * channels)
        assertTrue("a quiet room read as $room", room < AudioFrame.SILENCE_FLOOR)
        val music = AudioAnalysis.rmsFromPcm(sine(1_000f, 0.5f), frames * channels)
        assertTrue("half-scale music read as $music", music > AudioFrame.SILENCE_FLOOR)
    }
}
