package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sandbox, attacked.
 *
 * The other suites in this package check that the API *works*. This one checks
 * that it cannot be used to do anything else, which is a different question and
 * the one that matters the moment an animation stops being only the user's own
 * typing: a script shared by a stranger, or downloaded from somewhere, is
 * arbitrary code driving hardware out of a foreground service.
 *
 * Every probe runs through the real [LuaScriptEngine] with a real
 * [DeviceProfileFactory.forPreview] profile, because a probe that skipped the
 * sandbox would report safety it did not test.
 *
 * ### What "safe" means here
 *
 * A probe PASSES when the script is stopped, refused, or runs without touching
 * anything it should not. Several probes are expected to *fail to escape* while
 * *succeeding at their harmless half* — the assertion is always on the harm,
 * never on the script finishing. A probe that raises [ScriptStatus.RUNTIME_ERROR]
 * with a message is as good as one that completes, and usually better: it means
 * the sandbox said no in a way the author can read.
 */
class ScriptSandboxSecurityTest {

    /** Records frames instead of lighting LEDs. */
    private class FakeHost : GlyphScriptHost {
        var frames = 0
        override fun draw(channels: List<Int>, brightness: Int) { frames++ }
        override fun blank() = Unit
        override fun batteryPercent(): Int = 64
        override fun isCharging(): Boolean = true
    }

    private val profile = DeviceProfileFactory.forPreview()

    private fun run(source: String, durationMs: Long = 5_000L): ScriptRunResult =
        LuaScriptEngine(profile, FakeHost()).run(source, durationMs)

    /** What a script managed to reach, for the "harm" half of a probe. */
    private fun reached(source: String): String {
        val host = FakeHost()
        val result = LuaScriptEngine(profile, host).run(source, 5_000L)
        return "${result.status}:${host.frames}"
    }

    // region Nothing is reachable

