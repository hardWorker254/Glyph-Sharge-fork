package com.bleelblep.glyphsharge.glyph.animations

import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Animations that walk a fixed sequence of channels: waves, sweeps, blinks.
 *
 * Each one is a `suspend` extension on [GlyphRenderer] and checks
 * [GlyphRenderer.isRunning] inside its loops, so [GlyphRenderer.stop] takes
 * effect at the next step rather than after the whole sequence.
 */

private const val PULSE_CYCLES = 3
private const val PULSE_ON_MS = 300L
private const val PULSE_OFF_MS = 300L

private const val HEARTBEAT_CYCLES = 3
private const val HEARTBEAT_BEAT_MS = 200L
private const val HEARTBEAT_GAP_MS = 100L
private const val HEARTBEAT_RECOVER_MS = 300L

private const val LOCK_STEP_MS = 100L
private const val LOCK_FINAL_ON_MS = 700L
private const val LOCK_DIM_RATIO = 0.5f

private const val SPIRAL_FULL_HOLD_MS = 250L
private const val SPIRAL_END_BLINK_MS = 200L
private const val SPIRAL_END_GAP_MS = 100L
private const val SPIRAL_STAGE_EXTRA_MS = 10L
private const val SPIRAL_FINALE_MS = 500L
private const val SPIRAL_FINALE_GAP_MS = 200L
private const val SPIRAL_FINALE_TAIL_MS = 300L

/**
 * Blinks [channels] [cycles] times. Shared by the plain pulse effect, the
 * beedah finale, and the fallback used for unknown animation ids.
 */
internal suspend fun GlyphRenderer.pulseCycles(channels: Collection<Int>, cycles: Int) {
    repeat(cycles) {
        if (!isRunning) return
        pulse(channels, onMs = PULSE_ON_MS, offMs = PULSE_OFF_MS)
    }
}

/** A wave travelling through the profile's groups, pausing after each group. */
internal suspend fun GlyphRenderer.runWaveAnimation(profile: DeviceProfile) {
    for (group in profile.waveGroups) {
        for (segment in group.segments) {
            if (!isRunning) return
            pulse(listOf(segment), onMs = group.step, offMs = group.off)
        }
    }
}

/** The wave without the group pause, ending in a collective blink. */
internal suspend fun GlyphRenderer.runBeedahAnimation(profile: DeviceProfile) {
    val lit = mutableListOf<Int>()

    for (group in profile.beedahGroups) {
        for (segment in group.segments) {
            if (!isRunning) return
            lit.add(segment)
            toggleChannels(lit, delayMs = group.step)
        }
    }

    pulseCycles(lit, PULSE_CYCLES)
}

/** Three "lub-dub" beats over the whole strip. */
internal suspend fun GlyphRenderer.runHeartbeatAnimation(profile: DeviceProfile) {
    repeat(HEARTBEAT_CYCLES) {
        if (!isRunning) return
        pulse(profile.all, onMs = HEARTBEAT_BEAT_MS, offMs = HEARTBEAT_GAP_MS)
        if (!isRunning) return
        pulse(profile.all, onMs = HEARTBEAT_BEAT_MS, offMs = HEARTBEAT_RECOVER_MS)
    }
}

/** A sequential pass along the C strip, forward and then back. */
internal suspend fun GlyphRenderer.runC1SequentialAnimation(profile: DeviceProfile) {
    if (profile.c.isEmpty()) return

    runC1Phase(profile, forward = true)
    delay(profile.c1SeqHold.milliseconds)
    runC1Phase(profile, forward = false)
}

private suspend fun GlyphRenderer.runC1Phase(profile: DeviceProfile, forward: Boolean) {
    val main = profile.c
    val indices = if (forward) main.indices else main.indices.reversed()

    for (i in indices) {
        if (!isRunning) return
        val builder = builder() ?: break

        val range = if (forward) 0..i else i downTo 0
        for (j in range) {
            builder.buildChannel(main[j], GLYPH_MAX_BRIGHTNESS)
        }

        // The rest of the strip brightens as the pass advances, so the whole
        // phone reacts even though only the C strip is animated.
        val supportBrightness =
            (GLYPH_MAX_BRIGHTNESS * ((i + 1) / main.size.toFloat())).toInt()
        profile.nonC.forEach { builder.buildChannel(it, supportBrightness) }

        toggle(builder, profile.c1SeqStep)
    }
}

