package com.bleelblep.glyphsharge.glyph.battery

import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import com.nothing.ketchum.GlyphFrame
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * The charging and power-peek animation: a battery bar that fills up along the
 * C strip over the configured duration, with per-model accents layered on top.
 *
 * Used by `playPowerPeekAnimation` and `playChargingAnimationAnimation`, which
 * differ only in how long they run.
 */

private const val STEP_DELAY_MS = 50L
private const val LOW_BATTERY_THRESHOLD_PERCENT = 20
private const val FULL_BATTERY_PERCENT = 100

/** How often an unfilled segment twinkles, in animation steps. */
private const val TWINKLE_PERIOD = 20

/**
 * Fills the C strip from empty to [batteryPercentage] over [durationMs],
 * reporting progress to [onProgressUpdate] as it goes.
 *
 * The bar fills once and then holds while the accents animate, so the gesture
 * communicates both "how much is left" and "what the phone is doing".
 */
internal suspend fun GlyphRenderer.animateBattery(
    profile: DeviceProfile,
    batteryPercentage: Int,
    isCharging: Boolean,
    durationMs: Long,
    onProgressUpdate: (Float) -> Unit,
) {
    val bar = profile.c
    if (durationMs <= 0 || bar.isEmpty()) return

    val total = bar.size
    val target = (batteryPercentage / 100f * total).toInt().coerceIn(0, total)
    val base = baseBrightness(batteryPercentage, isCharging)

    var current = 0
    var step = 0
    val startTime = System.currentTimeMillis()

    while (isRunning) {
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= durationMs) {
            onProgressUpdate(1f)
            break
        }

        onProgressUpdate((elapsed / durationMs.toFloat()).coerceIn(0f, 1f))

        try {
            val builder = builder() ?: break
            if (current < target) current++

            for (i in 0 until current) {
                val brightness =
                    if (isCharging) chargingWave(profile, i, base, step) else base
                builder.buildChannel(bar[i], brightness.coerceIn(0, GLYPH_MAX_BRIGHTNESS))
            }

            // The accents only make sense once the bar has reached its level.
            if (current == target) {
                if (isCharging) {
                    addBatteryEndBlink(builder, profile, bar, batteryPercentage, target, base, step)
                    addChargeDot(builder, profile, base, step)
                } else {
                    addIdleAccents(builder, profile, batteryPercentage, current, bar, base, step)
                }
            }

            toggle(builder)
            delay(STEP_DELAY_MS.milliseconds)
            step++
        } catch (e: Exception) {
            onError(e, "Battery animation error", STEP_DELAY_MS)
            step++
        }
    }
}

/** How bright the filled part of the bar sits: dimmer only when nearly empty. */
private fun baseBrightness(batteryPercentage: Int, isCharging: Boolean): Int = when {
    isCharging -> GLYPH_MAX_BRIGHTNESS
    batteryPercentage < LOW_BATTERY_THRESHOLD_PERCENT -> GLYPH_MAX_BRIGHTNESS / 3
    else -> (GLYPH_MAX_BRIGHTNESS * 0.7f).toInt()
}

/** A travelling wave so a full bar still reads as "charging", not "done". */
private fun chargingWave(profile: DeviceProfile, index: Int, base: Int, step: Int): Int {
    val spacing = if (profile.type == DeviceType.PHONE1) 0.5f else 0.3f
    val offset = index * spacing
    return (base * (0.6f + 0.4f * sin(step * 0.2f - offset))).toInt()
}

// region Charging accents

/** Fades a short run of segments past the fill point, hinting at more charge. */
private fun addBatteryEndBlink(
    builder: GlyphFrame.Builder,
    profile: DeviceProfile,
    bar: List<Int>,
    batteryPercentage: Int,
    target: Int,
    base: Int,
    step: Int,
) {
    // A full battery has nothing left to hint at.
    if (batteryPercentage >= FULL_BATTERY_PERCENT) return

    val extra = if (profile.type == DeviceType.PHONE1) 2 else 3
    val end = minOf(target + extra, bar.size)

    for (j in target until end) {
        val offset = (j - target) * if (profile.type == DeviceType.PHONE1) 0.8f else 0.5f
        val brightness = (base * (0.1f + 0.9f * abs(sin(step * 0.15f - offset)))).toInt()
        builder.buildChannel(bar[j], brightness.coerceIn(0, GLYPH_MAX_BRIGHTNESS))
    }
}

