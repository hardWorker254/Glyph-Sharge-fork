package com.bleelblep.glyphsharge.glyph.animations

import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import kotlin.random.Random

/**
 * Animations that scatter many short pulses over the strip: falling drops,
 * explosions, a double helix.
 *
 * All three are randomised, so the per-model budgets that control their density
 * live in [DeviceProfile.matrixConfig], [DeviceProfile.fireworksConfig] and
 * [DeviceProfile.dnaConfig] rather than here.
 */

/**
 * A random length in `[min, max)` that never asks for more channels than the
 * strip actually has. Without the clamp, a Phone (1) configured for 35 drops of
 * 15 segments would index past the end of its own channel list.
 */
private fun randomCount(min: Int, max: Int, available: Int): Int {
    val safeMax = max.coerceAtMost(available + 1).coerceAtLeast(min + 1)
    return Random.nextInt(min, safeMax).coerceAtMost(available)
}

/** Drops of decreasing brightness falling down the strip. */
internal suspend fun GlyphRenderer.runMatrixRainAnimation(profile: DeviceProfile) {
    val cfg = profile.matrixConfig
    val channels = profile.all
    if (channels.isEmpty()) return

    repeat(cfg.drops) {
        if (!isRunning) return

        val length = randomCount(cfg.minLength, cfg.maxLength, channels.size)
        val maxStart = (channels.size - length).coerceAtLeast(0)
        val start = if (maxStart == 0) 0 else Random.nextInt(maxStart)

        for (i in 0 until length) {
            if (!isRunning) return
            val brightness =
                (GLYPH_MAX_BRIGHTNESS - i * cfg.brightnessDecrement).coerceAtLeast(0)
            pulse(
                listOf(channels[start + i]),
                onMs = cfg.stepDelayMs,
                offMs = cfg.offDelayMs,
                brightness = brightness,
            )
        }
    }
}

/** A single bright launch followed by a wide, fading explosion. */
internal suspend fun GlyphRenderer.runFireworksAnimation(profile: DeviceProfile) {
    val cfg = profile.fireworksConfig
    val channels = profile.all
    if (channels.isEmpty()) return

    repeat(cfg.count) {
        if (!isRunning) return

        pulse(listOf(channels.random()), onMs = cfg.launchDelayMs)

        val blast = randomCount(cfg.minExplosion, cfg.maxExplosion, channels.size)
        pulse(
            channels.shuffled().take(blast),
            onMs = cfg.explosionDelayMs,
            offMs = cfg.fadeDelayMs,
        )
    }
}

/** Two strands walking in opposite directions, half a strip apart. */
internal suspend fun GlyphRenderer.runDNAHelixAnimation(profile: DeviceProfile) {
    val cfg = profile.dnaConfig
    val channels = profile.all
    if (channels.isEmpty()) return

    val size = channels.size
    val half = size / 2

    repeat(cfg.rotations) {
        for (i in channels.indices) {
            if (!isRunning) return
            pulse(
                listOf(channels[i], channels[(i + half) % size]),
                onMs = cfg.stepDelayMs,
                offMs = cfg.offDelayMs
            )
        }
    }
}
