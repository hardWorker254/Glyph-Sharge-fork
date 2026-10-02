package com.bleelblep.glyphsharge.glyph

import android.content.Context
import android.util.Log
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.animations.AnimationRunner
import com.bleelblep.glyphsharge.glyph.animations.BuiltInAnimations
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualisation
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import com.bleelblep.glyphsharge.glyph.battery.BatteryStateReader
import com.bleelblep.glyphsharge.glyph.battery.animateBattery
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptCheckResult
import com.bleelblep.glyphsharge.glyph.script.ScriptPlayback
import com.bleelblep.glyphsharge.glyph.script.ScriptRunResult
import com.bleelblep.glyphsharge.glyph.script.ScriptRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The public entry point for everything that lights up the Glyph strip.
 *
 * This class stays a thin façade on purpose: it holds no drawing code. What is
 * left here is the part that is about *the app* — which settings id a feature
 * plays, how long it may run, and how a caller bounds it — rather than about
 * pixels. The actual work is split by concern, so a change stays in one place:
 *
 * | Concern | Where |
 * |---------|-------|
 * | Which channels exist on this phone | [com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory] |
 * | Frame building, error handling, cancellation | [GlyphRenderer] |
 * | The guards and the blank-strip-around-it lifecycle | [AnimationRunner] |
 * | Wave / sweep / blink / particle sequences | [BuiltInAnimations] |
 * | Randomised particle effects | `glyph.animations.ParticleAnimations` |
 * | The music visualiser and its preview | [MusicVisualisation] |
 * | Charging and battery bar | `glyph.battery.BatteryGlyphAnimator` |
 * | Hosting a user-written Lua animation | [ScriptPlayback] |
 * | Executing the Lua itself | [ScriptRunner] |
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
    private val scriptRunner: ScriptRunner,
    private val runTrace: RunTrace,
    private val runner: AnimationRunner,
    private val builtIn: BuiltInAnimations,
    private val musicVisualisation: MusicVisualisation,
    private val scriptPlayback: ScriptPlayback,
) {
    private companion object {
        const val TAG = "GlyphAnimationManager"

        /** Nominal length of one pulse cycle, used to turn a duration into a count. */
        const val PULSE_CYCLE_MS = 500L
    }

    // region Built-in animations

    suspend fun runWaveAnimation() = builtIn.runWaveAnimation()

    suspend fun runBeedahAnimation() = builtIn.runBeedahAnimation()

    suspend fun runSpiralAnimation() = builtIn.runSpiralAnimation()

    suspend fun runC1SequentialAnimation() = builtIn.runC1SequentialAnimation()

    suspend fun runLockPulseAnimation() = builtIn.runLockPulseAnimation()

    suspend fun runHeartbeatAnimation() = builtIn.runHeartbeatAnimation()

    suspend fun runMatrixRainAnimation() = builtIn.runMatrixRainAnimation()

    suspend fun runFireworksAnimation() = builtIn.runFireworksAnimation()

    suspend fun runDNAHelixAnimation() = builtIn.runDNAHelixAnimation()

    suspend fun runPulseEffect(cycles: Int = 3) = builtIn.runPulseEffect(cycles)

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

    suspend fun playVpnConnectedAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(
            settingsRepository.getVpnConnectedAnimationId(),
            settingsRepository.getVpnConnectedDuration(),
        )
    }

    suspend fun playPowerPeekAnimation(
        context: Context,
        onProgressUpdate: (Float) -> Unit = {},
    ) = playBatteryBar(
        context = context,
        durationMs = settingsRepository.getPowerPeekDuration(),
        onProgressUpdate = onProgressUpdate,
    )

    suspend fun playChargingAnimationAnimation(
        context: Context,
        onProgressUpdate: (Float) -> Unit = {},
    ) = playBatteryBar(
        context = context,
        durationMs = settingsRepository.getChargingAnimationDuration(),
        onProgressUpdate = onProgressUpdate,
    )

    /**
     * Shared by Power Peek and the charging animation, which differ only in
     * their configured duration. The battery is sampled once, before the
     * animation starts, so the bar cannot jump if the level changes mid-run.
     */
    private suspend fun playBatteryBar(
        context: Context,
        durationMs: Long,
        onProgressUpdate: (Float) -> Unit,
    ) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        val state = BatteryStateReader.read(context)

        runner.anim {
            animateBattery(it, state.percentage, state.isCharging, durationMs, onProgressUpdate)
        }
    }

    // endregion

    // region Music visualiser

    /** Runs the visualiser the user configured, for as long as the service holds the strip. */
    suspend fun playMusicVisualizerAnimation() = musicVisualisation.play()

    /** Shows what a mode looks like, on made-up audio. */
    suspend fun previewMusicVisualizer(
        mode: MusicVisualizationMode,
        sensitivity: Float = 1f,
    ) = musicVisualisation.preview(mode, sensitivity)

    // endregion

    // region Custom scripts

    /** The wall-clock cap that actually applies to [runtimeId]. */
    fun runCapMs(runtimeId: String, featureDurationMs: Long): Long =
        scriptPlayback.runCapMs(runtimeId, featureDurationMs)

    /** Plays a user's script on the glyph, returning why the run ended. */
    suspend fun playCustomAnimation(runtimeId: String): ScriptRunResult =
        scriptPlayback.playCustomAnimation(runtimeId)

    /**
     * Previews a script from the editor, before the animation has been saved.
     *
     * The whole [ScriptRunResult] is passed back, log lines included: the
     * studio is the only reader of a preview, and it needs the script's own
     * output as well as the verdict on it.
     */
    suspend fun previewScript(source: String, durationMs: Long): ScriptRunResult =
        scriptPlayback.previewScript(source, durationMs)

    /**
     * Compiles without drawing anything, for the editor's Check button.
     *
     * Typed rather than a bare message because "this file does not parse" and
     * "this file names a module we do not ship" are two different findings,
     * and the studio words them differently.
     */
    fun checkScript(source: String): ScriptCheckResult = scriptPlayback.checkScript(source)

    // endregion

    /**
     * Aborts whatever is playing — built-in or scripted — and blanks the strip.
     *
     * This is the preempting side, so it clears the renderer's claim outright
     * rather than through a lease: the animation being interrupted has no lease
     * to prove ownership with, and its own teardown will arrive separately.
     * That separation is the point — a holder's `renderer.stop(lease)` can only
     * ever stop itself, so an interrupted animation unwinding late cannot clear
     * the flag of whatever took over.
     */
    fun stopAnimations() {
        scriptRunner.stop()
        renderer.stopAll()
        renderer.turnOff()
    }

    /**
     * Runs [block] on a dispatcher of its own, for at most [capMs], and stops it
     * when the cap expires.
     *
     * This is the watchdog every feature service was open-coding around its
     * animation: a drawing coroutine that may run forever, plus a timer that
     * cuts it off, plus the join that makes the cut-off deterministic.
     *
     * The stop is a cancellation *and* a [stopAnimations], and it is that order:
     *
     *  - `cancelAndJoin` first, so the animator unwinds through its own
     *    `finally` and the last thing that touches the renderer is its own
     *    teardown, not a frame drawn over a strip this method has just blanked.
     *  - [stopAnimations] second, for whatever the cancellation alone cannot
     *    reach — a `scriptRunner` run, or a loop parked in a non-cancellable
     *    draw. It is the same call [GlyphFeatureCoordinator.acquireNow] uses to
     *    interrupt the current owner.
     *
     * A [block] that finishes on its own is left alone: the watchdog is just
     * cancelled, and none of the above happens. An exception from [block] is
     * not swallowed — it propagates to the caller, which is where the services
     * log it.
     *
     * This deliberately does not go through [AnimationRunner.anim] or
     * [ScriptPlayback]: those own the renderer lifecycle for one animation, and
     * the watchdog is the caller's to impose from the outside.
     *
     * @param onTimeout run after the cap fired and the strip has been stopped —
     *   for the per-feature teardown the manager does not know about, such as
     *   the low-battery alert's audio.
     */
    suspend fun <T> runCapped(
        capMs: Long,
        onTimeout: () -> Unit = {},
        block: suspend () -> T,
    ): T = coroutineScope {
        val animJob = async(Dispatchers.Default) { block() }

        val watchdogJob = launch {
            delay(capMs.milliseconds)
            animJob.cancelAndJoin()   // cancel, then wait for cleanup
            stopAnimations()
            onTimeout()
        }

        animJob.join()
        // The animation finished on its own: the watchdog must not fire.
        watchdogJob.cancel()

        // The cap firing cancels the animation job, and awaiting a cancelled job
        // would rethrow that as a `CancellationException` — turning the normal
        // end of a capped run into a failure every caller would log. There is
        // no value to report in that case, so `Unit` stands in: every caller
        // blocks on a `Unit`-returning animation that was cut off mid-flight.
        @Suppress("UNCHECKED_CAST")
        if (animJob.isCancelled) Unit as T else animJob.await()
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
            // Every feature ignores the result, so the status, frame count and
            // duration are the only record of a script that failed to parse or
            // that drew nothing because the SDK was unreachable.
            Log.d(
                TAG,
                "Script '${ScriptAnimation.stripPrefix(id)}' -> ${result.status}, " +
                    "${result.frames} frames in ${result.elapsedMs}ms${result.message?.let { ": $it" } ?: ""}",
            )
            runTrace.record(
                "script",
                ScriptAnimation.stripPrefix(id),
                "${result.status} frames=${result.frames} in ${result.elapsedMs}ms" +
                    (result.message?.let { " $it" } ?: ""),
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

    private fun isGlyphServiceEnabled(): Boolean = runner.isGlyphServiceEnabled()

    // endregion
}
