package com.bleelblep.glyphsharge.glyph

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards who is allowed to draw on the strip.
 *
 * Two defects, both invisible at runtime and both with the same cause: nothing
 * had to declare *that it was drawing*, so a second drawer could arrive and
 * share state with the first without either of them knowing.
 */
class StripOwnershipTest {

    private val root = File("src/main/java/com/bleelblep/glyphsharge")

    private fun codeOf(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val renderer = codeOf("glyph/engine/GlyphRenderer.kt")
    private val coordinator = codeOf("glyph/GlyphFeatureCoordinator.kt")

    @Test
    fun `releasing the renderer requires proving you still hold it`() {
        // `running` was one @Volatile Boolean that anybody could clear.
        //
        // The failure is a timing one, and that is what made it survive: when
        // one feature preempts another, the preempted animation unwinds through
        // its own `finally` — and can do so *after* the preempting one has
        // already started. The stale `renderer.stop()` then cleared the flag the
        // new owner's `while (isRunning)` loop was watching, and that animation
        // stopped at its next step, halfway, with nothing in any log.
        assertTrue(
            "the renderer should hand out a lease rather than a bare flag",
            renderer.contains("fun start(owner: String): RenderLease"),
        )
        assertTrue(
            "and releasing should compare against it",
            (renderer.contains("fun stop(lease: RenderLease)")) &&
                (renderer.contains("holder.compareAndSet(lease, null)")),
        )
        assertFalse(
            "there must be no way to clear the claim without a lease",
            renderer.contains("fun stop() {"),
        )
        assertTrue(
            "the preempting side keeps its own escape hatch",
            renderer.contains("fun stopAll()"),
        )
    }

    @Test
    fun `every drawer keeps its lease and releases it with that lease`() {
        // Three call sites, all of which used to call a bare `stop()` in a
        // `finally`. Any of them could have been the one that killed an
        // unrelated animation.
        mapOf(
            "glyph/animations/AnimationRunner.kt" to "animation",
            "glyph/script/ScriptPlayback.kt" to "script",
            "glyph/audio/MusicVisualisation.kt" to "music-preview",
        ).forEach { (path, owner) ->
            val code = codeOf(path)
            assertTrue(
                "$path should claim the renderer with a name",
                code.contains("renderer.start(\"$owner\")"),
            )
            assertTrue(
                "$path must release that lease, not clear the flag: a bare stop() here " +
                    "would take down whichever animation started in the meantime",
                code.contains("renderer.stop(lease)"),
            )
        }
    }

    @Test
    fun `only the preempting side may clear the claim unconditionally`() {
        // `stopAnimations` is the exception, and deliberately: it is what
        // interrupts the owner the coordinator is about to replace, and an
        // interrupted animation has no lease left to prove itself with.
        val manager = codeOf("glyph/GlyphAnimationManager.kt")

        assertTrue(
            "the interrupting path should clear the claim outright",
            manager.contains("renderer.stopAll()"),
        )
        assertFalse(
            "but it must not reach for the lease-taking path",
            manager.contains("renderer.stop("),
        )
    }

    @Test
    fun `previews take the strip through the coordinator`() {
        // The other half. Both previews used to draw straight onto the strip
        // without the mutex: the studio opened while a service was playing, both
        // interleaved on the same LEDs, and the preview's teardown cleared the
        // flag the service's loop was watching.
        //
        // `preempt = false` on both — a preview is the user checking something
        // out, and interrupting a charging animation for that is worse than the
        // preview not appearing.
        listOf(
            "glyph/script/ScriptPlayback.kt",
            "glyph/audio/MusicVisualisation.kt",
        ).forEach { path ->
            val preview = codeOf(path).substringAfter("suspend fun preview")
            assertTrue("$path's preview should go through withStrip", preview.contains("withStrip("))
            assertTrue(
                "$path's preview should not preempt a feature",
                preview.contains("preempt = false"),
            )
            assertTrue(
                "$path should claim ownership as PREVIEW, which has a spec-less entry " +
                    "rather than quietly skipping the mutex",
                preview.contains("GlyphFeature.PREVIEW"),
            )
        }
    }

    @Test
    fun `a preview that cannot get the strip says so`() {
        // `withStrip` returns `null` when it did not take the strip. Propagating
        // that, or a result that looks like the script ran, would leave the user
        // watching an editor that appeared to do nothing.
        val preview = codeOf("glyph/script/ScriptPlayback.kt")
            .substringAfter("suspend fun previewScript")
            .substringBefore("\n    }")

        assertTrue(
            "a refused preview should be an answer the studio can print",
            preview.contains("?: ScriptRunResult("),
        )
        assertTrue("and it should name the reason", preview.contains("busy"))
    }

    @Test
    fun `PREVIEW is a participant without a feature spec, and is listed as such`() {
        // `FeatureSpecs` fails the build-up if any GlyphFeature has no entry,
        // which is right for a feature — no spec means no service and no
        // preference. PREVIEW has neither, so it is listed explicitly rather
        // than the check being loosened into a no-op.
        val specs = codeOf("services/FeatureSpec.kt")

        assertTrue(
            "PREVIEW should be declared as a non-feature participant",
            (specs.contains("NON_FEATURE_PARTICIPANTS")) &&
                (specs.contains("setOf(GlyphFeature.PREVIEW)")),
        )
        assertTrue(
            "and the check should exempt exactly that set",
            specs.contains("filterNot { it in NON_FEATURE_PARTICIPANTS }"),
        )
        assertFalse(
            "PREVIEW must not have a spec of its own: it has no service to start " +
                "and no preference to read",
            specs.contains("feature = GlyphFeature.PREVIEW"),
        )
    }

    @Test
    fun `the coordinator no longer waits for the session while holding the strip`() {
        // Carried over from an earlier fix and worth keeping pinned: `acquire`
        // used to take the strip mutex and *then* wait up to two seconds for
        // the Glyph service to bind, while every other feature gives up after
        // 500 ms. One trigger during a reconnect dropped every other trigger in
        // that window — and the feature that got in could not draw either.
        val acquire = coordinator.substringAfter("suspend fun acquire(")
            .substringBefore("\n    }")

        assertTrue("acquire should ensure the session", acquire.contains("ensureSession()"))
        assertTrue(
            "and only then contend for the strip",
            (acquire.indexOf("ensureSession()")) < (acquire.indexOf("tryLockWithin(")),
        )
    }
}