/** A breathing dot on the accent segments; Phone (2) and (2a) only. */
private fun addChargeDot(
    builder: GlyphFrame.Builder,
    profile: DeviceProfile,
    base: Int,
    step: Int
) {
    val floor = if (profile.type == DeviceType.PHONE2A) 0.6f else 0.5f
    val phase = if (profile.type == DeviceType.PHONE2A) 0.25f else 0.2f
    val channels = when (profile.type) {
        DeviceType.PHONE2 -> profile.b
        DeviceType.PHONE2A -> profile.b.take(1)
        else -> emptyList()
    }

    val brightness = (base * (floor + (1f - floor) * sin(step * phase)))
        .toInt()
        .coerceIn(0, GLYPH_MAX_BRIGHTNESS)

    channels.forEach { builder.buildChannel(it, brightness) }
}

// endregion

// region Idle accents

/**
 * What the bar does once it is full while *not* charging: a soft glow when the
 * battery is healthy, a pulsing alert when it is low.
 */
private fun GlyphRenderer.addIdleAccents(
    builder: GlyphFrame.Builder,
    profile: DeviceProfile,
    batteryPercentage: Int,
    current: Int,
    bar: List<Int>,
    base: Int,
    step: Int
) {
    val isLow = batteryPercentage < LOW_BATTERY_THRESHOLD_PERCENT

    when (profile.type) {
        DeviceType.PHONE1 -> {
            if (!isLow) {
                val filled = batteryPercentage / 100f * bar.size
                addGlow(builder, bar, filled, base, step) { index, s ->
                    0.75f + 0.25f * sin((s + index) * 0.25f)
                }
                addTwinkle(builder, bar, filled, step)
            } else if (current > 0) {
                addAlert(builder, bar[current - 1], step)
            }
        }

        DeviceType.PHONE2 -> {
            if (!isLow) {
                addAccentGlow(builder, profile, step)
                addGlow(builder, bar, current.toFloat(), base, step) { index, s ->
                    0.05f + 1.15f * (0.5f + 0.5f * sin(s * 0.5f - index * 0.6f))
                }
            } else {
                profile.a.forEach { addAlert(builder, it, step) }
            }
        }

        DeviceType.PHONE2A ->
            if (isLow) profile.a.firstOrNull()?.let { addAlert(builder, it, step) }

        DeviceType.PHONE3A ->
            if (isLow && current > 0) addAlert(builder, bar[current - 1], step)
    }
}

/**
 * Lights every segment up to [filledLevel] at [baseBrightness], with a soft
 * falloff on the segment right above the level, then modulates the result with
 * [wave]. The two glow flavours differ only in that wave.
 */
private fun addGlow(
    builder: GlyphFrame.Builder,
    segments: List<Int>,
    filledLevel: Float,
    baseBrightness: Int,
    step: Int,
    wave: (index: Int, step: Int) -> Float,
) {
    segments.forEachIndexed { index, channel ->
        val base = fillBrightness(index, filledLevel, baseBrightness)
        if (base == 0) return@forEachIndexed

        val brightness = (base * wave(index, step)).toInt().coerceIn(0, GLYPH_MAX_BRIGHTNESS)
        builder.buildChannel(channel, brightness)
    }
}

/** Full brightness below the fill level, fading out over the next segment. */
private fun fillBrightness(index: Int, filledLevel: Float, baseBrightness: Int): Int = when {
    index + 1 <= filledLevel -> baseBrightness
    index < filledLevel -> (baseBrightness * (filledLevel - index)).toInt()
    else -> 0
}

/** Every [TWINKLE_PERIOD] steps, wakes one random unfilled segment. */
private fun GlyphRenderer.addTwinkle(
    builder: GlyphFrame.Builder,
    segments: List<Int>,
    filledLevel: Float,
    step: Int
) {
    if (step % TWINKLE_PERIOD != 0) return

    val unused = segments.indices.filter { it >= filledLevel.toInt() }
    if (unused.isEmpty()) return

    val channel = segments[unused.random()]
    builder.buildChannel(channel, (GLYPH_MAX_BRIGHTNESS * 0.5f).toInt())
}

/** Phase-shifted breathing glow across the B and E accent segments. */
private fun GlyphRenderer.addAccentGlow(
    builder: GlyphFrame.Builder,
    profile: DeviceProfile,
    step: Int
) {
    val glow = (GLYPH_MAX_BRIGHTNESS * (0.15f + 0.15f * sin(step * 0.18f))).toInt()
    val glowShifted = (GLYPH_MAX_BRIGHTNESS * (0.15f + 0.15f * sin(step * 0.18f + 1.5f))).toInt()

    profile.b.forEach { builder.buildChannel(it, glow) }
    profile.e.forEach { builder.buildChannel(it, glowShifted) }
}

/** The low-battery pulse on a single channel. */
private fun GlyphRenderer.addAlert(builder: GlyphFrame.Builder, channel: Int, step: Int) {
    val brightness = (GLYPH_MAX_BRIGHTNESS * (0.2f + 0.8f * abs(sin(step * 0.3f)))).toInt()
    builder.buildChannel(channel, brightness.coerceIn(0, GLYPH_MAX_BRIGHTNESS))
}

// endregion
