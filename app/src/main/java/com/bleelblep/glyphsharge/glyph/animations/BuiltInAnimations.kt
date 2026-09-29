package com.bleelblep.glyphsharge.glyph.animations

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The built-in sequences a settings id can name, bound to the shared runner.
 *
 * Each one is a one-liner: hand the sequence to [AnimationRunner.anim] and let
 * it own the guards and the renderer lifecycle. What actually draws is in this
 * package already — [SequenceAnimations], [ParticleAnimations],
 * [AudioAnimations] — and none of these methods adds timing of its own.
 *
 * They stay separate from the animation manager because they are the one
 * concern that has nothing to say to settings, scripts or audio: given a
 * channel layout, light it up.
 */
@Singleton
class BuiltInAnimations @Inject constructor(
    private val runner: AnimationRunner,
) {

    suspend fun runWaveAnimation() = runner.anim { runWaveAnimation(it) }

    suspend fun runBeedahAnimation() = runner.anim { runBeedahAnimation(it) }

    suspend fun runSpiralAnimation() = runner.anim { runSpiralAnimation(it) }

    suspend fun runC1SequentialAnimation() = runner.anim { runC1SequentialAnimation(it) }

    suspend fun runLockPulseAnimation() = runner.anim { runLockPulseAnimation(it) }

    suspend fun runHeartbeatAnimation() = runner.anim { runHeartbeatAnimation(it) }

    suspend fun runMatrixRainAnimation() = runner.anim { runMatrixRainAnimation(it) }

    suspend fun runFireworksAnimation() = runner.anim { runFireworksAnimation(it) }

    suspend fun runDNAHelixAnimation() = runner.anim { runDNAHelixAnimation(it) }

    suspend fun runPulseEffect(cycles: Int = 3) = runner.anim { pulseCycles(it.pulseSegments, cycles) }
}
