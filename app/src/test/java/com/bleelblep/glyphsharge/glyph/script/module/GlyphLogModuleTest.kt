package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for `require("glyph.log")`.
 *
 * The behaviour worth pinning is which of the three stops the run. `info` and
 * `warn` are progress notes and must let the script carry on; `error` is a
 * script saying it cannot continue, and if it let the run go on it would spend
 * the rest of its duration drawing a frame the author already knows is wrong.
 */
class GlyphLogModuleTest {

    private fun run(source: String) = engineAt(hour = 12).run(
        """
        local log = require("glyph.log")
        ${source.trimIndent()}
        """.trimIndent(),
    )

    @Test
    fun `info and warn let the run continue`() {
        val result = run(
            """
            log.info("starting")
            log.warn("the battery is low")
            glyph.set({ 1 }, 1000)
            """,
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
        assertEquals(1, result.frames)
    }

    @Test
    fun `error raises and stops the run`() {
        val result = run(
            """
            log.error("nothing to animate")
            glyph.set({ 1 }, 1000)
            """,
        )

        assertEquals(ScriptStatus.RUNTIME_ERROR, result.status)
        // LuaJ prefixes the message with the chunk and the line, so this is a
        // containment check rather than an equality one.
        assertTrue(result.message!!.contains("nothing to animate"))
        // The frame after the error must never be drawn: the point of raising
        // is that the run stops there.
        assertEquals(0, result.frames)
    }

    @Test
    fun `the level is reported once and the prefix is not doubled`() {
        // `LuaError` is a `RuntimeException`, so raising through the binding
        // wrapper would come back out as `glyph.log.error: glyph.log.error: …`.
        val result = run("log.error(\"stop here\")")

        assertEquals(ScriptStatus.RUNTIME_ERROR, result.status)
        assertTrue(
            "the prefix must appear exactly once: ${result.message}",
            result.message!!.contains("glyph.log.error: stop here"),
        )
        assertEquals(1, result.message.split("glyph.log.error").size - 1)
    }

    @Test
    fun `a module error is reported against the module that raised it`() {
        val result = run("require('glyph.util').shuffle(7)")

        assertEquals(ScriptStatus.RUNTIME_ERROR, result.status)
        assertTrue(
            "expected the module to name itself: ${result.message}",
            result.message!!.contains("glyph.util.shuffle: "),
        )
    }
}
