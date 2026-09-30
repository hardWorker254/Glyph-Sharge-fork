package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.LuaScriptEngine
import com.bleelblep.glyphsharge.glyph.script.ScriptRunResult
import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the one door the sandbox opened, and for what is still shut.
 *
 * Every script here is run through the real engine rather than by calling
 * [ModuleRegistry] directly. The claim under test is not "the registry has six
 * entries" — it is that a `require` in a real sandbox reaches those six and
 * nothing else, and that `io`, `os`, `dofile` and `load` are still gone with
 * `require` installed. A test that skipped the sandbox would pass with the
 * whole escape hatch open.
 */
class ModuleRegistryTest {

    private fun run(
        source: String,
        host: FakeHost = FakeHost(),
        durationMs: Long = 5_000L
    ): ScriptRunResult = LuaScriptEngine(testProfile(), host).run(source, durationMs)

    private fun runClean(source: String) {
        val result = run(source.trimIndent())
        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    // region Resolving

    @Test
    fun `require resolves a registered module`() {
        runClean(
            """
            local time = require("glyph.time")
            assert(type(time.hour) == "number", "glyph.time.hour must be a number")
            assert(type(time.isNight) == "boolean", "glyph.time.isNight must be a boolean")
            """
        )
    }

    @Test
    fun `two requires in one script return the same table`() {
        // Lua caches modules, and a script is entitled to rely on it: identity
        // is what makes `require` safe to call from inside a loop.
        runClean(
            """
            local first = require("glyph.util")
            local second = require("glyph.util")
            assert(first == second, "the second require must return the same table")
            first.marker = 1
            assert(second.marker == 1, "they must be the same table, not two alike")
            """
        )
    }

    @Test
    fun `unknown module raises and lists what is available`() {
        val result = run(
            """
            local ok, err = pcall(function() require("glyph.nett") end)
            assert(not ok, "a typo must raise, not return nil")
            assert(string.find(err, "glyph.nett", 1, true) ~= nil, "the name must be in the message")
            assert(string.find(err, "glyph.time", 1, true) ~= nil, "the available list must be there")
            """
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `module table is fresh per run`() {
        // Several services can run scripts at once, and a registry that handed
        // out one shared table would let the charging animation and the music
        // visualiser write into each other's state. Per-run construction is the
        // whole of the isolation, so it is the thing worth pinning.
        val first = run(
            """
            local time = require("glyph.time")
            time.hour = 99
            assert(time.hour == 99, "the script owns the table it was given")
            """
        )
        assertEquals(first.message, ScriptStatus.COMPLETED, first.status)

        // A pinned clock, so the second run knows what the hour must be.
        val second = engineAt(hour = 13, minute = 45).run(
            """
            assert(require("glyph.time").hour == 13, "the second run must see a fresh table")
            """
        )
        assertEquals(second.message, ScriptStatus.COMPLETED, second.status)
    }

    // endregion

    // region The sandbox that has to survive

    @Test
    fun `require io returns nil`() {
        // `io` is not a module this registry ships, so there is no table behind
        // the name and the script is told so. What matters is that `require`
        // does not go looking for one on the file system to satisfy it.
        val result = run(
            """
            local ok = pcall(function() require("io") end)
            assert(not ok, "io must not be reachable through require")
            """
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `require os returns nil`() {
        val result = run(
            """
            local ok = pcall(function() require("os") end)
            assert(not ok, "os must not be reachable through require")
            """
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `dofile is still nil`() {
        runClean(
            """
            assert(dofile == nil, "dofile must stay blanked")
            assert(loadfile == nil, "loadfile must stay blanked")
            assert(package == nil, "package must stay blanked")
            """
        )
    }

    @Test
    fun `load and loadstring are still nil`() {
        runClean(
            """
            assert(load == nil, "load must stay blanked")
            assert(loadstring == nil, "loadstring must stay blanked")
            assert(luajava == nil, "luajava must stay blanked")
            assert(coroutine == nil, "coroutine must stay blanked")
            assert(collectgarbage == nil, "collectgarbage must stay blanked")
            """
        )
    }

    // endregion
}
