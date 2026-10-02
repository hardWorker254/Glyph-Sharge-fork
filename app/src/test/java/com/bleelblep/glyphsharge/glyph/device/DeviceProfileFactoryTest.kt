package com.bleelblep.glyphsharge.glyph.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the per-model layout tables.
 *
 * These are channel numbers, and nothing else in the project checks them. A
 * range that is off by one is not a crash: the strip lights a segment that is
 * not there and leaves a real one dark, which looks like a hardware fault and
 * is indistinguishable, in a bug report, from a broken SDK. So the tables are
 * pinned against the relationships that must hold for every model, plus the
 * handful of facts that are specific to a phone.
 *
 * The factory methods under test take a [DeviceType] and never call the SDK, so
 * the whole thing runs on a laptop.
 */
class DeviceProfileFactoryTest {

    private fun profile(type: DeviceType): DeviceProfile =
        requireNotNull(DeviceProfileFactory.forDeviceOrNull(type)) { "$type has no layout" }

    private val supported = listOf(
        DeviceType.PHONE1,
        DeviceType.PHONE2,
        DeviceType.PHONE2A,
        DeviceType.PHONE3A,
    )

    // region Every model

    @Test
    fun `every supported model has a layout`() {
        supported.forEach { type ->
            assertNotNull("$type has no layout", DeviceProfileFactory.forDeviceOrNull(type))
        }
    }

    @Test
    fun `unsupported hardware has no layout`() {
        // The path the app takes on a phone that is not a Nothing one: no
        // profile, and the feature cards stay inactive rather than throwing.
        assertNull(DeviceProfileFactory.forDeviceOrNull(null))
    }

    @Test
    fun `every group names channels the phone actually has`() {
        supported.forEach { type ->
            val p = profile(type)
            val everywhere = p.a + p.b + p.c + p.d + p.e

            assertTrue(
                "$type: a group names a channel that is not wired",
                everywhere.all { it in p.all },
            )
            // No channel is in two groups. `all` may be larger than the sum of
            // the groups — Phone (2) wires a second C run that is deliberately
            // in neither `c` nor any other group — but never smaller.
            assertEquals(
                "$type: a channel appears in two groups",
                everywhere.size,
                everywhere.toSet().size,
            )
            assertTrue(
                "$type: a group is not wired",
                everywhere.size <= p.all.size,
            )
        }
    }

    @Test
    fun `a channel is never wired twice`() {
        supported.forEach { type ->
            val p = profile(type)

            assertEquals("$type: a duplicate channel", p.all.size, p.all.toSet().size)
        }
    }

    @Test
    fun `the spiral pass covers the whole strip exactly once`() {
        supported.forEach { type ->
            val p = profile(type)

            assertEquals("$type: the spiral skips a channel", p.all.toSet(), p.spiralOrder.toSet())
            assertEquals("$type: the spiral repeats a channel", p.all.size, p.spiralOrder.size)
        }
    }

    @Test
    fun `the pulse segments are real channels`() {
        supported.forEach { type ->
            val p = profile(type)

            assertTrue("$type: no pulse segments", p.pulseSegments.isNotEmpty())
            assertTrue(
                "$type: a pulse segment is not a channel",
                p.pulseSegments.all { it in p.all },
            )
        }
    }

    @Test
    fun `every wave group lights real channels and lasts a readable time`() {
        supported.forEach { type ->
            val p = profile(type)

            assertTrue("$type: no wave groups", p.waveGroups.isNotEmpty())
            p.waveGroups.forEach { group ->
                assertTrue("$type: an empty wave group", group.segments.isNotEmpty())
                assertTrue(
                    "$type: a wave group names a channel that is not wired",
                    group.segments.all { it in p.all },
                )
                assertTrue("$type: a zero-length wave step", group.step > 0)
                assertTrue("$type: a negative wave pause", group.off >= 0)
            }
        }
    }

    @Test
    fun `the supporting light is everything except the C strip`() {
        supported.forEach { type ->
            val p = profile(type)

            assertEquals("$type: nonC", p.all.toSet() - p.c.toSet(), p.nonC.toSet())
            assertTrue(
                "$type: the supporting light is empty",
                p.nonC.isNotEmpty(),
            )
        }
    }

    @Test
    fun `the beedah rhythm is the wave rhythm without the pauses`() {
        supported.forEach { type ->
            val p = profile(type)

            assertEquals("$type: group count", p.waveGroups.size, p.beedahGroups.size)
            p.beedahGroups.forEachIndexed { i, beedah ->
                val wave = p.waveGroups[i]
                assertEquals("$type: segments", wave.segments, beedah.segments)
                assertEquals("$type: step", wave.step, beedah.step)
                assertEquals("$type: the pause was kept", 0L, beedah.off)
            }
        }
    }

    // endregion

    // region Model progression

