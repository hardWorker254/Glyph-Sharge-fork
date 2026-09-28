package com.bleelblep.glyphsharge.glyph.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the kick-drum detector that drives `glyph.audio.beat`.
 *
 * The clock is passed in rather than read, so these run instantly and
 * deterministically: every test here is a different arrangement of level and
 * timestamp, not a different amount of sleeping. A 30-second wait per case
 * would make the suite slow *and* would still not say anything a table of
 * numbers does not.
 *
 * What is being pinned is the shape of the rule — "the low end just jumped"
 * against a rolling average — and not the constants, which are tuning.
 */
class BeatDetectorTest {

    /**
     * A realistic starting point.
     *
     * The detector compares against a `lastBeatMs` of 0, so a clock that
     * starts there would swallow the first kick of the first track as though
     * it arrived inside the cooldown. Real callers pass a monotonic clock that
     * has been up for hours, so the tests do the same.
     */
    private val start = 10_000L

    private fun detector(
        minLevel: Float = 0.18f,
        ratio: Float = 1.35f,
        cooldownMs: Long = 180L,
    ) = BeatDetector(minLevel, ratio, cooldownMs)

    /** Feeds a level per frame and returns the timestamps on which one beat. */
    private fun BeatDetector.beatsWhile(
        frames: Int,
        stepMs: Long = 200L,
        from: Long = start,
        bassAt: (Int) -> Float,
    ): List<Long> {
        var now = from
        return buildList {
            repeat(frames) {
                if (update(bassAt(it), now)) add(now)
                now += stepMs
            }
        }
    }

    // region The rolling average

    @Test
    fun `the first frame of a run is never a beat`() {
        // The average is still 0 on the first frame, so *any* level would look
        // like an infinite jump. Without this, the first loud note of a track
        // counts and every note after it does not.
        assertFalse(detector().update(1f, start))
    }

    @Test
    fun `a kick above the running average is a beat`() {
        val detector = detector()

        detector.update(0.1f, start)

        assertTrue("a jump from 0.1 to 0.9 is a kick", detector.update(0.9f, start + 200))
    }

    @Test
    fun `a level below the threshold is never a beat`() {
        // No amount of loudness relative to the average helps if the music is
        // not there at all.
        val beats = detector().beatsWhile(frames = 50) { 0.05f }

        assertEquals(emptyList<Long>(), beats)
    }

    @Test
    fun `a constant level does not keep beating`() {
        // This is what "just jumped" rules out. A level that stays put is
        // carried by the rolling average within a second or two, and from then
        // on it is no longer a jump.
        val beats = detector().beatsWhile(frames = 80) { 0.8f }

        assertTrue("beat on ${beats.size} of 80 frames", beats.size < 30)
        assertTrue("still beating at the end: $beats", beats.none { it > start + 12_000 })
    }

    @Test
    fun `a small wobble above the average is not a beat`() {
        // The detector is for kicks, not for a bass line moving inside the mix.
        val detector = detector()
        // Warm the average up to the level first.
        detector.beatsWhile(frames = 40) { 0.6f }

        val beats = detector.beatsWhile(frames = 40, from = start + 20_000) { i ->
            if (i % 2 == 0) 0.65f else 0.6f
        }

        assertEquals(emptyList<Long>(), beats)
    }

    // endregion

    // region Cooldown

    @Test
    fun `a second kick inside the cooldown is ignored`() {
        val detector = detector()
        detector.update(0.1f, start)

        assertTrue(detector.update(0.9f, start + 200))
        // 100 ms later: a real kick drum is not this close, and a hi-hat on
        // the off-beat is.
        assertFalse(detector.update(0.9f, start + 300))
    }

    @Test
    fun `a kick after the cooldown is detected again`() {
        val detector = detector()
        detector.update(0.1f, start)

        assertTrue(detector.update(0.9f, start + 200))
        assertTrue("300 ms is past the 180 ms cooldown", detector.update(0.9f, start + 500))
    }

    @Test
    fun `the cooldown is measured on the clock the caller passes`() {
        // If the cooldown were read from the wall clock instead, a caller
        // replaying frames — the studio's preview, a captured trace — would
        // get a different answer every run.
        val detector = detector()
        detector.update(0.1f, start)
        detector.update(0.9f, start + 200)

        assertFalse(detector.update(0.9f, start + 300))
    }

    // endregion

    // region Tuning

    @Test
    fun `the threshold is configurable`() {
        val detector = detector(minLevel = 0.5f)
        detector.update(0.1f, start)

        assertFalse("0.4 is under this detector's threshold", detector.update(0.4f, start + 200))
    }

    @Test
    fun `the cooldown is configurable`() {
        val detector = detector(cooldownMs = 1_000)
        detector.update(0.1f, start)
        assertTrue(detector.update(0.9f, start + 200))

        // Fine for the default 180 ms, refused for this detector's 1000 ms.
        assertFalse(detector.update(0.9f, start + 800))
        assertTrue(detector.update(0.9f, start + 1_300))
    }

    // endregion

    // region Track changes

    @Test
    fun `reset forgets the track that came before`() {
        val detector = detector()
        detector.update(0.1f, start)
        assertTrue(detector.update(0.9f, start + 200))

        detector.reset()

        // The first frame after a reset is always a false, whatever the level:
        // the next track must not be measured against the one before it.
        assertFalse(detector.update(0.9f, start + 400))
    }

    @Test
    fun `a detector can find a beat again after a reset`() {
        val detector = detector()
        detector.reset()

        detector.update(0.1f, start)
        assertTrue(detector.update(0.9f, start + 200))
    }

    // endregion
}
