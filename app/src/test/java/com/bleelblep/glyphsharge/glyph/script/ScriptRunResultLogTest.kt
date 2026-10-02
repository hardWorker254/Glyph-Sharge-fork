package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.sensor.SensorControl
import com.bleelblep.glyphsharge.glyph.script.module.FakeHost
import com.bleelblep.glyphsharge.glyph.script.module.testProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that a script's own output survives the run that produced it.
 *
 * `print` and `glyph.log` are the only way a script can say anything, and until
 * the lines rode out on [ScriptRunResult] they were written to logcat and
 * dropped: the studio console was built from the outcome alone, so a script
 * debugging itself was debugging into a void. These pin the four things that
 * has to be true for the console to show them — the lines arrive, they keep
 * their level, a silent script says so with an empty list rather than a null,
 * and they were read before the session was torn down.
 */
class ScriptRunResultLogTest {

    private fun run(
        source: String,
        host: FakeHost = FakeHost(),
        sensorControl: SensorControl = SensorControl.NONE,
    ): ScriptRunResult = LuaScriptEngine(
        profile = testProfile(),
        host = host,
        sensorControl = sensorControl,
    ).run(source)

    @Test
    fun `print puts its output on the result`() {
        val result = run(
            """
            print("starting")
            print("strip has " .. #glyph.ch.c .. " segments")
            """.trimIndent(),
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals(
            listOf("starting", "strip has 10 segments"),
            result.logLines.map { it.text },
        )
    }

    @Test
    fun `print is an info line, and glyph log keeps the level it was given`() {
        val result = run(
            """
            local log = require("glyph.log")
            print("progress")
            log.info("also progress")
            log.warn("battery is low")
            """.trimIndent(),
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals(
            listOf(LogLevel.INFO, LogLevel.INFO, LogLevel.WARN),
            result.logLines.map { it.level },
        )
    }

    @Test
    fun `glyph log error stops the run and the line is still there`() {
        val host = FakeHost()
        val result = run(
            """
            local log = require("glyph.log")
            log.info("about to give up")
            log.error("nothing to animate")
            print("never reached")
            """.trimIndent(),
            host,
        )

        // Both halves matter: a line that is on the result but did not stop the
        // run would be a lie, and a run that stopped without saying why is the
        // case the whole `error` level exists for. The line after it is the
        // one that must *not* be there, or nothing stopped.
        assertEquals(ScriptStatus.RUNTIME_ERROR, result.status)
        assertEquals(
            listOf(
                ScriptLogLine("about to give up", LogLevel.INFO),
                ScriptLogLine("nothing to animate", LogLevel.ERROR),
            ),
            result.logLines,
        )
        // Nothing was drawn, because `error` raised before the script could.
        assertTrue(host.frames.isEmpty())
    }

    @Test
    fun `a run that says nothing has an empty list, not a null`() {
        val result = run("glyph.set({ 1 }, 1000)")

        // The field is not nullable and the console maps it, so "the script
        // said nothing" has to be a list of length zero — a null here would
        // have to be handled in the one place that reads it, and a default of
        // empty means the many results built without a run can leave it alone.
        assertEquals(emptyList<ScriptLogLine>(), result.logLines)
    }

    @Test
    fun `the lines are read before the session is closed`() {
        // The engine blanks the strip and closes the session in a `finally`,
        // and clears its active slot, so a caller that asked for the log after
        // `run` returned would be reading a session that no longer exists.
        // A run that takes a sensor reference proves the teardown really did
        // run, so the lines below were captured before it rather than after.
        val control = CountingSensorControl()
        val result = run(
            """
            print("about to read the sensor")
            local sensor = require("glyph.sensor")
            local tilt = sensor.magnitude
            print("tilt is " .. tilt)
            """.trimIndent(),
            sensorControl = control,
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals(1, control.starts)
        assertEquals(1, control.stops)
        // The text of the second line is not asserted: it carries a number the
        // VM formats, and this test is about the lines outliving the session,
        // not about how Lua prints a double.
        assertEquals("about to read the sensor", result.logLines.first().text)
        assertTrue(result.logLines.last().text.startsWith("tilt is "))
    }

    /**
     * A [SensorControl] that counts, and nothing else.
     *
     * The real one is a singleton holding a listener and a handler thread,
     * which a JVM test can neither observe nor clean up. What matters here is
     * only that the release half of the teardown really happened before the
     * assertions ran.
     */
    private class CountingSensorControl : SensorControl {
        var starts = 0
            private set
        var stops = 0
            private set

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }
}