    @Test
    fun `newer phones get more segments and quicker steps`() {
        // The animation budgets follow the hardware: a strip with more segments
        // needs shorter steps to read at the same speed, and can afford a
        // larger particle budget. Phone (1) to Phone (3a) is the progression.
        val first = profile(DeviceType.PHONE1)
        val last = profile(DeviceType.PHONE3A)

        assertTrue(
            "Phone (3a) has ${last.c.size} C segments against ${first.c.size}",
            last.all.size > first.all.size,
        )
        assertTrue(
            "the spiral step did not shrink: ${first.spiralStep} then ${last.spiralStep}",
            last.spiralStep < first.spiralStep,
        )
        assertTrue(
            "the matrix budget did not grow",
            last.matrixConfig.drops > first.matrixConfig.drops,
        )
        assertTrue(
            "the fireworks count did not grow",
            last.fireworksConfig.count > first.fireworksConfig.count,
        )
    }

    @Test
    fun `no animation budget is degenerate on any model`() {
        supported.forEach { type ->
            val p = profile(type)

            with(p.matrixConfig) {
                assertTrue("$type: no drops", drops > 0)
                assertTrue("$type: minLength above maxLength", minLength <= maxLength)
                assertTrue("$type: a zero-length matrix step", stepDelayMs > 0)
            }
            with(p.fireworksConfig) {
                assertTrue("$type: no fireworks", count > 0)
                assertTrue("$type: minExplosion above maxExplosion", minExplosion <= maxExplosion)
                assertTrue("$type: a zero-length launch delay", launchDelayMs > 0)
            }
            with(p.dnaConfig) {
                assertTrue("$type: no rotations", rotations > 0)
                assertTrue("$type: a zero-length dna step", stepDelayMs > 0)
            }
            assertTrue("$type: a zero-length spiral step", p.spiralStep > 0)
            assertTrue("$type: a zero-length C1 step", p.c1SeqStep > 0)
            assertTrue("$type: a zero-length C1 hold", p.c1SeqHold > 0)
        }
    }

    // endregion

    // region Per model

    @Test
    fun `Phone (1) is A, B, C1 to C4, E, then the bottom strip`() {
        // The shortest layout in the project, and the one where a mistyped
        // range is easiest to make: every group here is adjacent.
        val p = profile(DeviceType.PHONE1)

        assertEquals(listOf(0), p.a)
        assertEquals(listOf(1), p.b)
        assertEquals(listOf(2, 3, 4, 5), p.c)
        assertEquals(listOf(6), p.e)
        assertEquals((7..14).toList(), p.d)
        assertEquals((0..14).toList(), p.all)
        assertEquals(15, p.all.size)
        assertEquals(listOf(0, 1, 6), p.pulseSegments)
        assertEquals(250L, p.c1SeqStep)
        assertEquals(1000L, p.c1SeqHold)
        assertEquals(100L, p.spiralStep)
    }

    @Test
    fun `Phone (2) keeps its second C run out of the bar`() {
        // Channels 19..23 are a C run as far as the wiring is concerned, but
        // the battery bar is the main strip only. Reading it as part of `c`
        // would make the bar two thirds longer than the battery is.
        val p = profile(DeviceType.PHONE2)

        assertEquals((3..18).toList(), p.c)
        assertEquals(33, p.all.size)
        assertTrue("the second C run is wired", (19..23).all { it in p.all })
        assertTrue("the second C run is not the bar", (19..23).none { it in p.c })
        assertTrue((19..23).all { it in p.nonC })
    }

    @Test
    fun `Phone (2a) has no D or E strip`() {
        val p = profile(DeviceType.PHONE2A)

        assertEquals((0..25).toList(), p.all)
        assertEquals((0..23).toList(), p.c)
        assertEquals(listOf(24), p.b)
        assertEquals(listOf(25), p.a)
        assertTrue(p.d.isEmpty())
        assertTrue(p.e.isEmpty())
    }

    @Test
    fun `Phone (3a) runs C first, then A, then B`() {
        val p = profile(DeviceType.PHONE3A)

        assertEquals((0..19).toList(), p.c)
        assertEquals((20..30).toList(), p.a)
        assertEquals((31..35).toList(), p.b)
        assertEquals(36, p.all.size)
        assertEquals(p.all, p.spiralOrder)
        // The supporting light of a phone with no D or E is exactly A and B.
        assertEquals((20..35).toList(), p.nonC)
    }

    // endregion

    // region Preview

    @Test
    fun `the studio previews on the richest layout`() {
        // A script has to be checkable on a phone that is not a Nothing one,
        // so the widest strip stands in. Nothing that drives real LEDs uses
        // this, which is why it is allowed to differ from the hardware.
        // DeviceProfile is a plain class, so the fields are compared rather
        // than the instances.
        val preview = DeviceProfileFactory.forPreview()

        assertEquals(DeviceType.PHONE3A, preview.type)
        assertEquals(profile(DeviceType.PHONE3A).all, preview.all)
        assertEquals(profile(DeviceType.PHONE3A).c, preview.c)
        assertEquals(profile(DeviceType.PHONE3A).nonC, preview.nonC)
    }

    // endregion
}
