package com.bleelblep.glyphsharge.services

import com.bleelblep.glyphsharge.glyph.GlyphFeature
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nobody may walk the enum looking for something the registry does not have.
 *
 * This file exists because the app would not launch.
 *
 * `GlyphFeature` carries `PREVIEW` — a strip participant with no service and
 * no preference, and therefore no `FeatureSpec`. Three places iterated
 * `GlyphFeature.entries` and asked `FeatureSpecs.of` about each value:
 * `startAllEnabled` and `stopAll` in the controller, and `refreshFeatures` in
 * the home ViewModel. The first threw, out of `MainActivity.onCreate`, before
 * a frame was drawn.
 *
 * The existing tests missed it because they asked the registry too. Everything
 * was verified from `FeatureSpecs.all`, which by construction contains only
 * values that *have* a spec — so the one thing that mattered, the gap between
 * the two collections, was never in view. These are written against the enum,
 * which is the collection that caused it.
 */
class FeatureRegistryCoverageTest {

    private val root = File("src/main/java/com/bleelblep/glyphsharge")

    private fun codeOf(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val controller = codeOf("services/FeatureServiceController.kt")
    private val homeViewModel = codeOf("ui/viewmodel/HomeViewModel.kt")
    private val specs = codeOf("services/FeatureSpec.kt")

    /** Values that legitimately have no spec, and so must never reach `of()`. */
    private val specless: Set<GlyphFeature> = FeatureSpecs.NON_FEATURE_PARTICIPANTS

    @Test
    fun `the enum and the registry are expected to differ, and that is fine`() {
        // Stated rather than asserted away, because the instinct this file is
        // here to correct is to "fix" the difference by deleting PREVIEW — which
        // would put the studio's preview back outside the strip mutex, where it
        // drew over a live service's animation.
        assertTrue(
            "if nothing is spec-less any more, NON_FEATURE_PARTICIPANTS should be " +
                "empty and this whole concern goes away",
            specless.isNotEmpty(),
        )
        assertTrue(
            "and every spec-less value really is absent from the registry",
            specless.none { feature -> FeatureSpecs.all.any { it.feature == feature } },
        )
    }

    @Test
    fun `a spec-less value has no entry, and asking for one fails loudly`() {
        // Loudly is the point. This is what turned a missing spec into a stack
        // trace instead of a feature that silently never runs — and it is also
        // what a stack-less launch crash gave us to work with.
        specless.forEach { feature ->
            val thrown = runCatching { FeatureSpecs.of(feature) }.exceptionOrNull()
            assertTrue(
                "FeatureSpecs.of($feature) should throw — it is what makes this " +
                    "class of mistake visible instead of silent",
                thrown is IllegalStateException,
            )
        }
    }

    @Test
    fun `the controller walks the registry, not the enum`() {
        // These two threw, and one of them runs in `onCreate`.
        assertTrue(
            "startAllEnabled must iterate the registry; the enum contains a value " +
                "with no service and the lookup would throw during Activity launch",
            controller.contains("FeatureSpecs.all.forEach"),
        )
        assertTrue(
            "stopAll must too",
            controller.substringAfter("fun stopAll()").contains("FeatureSpecs.all"),
        )
        assertTrue(
            "and neither may reach for GlyphFeature.entries again",
            !controller.contains("GlyphFeature.entries"),
        )
    }

    @Test
    fun `the home screen walks the registry, not the enum`() {
        // This one did not crash — it put a card on the home screen for a
        // setting that does not exist, and asked `readAll` about a key it will
        // never hold. Quieter, and it is why this is a file and not one
        // assertion.
        val refresh = homeViewModel.substringAfter("fun refreshFeatures()")
            .substringBefore("\n    }")

        assertTrue("the feature rows must come from the registry", refresh.contains("FeatureSpecs.all.associate"))
        assertTrue("and not from the enum", !refresh.contains("GlyphFeature.entries"))
    }

    @Test
    fun `the enum is only walked where the gap is the point`() {
        // One legitimate use remains, in `FeatureSpecs.init`, and it is the check
        // that makes an unlisted value a build-up failure rather than a crash at
        // launch.
        assertTrue(
            "the registry's own completeness check should still compare against the enum",
            specs.contains("GlyphFeature.entries"),
        )
        assertTrue(
            "and should exempt exactly the declared participants",
            specs.contains("filterNot { it in NON_FEATURE_PARTICIPANTS }"),
        )
    }

    @Test
    fun `no source file walks the enum and then looks up a spec`() {
        // The general form of the three fixes above, so the next one is caught
        // by a test rather than by a user's phone refusing to open.
        val offenders = File(root, "").walkTopDown()
            .filter { (it.isFile) && (it.extension == "kt") }
            .filter { file ->
                val code = codeOf(file.relativeTo(root).path)
                code.contains("GlyphFeature.entries") && code.contains("FeatureSpecs.of")
            }
            .map { it.relativeTo(root).path }
            .filterNot { it == "services/FeatureSpec.kt" }
            .toList()

        assertTrue(
            "these files walk the enum and then ask the registry, which is the " +
                "combination that crashed on launch:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}