package com.bleelblep.glyphsharge.glyph

import android.content.Context
import android.util.Log
import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.animations.pulseCycles
import com.bleelblep.glyphsharge.glyph.animations.runBeedahAnimation
import com.bleelblep.glyphsharge.glyph.animations.runC1SequentialAnimation
import com.bleelblep.glyphsharge.glyph.animations.runDNAHelixAnimation
import com.bleelblep.glyphsharge.glyph.animations.runFireworksAnimation
import com.bleelblep.glyphsharge.glyph.animations.runHeartbeatAnimation
import com.bleelblep.glyphsharge.glyph.animations.runLockPulseAnimation
import com.bleelblep.glyphsharge.glyph.animations.runMatrixRainAnimation
import com.bleelblep.glyphsharge.glyph.animations.runSpiralAnimation
import com.bleelblep.glyphsharge.glyph.animations.runWaveAnimation
import com.bleelblep.glyphsharge.glyph.battery.BatteryStateReader
import com.bleelblep.glyphsharge.glyph.battery.animateBattery
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptRunResult
import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import com.bleelblep.glyphsharge.glyph.script.ScriptRunner
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The public entry point for everything that lights up the Glyph strip.
 *
 * This class stays a thin façade on purpose: it holds no drawing code. The
 * actual work is split by concern, so a change stays in one place:
 *
 * | Concern | Where |
 * |---------|-------|
 * | Which channels exist on this phone | [DeviceProfileFactory] |
 * | Frame building, error handling, cancellation | [GlyphRenderer] |
 * | Wave / sweep / blink sequences | `glyph.animations.SequenceAnimations` |
 * | Randomised particle effects | `glyph.animations.ParticleAnimations` |
 * | Charging and battery bar | `glyph.battery.BatteryGlyphAnimator` |
 * | User-written Lua animations | `glyph.script.ScriptRunner` |
 *
 * All public entry points are `suspend` and are safe to call concurrently:
 * [stopAnimations] flips the renderer's cancellation flag, and the glyphs are
 * always turned off when a sequence ends or is cancelled.
 */
