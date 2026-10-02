package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.sensor.SensorControl
import com.bleelblep.glyphsharge.glyph.sensor.SensorSnapshot
import com.bleelblep.glyphsharge.glyph.script.LuaScriptEngine
import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for `require("glyph.sensor")`, against a pinned accelerometer.
 *
 * Every field is live, so the only question these can answer honestly is what
 * a script is handed for a given reading. The reading is therefore injected
 * rather than measured: a test that asked for the phone to be shaken would
 * pass on one host and fail on every other, and the run would have to last
 * long enough to see it.
 *
 * ### What is deliberately not tested here
 *
 * [com.bleelblep.glyphsharge.glyph.sensor.SensorSource] itself. These suites
 * run without a Robolectric runner and with `isReturnDefaultValues = true`, so
 * `SensorManager` is a stub: registering on it proves nothing about a real
 * device, and a test that passed against a stub would be the most misleading
 * kind of green there is. What is testable without hardware is the
 * script-visible surface — which fields exist, what type they arrive as,
 * whether they move, and above all *when* a run asks the sensor to start and
 * give it back — and that is what these cover, through injected values.
 */
class GlyphSensorModuleTest {

    /** A hard flick: well past the shake threshold, on one axis. */
    private val shaken = SensorSnapshot(
        x = 18.5f,
        y = -3.25f,
        z = 1.5f,
        magnitude = 18.9f,
        shaken = true,
    )

    /** A phone face-up on a desk, and the `STILL` shape it should look like. */
    private val resting = SensorSnapshot(
        x = 0.4f,
        y = -0.2f,
        z = 9.8f,
        magnitude = 9.82f,
        shaken = false,
    )

    /**
     * A [SensorControl] that only remembers what it was told.
     *
     * Counts rather than a list of names: the question is *how many* times a
     * run started and stopped the sensor, and a run that reads a field sixty
     * times must still have started it once. Also the only reason this test
     * can exist — the real source is a `@Singleton` holding a live listener
     * and a `HandlerThread`, which a JVM test could neither observe nor
     * clean up.
     */
    private class RecordingSensorControl : SensorControl {
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

