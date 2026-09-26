package com.bleelblep.glyphsharge.glyph.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the `.glyphlua` container.
 *
 * The format is the app's file interchange — a user exports a script, sends it
 * to a friend, and that phone imports it — so the round trip has to be exact,
 * and a file from elsewhere has to be importable without one.
 */
class ScriptFileFormatTest {

    private val original = ScriptAnimation(
        id = "abc123def456",
        name = "My Heartbeat",
        source = "local C = glyph.ch.c\nfor i = 1, #C do\n  glyph.set({ C[i] }, 1000)\n  glyph.hold(80)\nend",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_500_000L
    )

    @Test
    fun `a round trip keeps every field`() {
        val decoded = ScriptFileFormat.decode(ScriptFileFormat.encode(original))

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.source, decoded.source)
        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.updatedAt, decoded.updatedAt)
    }

    @Test
    fun `the exported file is still a plain lua script`() {
        val encoded = ScriptFileFormat.encode(original)

        // The header is comments, so the body can be pasted into any Lua host.
        assertTrue(encoded.startsWith("--"))
        assertTrue(encoded.contains("glyph.set({ C[i] }, 1000)"))
    }

    @Test
    fun `a headerless file is imported as a new animation`() {
        val decoded = ScriptFileFormat.decode("glyph.setAll(glyph.MAX)")

        assertEquals("Imported script", decoded.name)
        assertNotEquals(original.id, decoded.id)
        assertEquals("glyph.setAll(glyph.MAX)", decoded.source)
    }

    @Test
    fun `a name with a newline cannot corrupt the header`() {
        val awkward = original.copy(name = "Two\nlines")
        val decoded = ScriptFileFormat.decode(ScriptFileFormat.encode(awkward))

        assertEquals("Two lines", decoded.name)
        assertEquals(original.source, decoded.source)
    }

    @Test
    fun `a file with no lua body is rejected`() {
        val error = runCatching { ScriptFileFormat.decode("-- Glyph Sharge animation\n-- name: x\n") }

        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull() is ScriptFileFormat.InvalidScriptException)
    }

    @Test
    fun `suggested file names are safe for a file system`() {
        // Slashes, colons and question marks are not legal in a file name on
        // every platform, and spaces would be mangled by some pickers.
        val name = ScriptFileFormat.suggestedFileName(original.copy(name = "My/Heartbeat: v2?"))

        assertEquals("My_Heartbeat__v2.glyphlua", name)
    }

    @Test
    fun `runtime ids are namespaced so they cannot collide with built-ins`() {
        assertTrue(ScriptAnimation.isCustomId("custom:abc"))
        assertEquals("abc", ScriptAnimation.stripPrefix("custom:abc"))
        assertEquals("custom:abc", ScriptAnimation.runtimeIdOf("abc"))
        assertEquals("custom:abc", ScriptAnimation.runtimeIdOf("custom:abc"))
    }
}
