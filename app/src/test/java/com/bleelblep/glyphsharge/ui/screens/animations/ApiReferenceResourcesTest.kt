package com.bleelblep.glyphsharge.ui.screens.animations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** The one prose string under the module list, present in every locale. */
private const val MODULES_NOTE = "studio_reference_modules_note"

/**
 * The editor's cheat sheet, and the pairing it depends on.
 *
 * ### Why a test and not a lint rule
 *
 * The cheat sheet is four `<string-array>` resources: a call and its
 * description, in Russian. The arrays are **index-aligned**, so a row added to
 * one and not to the other attaches every description to the call above it —
 * and a *missing translation* row shifts all of them by one, silently, in the
 * one place a script author is looking.
 *
 * Lint flags a missing `<string>`. It does not flag a missing `<string-array>`
 * item. So nothing would catch "someone added `glyph.blink()` to the English
 * list and did not translate it": the Russian card would keep rendering, one
 * row short, with the last two explanations swapped.
 *
 * This suite is what catches it. It reads the XML rather than the compiled
 * resources, because `stringArrayResource` silently falls back to the default
 * locale — which is the exact failure being guarded against, and it would make
 * the test pass while the bug shipped.
 */
class ApiReferenceResourcesTest {

    private val defaultDir = File("src/main/res/values")
    private val russianDir = File("src/main/res/values-ru-rRU")

    @Before
    fun setUp() {
        // A relative path is the working directory Gradle gives the test task.
        // Asserted rather than assumed: a missing file would otherwise read as
        // a missing translation and "pass" in the wrong direction.
        assertTrue("cannot find ${defaultDir.path}", defaultDir.isDirectory)
        assertTrue("cannot find ${russianDir.path}", russianDir.isDirectory)
    }

    /**
     * The parsed `strings.xml` root.
     *
     * `javax.xml` rather than `org.xmlpull`, whose factory moved packages
     * between versions and is not worth the import churn for two helpers.
     */
    private fun rootOf(directory: File): Element =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(directory, "strings.xml"))
            .documentElement

    /** `<item>` texts of a `<string-array>` in a given `strings.xml`. */
    private fun itemsIn(directory: File, arrayName: String): List<String> {
        val arrays = rootOf(directory).getElementsByTagName("string-array")
        for (index in 0 until arrays.length) {
            val array = arrays.item(index) as Element
            if (array.getAttribute("name") == arrayName) {
                val items = array.getElementsByTagName("item")
                return (0 until items.length).map { items.item(it).textContent.trim() }
            }
        }
        throw AssertionError("no <string-array name=\"$arrayName\"> in $directory")
    }

    /** The one prose string under the module list, present in every locale. */
    private fun stringIn(directory: File): String? {
        val strings = rootOf(directory).getElementsByTagName("string")
        for (index in 0 until strings.length) {
            val node = strings.item(index) as Element
            if (node.getAttribute("name") == MODULES_NOTE) {
                return node.textContent.trim()
            }
        }
        return null
    }

    /**
     * The one that matters: an added API row with no Russian translation would
     * shift every explanation below it by one, and nothing else would notice.
     */
    @Test
    fun `every call has a russian description`() {
        val calls = itemsIn(defaultDir, "studio_api_calls")
        val russian = itemsIn(russianDir, "studio_api_descriptions")

        assertEquals(
            "a call was added without a russian description — every explanation " +
                "below it is now attached to the wrong call",
            calls.size,
            russian.size,
        )
    }

    @Test
    fun `every module has a russian description`() {
        val calls = itemsIn(defaultDir, "studio_module_calls")
        val russian = itemsIn(russianDir, "studio_module_descriptions")

        assertEquals(
            "a module was added without a russian description",
            calls.size,
            russian.size,
        )
    }

    /**
     * Rows whose "description" is a list of API *values* rather than prose.
     *
     * `glyph.ease(t, kind)` takes these names as arguments, so a translation
     * would teach values the engine does not accept. They are legitimately the
     * same in both locales, and this list is where that exception lives — named
     * rather than inferred, so that a *second* row accidentally left untranslated
     * fails the test above instead of quietly joining this one.
     */
    private val identifierRows = setOf(
        "linear, in, out, inout, bounce, wave, pulse",
    )

    /**
     * No Russian row may simply be the English one. Pasting the English list
     * into the Russian file satisfies every other test in this suite.
     */
    @Test
    fun `the russian descriptions are actually translated`() {
        val english = itemsIn(defaultDir, "studio_api_descriptions")
        val russian = itemsIn(russianDir, "studio_api_descriptions")

        val untranslated = russian.indices.filter { index ->
            (russian[index] == english[index]) && (english[index] !in identifierRows)
        }
        assertTrue(
            "still in english at $untranslated: ${untranslated.map { english[it] }}",
            untranslated.isEmpty(),
        )
    }

    @Test
    fun `no russian description is blank`() {
        listOf("studio_api_descriptions", "studio_module_descriptions").forEach { array ->
            val blanks = itemsIn(russianDir, array).asSequence().withIndex()
                .filter { it.value.isBlank() }
                .map { it.index }
                .toList()
            assertTrue("$array has blank rows at $blanks", blanks.isEmpty())
        }
    }

    /**
     * A signature is an identifier, so it is defined in the default locale
     * only. A Russian copy would be a second definition that can drift, and a
     * drifted one would teach a function that does not exist.
     */
    @Test
    fun `the russian locale does not redefine the signatures`() {
        val text = File(russianDir, "strings.xml").readText()

        listOf("studio_api_calls", "studio_module_calls").forEach { array ->
            assertTrue(
                "$array is defined in the russian locale; signatures must have " +
                    "exactly one definition",
                !text.contains("name=\"$array\""),
            )
        }
    }

    /** The note under the module list is prose, so it is translated. */
    @Test
    fun `the module note is translated`() {
        val english = stringIn(defaultDir)
        val russian = stringIn(russianDir)

        assertNotNull("no english note", english)
        assertNotNull("no russian note", russian)
        assertTrue("the note is still english", english != russian)
    }

    /**
     * That the cheat sheet names the *real* API is deliberately **not** checked
     * here.
     *
     * A regex over the arrays cannot do it: `glyph.ch.c` and `glyph.set` differ
     * only after the dot, and any prefix rule that catches `setAll` would also
     * accept a name that does not exist. Doing it properly means asking the
     * engine — run a snippet that reads each name — which is what
     * `ScriptSandboxResourceTest` already does for the API surface.
     *
     * A weak version of this check is worse than none: it would pass while
     * letting through a typo, and it would be the only test in the suite with a
     * name-shaped hole in it.
     *
     * What this suite guarantees is the translation stays in step with the
     * document — which is the failure that silently reaches a phone, and the
     * only one a resource test can honestly catch.
     */
}