/** A "padlock": a wedge that keeps widening along the C strip. */
internal suspend fun GlyphRenderer.runLockPulseAnimation(profile: DeviceProfile) {
    if (profile.c.isEmpty()) return

    val dimmedBrightness = (GLYPH_MAX_BRIGHTNESS * LOCK_DIM_RATIO).toInt()

    for (idx in profile.c.indices) {
        if (!isRunning) return
        val builder = builder() ?: break

        profile.nonC.forEach { builder.buildChannel(it, dimmedBrightness) }

        for (j in 0..idx) {
            val brightness = if ((idx == 0 || j == idx)) {
                GLYPH_MAX_BRIGHTNESS
            } else {
                (GLYPH_MAX_BRIGHTNESS * (0.3f + 0.7f * (j.toFloat() / idx))).toInt()
            }
            builder.buildChannel(profile.c[j], brightness)
        }

        toggle(builder, LOCK_STEP_MS)
    }

    pulse(profile.all, onMs = LOCK_FINAL_ON_MS)
}

/**
 * A brightness ramp that grows outwards, one step per segment.
 *
 * @param base channels that stay lit underneath the sweep, with their brightness
 * @param brightness computes the brightness of the `j`-th channel on step `i`
 */
private suspend fun GlyphRenderer.progressiveSweep(
    segments: List<Int>,
    stepMs: Long,
    base: List<Pair<Int, Int>> = emptyList(),
    brightness: (j: Int, i: Int) -> Int,
) {
    for (i in segments.indices) {
        if (!isRunning) return
        val builder = builder() ?: break

        base.forEach { (channel, baseBrightness) -> builder.buildChannel(channel, baseBrightness) }
        for (j in 0..i) {
            builder.buildChannel(segments[j], brightness(j, i))
        }

        toggle(builder, stepMs)
    }
}

private fun dimmed(channels: List<Int>, ratio: Float): List<Pair<Int, Int>> =
    channels.map { it to (GLYPH_MAX_BRIGHTNESS * ratio).toInt() }

/** Phone (3a) has enough channels for a staged spiral instead of a simple sweep. */
private suspend fun GlyphRenderer.runPhone3aSpiral(profile: DeviceProfile) {
    val step = profile.spiralStep

    progressiveSweep(profile.c, step) { j, i ->
        if (j == i) {
            GLYPH_MAX_BRIGHTNESS
        } else {
            (GLYPH_MAX_BRIGHTNESS * (0.3f + (j.toFloat() / i.coerceAtLeast(1)) * 0.4f)).toInt()
        }
    }

    progressiveSweep(profile.a, step + SPIRAL_STAGE_EXTRA_MS, dimmed(profile.c, 0.3f)) { j, i ->
        if (j == i) {
            GLYPH_MAX_BRIGHTNESS
        } else {
            (GLYPH_MAX_BRIGHTNESS * (0.5f + (j.toFloat() / i.coerceAtLeast(1)) * 0.5f)).toInt()
        }
    }

    progressiveSweep(
        segments = profile.b,
        stepMs = step + SPIRAL_STAGE_EXTRA_MS * 2,
        base = dimmed(profile.c, 0.4f) + dimmed(profile.a, 0.7f)
    ) { _, _ -> GLYPH_MAX_BRIGHTNESS }

    toggle(frame(profile.all), SPIRAL_FINALE_MS)
    turnOff()
    delay(SPIRAL_FINALE_GAP_MS.milliseconds)
    toggle(frame(profile.all), SPIRAL_FINALE_TAIL_MS)
}

/** A ramp that grows in, holds, then contracts — the classic spiral sweep. */
private suspend fun GlyphRenderer.runSpiralOrder(profile: DeviceProfile) {
    val segments = profile.spiralOrder.ifEmpty { profile.all }
    if (segments.isEmpty()) return

    val size = segments.size

    for (i in segments.indices) {
        if (!isRunning) return
        val builder = builder() ?: break

        for (j in 0..i) {
            val brightness = (GLYPH_MAX_BRIGHTNESS * (0.6f + (j.toFloat() / size) * 0.4f)).toInt()
            builder.buildChannel(segments[j], brightness)
        }

        toggle(builder, profile.spiralStep)
    }

    toggleChannels(segments, delayMs = SPIRAL_FULL_HOLD_MS)

    for (i in segments.indices.reversed()) {
        if (!isRunning) return
        val builder = builder() ?: break

        val denom = (size - i).coerceAtLeast(1)
        for (j in i until size) {
            val brightness =
                (GLYPH_MAX_BRIGHTNESS * (0.6f + ((size - j).toFloat() / denom) * 0.4f)).toInt()
            builder.buildChannel(segments[j], brightness)
        }

        toggle(builder, profile.spiralStep)
    }

    val head = segments.first()
    pulse(listOf(head), onMs = SPIRAL_END_BLINK_MS, offMs = SPIRAL_END_GAP_MS)
    pulse(listOf(head), onMs = SPIRAL_END_BLINK_MS)
}

/** Phone (3a) gets the staged spiral; every other model gets the simple sweep. */
internal suspend fun GlyphRenderer.runSpiralAnimation(profile: DeviceProfile) {
    if (profile.type == DeviceType.PHONE3A) {
        runPhone3aSpiral(profile)
    } else {
        runSpiralOrder(profile)
    }
}
