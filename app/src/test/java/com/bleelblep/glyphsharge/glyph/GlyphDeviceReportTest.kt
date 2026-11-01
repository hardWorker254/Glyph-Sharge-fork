package com.bleelblep.glyphsharge.glyph

import com.bleelblep.glyphsharge.glyph.device.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app concludes about the phone it is on, and what the user is told.
 *
 * The failure this exists for is the quietest one in the app: the phone is
 * recognised, every toggle is in the right place, every service starts, and the
 * strip never lights — because the system Glyph service refused every id
 * offered. Nothing throws, nothing is logged where the user can reach it, and
 * from the screen it is indistinguishable from a feature that does not work.
 *
 * Two properties are worth pinning, and they pull in opposite directions:
 *
 *  * a phone the app does not claim publishes nothing, because a warning about
 *    hardware with no strip is noise on every launch;
 *  * a phone it *does* claim and the system refuses says so once, naming the
 *    model — the one fact a bug report needs, and the one the user cannot get
 *    from anywhere else.
 */
class GlyphDeviceReportTest {

    private val report = GlyphDeviceReport()

    @Test
    fun `nothing is known before registration is attempted`() {
        assertNull("a fresh report should have no verdict", report.verdict.value)
    }

    @Test
    fun `an accepted id means the strip can be driven`() {
        report.publish("A059P", DeviceType.PHONE3A, "A059", listOf("A059", "A059P"))

        val verdict = requireNotNull(report.verdict.value)
        assertTrue(verdict.registered)
        assertEquals("A059P", verdict.model)
        assertEquals(DeviceType.PHONE3A, verdict.type)
        // The offered list is kept even on success: which id was tried first and
        // which one the service kept is the whole of what a later report needs.
        assertEquals(listOf("A059", "A059P"), verdict.offeredIds)
    }

    @Test
    fun `a refusal is published, and is not a verdict that says nothing`() {
        report.publish("A059P", DeviceType.PHONE3A, acceptedId = null, offeredIds = listOf("A059", "A059P"))

        val verdict = requireNotNull(report.verdict.value)
        assertFalse(
            "a phone the service refused is still a phone the app claimed, and the " +
                "screen needs it to tell the user rather than to stay quiet",
            verdict.registered,
        )
        assertEquals("A059P", verdict.model)
        assertEquals(DeviceType.PHONE3A, verdict.type)
    }

    @Test
    fun `a later verdict replaces an earlier one`() {
        // The service drops and rebinds; the refusal has to be able to become a
        // success and the success a refusal again, or the first answer is the
        // only one the screen ever shows.
        report.publish("A059P", DeviceType.PHONE3A, null, listOf("A059", "A059P"))
        report.publish("A059P", DeviceType.PHONE3A, "A059P", listOf("A059", "A059P"))

        val verdict = requireNotNull(report.verdict.value)
        assertTrue(verdict.registered)
        assertEquals("A059P", verdict.acceptedId)
    }
}