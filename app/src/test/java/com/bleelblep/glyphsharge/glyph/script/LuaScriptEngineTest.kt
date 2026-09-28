package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.device.DnaConfig
import com.bleelblep.glyphsharge.glyph.device.FireworksConfig
import com.bleelblep.glyphsharge.glyph.device.MatrixConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tests for the two things that cannot be checked by reading the code: that a
 * script actually runs, and that the sandbox and watchdog really hold.
 *
 * The hardware side is replaced by [FakeHost], which records what would have
 * been drawn. That is the same shape the studio's on-screen preview uses, so a
 * passing test is evidence the preview works too.
 */
class LuaScriptEngineTest {

    private fun profile() = DeviceProfile(
        type = DeviceType.PHONE3A,
        all = (0..19).toList(),
        a = (20..24).toList(),
        b = (25..27).toList(),
        c = (0..9).toList(),
        d = emptyList(),
        e = emptyList(),
        waveGroups = emptyList(),
        spiralOrder = (0..9).toList(),
        spiralStep = 60L,
        pulseSegments = listOf(3, 5, 7),
        c1SeqStep = 200L,
        c1SeqHold = 1000L,
        matrixConfig = MatrixConfig(1, 1, 1, 1, 1, 1),
        fireworksConfig = FireworksConfig(1, 1, 1, 1, 1, 1),
        dnaConfig = DnaConfig(1, 1, 1)
    )

    /** Records frames instead of lighting LEDs. */
    private class FakeHost : GlyphScriptHost {
        val frames = mutableListOf<Pair<List<Int>, Int>>()

        override fun draw(channels: List<Int>, brightness: Int) {
            frames.add(channels to brightness)
        }

        override fun blank() = Unit

        override fun batteryPercent(): Int = 64

        override fun isCharging(): Boolean = true
    }

    private fun run(source: String, host: FakeHost = FakeHost(), durationMs: Long = 5_000L) =
        LuaScriptEngine(profile(), host).run(source, durationMs)

    // region Running

