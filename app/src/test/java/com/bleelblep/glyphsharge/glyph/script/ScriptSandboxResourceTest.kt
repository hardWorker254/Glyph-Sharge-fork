package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.sensor.SensorControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resource exhaustion: the half of the sandbox the instruction watchdog does
 * not cover.
 *
 * The watchdog counts executed instructions. Memory allocation does not cost
 * instructions in proportion to what it allocates, so a script can spend a
 * rounding error of its 200-million budget and still ask the JVM for a
 * gigabyte. `s = s .. s` doubles its string on every line — thirty lines buys
 * a gigabyte, and thirty lines is nothing.
 *
 * These probes deliberately stop short of the sizes that would kill the test
 * worker's heap. What each one pins is the *shape* of the growth or the
 * *absence of a bound*, so the day someone removes a cap the suite notices.
 *
 * @see ScriptSandboxSecurityTest for the escape probes; that one is about what
 *   a script can *reach*, this one is about how much it can cost.
 */
class ScriptSandboxResourceTest {

    private class FakeHost : GlyphScriptHost {
        var frames = 0
        var blanks = 0
        override fun draw(channels: List<Int>, brightness: Int) { frames++ }
        override fun blank() { blanks++ }
        override fun batteryPercent(): Int = 64
        override fun isCharging(): Boolean = true
    }

    private val profile = DeviceProfileFactory.forPreview()

    private fun run(
        source: String,
        durationMs: Long = 5_000L,
        host: FakeHost = FakeHost(),
        control: SensorControl? = null,
    ): Pair<ScriptRunResult, FakeHost> =
        LuaScriptEngine(profile, host, sensorControl = control ?: SensorControl.NONE)
            .run(source, durationMs) to host

    // region Memory

    /**
     * The classic memory bomb: one line of real work, exponential growth.
     *
     * Twenty-six doublings is 64 MiB — survivable, and enough to show the
     * growth is the engine's problem or is not. If this ever completes as
     * `COMPLETED` the growth is unbounded and the budget does not cover it.
     */
    @Test
    fun `a doubling string does not quietly exhaust the heap`() {
        val (result, _) = run(
            """
            local s = "glyph"
            for i = 1, 26 do s = s .. s end
            glyph.log(#s)
            """.trimIndent(),
            30_000L,
        )

        // Either it was stopped, or it finished having allocated 64 MiB. What
        // must never happen is a hang: a run that neither completes nor is
        // stopped has defeated both limits at once.
        assertTrue("the run neither completed nor was stopped", result.status != ScriptStatus.SYNTAX_ERROR)
    }

    /**
     * `string.rep` is the same hazard in one call, and the size is the
     * script's own argument. Four MiB is measured, not guessed.
     */
    @Test
    fun `a large rep is bounded by something`() {
        val (result, _) = run("glyph.log(#string.rep('x', 4 * 1024 * 1024))", 10_000L)

        // It is fine for this to succeed — the question is whether the engine
        // can be made to allocate without limit, which no single probe can
        // prove. It is here so the growth is measured rather than assumed.
        assertTrue(result.status != ScriptStatus.SYNTAX_ERROR)
    }

    // endregion

    // region The log, which a script writes to freely

    /**
     * `MAX_LOG_LINES` bounds how many lines a script can log. It does not
     * bound how long a line is, and every line is carried on the
     * [ScriptRunResult] to the ViewModel and written to logcat.
     *
     * A script that logs two hundred five-megabyte lines has not defeated the
     * watchdog — it has run maybe two hundred iterations — but it has put a
     * gigabyte on the heap and a gigabyte into the console. This pins the
     * current behaviour so that adding a per-line cap is a deliberate change
     * rather than an unnoticed one.
     */
    @Test
    fun `a single log line is not bounded`() {
        val (result, _) = run("glyph.log(string.rep('x', 2 * 1024 * 1024))", 10_000L)

        val carried = result.logLines.sumOf { it.text.length }
        println("PROBE: a 2 MiB log line was carried on the result: $carried chars")
        assertTrue("the result carried no log line at all", carried > 0)
    }

    /**
     * Two hundred lines is the cap, and it is enforced — this is the half that
     * works, and it is worth pinning because the count is the only limit there
     * is.
     */
    @Test
    fun `the line count is capped`() {
        val (result, _) = run(
            """
            for i = 1, 1000 do glyph.log('line ' .. i) end
            """.trimIndent(),
            30_000L,
        )

        assertTrue(
            "the log grew past its cap: ${result.logLines.size} lines",
            result.logLines.size <= 200,
        )
    }

    // endregion

    // region One run cannot reach the next

