package com.bleelblep.glyphsharge

import com.bleelblep.glyphsharge.glyph.script.ServiceScriptEngine
import com.bleelblep.glyphsharge.glyph.script.ServiceScriptFileFormat
import com.bleelblep.glyphsharge.glyph.script.module.FakeHost
import com.bleelblep.glyphsharge.glyph.script.module.testProfile
import com.bleelblep.glyphsharge.triggers.TriggerEvent
import com.bleelblep.glyphsharge.triggers.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The code that ships inside a string resource has to survive aapt's reading
 * of it, and for a while nothing checked that it did.
 *
 * ### What aapt does to a quote
 *
 * A double quote in a string resource is a *delimiter*, not a character: write
 * one raw, or write `&quot;`, and the value that reaches `getString` has no
 * quote at all. The only form that survives is `\"`. The service starter was
 * written with `&quot;`, so the template the constructor offered was missing
 * its quotes on every device — `glyph.on(screen_off, function()` — which is a
 * nil argument at load time, the script never loads, and the rule built from
 * it is dark forever with nothing on screen to say why.
 *
 * ### Why a test and not careful review
 *
 * Nothing about the written resource looks wrong: `&quot;` is the documented,
 * obviously-correct way to put a quote in XML, so review passes it every time,
 * and the app has no way to complain until a rule silently fails to load. Two
 * guards close that: every quote in every resource is checked to be written
 * the way aapt keeps, and the starter itself is resolved the way aapt resolves
 * it and put through the real engine — so a template that would not parse can
 * no longer reach a build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodeTemplateStringsTest {

    private val locales = listOf(
        File("src/main/res/values") to "EN",
        File("src/main/res/values-ru-rRU") to "RU",
    )

    @Before
    fun setUp() {
        locales.forEach { (dir, tag) ->
            assertTrue("cannot find ${dir.path}", dir.isDirectory)
            assertTrue("cannot find ${File(dir, "strings.xml").path}", File(dir, "strings.xml").isFile)
            tag.length // keeps the pair readable at the call sites below
        }
    }

    /**
     * Raw XML text of every `<string>` and every `<item>` in one locale.
     *
     * The items are walked too because the studio's cheat sheet is a
     * `string-array` of Lua signatures — code-shaped text in the same sense
     * the starter is, and the same resource rule applies to it.
     */
    private fun rawResources(dir: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(dir, "strings.xml"))
        val root = doc.documentElement

        val result = LinkedHashMap<String, String>()
        val strings = root.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i) as Element
            result[element.getAttribute("name")] = element.textContent.orEmpty()
        }
        val items = root.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val element = items.item(i) as Element
            val owner = (element.parentNode as? Element)
                ?.getAttribute("name")
                ?.takeIf { it.isNotBlank() }
                ?: "item"
            result["$owner[$i]"] = element.textContent.orEmpty()
        }
        return result
    }

    /**
     * Resolves one resource the way `getString` would see it.
     *
     * One pass, because the order is the whole point: entities are decoded
     * first, so `&quot;` *becomes* a raw quote — and a raw quote is a delimiter
     * aapt drops, which is exactly how the starter lost its quotes. A quote
     * that was written `\"` is an escape and survives, so it has to be resolved
     * as it is walked rather than stripped afterwards.
     */
    private fun resolveLikeAapt(raw: String): String {
        val entities = raw
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

        return buildString {
            var i = 0
            while (i < entities.length) {
                val ch = entities[i]
                when {
                    ch == '\\' && i + 1 < entities.length -> when (val next = entities[i + 1]) {
                        'n' -> { append('\n'); i += 2 }
                        't' -> { append('\t'); i += 2 }
                        '\\' -> { append('\\'); i += 2 }
                        '"' -> { append('"'); i += 2 }
                        '\'' -> { append('\''); i += 2 }
                        else -> { append(ch).append(next); i += 2 }
                    }
                    // A quote that is not an escape is a delimiter, and aapt
                    // removes it. This is the line the starter tripped over.
                    ch == '"' -> i++
                    else -> { append(ch); i++ }
                }
            }
        }
    }

    @Test
    fun `every quote in a resource is written the way aapt keeps it`() {
        for ((dir, tag) in locales) {
            for ((name, raw) in rawResources(dir)) {
                assertFalse(
                    "[$tag] '$name' uses &quot;, which aapt resolves to a quote and then drops",
                    raw.contains("&quot;"),
                )
                var i = 0
                while (i < raw.length) {
                    if (raw[i] == '"') {
                        assertTrue(
                            "[$tag] '$name' has a raw quote at $i; aapt drops it — write \\\"",
                            i > 0 && raw[i - 1] == '\\',
                        )
                    }
                    i++
                }
            }
        }
    }

    @Test
    fun `the service starter keeps its quotes through aapt and files screen_off`() {
        for ((dir, tag) in locales) {
            val raw = rawResources(dir)["rules_starter_script"]
            assertTrue("[$tag] rules_starter_script is missing", raw != null)

            val resolved = resolveLikeAapt(raw!!)
            assertTrue(
                "[$tag] the starter lost its quotes: $resolved",
                resolved.contains("glyph.on(\"screen_off\""),
            )

            var fired = false
            val engine = ServiceScriptEngine(
                profile = testProfile(),
                host = FakeHost(),
                granted = { true },
                onFireAnimation = { fired = true },
            )
            val load = engine.load(ServiceScriptFileFormat.decode(resolved, "Starter").source)

            assertTrue("[$tag] the starter does not load: $load", load.isSuccess)
            assertEquals("[$tag] wrong subscriptions", setOf(TriggerType.SCREEN_OFF), engine.subscriptions())
            engine.dispatch(TriggerEvent(TriggerType.SCREEN_OFF, TriggerType.SCREEN_OFF.id))
            assertTrue("[$tag] the starter never fired", fired)
            engine.stop()
        }
    }
}