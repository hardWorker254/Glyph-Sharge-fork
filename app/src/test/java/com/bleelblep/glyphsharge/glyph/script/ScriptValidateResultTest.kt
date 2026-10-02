package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.script.module.FakeHost
import com.bleelblep.glyphsharge.glyph.script.module.testProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the three answers [LuaScriptEngine.validate] can give.
 *
 * The result used to be a `String?`, which forced every caller to say the same
 * thing about every failure — and the studio wrapped all of it in "syntax
 * error", so a mistyped module was reported to its author as a parse failure.
 * A file that is valid Lua with a name in it that does not exist is a different
 * mistake with a different fix, and these pin the difference.
 *
 * The precedence is the other half: a file that does not parse is reported on
 * its own, because a `require` inside it is not yet a meaningful claim.
 */
class ScriptValidateResultTest {

    private val engine = LuaScriptEngine(testProfile(), FakeHost())

    @Test
    fun `a clean script is ok and carries no message`() {
        val result = engine.validate(
            """
            local time = require("glyph.time")
            local util = require("glyph.util")
            glyph.setAll(time.isNight and time.NIGHT or util.clamp(glyph.battery, 0, glyph.MAX))
            """.trimIndent(),
        )

        assertEquals(ScriptCheckStatus.OK, result.status)
        assertTrue(result.isOk)
        // Nothing to say means nothing to carry, so a caller that interpolates
        // the message for any other case cannot be handed a stale one here.
        assertNull(result.message)
    }

    @Test
    fun `a file that does not parse is a syntax error`() {
        val result = engine.validate("for i = 1 do")

        assertEquals(ScriptCheckStatus.SYNTAX_ERROR, result.status)
        assertNotNull(result.message)
        assertTrue(!result.isOk)
    }

    @Test
    fun `a typo in a require is a missing module, not a syntax error`() {
        val result = engine.validate("local time = require(\"glyph.nett\")")

        assertEquals(ScriptCheckStatus.MISSING_MODULE, result.status)
        // The message names the mistake and lists what was on offer, which is
        // the part an author can act on without opening this project.
        assertEquals(
            "require: no module 'glyph.nett' " +
                "(available: glyph.battery, glyph.log, glyph.net, glyph.sensor, " +
                "glyph.time, glyph.util)",
            result.message,
        )
    }

    @Test
    fun `a parse failure outranks a missing module in the same file`() {
        // The order is the point: telling the author about the module first
        // would send them to a line whose meaning depends on the file around it
        // not being valid Lua in the first place.
        val result = engine.validate("local t = require(\"glyph.nett\")\nfor i = 1 do")

        assertEquals(ScriptCheckStatus.SYNTAX_ERROR, result.status)
        assertTrue(!result.message!!.startsWith("require:"))
    }

    @Test
    fun `the ok result is available to callers that could not check anything`() {
        // A phone whose LED layout is unknown cannot be checked against, and
        // the runner hands back this rather than inventing a failure the
        // author could do nothing about.
        assertEquals(ScriptCheckStatus.OK, ScriptCheckResult.OK.status)
        assertTrue(ScriptCheckResult.OK.isOk)
    }
}
