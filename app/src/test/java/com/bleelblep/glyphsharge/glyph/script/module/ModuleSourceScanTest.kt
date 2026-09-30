package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.LuaScriptEngine
import com.bleelblep.glyphsharge.glyph.script.ScriptCheckStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the scan behind the editor's Check button.
 *
 * Compiling proves nothing about `require` — a misspelt module name is a valid
 * expression that survives a clean parse and then fails on the phone. The
 * comment handling is the part worth pinning: a commented-out `require` is the
 * most ordinary line in a script that has been edited a few times, and a
 * checker that reports it would be reporting on a line the author already
 * switched off.
 */
class ModuleSourceScanTest {

    @Test
    fun `finds a plain require`() {
        assertEquals(emptyList<String>(), ModuleSourceScan.missingModules("local t = require('glyph.time')"))
    }

    @Test
    fun `reports an unknown module`() {
        assertEquals(
            listOf("glyph.nett"),
            ModuleSourceScan.missingModules("local t = require(\"glyph.nett\")")
        )
    }

    @Test
    fun `accepts single and double quotes`() {
        val single = ModuleSourceScan.missingModules("require('glyph.nett')")
        val double = ModuleSourceScan.missingModules("require(\"glyph.nett\")")

        assertEquals(listOf("glyph.nett"), single)
        assertEquals(listOf("glyph.nett"), double)
    }

    @Test
    fun `ignores a commented require`() {
        val source = """
            -- require("glyph.nett") was the old name
            --[[
              require("glyph.tim")
            ]]
            local t = require("glyph.time")
        """.trimIndent()

        assertEquals(emptyList<String>(), ModuleSourceScan.missingModules(source))
    }

    @Test
    fun `reports nothing for a clean script`() {
        val source = """
            local time = require("glyph.time")
            local util = require("glyph.util")
            glyph.setAll(time.isNight and time.NIGHT or time.DAY)
            glyph.hold(util.clamp(100, 0, 50))
        """.trimIndent()

        assertEquals(emptyList<String>(), ModuleSourceScan.missingModules(source))
    }

    @Test
    fun `reports a missing module once however often it is required`() {
        val source = """
            for i = 1, 10 do
              local util = require("glyph.uil")
            end
        """.trimIndent()

        assertEquals(listOf("glyph.uil"), ModuleSourceScan.missingModules(source))
    }

    // region The Check button

    @Test
    fun `check reports a missing module`() {
        // The scan exists for this call. A clean compile is not evidence the
        // script runs, because `require("glyph.nett")` compiles perfectly and
        // then fails on the phone.
        val engine = LuaScriptEngine(testProfile(), FakeHost())
        val problem = engine.validate("local time = require(\"glyph.nett\")")

        // The case is asserted as well as the text, because the studio words a
        // missing module differently from a syntax error: reporting the typo as
        // a parse failure sends the author after a bracket that is not missing.
        assertEquals(ScriptCheckStatus.MISSING_MODULE, problem.status)
        // Spelled out rather than compared against the registry's own message:
        // the list of what *is* available is the part that saves the author's
        // script, and it is worth a test that changes if one is removed.
        assertEquals(
            "require: no module 'glyph.nett' " +
                "(available: glyph.battery, glyph.log, glyph.net, glyph.sensor, " +
                "glyph.time, glyph.util)",
            problem.message
        )
    }

    @Test
    fun `check stays quiet for a script that uses the modules correctly`() {
        val engine = LuaScriptEngine(testProfile(), FakeHost())

        assertTrue(engine.validate("local time = require(\"glyph.time\")\nglyph.setAll(time.DAY)").isOk)
    }

    @Test
    fun `check prefers a syntax error over a missing module`() {
        // A file that does not compile has no meaningful `require` in it, and
        // reporting the module first would send the author looking in the wrong
        // place.
        val engine = LuaScriptEngine(testProfile(), FakeHost())
        val problem = engine.validate("local time = require(\"glyph.nett\")\nfor i = 1 do")

        assertNotNull(problem.message)
        assertEquals(ScriptCheckStatus.SYNTAX_ERROR, problem.status)
        assertNotEquals("require:", problem.message)
    }

    // endregion
}
