package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.device.DeviceType
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
        updatedAt = 1_700_000_500_000L,
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

    // region devices

    @Test
    fun `a round trip keeps the declared models`() {
        val narrow = original.copy(devices = setOf(DeviceType.PHONE1, DeviceType.PHONE2))
        val decoded = ScriptFileFormat.decode(ScriptFileFormat.encode(narrow))

        assertEquals(setOf(DeviceType.PHONE1, DeviceType.PHONE2), decoded.devices)
    }

    @Test
    fun `a round trip of every model is the default`() {
        val all = original.copy(devices = ScriptAnimation.ALL_DEVICES)
        val decoded = ScriptFileFormat.decode(ScriptFileFormat.encode(all))

        assertEquals(ScriptAnimation.ALL_DEVICES, decoded.devices)
    }

    /**
     * The regression this field could have broken: every script already saved
     * on a phone was written before `devices` existed, and the whole point of
     * the default is that such a file keeps working.
     */
    @Test
    fun `a file written before the field existed claims every model`() {
        val legacy = """
            -- Glyph Sharge animation
            -- format: 1
            -- name: Old script
            -- id: abc123def456
            -- created: 1700000000000
            -- updated: 1700000000000

            glyph.setAll(glyph.MAX)
        """.trimIndent()

        val decoded = ScriptFileFormat.decode(legacy)

        assertEquals(ScriptAnimation.ALL_DEVICES, decoded.devices)
        assertEquals("Old script", decoded.name)
    }

    @Test
    fun `a headerless file claims every model`() {
        // Nothing declared anything, and a script written on the named groups
        // really does run everywhere.
        val decoded = ScriptFileFormat.decode("glyph.setAll(glyph.MAX)")

        assertEquals(ScriptAnimation.ALL_DEVICES, decoded.devices)
    }

    @Test
    fun `a model this build has never heard of is not a refusal`() {
        // PHONE4 is an honest claim about a phone that does not exist yet, and
        // refusing the script over it would leave it uninstallable anywhere.
        val future = """
            -- Glyph Sharge animation
            -- devices: PHONE4

            glyph.setAll(glyph.MAX)
        """.trimIndent()

        val decoded = ScriptFileFormat.decode(future)

        assertEquals(ScriptAnimation.ALL_DEVICES, decoded.devices)
    }

    @Test
    fun `an empty devices field claims every model`() {
        val decoded = ScriptFileFormat.decode(
            "-- Glyph Sharge animation\n-- devices:\n\nglyph.setAll(glyph.MAX)",
        )

        assertEquals(ScriptAnimation.ALL_DEVICES, decoded.devices)
    }

    @Test
    fun `a partly unknown list narrows to the models it named`() {
        val raw = """
            -- Glyph Sharge animation
            -- devices: PHONE1, PHONE9 ,PHONE2A

            glyph.setAll(glyph.MAX)
        """.trimIndent()

        val decoded = ScriptFileFormat.decode(raw)

        assertEquals(setOf(DeviceType.PHONE1, DeviceType.PHONE2A), decoded.devices)
    }

    @Test
    fun `the devices key is read whatever its case`() {
        val raw = """
            -- Glyph Sharge animation
            -- Devices: PHONE3A

            glyph.setAll(glyph.MAX)
        """.trimIndent()

        assertEquals(setOf(DeviceType.PHONE3A), ScriptFileFormat.decode(raw).devices)
    }

    @Test
    fun `the exported device line is stable for the same set`() {
        // A `sha256` over the file is only a content identity if two exports
        // of one script produce the same bytes, and a `Set` in declaration
        // order is what makes that true.
        val devices = setOf(DeviceType.PHONE3A, DeviceType.PHONE1, DeviceType.PHONE2)
        val first = ScriptFileFormat.encode(original.copy(devices = devices))
        val second = ScriptFileFormat.encode(original.copy(devices = devices.toList().reversed().toSet()))

        assertEquals(first, second)
        assertTrue(first.contains("-- devices: PHONE1,PHONE2,PHONE3A"))
    }

    @Test
    fun `every model is claimed unless the author says otherwise`() {
        // The default, asserted directly: a script written against the named
        // groups is portable, so "all of them" is the true default and not a
        // stand-in for "unknown".
        assertEquals(ScriptAnimation.ALL_DEVICES, original.devices)
        assertTrue(ScriptAnimation.ALL_DEVICES.containsAll(DeviceType.entries))
    }

    @Test
    fun `a script reports the models it supports`() {
        val narrow = original.copy(devices = setOf(DeviceType.PHONE1))

        assertTrue(narrow.supports(DeviceType.PHONE1))
        assertTrue(!narrow.supports(DeviceType.PHONE3A))
        // Hardware the app has no layout for supports nothing, which is what
        // `runScript` already refuses to run on.
        assertTrue(!narrow.supports(null))
    }

    @Test
    fun `a cyrillic id is replaced rather than trusted`() {
        // `ID_PATTERN` is `[a-zA-Z0-9_-]`, so this is not accepted — and the
        // point is that it is *silently* replaced, which is exactly why the
        // review checklist says the id is checked by eye.
        val raw = """
            -- Glyph Sharge animation
            -- id: abc123def456я

            glyph.setAll(glyph.MAX)
        """.trimIndent()

        val decoded = ScriptFileFormat.decode(raw)

        assertNotEquals("abc123def456я", decoded.id)
        assertTrue(decoded.id.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
    }

    // endregion
}
