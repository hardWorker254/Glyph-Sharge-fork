package com.bleelblep.glyphsharge

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every user-visible string exists in Russian, and no Russian translation is
 * blank or orphaned.
 *
 * ### Why a test and not a lint rule
 *
 * Android Lint does have `MissingTranslation`, so the honest question is why
 * another guard. Three reasons it does not cover what happened here:
 *
 *  1. It is not run by `testDebugUnitTest`. The constructor shipped its 28
 *     strings — the section title, the field labels, the whole trigger palette
 *     — into the Russian build while only the default locale had them, and the
 *     suite stayed green because lint is a separate, optional step. A rule
 *     nobody runs is not a rule.
 *  2. It fires as a *warning* in several configurations rather than failing the
 *     build, and new keys are exactly when it is easiest to wave away.
 *  3. It says nothing about the reverse direction. A key renamed in the default
 *     file leaves the old Russian entry behind, translating a string the app no
 *     longer asks for while the new one silently falls back to English.
 *
 * So this suite reads both XML files the same way [the studio cheat-sheet
 * suite][com.bleelblep.glyphsharge.ui.screens.animations.ApiReferenceResourcesTest]
 * does, for the same reason: compiled resources fall back to the default locale
 * on a miss, which would make the test pass while the bug shipped.
 *
 * ### Covered, and not
 *
 * `<string>` and `<plurals>` are checked by name. `<string-array>` deliberately
 * is not: `studio_api_calls` and `studio_module_calls` hold *signatures*, which
 * are identifiers and so live in the default locale only — a Russian copy would
 * be a second definition that can drift. The arrays that must line up are
 * guarded item-by-item by the cheat-sheet suite, which can tell a missing row
 * apart from a legitimately shared one; a name check here cannot.
 */
class LocalizationTest {

    private val defaultDir = File("src/main/res/values")
    private val russianDir = File("src/main/res/values-ru-rRU")

    @Before
    fun setUp() {
        // A relative path is the working directory Gradle gives the test task.
        // Asserted rather than assumed: a missing directory would otherwise read
        // as an empty locale and "pass" by reporting every string as missing.
        assertTrue("cannot find ${defaultDir.path}", defaultDir.isDirectory)
        assertTrue("cannot find ${russianDir.path}", russianDir.isDirectory)
    }

    /**
     * The parsed `strings.xml` root.
     *
     * `javax.xml` rather than `org.xmlpull`, matching the cheat-sheet suite for
     * the same reason it chose it: the factory is stable across the versions
     * this project's test classpath sees.
     */
    private fun rootOf(directory: File): Element =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(directory, "strings.xml"))
            .documentElement

    /** `name` attributes of every `<tag>` in a given `strings.xml`. */
    private fun namesIn(directory: File, tag: String): Set<String> {
        val nodes = rootOf(directory).getElementsByTagName(tag)
        return (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttribute("name") }
            .toSet()
    }

    /** `<string>` texts keyed by name, for the blank check. */
    private fun textsIn(directory: File): Map<String, String> {
        val nodes = rootOf(directory).getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it) as Element
            node.getAttribute("name") to node.textContent.trim()
        }
    }

    /**
     * The one that matters: a new string with no Russian translation renders
     * English inside an otherwise Russian app — which is exactly how the whole
     * rules section shipped.
     */
    @Test
    fun `every default string exists in russian`() {
        val missing = namesIn(defaultDir, "string") - namesIn(russianDir, "string")

        assertTrue(
            "missing russian for $missing — these render english in a russian app",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every default plural exists in russian`() {
        val missing = namesIn(defaultDir, "plurals") - namesIn(russianDir, "plurals")

        assertTrue("missing russian plural $missing", missing.isEmpty())
    }

    /**
     * A rename in the default file orphans the Russian entry: the app asks for
     * the new name, gets nothing, falls back to English, and the stale translation
     * sits there looking like coverage. The count-only checks above would not see
     * it — one added and one orphaned key keep the counts even.
     */
    @Test
    fun `no russian string has lost its default counterpart`() {
        val orphaned = namesIn(russianDir, "string") - namesIn(defaultDir, "string")

        assertTrue(
            "russian defines $orphaned, which the default locale no longer has",
            orphaned.isEmpty(),
        )
    }

    /**
     * A present-but-empty translation is worse than a missing one: it survives a
     * "is this key defined?" check and then renders nothing where a label
     * belongs.
     */
    @Test
    fun `no russian string is blank`() {
        val blanks = textsIn(russianDir).filterValues { it.isBlank() }.keys

        assertTrue("blank russian translations: $blanks", blanks.isEmpty())
    }
}
