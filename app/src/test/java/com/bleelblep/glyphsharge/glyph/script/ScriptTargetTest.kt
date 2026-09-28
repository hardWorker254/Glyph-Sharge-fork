package com.bleelblep.glyphsharge.glyph.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the target classifier and the picker filters.
 *
 * A script says which service it is for in a line of Lua, and two readers
 * have to agree: [ScriptTarget.detectIn] for the pickers, which must classify
 * a script before anyone runs it, and the `__newindex` handler for the studio
 * console. They share nothing but this enum, so the rules below — which
 * scripts are offered where, and which declarations are read at all — are what
 * keeps a music script out of the Pulse Lock list.
 *
 * The comment handling is the part worth pinning. A commented-out declaration
 * is the easiest way to end up with a script filed under the wrong service,
 * and the two failure modes are silent: the script simply does not appear.
 */
class ScriptTargetTest {

    private fun script(source: String) = ScriptAnimation(
        id = "abc123",
        name = "A script",
        source = source
    )

    // region Detecting the declaration

    @Test
    fun `a script that says nothing is offered everywhere`() {
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("glyph.setAll(glyph.MAX)"))
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn(""))
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("-- glyph.target = \"music\""))
    }

    @Test
    fun `the declaration is found with either quote style`() {
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("""glyph.target = "music""""))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("glyph.target = 'music'"))
    }

    @Test
    fun `the declaration is found through any spacing`() {
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("""glyph . target = "music""""))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("""glyph.target="music""""))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("""   glyph.target   =   "music"   """))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("glyph\n  .target\n  = 'music'"))
    }

    @Test
    fun `a declaration on its own line is found`() {
        val source = """
            glyph.target = "music"
            for i = 1, #glyph.ch.c do
              glyph.set({ glyph.ch.c[i] }, 1000)
              glyph.hold(60)
            end
        """.trimIndent()

        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn(source))
    }

    @Test
    fun `a commented out declaration is not read`() {
        // The single easiest way to file a script under the wrong service.
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("-- glyph.target = \"music\""))
        assertEquals(
            ScriptTarget.ANY,
            ScriptTarget.detectIn("glyph.setAll(1) -- glyph.target = 'music'")
        )
    }

    @Test
    fun `a comment does not swallow the line after it`() {
        assertEquals(
            ScriptTarget.MUSIC,
            ScriptTarget.detectIn("-- switching this off for now\nglyph.target = 'music'")
        )
    }

    @Test
    fun `a declaration inside a block comment is not read`() {
        assertEquals(
            ScriptTarget.ANY,
            ScriptTarget.detectIn("""--[[ glyph.target = "music" ]]""")
        )
        // The level of equals signs is the Lua convention, and the pattern has
        // to honour it or a `]]` inside the comment ends it early.
        assertEquals(
            ScriptTarget.ANY,
            ScriptTarget.detectIn("--[==[ glyph.target = 'music' ]==]")
        )
    }

    @Test
    fun `a commented out declaration does not hide a real one`() {
        val source = """
            --[[ was: glyph.target = "music" ]]
            -- glyph.target = 'music'
            glyph.target = "music"
        """.trimIndent()

        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn(source))
    }

    @Test
    fun `a value that is not a known target is ignored rather than rejected`() {
        // A script written for a future target still runs here instead of
        // failing to load, and it stays in the pickers that always work.
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("""glyph.target = "haptics""""))
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("""glyph.target = "MUSIC2""""))
    }

    @Test
    fun `a value that is not an identifier is not a declaration at all`() {
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("""glyph.target = "123""""))
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("""glyph.target = "two words""""))
    }

    @Test
    fun `the target name is case insensitive at runtime`() {
        // `detectIn` is deliberately strict — it only recognises the literal
        // the docs show — but a name that arrives at runtime is normalised,
        // because that is a value the author typed rather than a pattern.
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.fromName("music"))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.fromName("MUSIC"))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.fromName("  Music  "))
        assertEquals(ScriptTarget.ANY, ScriptTarget.fromName("any"))
    }

    @Test
    fun `an unknown runtime name is reported rather than guessed at`() {
        // `null`, not [ScriptTarget.ANY]: a typo in `glyph.target` is a
        // mistake worth telling the author about.
        assertNull(ScriptTarget.fromName("musci"))
        assertNull(ScriptTarget.fromName(""))
    }

    // endregion

    // region Which picker offers what

    @Test
    fun `a music script is hidden from the trigger features`() {
        val music = script("""glyph.target = "music"""")

        assertEquals(ScriptTarget.MUSIC, music.target)
        assertFalse("Pulse Lock must not offer it", music.isVisibleIn(ScriptScope.TRIGGER))
    }

    @Test
    fun `a script with no target is offered by every picker`() {
        val plain = script("glyph.setAll(glyph.MAX)")

        assertEquals(ScriptTarget.ANY, plain.target)
        assertTrue(plain.isVisibleIn(ScriptScope.TRIGGER))
        assertTrue(plain.isVisibleIn(ScriptScope.MUSIC))
    }

    @Test
    fun `the music picker shows everything`() {
        // The asymmetry is the whole rule: a script with no target means
        // "anywhere", so it belongs in both; the reverse is not true, which
        // is why one enum value and a one-sided filter is enough.
        val music = script("""glyph.target = "music"""")

        assertTrue("the visualiser is where it belongs", music.isVisibleIn(ScriptScope.MUSIC))
    }

    // endregion
}
