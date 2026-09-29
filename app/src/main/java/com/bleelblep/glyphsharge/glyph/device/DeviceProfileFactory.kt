package com.bleelblep.glyphsharge.glyph.device

/**
 * Builds the [DeviceProfile] describing the connected phone.
 *
 * The per-model data is split in two tables so that adding a phone touches one
 * place instead of a hundred lines of profile literal:
 *
 *  * [layouts] — what is actually different between models: the physical
 *    channel groups, their order, the wave rhythm and the C1 timings.
 *  * [timings] — what follows a model progression: newer phones have more and
 *    longer segments, so step delays shrink while particle budgets grow.
 */
object DeviceProfileFactory {

    /** Channels and rhythm of one phone model. */
    private class Layout(
        val type: DeviceType,
        val a: List<Int>,
        val b: List<Int>,
        val c: List<Int>,
        val d: List<Int>,
        val e: List<Int>,
        /** Every channel, in physical wiring order. */
        val all: List<Int>,
        val waveGroups: List<AnimGroup>,
        val spiralOrder: List<Int>,
        val pulseSegments: List<Int>,
        val c1SeqStep: Long,
        val c1SeqHold: Long,
    )

    /** Animation budgets that scale with the model. */
    private class Timings(
        val spiralStep: Long,
        val matrix: MatrixConfig,
        val fireworks: FireworksConfig,
        val dna: DnaConfig,
    )

    private fun range(from: Int, to: Int): List<Int> = (from..to).toList()

    private val layouts: Map<DeviceType, Layout> = mapOf(
        DeviceType.PHONE1 to phone1(),
        DeviceType.PHONE2 to phone2(),
        DeviceType.PHONE2A to phone2a(),
        DeviceType.PHONE3A to phone3a()
    )

    private val timings: Map<DeviceType, Timings> = mapOf(
        DeviceType.PHONE1 to Timings(
            spiralStep = 100L,
            matrix = MatrixConfig(20, 3, 8, stepDelayMs = 100L, offDelayMs = 50L, brightnessDecrement = 200),
            fireworks = FireworksConfig(5, 5, 10, launchDelayMs = 300L, explosionDelayMs = 500L, fadeDelayMs = 200L),
            dna = DnaConfig(rotations = 3, stepDelayMs = 150L, offDelayMs = 50L)
        ),
        DeviceType.PHONE2 to Timings(
            spiralStep = 80L,
            matrix = MatrixConfig(25, 4, 10, stepDelayMs = 80L, offDelayMs = 40L, brightnessDecrement = 150),
            fireworks = FireworksConfig(6, 8, 15, launchDelayMs = 250L, explosionDelayMs = 400L, fadeDelayMs = 150L),
            dna = DnaConfig(rotations = 3, stepDelayMs = 120L, offDelayMs = 40L)
        ),
        DeviceType.PHONE2A to Timings(
            spiralStep = 70L,
            matrix = MatrixConfig(30, 5, 12, stepDelayMs = 70L, offDelayMs = 35L, brightnessDecrement = 120),
            fireworks = FireworksConfig(7, 10, 20, launchDelayMs = 200L, explosionDelayMs = 350L, fadeDelayMs = 100L),
            dna = DnaConfig(rotations = 3, stepDelayMs = 100L, offDelayMs = 30L)
        ),
        DeviceType.PHONE3A to Timings(
            spiralStep = 60L,
            matrix = MatrixConfig(35, 6, 15, stepDelayMs = 60L, offDelayMs = 30L, brightnessDecrement = 100),
            fireworks = FireworksConfig(8, 12, 25, launchDelayMs = 180L, explosionDelayMs = 300L, fadeDelayMs = 80L),
            dna = DnaConfig(rotations = 3, stepDelayMs = 80L, offDelayMs = 25L)
        )
    )

    /** Phone (1): A, B, C1..C4, E, D1..D8, wired in that order. */
    private fun phone1(): Layout {
        val a = listOf(0)
        val b = listOf(1)
        val c = range(2, 5)
        val e = listOf(6)
        val d = range(7, 14)
        val all = a + b + c + e + d
        return Layout(
            type = DeviceType.PHONE1,
            a = a, b = b, c = c, d = d, e = e,
            all = all,
            waveGroups = listOf(AnimGroup(all, step = 150L, off = 50L)),
            spiralOrder = e + a + b + c + d,
            pulseSegments = a + b + e,
            c1SeqStep = 250L,
            c1SeqHold = 1000L
        )
    }

