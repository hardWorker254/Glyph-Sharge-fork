package com.bleelblep.glyphsharge.glyph.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import prompt's honesty, in one function: [ScriptCapability.detect] is
 * what decides *what the user is asked about*, so a miss here is a capability
 * that gets used without ever being granted.
 */
class ScriptCapabilityTest {

    @Test
    fun `glyph battery and charging declare DEVICE_STATE`() {
        val source = """
            glyph.on("power_changed", function()
                if glyph.battery < 20 then glyph.playAnimation() end
            end)
            if glyph.charging then glyph.log("charging") end
        """.trimIndent()

        assertEquals(setOf(ScriptCapability.DEVICE_STATE), ScriptCapability.detect(source))
    }

    @Test
    fun `a script that reaches for nothing declares nothing`() {
        val source = """
            glyph.on("screen_off", function()
                glyph.playAnimation()
            end)
        """.trimIndent()

        assertTrue(ScriptCapability.detect(source).isEmpty())
    }

    @Test
    fun `whitespace between the dots still counts`() {
        // Lua accepts `glyph . battery`; the scanner must read it the same way,
        // or the prompt would be skippable by a formatter.
        assertTrue(ScriptCapability.DEVICE_STATE.isRequestedIn("local x = glyph . battery"))
    }

    @Test
    fun `a name that merely starts like the api does not count`() {
        // `glyph.batteryLevel` is a different key — a future API, not this one.
        assertFalse(ScriptCapability.DEVICE_STATE.isRequestedIn("local x = glyph.batteryLevel"))
    }

    @Test
    fun `a comment mentioning the api does count, and that is the right trade`() {
        // Scanning is static: a mention in a comment asks for the grant even
        // though the code never reads the value. The alternative — refusing to
        // prompt because the reach "looks" commented — is a bypass waiting for
        // a clever author, so over-asking wins.
        assertTrue(ScriptCapability.DEVICE_STATE.isRequestedIn("-- reads glyph.battery below"))
    }

    @Test
    fun `stored ids resolve back to their capability`() {
        assertEquals(ScriptCapability.DEVICE_STATE, ScriptCapability.ofId("device_state"))
    }
}
