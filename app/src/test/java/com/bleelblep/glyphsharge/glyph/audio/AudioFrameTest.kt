package com.bleelblep.glyphsharge.glyph.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for the read-only frame every animation and every Lua script sees.
 *
 * The interesting part is [AudioFrame.bandsInto]: it is called once per
 * rendered frame with the number of LED segments the phone actually has, so it
 * has to cope with any ratio of segments to bands — and it writes into the
 * caller's array, which the tests hold to by identity.
 */
class AudioFrameTest {

    private fun frame(
        bands: FloatArray,
        rms: Float = 0f,
        seq: Long = 1L,
        beat: Boolean = false,
    ) = AudioFrame(
        seq = seq,
        bands = bands,
        bass = 0f,
        mid = 0f,
        treble = 0f,
        rms = rms,
        beat = beat,
    )

    private fun bandsOf(vararg values: Float) = FloatArray(values.size) { values[it] }

    // region Silence

    @Test
    fun `the frame every reader sees first is silent and empty`() {
        val silent = AudioFrame.SILENT

        assertEquals(AudioFrame.BAND_COUNT, silent.bands.size)
        assertEquals(0f, silent.rms, 0f)
        assertTrue(silent.bands.all { it == 0f })
        assertTrue(silent.isSilent)
        assertFalse(silent.beat)
        assertEquals(0L, silent.seq)
    }

    @Test
    fun `silence is measured against the floor, not against zero`() {
        // A real recording is never truly silent, and a floor at exactly zero
        // leaves the last frame's bars stuck on the strip.
        assertTrue(frame(FloatArray(4), rms = 0f).isSilent)
        assertTrue(frame(FloatArray(4), rms = AudioFrame.SILENCE_FLOOR - 0.001f).isSilent)
        assertFalse(frame(FloatArray(4), rms = AudioFrame.SILENCE_FLOOR + 0.001f).isSilent)
        assertFalse(frame(FloatArray(4), rms = 1f).isSilent)
    }

    // endregion

    // region Resampling

    @Test
    fun `a group takes the loudest band, not the average`() {
        // With twenty segments and thirty-two bands most groups cover one or
        // two bands. Averaging a group that holds one loud note and one quiet
        // one throws the note away, and a narrow peak is exactly what a viewer
        // reads as a musical note.
        val out = frame(bandsOf(0f, 0f, 0.9f, 0f, 0f, 0f, 0f, 0f)).bandsInto(FloatArray(2))

        assertEquals(0.9f, out[0], 0f)
        assertEquals(0f, out[1], 0f)
    }

    @Test
    fun `more segments than bands still reads every band`() {
        // Five segments over two bands: each band is copied rather than lost
        // to an empty group, because a segment with no band behind it is a
        // dead pixel on the strip.
        val out = frame(bandsOf(0.1f, 0.2f, 0.3f, 0.4f)).bandsInto(FloatArray(8))

        assertEquals(
            listOf(0.1f, 0.1f, 0.2f, 0.2f, 0.3f, 0.3f, 0.4f, 0.4f),
            out.toList(),
        )
    }

    @Test
    fun `fewer segments than bands still reaches the loud one`() {
        val out = frame(bandsOf(0.1f, 0f, 0.0f, 0.9f, 0.0f, 0.0f, 0.2f, 0.0f))
            .bandsInto(FloatArray(3))

        assertEquals(listOf(0.1f, 0.9f, 0.2f), out.toList())
    }

    @Test
    fun `no band is lost when the counts do not divide evenly`() {
        // Whichever way round the two numbers fall, the loudest band of the
        // frame has to reach the strip. A group boundary that skipped it would
        // be one dark segment in the middle of a loud passage, with no error
        // and nothing in the log.
        val bands = bandsOf(0.1f, 0f, 0.0f, 0.0f, 0.9f, 0f, 0.2f)

        listOf(2, 3, 4, 5, 6, 7, 8, 12, 20, 32).forEach { segments ->
            val out = frame(bands).bandsInto(FloatArray(segments))

            assertEquals(
                "$segments segments lost the loudest band",
                0.9f,
                out.max(),
            )
        }
    }

    @Test
    fun `the caller's array is the one that is written`() {
        // The render loop calls this every frame; a fresh array thirty times a
        // second for no reason is the sort of thing that shows up as jank.
        val out = FloatArray(4)
        val returned = frame(FloatArray(4) { 0.5f }).bandsInto(out)

        assertSame(out, returned)
    }

    @Test
    fun `an empty segment list is returned untouched`() {
        val out = FloatArray(0)

        assertSame(out, frame(bandsOf(1f)).bandsInto(out))
    }

    @Test
    fun `a frame with no bands fills the segments with zero`() {
        // Nothing to plot, but the strip must go dark rather than keep the
        // previous frame's brightness.
        val out = frame(FloatArray(0)).bandsInto(FloatArray(4) { 1f })

        assertEquals(listOf(0f, 0f, 0f, 0f), out.toList())
    }

    // endregion

    // region Identity

    @Test
    fun `two frames with identical numbers are not equal`() {
        // Deliberate, and worth pinning: a data class would compare `bands`
        // by array identity, so two identical frames would compare unequal and
        // every comparison in the renderer would quietly mean "different".
        // Identity is also the cheaper check for a per-frame object that is
        // replaced rather than mutated.
        val first = frame(FloatArray(AudioFrame.BAND_COUNT) { 0.5f }, seq = 7L)
        val second = frame(FloatArray(AudioFrame.BAND_COUNT) { 0.5f }, seq = 7L)

        assertNotEquals(first, second)
    }

    @Test
    fun `the sequence number is what tells two frames apart`() {
        assertNotEquals(frame(FloatArray(2), seq = 1L).seq, frame(FloatArray(2), seq = 2L).seq)
    }

    @Test
    fun `the console line names the frame and its level`() {
        // Read by the studio's console, so the sequence and the level have to
        // be in it — a column of identical lines is unusable when a script is
        // being debugged frame by frame.
        val text = frame(FloatArray(2), rms = 0.25f, seq = 42L, beat = true).toString()

        assertTrue(text, text.contains("seq=42"))
        assertTrue(text, text.contains("beat=true"))
    }

    @Test
    fun `the console line does not follow the device locale`() {
        // A diagnostic string is read by a person and copied into a bug report,
        // so it has to read the same everywhere. `String.format` without a
        // locale writes `0,250` on any device set to a language that uses a
        // comma for the decimal point — which includes the one this app is
        // written in.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val german = frame(FloatArray(2), rms = 0.25f, seq = 42L).toString()

            Locale.setDefault(Locale.US)
            val english = frame(FloatArray(2), rms = 0.25f, seq = 42L).toString()

            assertEquals(english, german)
            assertTrue(german, german.contains("0.250"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    // endregion
}
