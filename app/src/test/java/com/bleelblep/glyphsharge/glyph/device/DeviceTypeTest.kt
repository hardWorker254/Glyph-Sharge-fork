package com.bleelblep.glyphsharge.glyph.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which `Build.MODEL` string maps to which phone, and which id gets offered to
 * the system service.
 *
 * Two facts about a new phone are easy to get wrong and impossible to notice
 * from the code: what its model string actually is, and whether the Glyph
 * service wants to be told that string or its layout sibling's. The first is
 * checkable here because identification is a pure function of a string; the
 * second is not guessable at all, which is why [DeviceType.registrationIds]
 * offers both and lets the device refuse one.
 *
 * Phone (3a) Pro is the case that produced both problems. It reports `A059P`
 * while the SDK snapshot packaged here — 2025-03-19, months before the phone —
 * knows only `A059`. Detection still lands on the (3a) because `A059P` contains
 * `A059` and the strip is identical; what the snapshot cannot answer is whether
 * the service accepts `A059` on that phone, so both are offered.
 */
class DeviceTypeTest {

    // The model strings of the phones this app supports, as they appear in
    // `Build.MODEL`. Written out rather than read back from the enum: a table
    // built from the thing it is meant to check proves nothing.
    private val known = mapOf(
        "A063" to DeviceType.PHONE1,
        "A065" to DeviceType.PHONE2,
        // The Phone (2) Global: more RAM, a different SoC, the same strip.
        "AIN065" to DeviceType.PHONE2,
        "A142" to DeviceType.PHONE2A,
        "A142P" to DeviceType.PHONE2A,
        "A059" to DeviceType.PHONE3A,
        "A059P" to DeviceType.PHONE3A,
    )

    @Test
    fun `every supported phone is recognised by its model string`() {
        known.forEach { (model, expected) ->
            assertEquals(
                "$model is not mapped to $expected — on this phone the app would " +
                    "treat the strip as absent and every feature would quietly do " +
                    "nothing",
                expected,
                DeviceType.detect(model),
            )
        }
    }

    @Test
    fun `a phone that is not a Nothing one is not claimed`() {
        // The path that keeps the app usable to look at on any handset: no
        // profile, inactive cards, no crash.
        listOf(null, "", "Pixel 9", "SM-S928B", "a059").forEach { model ->
            assertNull(
                "\"$model\" was claimed as a Nothing phone",
                DeviceType.detect(model),
            )
        }
    }

    @Test
    fun `the 3a Pro draws the same strip as the 3a`() {
        // One entry for both is a claim about the hardware, and it is the
        // reason the Pro needs no second set of channel tables. If the strip
        // ever does differ, this fails and the two belong apart.
        assertEquals(
            DeviceType.PHONE3A,
            DeviceType.detect("A059P"),
        )

        val profile = requireNotNull(DeviceProfileFactory.forDeviceOrNull(DeviceType.PHONE3A))
        assertEquals((0..19).toList(), profile.c)
        assertEquals((20..30).toList(), profile.a)
        assertEquals((31..35).toList(), profile.b)
        assertEquals(36, profile.all.size)
    }

    @Test
    fun `the 3a Pro is offered both ids, the plain 3a the one that works`() {
        // Order matters: the id the service already accepts is tried first, so
        // a phone that takes it never sees a second registration attempt. The
        // Pro's own string is the fallback, tried only if the first is refused.
        assertEquals(
            listOf("A059", "A059P"),
            DeviceType.PHONE3A.registrationIds("A059P"),
        )
        assertEquals(
            listOf("A059", "A059P"),
            DeviceType.PHONE3A.registrationIds("A059"),
        )
    }

    @Test
    fun `every phone offers at least one id, and never repeats itself`() {
        DeviceType.entries.forEach { type ->
            val ids = type.registrationIds(known.entries.first { it.value == type }.key)

            assertTrue("$type offers no id to register with", ids.isNotEmpty())
            assertEquals(
                "$type offers the same id twice, so a refusal would be tried again " +
                    "and refuse again",
                ids.size,
                ids.toSet().size,
            )
        }
    }

    @Test
    fun `a variant the SDK names is registered under its own id`() {
        // Two model strings, two answers, both of them spellings the service
        // already knows — so each is offered first on its own phone. `A142P`
        // before `A142` matters in particular: `A142P` *contains* `A142`, so an
        // ordering that went by declaration order would offer the Plus the
        // base id and leave it refused.
        assertEquals("A142P", DeviceType.PHONE2A.registrationIds("A142P").first())
        assertEquals("A142", DeviceType.PHONE2A.registrationIds("A142").first())

        assertEquals("AIN065", DeviceType.PHONE2.registrationIds("AIN065").first())
        assertEquals("A065", DeviceType.PHONE2.registrationIds("A065").first())
    }

    @Test
    fun `the Phone 2 Global never borrows the base id of a different family`() {
        // `AIN065` is the trap in this table: it ends in the Phone (2)'s `065`
        // but shares no substring with `A065`, so a phone reporting it matches
        // nothing but PHONE2. Getting that wrong would draw the Phone (2)'s
        // 33 channels on a handset that has them — or, the other way round,
        // claim a (2a) as a (2).
        val ids = DeviceType.PHONE2.registrationIds("AIN065")
        assertTrue(
            "AIN065 was offered a foreign layout's id: $ids",
            ids.all { it == "A065" || it == "AIN065" },
        )
    }
}