@Singleton
class GlyphAnimationManager @Inject constructor(
    private val glyphManager: GlyphManager,
    private val renderer: GlyphRenderer,
    private val settingsRepository: SettingsRepository,
    private val customAnimationRepository: CustomAnimationRepository,
    private val scriptRunner: ScriptRunner,
    private val runTrace: RunTrace,
) {
    private companion object {
        const val TAG = "GlyphAnimationManager"

        /** Pause after forcing the strip off, so the previous frame is really gone. */
        const val CLEANUP_DELAY_MS = 100L

        /** Nominal length of one pulse cycle, used to turn a duration into a count. */
        const val PULSE_CYCLE_MS = 500L
    }

    /**
     * Resolved once: the LED layout cannot change while the process lives, and
     * `null` means the hardware is not supported.
     */
    private val profile: DeviceProfile? by lazy { DeviceProfileFactory.forConnectedDevice() }

    // region Base animations

    suspend fun runWaveAnimation() = anim { runWaveAnimation(it) }

    suspend fun runBeedahAnimation() = anim { runBeedahAnimation(it) }

    suspend fun runSpiralAnimation() = anim { runSpiralAnimation(it) }

    suspend fun runC1SequentialAnimation() = anim { runC1SequentialAnimation(it) }

    suspend fun runLockPulseAnimation() = anim { runLockPulseAnimation(it) }

    suspend fun runHeartbeatAnimation() = anim { runHeartbeatAnimation(it) }

    suspend fun runMatrixRainAnimation() = anim { runMatrixRainAnimation(it) }

    suspend fun runFireworksAnimation() = anim { runFireworksAnimation(it) }

    suspend fun runDNAHelixAnimation() = anim { runDNAHelixAnimation(it) }

    suspend fun runPulseEffect(cycles: Int = 3) = anim { pulseCycles(it.pulseSegments, cycles) }

    // endregion

    // region Feature scenarios

    suspend fun playPulseLockAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(
            settingsRepository.getPulseLockAnimationId(),
            settingsRepository.getPulseLockDuration(),
        )
    }

    suspend fun playLowBatteryAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(
            settingsRepository.getLowBatteryAnimationId(),
            settingsRepository.getLowBatteryDuration(),
        )
    }

    suspend fun playScreenOffAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(
            settingsRepository.getScreenOffAnimationId(),
            settingsRepository.getScreenOffDuration(),
        )
    }

    suspend fun playNfcAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(
            settingsRepository.getNfcAnimationId(),
            settingsRepository.getNfcAnimationDuration(),
        )
    }

    suspend fun playPowerPeekAnimation(
        context: Context,
        onProgressUpdate: (Float) -> Unit = {}
    ) = playBatteryBar(
        context = context,
        durationMs = settingsRepository.getPowerPeekDuration(),
        onProgressUpdate = onProgressUpdate
    )

    suspend fun playChargingAnimationAnimation(
        context: Context,
        onProgressUpdate: (Float) -> Unit = {}
    ) = playBatteryBar(
        context = context,
        durationMs = settingsRepository.getChargingAnimationDuration(),
        onProgressUpdate = onProgressUpdate
    )

    /**
     * Shared by Power Peek and the charging animation, which differ only in
     * their configured duration. The battery is sampled once, before the
     * animation starts, so the bar cannot jump if the level changes mid-run.
     */
    private suspend fun playBatteryBar(
        context: Context,
        durationMs: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        val state = BatteryStateReader.read(context)

        anim { animateBattery(it, state.percentage, state.isCharging, durationMs, onProgressUpdate) }
    }

    // endregion

    // region Custom scripts

    /**
     * The wall-clock cap that actually applies to [runtimeId].
     *
     * A built-in animation uses the feature's Duration setting, because that is
     * how the built-ins are written. A script gets a generous safety cap
     * instead: cutting a script off at whatever the feature's slider happened to
     * say was wrong twice over — the slider is hidden for scripts, and a
     * script that draws for four seconds was being killed at three.
     *
     * The feature services ask this for their watchdog rather than reading the
     * setting directly, so the animation and the thing that stops it can never
     * disagree.
     */
    fun runCapMs(runtimeId: String, featureDurationMs: Long): Long =
        if (ScriptAnimation.isCustomId(runtimeId)) ScriptAnimation.SAFETY_CAP_MS
        else featureDurationMs

    /**
     * Plays a user's script on the glyph.
     *
     * There is no duration parameter, on purpose. A script is a program: it
     * lights what it was told to light and returns when its code is done. The
     * only thing that stops it early is the next event — see
     * [GlyphFeatureCoordinator.acquireNow] — or the safety cap in
     * [ScriptAnimation.SAFETY_CAP_MS] for a script that never finishes.
     *
     * @param runtimeId a `custom:` id as stored in the feature settings
     * @return why the run ended, so the studio can show it
     */
    suspend fun playCustomAnimation(runtimeId: String): ScriptRunResult {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) {
            return ScriptRunResult(ScriptStatus.STOPPED, "The glyph service is off.")
        }
        val animation = customAnimationRepository.findByRuntimeId(runtimeId)
            ?: return ScriptRunResult(ScriptStatus.STOPPED, "That animation no longer exists.")

        return playScript {
            scriptRunner.runScript(animation.source, ScriptAnimation.SAFETY_CAP_MS)
        }
    }

    /**
     * Preview from the editor, before the animation has been saved.
     *
     * No service check: the studio is an explicit user action, and the glyph
     * session is opened by `MainActivity` regardless of the feature toggles.
     */
    suspend fun previewScript(source: String, durationMs: Long): ScriptRunResult {
        if (!glyphManager.isNothingPhone()) {
            return ScriptRunResult(ScriptStatus.STOPPED, "Glyph is not available on this device.")
        }
        return playScript { scriptRunner.runScript(source, durationMs) }
    }

    /** Compiles without drawing anything, for the editor's Check button. */
    fun checkScript(source: String): String? = scriptRunner.check(source)

    /**
     * Runs a script under the same guards as a built-in animation: the strip is
     * blanked before and after, and a failure never escapes into the caller.
     */
    private suspend fun playScript(block: suspend () -> ScriptRunResult): ScriptRunResult {
        if (profile == null) {
            return ScriptRunResult(ScriptStatus.STOPPED, "This phone's LED layout is unknown.")
        }

        renderer.start()
        try {
            renderer.turnOff()
            delay(CLEANUP_DELAY_MS.milliseconds)
            return block()
        } catch (e: Exception) {
            renderer.onError(e, "Script error")
            return ScriptRunResult(ScriptStatus.RUNTIME_ERROR, e.message)
        } finally {
            renderer.stop()
            renderer.turnOff()
        }
    }

    // endregion

    /** Aborts whatever is playing — built-in or scripted — and blanks the strip. */
    fun stopAnimations() {
        scriptRunner.stop()
        renderer.stop()
        renderer.turnOff()
    }

    // region Dispatch and running

    /**
     * Plays the animation named by a settings id.
     *
     * Only the `PULSE` fallback uses [durationMs]: the named animations have
     * their own natural length and ignore the duration setting.
     */
    private suspend fun playAnimation(id: String, durationMs: Long) {
        // A user script arrives before the built-in lookup: its id is namespaced
        // precisely so the two cannot collide.
        if (ScriptAnimation.isCustomId(id)) {
            val result = playCustomAnimation(id)
            // Every feature ignores the result, so a script that failed to parse,
            // or drew nothing because the SDK was unreachable, used to be
            // indistinguishable from a script that ran perfectly.
            Log.d(
                TAG,
                "Script '${ScriptAnimation.stripPrefix(id)}' -> ${result.status}, " +
                    "${result.frames} frames in ${result.elapsedMs}ms${result.message?.let { ": $it" } ?: ""}"
            )
            runTrace.record(
                "script",
                ScriptAnimation.stripPrefix(id),
                "${result.status} frames=${result.frames} in ${result.elapsedMs}ms" +
                    (result.message?.let { " $it" } ?: "")
            )
            return
        }

        val cycles = (durationMs / PULSE_CYCLE_MS).toInt().coerceAtLeast(1)

        when (GlyphAnimationId.of(id)) {
            GlyphAnimationId.C1 -> runC1SequentialAnimation()
            GlyphAnimationId.WAVE -> runWaveAnimation()
            GlyphAnimationId.BEEDAH -> runBeedahAnimation()
            GlyphAnimationId.LOCK -> runLockPulseAnimation()
            GlyphAnimationId.SPIRAL -> runSpiralAnimation()
            GlyphAnimationId.HEARTBEAT -> runHeartbeatAnimation()
            GlyphAnimationId.MATRIX -> runMatrixRainAnimation()
            GlyphAnimationId.FIREWORKS -> runFireworksAnimation()
            GlyphAnimationId.DNA -> runDNAHelixAnimation()
            // PULSE, and anything unrecognised.
            GlyphAnimationId.PULSE, null -> runPulseEffect(cycles)
        }
    }

    private fun isGlyphServiceEnabled(): Boolean {
        val enabled = settingsRepository.getGlyphServiceEnabled()
        if (!enabled) {
            Log.d(TAG, "Glyph service disabled - animation call ignored")
        }
        return enabled
    }

    /**
     * Runs one sequence under the shared guards: the service must be enabled,
     * the hardware supported, and a profile available. The strip is blanked
     * before and after the sequence, and a failing step is logged rather than
     * propagated — except coroutine cancellation, which must keep unwinding.
     */
    private suspend fun anim(block: suspend GlyphRenderer.(DeviceProfile) -> Unit) {
        if (!isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return

        val device = profile ?: return

        renderer.start()
        try {
            renderer.turnOff()
            delay(CLEANUP_DELAY_MS.milliseconds)
            block(renderer, device)
        } catch (e: Exception) {
            renderer.onError(e, "Animation error")
        } finally {
            renderer.stop()
            renderer.turnOff()
        }
    }

    // endregion
}
