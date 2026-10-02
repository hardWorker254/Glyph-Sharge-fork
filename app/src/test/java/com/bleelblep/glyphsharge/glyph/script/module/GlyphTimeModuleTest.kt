package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.LuaScriptEngine
import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Tests for `require("glyph.time")`, against a pinned clock.
 *
 * Every field is live, so the only question these can answer honestly is what
 * a script sees at a chosen instant. The clock is therefore injected — a test
 * that waited for 22:00 would have been a test that never ran, and one that
 * read the real clock would pass at 2am and fail at 3pm.
 */
class GlyphTimeModuleTest {

    /** Runs [source] as if it were [hour]:[minute] on the clock. */
    private fun at(hour: Int, minute: Int, source: String) {
        val result = engineAt(hour = hour, minute = minute).run(
            """
            local time = require("glyph.time")
            ${source.trimIndent()}
            """.trimIndent(),
        )
        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `minuteOfDay equals hour times sixty plus minute`() {
        at(13, 45, "assert(time.minuteOfDay == time.hour * 60 + time.minute, '13:45')")
        at(0, 0, "assert(time.minuteOfDay == 0, '00:00')")
        at(23, 59, "assert(time.minuteOfDay == 1439, '23:59')")
    }

    @Test
    fun `isNight starts at 22 00`() {
        at(21, 59, "assert(time.isNight == false, '21:59 is still day')")
        at(22, 0, "assert(time.isNight == true, '22:00 is the first minute of night')")
    }

    @Test
    fun `isNight ends at 06 00`() {
        // "From 22:00 to 06:00" is a duration, so 06:00 is the moment the night
        // ends and the first minute of the day. Half-open, like a lamp on a
        // timer: off at 22:00, on again at 06:00.
        at(5, 59, "assert(time.isNight == true, '05:59 is still night')")
        at(6, 0, "assert(time.isNight == false, '06:00 is day again')")
        at(12, 0, "assert(time.isNight == false, 'noon is not night')")
    }

    @Test
    fun `the brightness levels are ordered and reachable`() {
        at(
            12, 0, """
            assert(time.DAY == glyph.MAX, 'DAY is full brightness')
            assert(time.NIGHT == 0, 'NIGHT is off')
            assert(time.DUSK < time.DAY and time.DUSK > time.NIGHT, 'DUSK is in between')
        """,
        )
    }

    @Test
    fun `the clock is read again on every access`() {
        // A module that snapshotted the clock while it was being built would
        // hand the same hour out for the whole run, and a script that loops on
        // the hour — the only reason to ask for it — would never notice the
        // hour turn over. A clock that moves on each read is the only way to
        // tell those two apart.
        var reads = 0
        val engine = LuaScriptEngine(
            profile = testProfile(),
            host = FakeHost(),
            // Named rather than trailing: the engine has more injectable
            // clocks than one, and a trailing lambda would silently bind to
            // whichever happens to be last.
            wallClock = {
                reads++
                ZonedDateTime.of(2024, 1, 15, 13, reads, 0, 0, ZoneId.systemDefault())
            },
        )

        val result = engine.run(
            """
            local time = require("glyph.time")
            local first = time.minute
            local second = time.minute
            assert(first ~= second, "each read must consult the clock again")
            """.trimIndent(),
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }
}