    /** Phone (2): adds a second C run and a longer bottom strip. */
    private fun phone2(): Layout {
        val a = listOf(0, 1)
        val b = listOf(2)
        val c = range(3, 18)
        // The extra C run of Phone (2); it is part of `all` but not of the bar.
        val cOther = range(19, 23)
        val e = listOf(24)
        val d = range(25, 32)
        val all = a + b + c + cOther + e + d
        return Layout(
            type = DeviceType.PHONE2,
            a = a, b = b, c = c, d = d, e = e,
            all = all,
            waveGroups = listOf(AnimGroup(all, step = 100L, off = 30L)),
            spiralOrder = e + a + b + c + cOther + d,
            pulseSegments = a + b + e,
            c1SeqStep = 250L,
            c1SeqHold = 1000L
        )
    }

    /** Phone (2a): C, B, A are simply channels 0..25. */
    private fun phone2a(): Layout {
        val a = listOf(25)
        val b = listOf(24)
        val c = range(0, 23)
        val all = c + b + a
        return Layout(
            type = DeviceType.PHONE2A,
            a = a, b = b, c = c, d = emptyList(), e = emptyList(),
            all = all,
            waveGroups = listOf(
                AnimGroup(c, step = 80L, off = 30L),
                AnimGroup(a + b, step = 160L, off = 50L)
            ),
            spiralOrder = a + b + c,
            pulseSegments = a + b,
            c1SeqStep = 180L,
            c1SeqHold = 1500L
        )
    }

    /** Phone (3a): C1..C20, A1..A11, B1..B5. */
    private fun phone3a(): Layout {
        val c = range(0, 19)
        val a = range(20, 30)
        val b = range(31, 35)
        val all = c + a + b
        return Layout(
            type = DeviceType.PHONE3A,
            a = a, b = b, c = c, d = emptyList(), e = emptyList(),
            all = all,
            waveGroups = listOf(
                AnimGroup(c, step = 80L, off = 25L),
                AnimGroup(a, step = 100L, off = 30L),
                AnimGroup(b, step = 120L, off = 40L)
            ),
            spiralOrder = all,
            pulseSegments = listOf(25, 33, 9),
            c1SeqStep = 200L,
            c1SeqHold = 2000L
        )
    }

    private fun Layout.toProfile(timing: Timings): DeviceProfile = DeviceProfile(
        type = type,
        all = all,
        a = a,
        b = b,
        c = c,
        d = d,
        e = e,
        waveGroups = waveGroups,
        spiralOrder = spiralOrder,
        spiralStep = timing.spiralStep,
        pulseSegments = pulseSegments,
        c1SeqStep = c1SeqStep,
        c1SeqHold = c1SeqHold,
        matrixConfig = timing.matrix,
        fireworksConfig = timing.fireworks,
        dnaConfig = timing.dna
    )

    /** The profile of the connected phone, or `null` on unsupported hardware. */
    fun forConnectedDevice(): DeviceProfile? = forDeviceOrNull(DeviceType.detect())

    /**
     * The layout the studio's on-screen preview draws.
     *
     * A script has to be checkable and previewable on a phone that is not a
     * Nothing one, or on an emulator, so the richest layout stands in when no
     * glyph hardware is present. Nothing that drives real LEDs ever uses this.
     */
    fun forPreview(): DeviceProfile =
        forDeviceOrNull(DeviceType.PHONE3A) ?: error("Phone (3a) layout is missing")

    /** The profile of [type], or `null` if the model is not supported. */
    fun forDeviceOrNull(type: DeviceType?): DeviceProfile? {
        val layout = layouts[type] ?: return null
        return layout.toProfile(timings.getValue(layout.type))
    }

    /**
     * Every LED channel of the connected phone, or an empty list on
     * unsupported hardware. This is the list that lights the whole strip at once.
     */
    fun allChannelsForConnectedDevice(): List<Int> =
        forConnectedDevice()?.all.orEmpty()
}
