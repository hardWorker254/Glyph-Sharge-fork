package com.bleelblep.glyphsharge.glyph.device

/**
 * A run of LED channels that is animated as a single step of a wave.
 *
 * @param segments channel ids lit during this step
 * @param step how long the step stays on, in ms
 * @param off pause after the step, in ms
 */
data class AnimGroup(
    val segments: List<Int>,
    val step: Long,
    val off: Long = 0L,
)

/** Tuning for the Matrix Rain animation. */
data class MatrixConfig(
    val drops: Int,
    val minLength: Int,
    val maxLength: Int,
    val stepDelayMs: Long,
    val offDelayMs: Long,
    val brightnessDecrement: Int,
)

/** Tuning for the Fireworks animation. */
data class FireworksConfig(
    val count: Int,
    val minExplosion: Int,
    val maxExplosion: Int,
    val launchDelayMs: Long,
    val explosionDelayMs: Long,
    val fadeDelayMs: Long
)

/** Tuning for the DNA Helix animation. */
data class DnaConfig(
    val rotations: Int,
    val stepDelayMs: Long,
    val offDelayMs: Long
)

/**
 * Immutable description of one phone's LED layout and animation timings.
 *
 * Channel groups are named after the physical segments printed on the back of
 * the device: `a`/`b` are the small strips next to the camera, `c` is the
 * long central strip, `d` the bottom strip and `e` the single dot.
 *
 * Built once per process by [DeviceProfileFactory].
 */
class DeviceProfile(
    val type: DeviceType,
    /** Every channel, in the order the device is physically wired. */
    val all: List<Int>,
    val a: List<Int>,
    val b: List<Int>,
    val c: List<Int>,
    val d: List<Int>,
    val e: List<Int>,
    val waveGroups: List<AnimGroup>,
    /** Channel order the spiral walks through. */
    val spiralOrder: List<Int>,
    val spiralStep: Long,
    val pulseSegments: List<Int>,
    val c1SeqStep: Long,
    val c1SeqHold: Long,
    val matrixConfig: MatrixConfig,
    val fireworksConfig: FireworksConfig,
    val dnaConfig: DnaConfig
) {
    /** Same rhythm as [waveGroups], but without the pause between groups. */
    val beedahGroups: List<AnimGroup> = waveGroups.map { AnimGroup(it.segments, it.step) }

    /** Every channel outside the main C strip; used as supporting light. */
    val nonC: List<Int> = all.filterNot { it in c.toSet() }
}
