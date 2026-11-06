package com.bleelblep.glyphsharge.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Two decisions that live in the gap between screens, and so are invisible
 * from either.
 *
 * The arc picker is one: it must appear only on the phone that has three
 * strips, and only for the rule that draws the charge bar. Both halves of
 * that condition are easy to lose — a refactor that keys it off the trigger
 * alone would offer arcs on every phone, and one that keys it off the
 * animation alone would offer them everywhere — and neither failure throws,
 * it just shows a control that does nothing.
 *
 * The reference is the other: it exists so the `glyph` cheat sheet is *somewhere
 * findable*. Nothing stops it drifting back into the studio editor, where it
 * was folded under the code it documents and never opened, so the split itself
 * is the thing worth asserting.
 *
 * Source rather than a rendered test: both defects are about which condition
 * guards which composable, which is a fact about the text — and a Compose
 * test would need a 3a `Build.MODEL` to show the picker at all.
 */
class ReferenceAndArcGuardTest {

    private val screensRoot =
        File("src/main/java/com/bleelblep/glyphsharge/ui/screens")

    /** Comments stripped, so a guard cannot be satisfied by its own prose. */
    private fun codeOf(relative: String): String =
        File(screensRoot, relative).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

    private val constructor: String get() = codeOf("RuleConstructorScreen.kt")

    @Test
    fun `the arc picker is offered only for the battery animation`() {
        // The condition is read in the body, not inside the list's content
        // lambda: `LazyListScope` is not a composable scope, so a branch
        // decided there would not recompose when the animation changes and
        // the picker would stick to whatever was selected when the screen
        // opened.
        assertTrue(
            "the arc picker should be gated on the battery animation id",
            "selectedAnimation.id == FIXED_CHARGING_ANIMATION_ID" in constructor,
        )
        assertTrue(
            "the gate should be read in the body so the list recomposes on it",
            "val showArcPicker" in constructor,
        )
        assertTrue(
            "the arc picker should be drawn under its gate",
            "if (showArcPicker)" in constructor,
        )
    }

    @Test
    fun `the arc picker is offered only on the phone with three arcs`() {
        assertTrue(
            "the arc picker should be gated on the Phone (3a), which is the " +
                "only model with A and B strips to offer",
            "DeviceType.detect() == DeviceType.PHONE3A" in constructor,
        )
    }

    @Test
    fun `the arc travels from the form into the saved rule`() {
        // A picker whose value never reaches `onSave` is a control that
        // changes nothing: the arc would reset to C on every save, which is
        // the same defect as not having the picker, one step further along.
        assertTrue(
            "the save callback should carry the arc",
            "batteryArc: String," in constructor,
        )
        assertTrue(
            "performSave should pass the arc it holds",
            "SHAKE_STEPS[shakeStep.toInt()],\n            batteryArc," in constructor,
        )
    }

    @Test
    fun `the arc reaches the animation that draws it`() {
        val manager = File(
            "src/main/java/com/bleelblep/glyphsharge/glyph/GlyphAnimationManager.kt",
        ).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

        assertTrue(
            "the battery branch should hand the arc to the bar",
            "arc = batteryArc," in manager,
        )
        assertTrue(
            "every firing should pass the rule's own arc",
            "rule.batteryArc" in
                File("src/main/java/com/bleelblep/glyphsharge/services/RuleHostService.kt")
                    .readText(),
        )
    }

    /**
     * The worked example reads the clock off `glyph`, with no `require`.
     *
     * A `require` left here would teach a step the script does not need, and a
     * service — where `require` is banned outright — could not run a line of
     * it. The clock went flat for exactly that reason, so the example is the
     * one place the change is visible to a reader.
     */
    @Test
    fun `the worked example needs no require`() {
        val source = codeOf("ReferenceScreen.kt")
        val delimiter = "MODULE_REFERENCE_EXAMPLE = \"\"\""
        assertTrue("the worked example is not where the test thinks", delimiter in source)

        val example = source.substringAfter(delimiter).substringBefore("\"\"\"")

        assertFalse("the example should not ask for a module", "require(" in example)
        assertTrue(
            "the example should read the clock off glyph",
            ("glyph.hour" in example) && ("glyph.isNight" in example),
        )
    }

    @Test
    fun `a rule row says which arc it lights`() {
        // Two battery rules that differ only in strip would otherwise read
        // identically in the list — the same defect the thresholds' numbers
        // already answer for, one field along.
        val descriptions = File(
            "src/main/java/com/bleelblep/glyphsharge/ui/viewmodel/RuleDescriptions.kt",
        ).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

        assertTrue(
            "a non-default arc should be appended to the row",
            "\" (\$batteryArc)\"" in descriptions,
        )
        assertTrue(
            "the default arc should be left off, so a phone without the " +
                "choice does not describe a control it never offered",
            "batteryArc != GlyphRule.DEFAULT_BATTERY_ARC" in descriptions,
        )
    }

    @Test
    fun `the reference lives in settings, not in the studio editor`() {        val editor = codeOf("animations/AnimationEditorScreen.kt")

        assertFalse(
            "the cheat sheet belongs in the reference, not folded under the " +
                "code it documents",
            "ApiReference" in editor,
        )
        assertFalse(
            "the worked example moved with it",
            "MODULE_REFERENCE_EXAMPLE" in editor,
        )

        val reference = codeOf("ReferenceScreen.kt")
        assertTrue(
            "the reference should carry the cheat sheet's rows",
            "R.array.studio_api_calls" in reference,
        )
        assertTrue(
            "and both documents, service first",
            (reference.indexOf("ServiceReferenceCard") >= 0) &&
                (reference.indexOf("AnimationReferenceCard") >
                    reference.indexOf("ServiceReferenceCard")),
        )
    }

    @Test
    fun `the reference offers both starter scripts`() {
        val reference = codeOf("ReferenceScreen.kt")

        // The starters are the same strings the forms seed their buffers
        // with, so an example here and an example in the constructor cannot
        // drift apart.
        assertTrue(
            "the service section should show the service starter",
            "R.string.rules_starter_script" in reference,
        )
        assertTrue(
            "the animation section should show the animation starter",
            "R.string.studio_starter_script" in reference,
        )
    }
}
