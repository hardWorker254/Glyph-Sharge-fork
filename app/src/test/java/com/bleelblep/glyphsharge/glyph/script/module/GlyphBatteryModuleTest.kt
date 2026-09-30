package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for `require("glyph.battery")`.
 *
 * The interesting part is [level]: a percentage is a number, but a script can
 * only act on it as a number of segments, and that number is different on a
 * Phone (3a) than on a Phone (1). A count written by hand works on the phone
 * its author happened to be holding and lights the wrong LEDs everywhere else.
 */
class GlyphBatteryModuleTest {

    /** Runs [source] against a host reporting [percent] and [charging]. */
    private fun run(source: String, percent: Int, charging: Boolean = true): FakeHost {
        val host = FakeHost(percent = percent, charging = charging)
        val result = engineAt(hour = 12, host = host).run(
            """
            local battery = require("glyph.battery")
            ${source.trimIndent()}
            """.trimIndent()
        )
        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
        return host
    }

    @Test
    fun `percent and charging come from the device`() {
        run(
            """
            assert(battery.percent == 64, 'the host reports 64')
            assert(battery.charging == true, 'and reports charging')
            """,
            percent = 64,
            charging = true
        )
        run(
            """
            assert(battery.charging == false, 'and it can report not charging')
            """,
            percent = 12,
            charging = false
        )
    }

    @Test
    fun `level turns a percentage into segments of the C strip`() {
        // The test profile's C strip is ten channels, so the counts below are
        // literal rather than derived — a change to the fixture is a change to
        // what this asserts.
        run("assert(battery.level() == 6, '64% of ten segments')", percent = 64)
        run("assert(battery.level() == 0, 'an empty battery is no segments')", percent = 0)
        run("assert(battery.level() == 10, 'a full battery is all of them')", percent = 100)
    }

    @Test
    fun `level never leaves the strip`() {
        // The host can report a percentage a real device would not, and a
        // script that lights `level()` segments must not be handed more than
        // the strip has.
        run("assert(battery.level() == 10, 'above 100 clamps to the whole strip')", percent = 140)
        run("assert(battery.level() == 0, 'below 0 clamps to none')", percent = -20)
    }

    @Test
    fun `bar draws the battery along the strip`() {
        val host = run("battery.bar(20)", percent = 64)

        // Ten segments, 64% of them lit, the bar filling as it goes.
        assertEquals(6, host.frames.last().first.size)
        assertEquals(4000, host.frames.last().second)
        assertEquals(10, host.frames.size)
    }
}
