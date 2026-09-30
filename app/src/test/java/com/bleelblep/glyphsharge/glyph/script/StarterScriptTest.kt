package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The template every new animation starts from has two promises, and neither
 * is checked anywhere else.
 *
 * **It must run.** The starter script is the first code anyone in this app
 * ever executes: press New, press Glyph. A typo in a function name fails on
 * the hardware, in the user's hand, with a phone to hand and no other
 * example to compare against. It is a string resource rather than a Kotlin
 * constant precisely so the comments can be localised, which is also why
 * this test reads the XML instead of the compiled resource.
 *
 * **It must run on every supported phone.** The template divides the step by
 * the length of the C strip, and that is a claim about four different
 * layouts: 4 segments on a Phone (1), 16 on a Phone (2), 24 on a Phone (2a),
 * 20 on a Phone (3a). A single pass works out at 1200 ms on three of them
 * and 1184 on the fourth, which is the point — the animation reads at the
 * same speed rather than crawling on the short strip and blurring on the
 * long one.
 */
class StarterScriptTest {

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

    private val supported = listOf(
        DeviceType.PHONE1,
        DeviceType.PHONE2,
        DeviceType.PHONE2A,
        DeviceType.PHONE3A
    )

    private fun profile(type: DeviceType): DeviceProfile =
        requireNotNull(DeviceProfileFactory.forDeviceOrNull(type)) { "$type has no layout" }

    // region The resource itself

    @Test
    fun `the template is present in both locales`() {
        listOf("values", "values-ru-rRU").forEach { folder ->
            assertTrue(
                "no studio_starter_script under res/$folder",
                File("src/main/res/$folder/strings.xml").readText()
                    .contains("name=\"studio_starter_script\"")
            )
        }
    }

    @Test
    fun `the Lua is byte-for-byte identical across locales`() {
        // The comment above the resource promises this, and the promise is
        // what lets the two translations drift apart only in prose. Nothing
        // enforces it, so without this test the English copy would quietly
        // gain a fix the Russian one never received.
        val english = luaOf("values")
        val russian = luaOf("values-ru-rRU")

        assertEquals("the Lua halves have drifted apart", english, russian)
        assertTrue("the template is empty", english.isNotBlank())
    }

    @Test
    fun `the template uses require, which is the whole point of the rewrite`() {
        // Not a style rule. A template that teaches the flat API teaches the
        // one the new modules were meant to replace, and the six names are
        // discoverable from the cheat sheet while `require` itself is not.
        assertTrue("the template never calls require", luaOf("values").contains("require("))
    }

    // endregion

    // region It runs, everywhere

    @Test
    fun `the template runs on every supported phone`() {
        val source = luaOf("values")

        supported.forEach { type ->
            val host = FakeHost()
            val result = LuaScriptEngine(profile(type), host).run(source, 10_000L)

            assertEquals(
                "${type.name}: the template did not run",
                ScriptStatus.COMPLETED,
                result.status
            )
            // One draw per segment per pass, and the template makes two passes.
            assertEquals(
                "${type.name}: wrong number of frames for ${profile(type).c.size} segments",
                2 * profile(type).c.size,
                host.frames.size
            )
        }
    }

    @Test
    fun `one pass takes about the same time on every supported phone`() {
        // The portability claim the template's own comment makes, measured
        // rather than asserted. The band is deliberately loose: the sleeps are
        // real, and a loaded machine is allowed to be slow. What would fail
        // here is a fixed step, which is exactly the regression.
        val source = luaOf("values")
        val timings = supported.associateWith { type ->
            LuaScriptEngine(profile(type), FakeHost()).run(source, 10_000L).elapsedMs
        }

        assertEquals(
            "a run was not measured: $timings",
            supported.size,
            timings.size
        )
        timings.forEach { (type, elapsed) ->
            assertTrue(
                "$type took ${elapsed}ms, outside the band a single pass should occupy",
                elapsed in 600L..2_000L
            )
        }
    }

    @Test
    fun `the wave ramps its brightness instead of lighting every segment the same`() {
        // `util.lerp` is why the template requires anything at all, so the
        // template is where it is worth pinning: without the ramp the module
        // call would be decoration.
        val type = DeviceType.PHONE3A
        val p = profile(type)
        val host = FakeHost()
        LuaScriptEngine(p, host).run(luaOf("values"), 10_000L)

        val first = host.frames.first().second
        val peak = host.frames[p.c.size].second
        val last = host.frames.last().second

        // The bottom of the ramp is `lerp(1000, MAX, 1/n)`, not 1000: the
        // first segment sits one step in, and a Phone (3a) has 20 of them.
        assertEquals("the wave should open at the bottom of the ramp", 1000 + 3000 / p.c.size, first)
        assertEquals("the wave should peak at MAX", 4000, peak)
        assertTrue("the wave should fade back down, was $last", last < peak)
        assertTrue("the wave should not be flat, first was $first", first < peak)
    }

    // endregion

    /**
     * The `studio_starter_script` value under `src/main/res/<folder>`, with
     * the Android escapes undone and the comments dropped.
     *
     * The XML is read directly because these suites run without a
     * Robolectric runner, so `R.string` is an int and `getString` returns a
     * stub. Reading the file is the only way to assert anything about a
     * resource here — and the thing worth asserting, that the Lua runs, is
     * worth the small ugliness.
     */
    private fun luaOf(folder: String): String {
        val xml = File("src/main/res/$folder/strings.xml").readText()
        val raw = Regex("<string name=\"studio_starter_script\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?: error("studio_starter_script is missing from res/$folder")

        return UNESCAPE.replace(raw) { match ->
            when (match.groupValues[1]) {
                "n" -> "\n"
                "t" -> "\t"
                else -> match.groupValues[1]
            }
        }
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }
            .joinToString("\n")
    }

    /** Android resource escaping, one pass so `\\n` is not read as a newline. */
    private val UNESCAPE = Regex("""\\(.)""", RegexOption.DOT_MATCHES_ALL)
}