    @Test
    fun `draws what the script asks for`() {
        val host = FakeHost()
        val result = run(
            """
            glyph.set({ 1, 2, 3 }, 2000)
            glyph.set({ 4 }, 500)
            """.trimIndent(),
            host
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals(listOf(listOf(1, 2, 3) to 2000, listOf(4) to 500), host.frames)
        assertEquals(2, result.frames)
    }

    @Test
    fun `runs loops, locals and the standard library`() {
        val host = FakeHost()
        val result = run(
            """
            local total = 0
            for i = 1, 5 do
              total = total + i
              glyph.set({ i }, 100 * i)
            end
            assert(total == 15, "sum must be 15")
            assert(math.floor(2.7) == 2, "math must be available")
            assert(#("abc"):upper() == 3, "string methods must be available")
            """.trimIndent(),
            host
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals(5, host.frames.size)
    }

    @Test
    fun `channel groups come from the device profile`() {
        val host = FakeHost()
        run("glyph.set('c', 1000)", host)

        assertEquals(listOf((0..9).toList() to 1000), host.frames)
    }

    @Test
    fun `brightness is clamped to the hardware range`() {
        val host = FakeHost()
        run("glyph.set({ 1 }, 999999)", host)

        assertEquals(4000, host.frames.single().second)
    }

    @Test
    fun `glyph exit finishes cleanly`() {
        val host = FakeHost()
        val result = run(
            """
            glyph.set({ 1 }, 1000)
            glyph.exit()
            glyph.set({ 2 }, 1000)
            """.trimIndent(),
            host
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals(1, host.frames.size)
    }

    // endregion

    // region Looping

    @Test
    fun `a script is not repeated once it finishes`() {
        val host = FakeHost()
        val result = run(
            """
            -- Nothing here asks for a repeat: the animation is exactly the code.
            for i = 1, 4 do
              glyph.set({ i }, 1000)
              glyph.hold(10)
            end
            """.trimIndent(),
            host,
            durationMs = 5_000L
        )

        assertEquals(ScriptStatus.COMPLETED, result.status)
        assertEquals("one pass is four frames", 4, host.frames.size)
    }

    @Test
    fun `a script longer than the cap is stopped by it`() {
        val host = FakeHost()
        val result = run(
            """
            while glyph.running do
              glyph.set({ 1 }, 1000)
              glyph.hold(10)
            end
            """.trimIndent(),
            host,
            durationMs = 300L
        )

        assertEquals(ScriptStatus.TIMED_OUT, result.status)
        assertTrue(result.frames > 1)
    }

    @Test
    fun `live state is a value, not a function`() {
        val result = run(
            """
            -- A function value is truthy in Lua, so a `while glyph.running do`
            -- loop would never end if these were bound as functions.
            assert(type(glyph.running) == 'boolean', 'running must be a boolean')
            assert(type(glyph.battery) == 'number', 'battery must be a number')
            assert(type(glyph.charging) == 'boolean', 'charging must be a boolean')
            assert(glyph.running == true, 'a fresh run is running')
            """.trimIndent()
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `a running loop ends when the animation is stopped`() {
        val host = FakeHost()
        val engine = LuaScriptEngine(profile(), host)
        val finished = AtomicBoolean(false)

        val thread = Thread {
            // No duration limit worth waiting for: only the stop request can
            // end this loop.
            engine.run(
                "local n = 0 while glyph.running do n = n + 1 end\nreturn n",
                maxDurationMs = 30_000L
            )
            finished.set(true)
        }
        thread.start()
        Thread.sleep(200L)
        engine.stop("test asked it to stop")
        thread.join(5_000L)

        assertTrue("the loop must notice the stop", finished.get())
    }

    // endregion

    // region Declaring a target

    @Test
    fun `a declared target can be read back by the script`() {
        // LuaJ hands a function-valued `__index` the key as its second
        // argument. Reading the first one instead made `glyph.target` come back
        // nil, so a script that declared a target and then checked it
        // disagreed with itself — with no error to explain the disagreement.
        val result = run(
            """
            glyph.target = "music"
            assert(glyph.target == "music", "the declared target must read back")
            """.trimIndent()
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `a completed run reports the target it declared`() {
        // `result.target` is the only place a runtime declaration can ever be
        // read, because the source scan cannot know whether the script really
        // got that far. A clean finish is the one case that has to carry it:
        // when the run ended on its own the script is working, which is exactly
        // when the studio wants to know which service it belongs to.
        val result = run(
            """
            glyph.target = "music"
            glyph.set({ 1, 2 }, 1500)
            """.trimIndent()
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
        assertEquals(ScriptTarget.MUSIC, result.target)
    }

    @Test
    fun `a run that declares nothing reports no target`() {
        // `null` is the pickers' cue that a script works anywhere, so a value
        // invented for an undeclared script would file an ordinary animation
        // under a single service and hide it from the other three.
        val result = run("glyph.set({ 1 }, 1000)")

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
        assertNull(result.target)
    }

    // endregion

    // region Errors and the watchdog

    @Test
    fun `a syntax error is reported without drawing anything`() {
        val host = FakeHost()
        val result = run("this is not lua", host)

        assertEquals(ScriptStatus.SYNTAX_ERROR, result.status)
        assertTrue(host.frames.isEmpty())
        assertNotNull(result.message)
    }

    @Test
    fun `a runtime error is reported with its message`() {
        val result = run("error('boom')")

        assertEquals(ScriptStatus.RUNTIME_ERROR, result.status)
        assertTrue(result.message!!.contains("boom"))
    }

    @Test
    fun `an infinite loop is killed by the watchdog`() {
        val host = FakeHost()
        val result = run("while true do end", host, durationMs = 300L)

        assertEquals(ScriptStatus.TIMED_OUT, result.status)
    }

    @Test
    fun `a runaway computation is killed even when it never draws`() {
        val result = run(
            "local n = 0 while true do n = n + 1 end",
            durationMs = 300L
        )

        assertEquals(ScriptStatus.TIMED_OUT, result.status)
    }

    @Test
    fun `a long hold is interrupted by stop`() {
        val host = FakeHost()
        val engine = LuaScriptEngine(profile(), host)
        val finished = AtomicBoolean(false)

        val thread = Thread {
            engine.run("glyph.hold(60000)", maxDurationMs = 60_000L)
            finished.set(true)
        }
        thread.start()
        Thread.sleep(200L)
        engine.stop("test asked it to stop")
        thread.join(5_000L)

        assertTrue("the run must not still be going", !thread.isAlive)
        assertTrue(finished.get())
    }

    @Test
    fun `pcall cannot swallow the watchdog`() {
        // A Lua-level error would be catchable; the watchdog throws past it.
        val result = run("while pcall(function() while true do end end) do end", durationMs = 300L)

        assertEquals(ScriptStatus.TIMED_OUT, result.status)
    }

    // endregion

    // region Sandbox

    @Test
    fun `the dangerous globals are gone`() {
        val host = FakeHost()
        val result = run(
            """
            local banned = { 'io', 'os', 'package', 'require', 'luajava',
                             'coroutine', 'load', 'loadstring', 'dofile', 'debug' }
            for i = 1, #banned do
              assert(_G[banned[i]] == nil, banned[i] .. ' must not be reachable')
            end
            """.trimIndent(),
            host
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `loading code at runtime is refused`() {
        val result = run("load('return 1')")

        assertEquals(ScriptStatus.RUNTIME_ERROR, result.status)
    }

    // endregion

    @Test
    fun `validate reports a syntax error and stays quiet otherwise`() {
        val engine = LuaScriptEngine(profile(), FakeHost())

        assertNull(engine.validate("glyph.set({ 1 }, 1000)"))
        assertNotNull(engine.validate("for i = 1 do"))
    }
}
