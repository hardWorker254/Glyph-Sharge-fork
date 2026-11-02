package com.bleelblep.glyphsharge.glyph

import com.bleelblep.glyphsharge.ui.components.GlyphAnimations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The picker catalogue and the playback catalogue have to agree: a chip the
 * user can choose must resolve to an id the manager knows how to play.
 */
class AnimationCatalogTest {

    @Test
    fun `battery level is a built-in animation`() {
        assertEquals(GlyphAnimationId.BATTERY, GlyphAnimationId.of("BATTERY"))

        val battery = GlyphAnimations.list.firstOrNull { it.id == GlyphAnimationId.BATTERY.id }
        assertNotNull("the battery animation must be offered by the picker", battery)
        assertEquals("Battery Level", battery?.displayName)
    }
}
