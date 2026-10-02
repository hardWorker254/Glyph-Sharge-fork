package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for `require("glyph.util")`.
 *
 * These are the helpers a script reaches for when it is about to do arithmetic
 * by hand, so the interesting cases are the ones nobody writes on purpose: a
 * value past the end of a range, and a range written backwards. A helper that
 * divides by a negative span and hands back NaN would put a NaN brightness in
 * front of the renderer, and that is a black strip with no error anywhere.
 */
class GlyphUtilModuleTest {

    private fun at(source: String) {
        val result = engineAt(hour = 12).run(
            """
            local util = require("glyph.util")
            ${source.trimIndent()}
            """.trimIndent(),
        )
        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    // region clamp

    @Test
    fun `clamp holds a value inside the bounds`() {
        at("assert(util.clamp(5, 0, 10) == 5, 'inside stays put')")
        at("assert(util.clamp(-3, 0, 10) == 0, 'below becomes the low bound')")
        at("assert(util.clamp(99, 0, 10) == 10, 'above becomes the high bound')")
    }

    @Test
    fun `clamp accepts its bounds`() {
        at("assert(util.clamp(0, 0, 10) == 0, 'the low bound itself')")
        at("assert(util.clamp(10, 0, 10) == 10, 'the high bound itself')")
    }

    @Test
    fun `clamp survives a reversed range`() {
        // Written the wrong way round, the comparison would be always false and
        // the value would come back untouched.
        at("assert(util.clamp(5, 10, 0) == 5, 'a reversed range still clamps')")
        at("assert(util.clamp(99, 10, 0) == 10, 'and still bounds from above')")
    }

    // endregion

    // region lerp

    @Test
    fun `lerp at both ends`() {
        at("assert(util.lerp(0, 100, 0) == 0, 't of zero is the start')")
        at("assert(util.lerp(0, 100, 1) == 100, 't of one is the end')")
        at("assert(util.lerp(0, 100, 0.5) == 50, 'halfway is halfway')")
    }

    @Test
    fun `lerp holds a position past the ends`() {
        // The hardware would clamp an extrapolated brightness anyway; a script
        // that believes it is still moving while the strip has stopped is the
        // worse of the two failures.
        at("assert(util.lerp(0, 100, 2) == 100, 'past the end is the end')")
        at("assert(util.lerp(0, 100, -1) == 0, 'before the start is the start')")
    }

    // endregion

    // region mapRange

    @Test
    fun `mapRange moves a value between ranges`() {
        at("assert(util.mapRange(0.5, 0, 1, 0, 4000) == 2000, 'audio level to brightness')")
        at("assert(util.mapRange(0, 0, 1, 0, 4000) == 0, 'the bottom of the input range')")
        at("assert(util.mapRange(1, 0, 1, 0, 4000) == 4000, 'the top of the input range')")
    }

    @Test
    fun `mapRange with a reversed range`() {
        // Inverting something is the reason to write a range backwards, and
        // the negative span is exactly what makes it come out inverted.
        at("assert(util.mapRange(0, 0, 1, 4000, 0) == 4000, 'reversed output range')")
        at("assert(util.mapRange(1, 0, 1, 4000, 0) == 0, 'and the far end with it')")
        at("assert(util.mapRange(5, 10, 0, 0, 100) == 50, 'a reversed input range still maps')")
    }

    @Test
    fun `mapRange with a degenerate range`() {
        at("assert(util.mapRange(5, 3, 3, 10, 20) == 10, 'no span, so the low end')")
    }

    @Test
    fun `mapRange pins a value outside the input range`() {
        at("assert(util.mapRange(5, 0, 1, 0, 4000) == 4000, 'above the input range')")
        at("assert(util.mapRange(-1, 0, 1, 0, 4000) == 0, 'below the input range')")
    }

    // endregion

    // region shuffle

    @Test
    fun `shuffle keeps every channel and leaves the original alone`() {
        val result = engineAt(hour = 12).run(
            """
            local util = require("glyph.util")
            glyph.seed(42)
            local source = glyph.ch.c
            local shuffled = util.shuffle(source)
            assert(#shuffled == #source, 'nothing may be lost')
            local sum = 0
            for i = 1, #shuffled do sum = sum + shuffled[i] end
            assert(sum == 45, 'the same channels, in another order')
            -- The caller's table is untouched: it is usually a constant, and a
            -- second loop iteration would otherwise work on a drifted order.
            local original = 0
            for i = 1, #source do original = original + source[i] end
            assert(original == 45, 'the source must be intact')
            """,
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `shuffle refuses something that is not a table`() {
        val result = engineAt(hour = 12).run(
            """
            local util = require("glyph.util")
            local ok = pcall(function() util.shuffle(7) end)
            assert(not ok, 'a number is not a list of channels')
            """,
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    // endregion
}
