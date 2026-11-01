package com.bleelblep.glyphsharge.glyph.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `.servicelua` container: round-trip, hand-written files, and the one
 * rejection that matters — a file with no Lua in it.
 */
class ServiceScriptFileFormatTest {

    @Test
    fun `encode then decode round-trips name and source`() {
        val script = ServiceScript(
            id = "abc123def456",
            name = "Warn at 15%",
            source = "glyph.on(\"low_battery\", function() glyph.playAnimation() end)",
        )

        val decoded = ServiceScriptFileFormat.decode(ServiceScriptFileFormat.encode(script))

        assertEquals(script.id, decoded.id)
        assertEquals(script.name, decoded.name)
        assertEquals(script.source, decoded.source)
    }

    @Test
    fun `a headerless file imports as the caller's fallback name`() {
        val decoded = ServiceScriptFileFormat.decode(
            "glyph.on(\"unlock\", function() end)",
            fallbackName = "My rule",
        )

        assertEquals("My rule", decoded.name)
        assertTrue(decoded.id.isNotBlank())
    }

    @Test
    fun `a file with no body is rejected`() {
        assertThrows(ScriptFileFormat.InvalidScriptException::class.java) {
            ServiceScriptFileFormat.decode("-- Glyph Sharge service rule\n-- name: Empty\n")
        }
    }

    @Test
    fun `the id is regenerated for an id that cannot name a file`() {
        val decoded = ServiceScriptFileFormat.decode(
            "-- id: ../../evil\nglyph.on(\"unlock\", function() end)",
        )

        // The header is not trusted into a file name — the pattern refuses it
        // and a fresh id is invented, same as the animation format.
        assertNotEquals("../../evil", decoded.id)
        assertTrue(Regex("[a-zA-Z0-9_-]{1,64}").matches(decoded.id))
    }

    @Test
    fun `the suggested file name carries the service extension`() {
        val name = ServiceScriptFileFormat.suggestedFileName(
            ServiceScript(id = "x", name = "Warn at 15%", source = "glyph.on(\"unlock\", function() end)"),
        )

        assertTrue(name.endsWith(".servicelua"))
    }

    @Test
    fun `a file that merely opens with comments keeps them`() {
        // The constructor's starter, and anyone's top-of-file note: a comment
        // run that declares no field is content, not a header. Eating it on
        // every save round-trip would leave the buffer and the file
        // disagreeing about what the script says.
        val decoded = ServiceScriptFileFormat.decode(
            "-- I keep this threshold low on purpose.\nglyph.on(\"low_battery\", function() end)",
            fallbackName = "Warn",
        )

        assertEquals("Warn", decoded.name)
        assertTrue(decoded.source.startsWith("-- I keep this threshold low on purpose."))
    }
}
