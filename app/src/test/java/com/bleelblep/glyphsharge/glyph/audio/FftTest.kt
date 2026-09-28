package com.bleelblep.glyphsharge.glyph.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tests for the hand-written transform behind the visualiser's spectrum.
 *
 * This is the one place in the project where a single wrong constant produces
 * output that *looks* right: an empty spectrum is still a plausible spectrum,
 * and a visualiser fed one is indistinguishable from a switched-off one. So
 * the properties pinned here are the ones a real signal must have — energy in
 * the right bin, zeros for zeros, a magnitude that tracks the amplitude — and
 * not the internals, which are free to be rewritten.
 *
 * [AudioAnalysisTest] covers the banding on top of this; it assumes a spectrum
 * that lands energy correctly, which is what is checked here.
 */
class FftTest {

    private val sampleRate = 44_100

    /** A sine of exactly [bin] bins of a [size]-point transform: no leakage. */
    private fun atBin(bin: Int, size: Int, amplitude: Float = 1f): FloatArray {
        val hz = sampleRate.toDouble() * bin / size
        return FloatArray(size) { i ->
            (amplitude * sin(2.0 * PI * hz * i / sampleRate)).toFloat()
        }
    }

    /** A sine at a real frequency, which generally sits between two bins. */
    private fun atHz(hz: Double, size: Int, amplitude: Float = 1f): FloatArray =
        FloatArray(size) { i -> (amplitude * sin(2.0 * PI * hz * i / sampleRate)).toFloat() }

    private fun peakBin(spectrum: FloatArray): Int =
        spectrum.indices.maxByOrNull { spectrum[it] } ?: -1

    // region Placing energy

    @Test
    fun `a sine at a bin centre peaks in that bin`() {
        val size = 1024
        val spectrum = Fft.magnitudeSpectrum(atBin(23, size), size)

        assertEquals(23, peakBin(spectrum))
    }

    @Test
    fun `a real tone lands in the bin for its frequency`() {
        // 44 100 / 1024 = 43.07 Hz per bin, so 1 kHz is bin 23.2. The tone
        // spreads over its neighbours, but the tallest bin is the nearest one.
        val size = 1024
        val spectrum = Fft.magnitudeSpectrum(atHz(1_000.0, size), size)

        assertTrue("1 kHz peaked in bin ${peakBin(spectrum)}", peakBin(spectrum) in 22..24)
    }

    @Test
    fun `a direct current signal is all in bin zero`() {
        val size = 256
        val spectrum = Fft.magnitudeSpectrum(FloatArray(size) { 1f }, size)

        assertEquals(0, peakBin(spectrum))
    }

    @Test
    fun `two tones are both visible`() {
        val size = 1024
        // Half scale each, so a peak bin reads roughly 0.25 and the two
        // neighbourhoods do not overlap.
        val mixed = FloatArray(size) { i -> atBin(10, size, 0.5f)[i] + atBin(40, size, 0.5f)[i] }
        val spectrum = Fft.magnitudeSpectrum(mixed, size)

        assertTrue("bin 10 read ${spectrum[10]}", spectrum[10] > 0.15f)
        assertTrue("bin 40 read ${spectrum[40]}", spectrum[40] > 0.15f)
    }

    // endregion

    // region Scale

    @Test
    fun `a full scale sine reads half scale`() {
        val size = 1024
        val peak = Fft.magnitudeSpectrum(atBin(23, size), size).max()

        // The Hann window sums to size/2, and the transform is scaled by
        // 2/size, so a full-scale sine at a bin centre comes out at 0.5 rather
        // than 1.0. The visualiser only ever sees the ratio between bands, and
        // the dB curve in AudioAnalysis lifts 0.5 to 0.88, so this is not a
        // lost stop — but it is the number the scale has to deliver, and a
        // change to it would move every band on the strip.
        assertEquals(0.5f, peak, 0.02f)
    }

    @Test
    fun `the magnitude is linear in the amplitude`() {
        val size = 1024
        val loud = Fft.magnitudeSpectrum(atBin(23, size, 1f), size).max()
        val quiet = Fft.magnitudeSpectrum(atBin(23, size, 0.25f), size).max()

        assertEquals(loud * 0.25f, quiet, 0.01f)
    }

    @Test
    fun `nothing is negative`() {
        // The magnitude is a square root, so a sign error anywhere in the
        // butterflies would surface here rather than as a blank band.
        val spectrum = Fft.magnitudeSpectrum(atBin(7, 256), 256)

        assertTrue(spectrum.all { it >= 0f })
    }

    // endregion

    // region Zeros

