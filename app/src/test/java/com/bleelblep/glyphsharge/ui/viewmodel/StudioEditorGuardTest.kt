package com.bleelblep.glyphsharge.ui.viewmodel

import com.bleelblep.glyphsharge.glyph.script.ScriptTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the studio must not lose, and must not recompute on every keystroke.
 *
 * Both halves of this file were defects that looked finished: the editor
 * tracked whether work was unsaved and drew a dot for it, and the row showed a
 * character count. Neither meant much, because the buffer was dropped on back
 * without asking and the count was a clamp.
 */
class StudioEditorGuardTest {

    private val root = File("src/main/java/com/bleelblep/glyphsharge")

    private fun codeOf(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val viewModel = codeOf("ui/viewmodel/AnimationStudioViewModel.kt")
    private val activity = codeOf("CustomAnimationsActivity.kt")
    private val listScreen = codeOf("ui/screens/animations/AnimationListScreen.kt")
    private val target = codeOf("glyph/script/ScriptTarget.kt")

    @Test
    fun `saving without an editor open says so instead of crashing`() {
        // `save()` was `requireNotNull(state.editing)`. The KDoc argued
        // `editing` "is never null while the editor is open" — which is a claim
        // about the editor, not about the function. It is a public method, and
        // anything that saves without an editor open took the process down with
        // an `IllegalArgumentException` raised from a ViewModel.
        assertFalse(
            "a ViewModel must not crash the app; the guard belongs in the UI " +
                "state, not in a precondition",
            viewModel.contains("requireNotNull(state.editing)"),
        )

        val save = viewModel.substringAfter("fun save()").substringBefore("\n    }")
        assertTrue("save() should read the editing draft", save.contains("state.editing"))
        assertTrue("and return rather than proceed without it", save.contains("return"))
    }

    @Test
    fun `leaving the editor with unsaved work asks first`() {
        // Both exits used to call `closeEditor()` directly, which drops `name`
        // and `source` and resets `isDirty`. A script half written and then lost
        // to a back press was indistinguishable from one never typed — while
        // `isDirty` was computed and drawn as a dot the whole time.
        val editorBranch = activity.substringAfter("state.isEditorOpen ->")
            .substringBefore("storeOpen ->")

        assertFalse(
            "back must not close the editor outright",
            editorBranch.contains("BackHandler { viewModel.closeEditor() }"),
        )
        assertTrue(
            "the editor's exits must go through one gate",
            editorBranch.contains("leaveEditor()"),
        )
        assertTrue(
            "and the gate must ask when the buffer is dirty",
            (editorBranch.contains("state.isDirty")) &&
                (editorBranch.contains("askToDiscard = true")),
        )
        assertTrue(
            "the question itself should survive a rotation",
            editorBranch.contains("rememberSaveable"),
        )
        assertTrue(
            "and the editor's own back button must go through the same gate as the " +
                "system gesture, or one of them still loses work",
            editorBranch.contains("onBackClick = { leaveEditor() }"),
        )
    }

    @Test
    fun `the character count is the real one`() {
        // `coerceAtMost(9999)` fed the *quantity* to `pluralStringResource`, so
        // a 12 000-character script reported 9 999 — in the plural form that
        // belongs to a number it did not have, on the one line whose job is to
        // say what is on the phone.
        assertFalse(
            "the count must not be clamped before it becomes the plural quantity",
            listScreen.contains("coerceAtMost(9999)"),
        )
        assertTrue(
            "the row should count the real source length",
            listScreen.contains("val charCount = animation.source.length"),
        )
    }

    @Test
    fun `detecting a script's target short-circuits before the expensive part`() {
        // `detectIn` is keyed on the source buffer, so it runs on every
        // keystroke while the user types. Both regex replaces allocate a full
        // copy of the script, twice, at the typing rate.
        val detect = target.substringAfter("fun detectIn(source: String): ScriptTarget {")
            .substringBefore("\n        }")

        val precheck = detect.indexOf("contains(FIELD_NAME)")
        val replaces = detect.indexOf("replace(BLOCK_COMMENT")

        assertTrue("detectIn should have a cheap pre-check", precheck >= 0)
        assertTrue("and should still strip comments for a script that has one", replaces >= 0)
        assertTrue(
            "the pre-check must come first, or it saves nothing",
            precheck in (0 until replaces),
        )
    }

    @Test
    fun `the pre-check cannot reject a declaration the pattern would have found`() {
        // The pattern accepts `glyph . target` with spaces, which is why the
        // pre-check could not simply be the pattern itself — it has to be looser.
        // Tightened to "glyph.target" it would be faster and would silently stop
        // classifying a spaced-out declaration, filing the script under the wrong
        // service.
        //
        // This is the property that makes the optimisation safe, so it is pinned
        // behaviourally rather than as a shape of the source.
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("""glyph . target = "music""""))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("""glyph.target = "music""""))
        assertEquals(ScriptTarget.MUSIC, ScriptTarget.detectIn("glyph\n  .target\n  = 'music'"))
    }

    @Test
    fun `a script with no declaration is still classified as any`() {
        // The whole point of the pre-check is that this path is common, so it
        // must not have changed what it answers.
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("glyph.setAll(glyph.MAX)"))
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn(""))
        assertEquals(
            "a word merely containing the field name must not be mistaken for it",
            ScriptTarget.ANY,
            ScriptTarget.detectIn("local retarget = 1\nglyph.hold(60)"),
        )
    }

    @Test
    fun `a commented-out declaration is still not read`() {
        // The fast path cannot change this, and it is the one classification
        // error that fails silently — the script simply does not appear in the
        // picker it belongs in.
        assertEquals(ScriptTarget.ANY, ScriptTarget.detectIn("""-- glyph.target = "music""""))
        assertEquals(
            ScriptTarget.ANY,
            ScriptTarget.detectIn("""--[[ glyph.target = "music" ]]"""),
        )
    }
}