    /**
     * Every capability the sandbox removes, checked as the script would find
     * it: read the global and see whether it is `nil`.
     *
     * Asserted through the script's own `assert` so a failure names the
     * specific global that came back, rather than "the probe failed".
     */
    @Test
    fun `no dangerous global is reachable`() {
        val result = run(
            """
            local banned = {
              'io', 'os', 'package', 'require_x', 'module',
              'dofile', 'loadfile', 'load', 'loadstring',
              'luajava', 'coroutine', 'collectgarbage', 'newproxy', 'debug'
            }
            for i = 1, #banned do
              local name = banned[i]
              assert(_G[name] == nil, name .. ' must be nil, got ' .. type(_G[name]))
            end
            """.trimIndent()
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    /**
     * `require` resolves the registry and nothing else, so the names a script
     * would use to *reach* something are not in it.
     */
    @Test
    fun `require cannot reach the removed capabilities`() {
        val result = run(
            """
            local wanted = { 'io', 'os', 'package', 'luajava', 'debug', 'coroutine' }
            for i = 1, #wanted do
              local ok = pcall(require, wanted[i])
              assert(not ok, wanted[i] .. ' must not resolve through require')
            end
            """.trimIndent()
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    /**
     * A non-string argument to `require` must be refused, not coerced into a
     * name that happens to resolve. `require(1)` and `require(nil)` are the
     * shapes a fuzzer reaches for.
     */
    @Test
    fun `require refuses a name that is not a string`() {
        val result = run(
            """
            for _, bad in ipairs({ 1, true, {}, print }) do
              local ok = pcall(require, bad)
              assert(not ok, 'require accepted a ' .. type(bad))
            end
            """.trimIndent()
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    /**
     * The metatable on `glyph` holds the two handlers and nothing else. Chained
     * lookups through them must not surface the Kotlin objects behind the API —
     * a `__index` that leaked its own table would hand the script the host.
     */
    @Test
    fun `the glyph metatable does not leak the host`() {
        val result = run(
            """
            local meta = getmetatable(glyph)
            assert(type(meta) == 'table', 'glyph must have a table metatable')
            local index = meta.__index
            local indexMeta = getmetatable(index)
            assert(indexMeta == nil or type(indexMeta) ~= 'table',
              'the __index handler must not carry a table metatable')
            -- Reading a key that is neither live nor declared is nil, and does
            -- not produce something script-shaped by accident.
            assert(glyph.nosuchkey == nil, 'an unknown key must be nil')
            assert(glyph.host == nil, 'there is no host on the table')
            assert(glyph.session == nil, 'there is no session on the table')
            """.trimIndent()
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    // endregion

    // region The watchdog cannot be argued with

    /**
     * A bare spin loop. The instruction budget has to end it, because nothing
     * in it ever calls back into Kotlin to notice a wall-clock check.
     */
    @Test
    fun `a spin loop is stopped by the instruction budget`() {
        val started = System.currentTimeMillis()
        val result = run("while true do end", 60_000L)
        val took = System.currentTimeMillis() - started

        assertTrue("a spin loop completed instead of being stopped", !result.isSuccess)
        assertTrue("the spin loop ran for ${took}ms, far past any useful cap", took < 30_000L)
    }

    /**
     * The one that matters most: the watchdog throws [ScriptAbortedError], a
     * Java `Error`, precisely so that `pcall` — which catches Lua errors and
     * every Java `Exception` — cannot swallow it and restart the loop.
     *
     * If this ever starts passing with the script still running, the sandbox
     * has a hole in its only defence against a hostile script, and the comment
     * in [LuaScriptEngine] claiming otherwise has become a lie.
     */
    @Test
    fun `pcall cannot swallow the watchdog`() {
        val started = System.currentTimeMillis()
        val result = run(
            """
            while true do
              pcall(function()
                while true do end
              end)
            end
            """.trimIndent(),
            60_000L
        )
        val took = System.currentTimeMillis() - started

        assertTrue("pcall defeated the watchdog", !result.isSuccess)
        assertTrue("the wrapped loop ran for ${took}ms", took < 30_000L)
    }

    /** The same trick through `xpcall`, which takes a handler of its own. */
    @Test
    fun `xpcall cannot swallow the watchdog either`() {
        val result = run(
            """
            local function handler(e) return false end
            while true do
              xpcall(function() while true do end end, handler)
            end
            """.trimIndent(),
            60_000L
        )

        assertTrue("xpcall defeated the watchdog", !result.isSuccess)
    }

    /**
     * A `repeat` loop has no condition to short-circuit and no function call
     * per iteration, so it is the cheapest way to burn the whole budget.
     */
    @Test
    fun `a repeat loop is stopped too`() {
        val result = run("repeat until false end", 60_000L)

        assertTrue("a repeat loop completed", !result.isSuccess)
    }

    /**
     * `MAX_HOLD_MS` caps one call, and the run cap ends the rest. A script
     * asking to hold the strip for a year must not be able to, because the
     * strip staying lit is the one side effect a user notices without the app.
     */
    @Test
    fun `a hold longer than the cap does not outlast the run`() {
        val started = System.currentTimeMillis()
        val result = run("glyph.hold(999999999)", 2_000L)
        val took = System.currentTimeMillis() - started

        assertTrue("the hold overran the run cap by a long way: ${took}ms", took < 20_000L)
        assertTrue("the run reported success despite being cut off", !result.isSuccess)
    }

    // endregion

    // region Failures are contained, not propagated

    /**
     * Deep recursion. The engine catches [ScriptAbortedError] and [Exception];
     * a stack overflow is a Java `Error` and is neither, so this probes
     * whether it escapes `run()` and takes the calling coroutine with it.
     *
     * A script that reaches this is a crash a stranger's shared animation could
     * cause on demand.
     */
    @Test
    fun `deep recursion does not escape the engine`() {
        val escaped = try {
            run("local f; f = function() return 1 + f() end f()", 5_000L)
            false
        } catch (e: Throwable) {
            // Reaching here means the Error left run() uncaught.
            System.out.println("PROBE: stack overflow escaped run(): ${e::class.java.name}")
            true
        }

        assertTrue("a StackOverflowError escaped LuaScriptEngine.run", !escaped)
    }

    /**
     * An error thrown from a metamethod is still an error the engine turns into
     * a result. It is the shape a malicious file most likely takes to probe
     * for a way past the result wrapper.
     */
    @Test
    fun `an error from a metamethod is contained`() {
        val result = run(
            """
            local t = setmetatable({}, { __index = function() error('boom') end })
            error(t.missing)
            """.trimIndent()
        )

        assertTrue("the run reported success", !result.isSuccess)
    }

    // endregion
}