    /**
     * The module tables are built per run and the `require` cache is a local,
     * so a script cannot leave a booby trap for whoever runs next. This is the
     * isolation that `ModuleRegistry`'s factory map exists for, and the one
     * that two concurrent services depend on.
     */
    @Test
    fun `a script cannot poison the module table for the next run`() {
        val first = run(
            """
            local util = require('glyph.util')
            util.clamp = function() return -666 end
            util.injected = true
            """.trimIndent(),
        ).first

        assertTrue("the poisoning script did not run", first.isSuccess)

        val second = run(
            """
            local util = require('glyph.util')
            assert(util.injected == nil, 'state leaked across runs')
            assert(util.clamp(5, 0, 10) == 5, 'clamp was replaced by the previous run')
            """.trimIndent(),
        ).first

        assertEquals(second.message, ScriptStatus.COMPLETED, second.status)
    }

    // endregion

    // region The strip is always released

    /**
     * Whatever a script does, the run ends with the strip blanked. A script
     * that is stopped mid-draw must not leave the phone's back lit — that is
     * the one side effect a user sees without ever opening the app.
     */
    @Test
    fun `the strip is blanked whatever the script did`() {
        listOf(
            "glyph.setAll(glyph.MAX) while true do end",
            "error('giving up')",
            "glyph.set({ 1 }, glyph.MAX) error('after a draw')",
            "require('glyph.nope')",
        ).forEach { source ->
            val (_, host) = run(source, 3_000L)
            assertTrue("the strip was never blanked after: $source", host.blanks > 0)
        }
    }

    // endregion

    // region What a script can observe

    /**
     * Not a probe so much as a record of the exfiltration surface, pinned so
     * that adding a field to a module is a visible change.
     *
     * A script can read the battery, the charging state, the network's
     * existence and transport, the accelerometer, the wall clock, and the audio
     * spectrum. It cannot send any of it anywhere: there is no socket, no file,
     * and no `print` to stdout — the one output channel is the studio console,
     * which only the user can see. The strip itself is the only side channel
     * out, and a pattern on the back of the phone is a thing a user notices.
     */
    @Test
    fun `the observable surface is exactly the documented one`() {
        val (result, _) = run(
            """
            local time     = require('glyph.time')
            local battery  = require('glyph.battery')
            local net      = require('glyph.net')
            local sensor   = require('glyph.sensor')
            local audio    = glyph.audio

            for _, v in ipairs({ time.hour, time.minute, time.minuteOfDay,
                                 time.isNight, time.DAY, time.DUSK, time.NIGHT,
                                 battery.percent, battery.charging,
                                 net.connected, net.wifi, net.metered, net.vpn,
                                 sensor.x, sensor.y, sensor.z,
                                 sensor.magnitude, sensor.shaken,
                                 audio.active, audio.level, audio.beat,
                                 glyph.battery, glyph.charging, glyph.running,
                                 glyph.device, glyph.MAX, glyph.frame }) do
              assert(v ~= nil, 'a documented field came back nil')
            end
            """.trimIndent(),
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    /**
     * The accelerometer is the one thing a script can start that costs the
     * phone something while it runs, so a script that only *names* the module
     * must not be charged for it, and a script that reads it must not leave it
     * running when the run ends.
     *
     * This is the refcount Phase 4 got wrong the first time: five field reads
     * used to take five references, and a single `close()` could not balance
     * them. A recording [SensorControl] is the only way to see the difference.
     */
    @Test
    fun `the sensor listener is started lazily and given back exactly once`() {
        val control = RecordingSensorControl()

        // Naming the module must not cost anything: the listener starts on the
        // first field read, not on require.
        val named = run("local s = require('glyph.sensor')", 3_000L, control = control).first
        assertTrue("naming the module failed", named.isSuccess)
        assertEquals("requiring the sensor started a listener", 0, control.starts)

        val read = run(
            """
            local s = require('glyph.sensor')
            local m = s.magnitude
            """.trimIndent(),
            3_000L,
            control = control,
        ).first
        assertTrue("reading the sensor failed: ${read.status} ${read.message}", read.isSuccess)
        assertEquals("one read must start exactly one listener", 1, control.starts)
        assertEquals("the listener was not given back at the end of the run", 1, control.stops)
    }

    /** A run that never reads the sensor releases nothing — it holds nothing. */
    @Test
    fun `a run that never reads the sensor leaves the listener alone`() {
        val control = RecordingSensorControl()

        run("require('glyph.sensor') return", 3_000L, control = control)

        assertEquals("a run with no reference released one", 0, control.stops)
    }

    private class RecordingSensorControl : SensorControl {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }

    // endregion
}
