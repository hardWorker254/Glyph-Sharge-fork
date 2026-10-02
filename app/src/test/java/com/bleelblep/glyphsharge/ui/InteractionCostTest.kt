package com.bleelblep.glyphsharge.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a drag must not cost.
 *
 * A `Slider` reports a value for every pixel of travel. Anything expensive or
 * physical attached to `onValueChange` therefore happens tens of times for one
 * gesture, and none of it is visible — the user sees one number and feels one
 * buzz. Both of these were attached to the per-pixel callback.
 */
class InteractionCostTest {

    private val root = File("src/main/java/com/bleelblep/glyphsharge")

    private fun codeOf(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val sliders = listOf(
        "ui/components/ChargingAnimation.kt",
        "ui/components/LowBattery.kt",
        "ui/components/MusicVisualizer.kt",
        "ui/components/NfcGlyph.kt",
        "ui/components/PowerPeek.kt",
        "ui/components/PulseLock.kt",
        "ui/components/ScreenOff.kt",
        "ui/components/VpnConnected.kt",
        "ui/components/FontSettingsComponents.kt",
    )

    /** The body of every `onValueChange = { ... }` lambda in [source], brace-balanced. */
    private fun onValueChangeBodies(source: String): List<String> =
        Regex("""onValueChange = \{""").findAll(source).map { match ->
            var depth = 1
            var i = match.range.last + 1
            while ((i < source.length) && (depth > 0)) {
                when (source[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            source.substring(match.range.last + 1, i)
        }.toList()

    @Test
    fun `no slider buzzes on every pixel of a drag`() {
        val offenders = sliders.asSequence().flatMap { path ->
            onValueChangeBodies(codeOf(path))
                .asSequence()
                .filter { it.contains("HapticUtils") }
                .map { path }
        }.distinct().toList()

        assertTrue(
            "these files still vibrate inside onValueChange, so a drag buzzes " +
                "continuously under the thumb instead of once at the end:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every slider answers the end of the gesture`() {
        // A slider with a per-pixel callback but no `onValueChangeFinished`
        // cannot express the one thing the haptic should hang off.
        sliders.forEach { path ->
            val code = codeOf(path)
            val count = Regex("""onValueChange = \{""").findAll(code).count()
            val finished = Regex("""onValueChangeFinished""").findAll(code).count()

            assertTrue(
                "$path has $count per-pixel callbacks but $finished finished-callbacks",
                finished >= count,
            )
        }
    }

    @Test
    fun `the app-wide font size is written once per gesture, not once per pixel`() {
        // This one mattered more than the others. The size is the app's whole
        // typography, so every intermediate value meant a `SharedPreferences`
        // commit *and* a recomposition of every screen in the app — for a value
        // the user was still dragging away from.
        val fontState = codeOf("ui/theme/FontState.kt")
        val update = fontState.substringAfter("fun updateFontSize(")
            .substringBefore("\n    }")

        assertTrue(
            "updateFontSize should be able to skip the write while a drag is in progress",
            (update.contains("persist: Boolean = true")) && (update.contains("if (persist)")),
        )

        val screen = codeOf("ui/screens/FontSettingsScreen.kt")
        assertTrue(
            "the live callback must not persist, or the split is pointless",
            screen.contains("persist = false"),
        )
        assertTrue(
            "and the end-of-gesture callback must",
            (screen.contains("onSizeChangeFinished")) && (screen.contains("persist = true")),
        )
    }

    @Test
    fun `the font size slider buzzes once, not twice`() {
        // The haptic used to fire inside `FontSizeSlider` *and* in the caller's
        // lambda, so every step of the drag was two vibrations. The slider now
        // leaves it entirely to the caller, which fires once the drag ends.
        val slider = codeOf("ui/components/FontSettingsComponents.kt")
            .substringAfter("private fun FontSizeSlider(")
            .substringBefore("\n    }\n}")

        assertFalse(
            "the slider itself must not vibrate; the caller's finished-callback does",
            onValueChangeBodies(slider).any { it.contains("HapticUtils") },
        )
        assertTrue(
            "and it must forward the end of the gesture",
            slider.contains("onValueChangeFinished"),
        )
    }

    @Test
    fun `the tile asks the registry for the run gate instead of repeating it`() {
        // The tile spelled out `isMusicVizEnabled() && getGlyphServiceEnabled()`
        // — the same pair `MusicVisualizerService` asks `FeatureSpec` about. A
        // third condition added to the gate would have reached the service and
        // not the tile, and the shade would have shown an active capture over a
        // dark strip.
        val tile = codeOf("tiles/MusicVisualizerTileService.kt")
        val isRunning = tile.substringAfter("private fun isRunning(): Boolean")
            .substringBefore("\n")

        assertTrue(
            "the tile should read the same registry entry the service does",
            isRunning.contains("spec.isRunnable(settingsRepository)"),
        )
        assertFalse(
            "the gate must not be written out longhand here",
            (isRunning.contains("isMusicVizEnabled()")) ||
                (isRunning.contains("getGlyphServiceEnabled()")),
        )
        assertTrue(
            "and it must come from the registry, not a literal",
            tile.contains("FeatureSpecs.of(GlyphFeature.MUSIC_VISUALIZER)"),
        )
    }
}