    /** Runs [source] as if the phone were reading [reading]. */
    private fun at(reading: SensorSnapshot, source: String) {
        val result = engineAt(hour = 12, sensor = reading).run(
            """
            local sensor = require("glyph.sensor")
            ${source.trimIndent()}
            """.trimIndent(),
        )
        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    // region What a script is handed

    @Test
    fun `every axis and the magnitude are numbers`() {
        // `sensor.x` used in arithmetic is the whole module, and in Lua a field
        // bound as a function is truthy rather than usable — so a getter that
        // was never called would not break this script, it would quietly
        // produce nonsense.
        at(
            resting, """
            assert(type(sensor.x) == 'number', 'x must be a number')
            assert(type(sensor.y) == 'number', 'y must be a number')
            assert(type(sensor.z) == 'number', 'z must be a number')
            assert(type(sensor.magnitude) == 'number', 'magnitude must be a number')
        """,
        )
    }

    @Test
    fun `shaken is a boolean`() {
        // `if sensor.shaken then` is the idiom the field exists for, and a
        // number is truthy for every reading including a phone lying still —
        // so the type is the behaviour here, not a detail of it.
        at(
            resting, """
            assert(type(sensor.shaken) == 'boolean', 'shaken must be a boolean')
            assert(sensor.shaken == false, 'and a resting phone is not a shake')
        """,
        )
    }

    @Test
    fun `a shake reaches the script unchanged`() {
        at(
            shaken, """
            assert(sensor.shaken == true, 'the phone was shaken')
            assert(math.abs(sensor.x - 18.5) < 0.001, 'x must survive the trip')
            assert(math.abs(sensor.y + 3.25) < 0.001, 'y is negative, and must stay so')
            assert(math.abs(sensor.z - 1.5) < 0.001, 'z must survive the trip')
            assert(math.abs(sensor.magnitude - 18.9) < 0.001, 'so must the magnitude')
        """,
        )
    }

    @Test
    fun `a resting phone reads as not shaken`() {
        at(
            resting, """
            assert(sensor.shaken == false, 'gravity alone is not a shake')
            assert(math.abs(sensor.z - 9.8) < 0.001, 'a face-up phone reads about 1g on z')
        """,
        )
    }

    @Test
    fun `the default reading is still, not an error`() {
        // A phone with no accelerometer, or one whose listener could not be
        // registered, must read as a still phone rather than as a nil field a
        // script has to guard every use of.
        at(
            SensorSnapshot.STILL, """
            assert(sensor.shaken == false, 'no reading is not a shake')
            assert(sensor.x == 0 and sensor.y == 0 and sensor.z == 0, 'and no reading is zero')
            assert(sensor.magnitude == 0, 'with a zero magnitude')
        """,
        )
    }

    // endregion

    // region Live, not frozen

    @Test
    fun `the reading is fetched again on every access`() {
        // A module that read the accelerometer while it was being built would
        // hand out the same numbers for the whole run, and a script looping on
        // them — the only reason to ask for a sensor — would never see the
        // phone move. A source that changes on each read is the only way to
        // tell those two apart, exactly as with the clock and the network.
        var reads = 0
        val engine = LuaScriptEngine(
            profile = testProfile(),
            host = FakeHost(),
            // Named, never trailing: three of the engine's four injectables
            // are lambdas and only the last could take one.
            sensor = {
                reads++
                if (reads < 3) SensorSnapshot.STILL else shaken
            },
        )

        val result = engine.run(
            """
            local sensor = require("glyph.sensor")
            local first = sensor.shaken
            local second = sensor.shaken
            local third = sensor.shaken
            assert(first == false, 'the first read is still')
            assert(second == false, 'so is the second')
            assert(third == true, 'and the third must ask again')
            """.trimIndent(),
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `require returns the same table within a run`() {
        // A module built per access would make the obvious `sensor.magnitude`
        // in a draw loop allocate a table every frame.
        at(
            resting, """
            local again = require("glyph.sensor")
            assert(again == sensor, 'a second require must be the same table')
        """,
        )
    }

    // endregion

    // region The lifecycle, which is the reason this module exists

    @Test
    fun `reading a field starts the sensor exactly once`() {
        // The decision the module is built around: `require` is free, and the
        // first *read* is the first moment the script has asked for
        // acceleration. Starting at `require` would hand a 50Hz listener to
        // every animation that so much as names the feature.
        val control = RecordingSensorControl()
        val engine = LuaScriptEngine(
            profile = testProfile(),
            host = FakeHost(),
            sensor = { resting },
            sensorControl = control,
        )

        val result = engine.run(
            """
            local sensor = require("glyph.sensor")
            local x = sensor.x
            local y = sensor.y
            local z = sensor.z
            local m = sensor.magnitude
            local s = sensor.shaken
            """.trimIndent(),
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
        assertEquals(
            "five field reads must take one reference, not five",
            1,
            control.starts,
        )
    }

    @Test
    fun `requiring the module starts nothing`() {
        val control = RecordingSensorControl()
        val engine = LuaScriptEngine(
            profile = testProfile(),
            host = FakeHost(),
            sensor = { resting },
            sensorControl = control,
        )

        val result = engine.run("local sensor = require(\"glyph.sensor\")")

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
        assertEquals(
            "a script that only names the module must not open the sensor",
            0,
            control.starts,
        )
    }

    @Test
    fun `the run gives the sensor back on the way out`() {
        // The whole reason `glyph.sensor` needed a lifecycle: a listener that
        // is not unregistered keeps a sensor powered for the rest of the
        // process, long after the animation that wanted it has gone.
        val control = RecordingSensorControl()
        val engine = LuaScriptEngine(
            profile = testProfile(),
            host = FakeHost(),
            sensor = { resting },
            sensorControl = control,
        )

        val result = engine.run(
            """
            local sensor = require("glyph.sensor")
            local reading = sensor.magnitude
            glyph.exit()
            """.trimIndent(),
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals(1, control.starts)
        assertEquals(
            "an ended run must release the sensor exactly once",
            1,
            control.stops,
        )
    }

    @Test
    fun `a run killed by the watchdog still gives the sensor back`() {
        // The path most likely to be forgotten: a script that spins reads the
        // sensor thousands of times and is then killed by the instruction
        // budget, which is the exit where a release placed only on the
        // success path would never be reached.
        val control = RecordingSensorControl()
        val engine = LuaScriptEngine(
            profile = testProfile(),
            host = FakeHost(),
            sensor = { resting },
            sensorControl = control,
        )

        val result = engine.run(
            """
            local sensor = require("glyph.sensor")
            local n = 0
            while true do
              local reading = sensor.magnitude
              n = n + 1
            end
            """.trimIndent(),
            maxDurationMs = 50L,
        )

        assertEquals(ScriptStatus.TIMED_OUT, result.status)
        assertTrue("the sensor must have been read at least once", control.starts > 0)
        assertEquals(
            "an aborted run must release the sensor too",
            1,
            control.stops,
        )
    }

    @Test
    fun `close is safe to call twice`() {
        // The engine calls it from a `finally` on every exit path, and a run
        // that already closed must not hand the same reference back a second
        // time — a count driven below its true value tears down whichever
        // listener the next run to finish thinks it owns.
        val control = RecordingSensorControl()
        val session = ScriptSession(
            profile = testProfile(),
            host = FakeHost(),
            maxDurationMs = 0L,
            nowMs = { 0L },
            sensor = { SensorSnapshot.STILL },
            sensorControl = control,
        )

        session.startSensor()
        session.close()
        session.close()

        assertEquals(1, control.starts)
        assertEquals(
            "two closes must release the one reference the run took",
            1,
            control.stops,
        )
    }

    @Test
    fun `a run that never read the sensor releases nothing`() {
        // The other half of the same invariant, and the more dangerous one: a
        // run that never took a reference must not give one back on its way
        // out. It cannot be merely an underflow to be clamped — if another
        // service is mid-run and holding the listener, this close is the thing
        // that unregisters it, and that script would sit there waiting for a
        // shake that could never arrive.
        val control = RecordingSensorControl()
        val session = ScriptSession(
            profile = testProfile(),
            host = FakeHost(),
            maxDurationMs = 0L,
            nowMs = { 0L },
            sensor = { SensorSnapshot.STILL },
            sensorControl = control,
        )

        session.close()
        session.close()

        assertEquals(0, control.starts)
        assertEquals(
            "a run that never started the sensor must not release anyone's",
            0,
            control.stops,
        )
    }

    @Test
    fun `startSensor and stopSensor reach the injected control`() {
        val control = RecordingSensorControl()
        val session = ScriptSession(
            profile = testProfile(),
            host = FakeHost(),
            maxDurationMs = 0L,
            nowMs = { 0L },
            sensor = { shaken },
            sensorControl = control,
        )

        assertEquals(shaken, session.sensorSnapshot())
        session.startSensor()
        session.stopSensor()

        assertEquals(1, control.starts)
        assertEquals(1, control.stops)
    }

    @Test
    fun `a session that was never given a control asks for nothing`() {
        // The default every test in this project gets, and what `validate()`
        // builds: a session that can be asked for a reading and cannot open
        // hardware by accident.
        val session = ScriptSession(
            profile = testProfile(),
            host = FakeHost(),
            maxDurationMs = 0L,
            nowMs = { 0L },
        )

        session.startSensor()
        assertEquals(SensorSnapshot.STILL, session.sensorSnapshot())
        session.close()
        session.close()
    }

    // endregion
}