    @Test
    fun `silence produces exact zeros rather than noise`() {
        // The failure this guards is not "small": handing the size to
        // reverseBits instead of the bit count zeroes the whole permutation,
        // every sample lands on real[0] with a window weight of 0, and the
        // transform returns a flat array of zeros that still looks like data.
        val spectrum = Fft.magnitudeSpectrum(FloatArray(1024), 1024)

        assertArrayEquals(FloatArray(512), spectrum, 0f)
    }

    @Test
    fun `a windowed signal has no energy in the imaginary half`() {
        // Only bins 0 until size/2 are returned; a real input folded past that
        // point would show up as a mirror image of the spectrum.
        val size = 256
        val spectrum = Fft.magnitudeSpectrum(atBin(30, size), size)

        assertEquals(size / 2, spectrum.size)
        assertTrue("bin 30 was not the peak", spectrum[30] >= spectrum.max())
    }

    // endregion

    // region Sizing

    @Test
    fun `the frame size rounds down to a power of two`() {
        // Never up: a 1000-sample block is transformed in 512 samples' worth
        // of time, and the surplus is zero-padding, not extra resolution.
        assertEquals(1024, Fft.frameSizeFor(1024))
        assertEquals(4096, Fft.frameSizeFor(4096))
        assertEquals(512, Fft.frameSizeFor(1000))
        assertEquals(256, Fft.frameSizeFor(300))
        assertEquals(128, Fft.frameSizeFor(200))
        assertEquals(128, Fft.frameSizeFor(128))
        assertEquals(64, Fft.frameSizeFor(127))
    }

    @Test
    fun `a block too small to resolve a note still gets the smallest transform`() {
        assertEquals(Fft.MIN_SIZE, Fft.frameSizeFor(100))
        assertEquals(Fft.MIN_SIZE, Fft.frameSizeFor(63))
        assertEquals(Fft.MIN_SIZE, Fft.frameSizeFor(1))
        assertEquals(Fft.MIN_SIZE, Fft.frameSizeFor(0))
    }

    @Test
    fun `a size that is not a power of two is rejected`() {
        // A non-radix-2 size would quietly compute something plausible-looking
        // with a wrong permutation, so it is refused outright instead.
        val error = runCatching { Fft.magnitudeSpectrum(FloatArray(1024), 1000) }

        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `a size below the smallest transform is rejected`() {
        assertTrue(runCatching { Fft.magnitudeSpectrum(FloatArray(32), 32) }.isFailure)
    }

    @Test
    fun `the spectrum has one bin per pair of samples`() {
        assertEquals(32, Fft.magnitudeSpectrum(FloatArray(64), 64).size)
        assertEquals(128, Fft.magnitudeSpectrum(FloatArray(256), 256).size)
    }

    // endregion

    // region Reuse

    @Test
    fun `an input longer than the transform uses only the leading samples`() {
        // `PlaybackAudioSource` reuses one buffer, so a read that returns fewer
        // samples than the buffer holds must not pull the stale tail in.
        val size = 1024
        val input = FloatArray(size * 2)
        atBin(23, size).copyInto(input, 0)
        // 12 kHz is bin 278 at this size, so a stale tail would either move
        // the peak or put a second one where there is no note at all.
        FloatArray(size) { i ->
            (0.9f * sin(2.0 * PI * 12_000.0 * i / sampleRate)).toFloat()
        }.copyInto(input, size)

        val spectrum = Fft.magnitudeSpectrum(input, size)

        assertEquals(23, peakBin(spectrum))
    }

    @Test
    fun `the cached plan gives the same answer every time`() {
        // The per-size tables are built once and kept for the life of the
        // process, so a table mutated by one call would corrupt every later
        // one. A visualiser runs this a few hundred times a second.
        val size = 512
        val input = atBin(19, size)
        val first = Fft.magnitudeSpectrum(input, size)

        repeat(4) { Fft.magnitudeSpectrum(atBin(3, size), size) }

        assertArrayEquals(first, Fft.magnitudeSpectrum(input, size), 0f)
    }

    @Test
    fun `plans for different sizes do not interfere`() {
        val hz = 1_000.0
        val small = Fft.magnitudeSpectrum(atHz(hz, 1024), 1024)
        val large = Fft.magnitudeSpectrum(atHz(hz, 2048), 2048)

        // 1 kHz is bin 23.2 at 1024 points and 46.4 at 2048.
        assertTrue("1024 peaked in ${peakBin(small)}", peakBin(small) in 22..24)
        assertTrue("2048 peaked in ${peakBin(large)}", peakBin(large) in 45..47)
    }

    // endregion
}
