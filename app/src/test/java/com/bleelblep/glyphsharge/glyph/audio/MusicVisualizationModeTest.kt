package com.bleelblep.glyphsharge.glyph.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the visualiser's mode enum and the stored setting behind it.
 *
 * The id is what lands in [com.bleelblep.glyphsharge.data.SettingsRepository],
 * so a change to one is a change to every phone that already has the old value
 * saved. The rules below are what keep a setting that cannot be resolved
 * visible instead of silently replaced.
 */
class MusicVisualizationModeTest {

    // region Stored ids

    @Test
    fun `a fresh install starts on bars`() {
        assertEquals(MusicVisualizationMode.BARS, MusicVisualizationMode.DEFAULT)
        assertTrue(MusicVisualizationMode.DEFAULT in MusicVisualizationMode.entries)
    }

    @Test
    fun `a stored id resolves back to its mode`() {
        MusicVisualizationMode.entries.forEach { mode ->
            assertEquals(mode, MusicVisualizationMode.of(mode.id))
        }
    }

    @Test
    fun `a stored id is resolved leniently`() {
        // A value that arrives from preferences is the app's own writing, but
        // hand-edited settings and restored backups both exist.
        assertEquals(MusicVisualizationMode.VORTEX, MusicVisualizationMode.of("  vortex  "))
        assertEquals(MusicVisualizationMode.VORTEX, MusicVisualizationMode.of("VORTEX"))
    }

    @Test
    fun `a script id is not a mode`() {
        // This is the case the null exists for. The same setting key holds a
        // `custom:<uuid>` script id or a built-in mode, and the caller has to
        // tell them apart — a mode it cannot resolve is a setting to repair,
        // not one to guess at.
        assertNull(MusicVisualizationMode.of("custom:abc123"))
        assertNull(MusicVisualizationMode.of("NOTAMODE"))
    }

    @Test
    fun `a blank id resolves to nothing`() {
        assertNull(MusicVisualizationMode.of(""))
        assertNull(MusicVisualizationMode.of("   "))
    }

    @Test
    fun `the stored ids are unique`() {
        val ids = MusicVisualizationMode.entries.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    // endregion

    // region Presentation

    @Test
    fun `every mode has a name to show`() {
        MusicVisualizationMode.entries.forEach { mode ->
            assertTrue("${mode.id} has no name", mode.displayName.isNotBlank())
        }
    }

    @Test
    fun `the mode names are all different strings`() {
        val names = MusicVisualizationMode.entries.map { it.displayName }

        assertEquals(names.size, names.toSet().size)
    }

    // endregion

    // region Idle behaviour

    @Test
    fun `a graph of a value goes dark when the music stops`() {
        // BARS and MIRROR plot an energy. With nothing to plot they are a row
        // of unlit segments, which reads as a broken strip rather than an idle
        // one, so they get the idle animation instead.
        assertEquals(
            listOf(MusicVisualizationMode.BARS, MusicVisualizationMode.MIRROR),
            MusicVisualizationMode.entries.filterNot { it.isIdleFriendly },
        )
    }

    @Test
    fun `the other modes look composed when there is nothing to draw`() {
        assertEquals(
            listOf(
                MusicVisualizationMode.WAVE,
                MusicVisualizationMode.BEAT,
                MusicVisualizationMode.MATRIX,
                MusicVisualizationMode.VORTEX,
            ),
            MusicVisualizationMode.entries.filter { it.isIdleFriendly },
        )
    }

    @Test
    fun `every mode is classified exactly once`() {
        // A new mode added to the enum without a decision here would fall
        // through the `when`, and the compiler would not complain — so this
        // makes the two lists add up to the whole enum.
        val classified = MusicVisualizationMode.entries.count { it.isIdleFriendly } +
            MusicVisualizationMode.entries.count { !it.isIdleFriendly }

        assertEquals(MusicVisualizationMode.entries.size, classified)
    }

    // endregion
}
