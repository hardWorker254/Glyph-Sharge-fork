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
import com.bleelblep.glyphsharge.glyph.animations.runMusicVisualization
import com.bleelblep.glyphsharge.glyph.audio.AudioAnalysis
import com.bleelblep.glyphsharge.glyph.audio.AudioFrame
import com.bleelblep.glyphsharge.glyph.audio.AudioFrameFeed
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
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
import kotlin.math.exp
import kotlin.math.sin
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
    private val audioFeed: AudioFrameFeed,
) {
    private companion object {
        const val TAG = "GlyphAnimationManager"

        /** Pause after forcing the strip off, so the previous frame is really gone. */
        const val CLEANUP_DELAY_MS = 100L

        /** Nominal length of one pulse cycle, used to turn a duration into a count. */
        const val PULSE_CYCLE_MS = 500L

        /** How long the settings dialog's preview runs: 150 frames at 33 ms, ~5 s. */
        const val PREVIEW_FRAMES = 150
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
        onProgressUpdate: (Float) -> Unit = {},
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

    // region Music visualiser

    /**
     * Runs the visualiser the user configured, for as long as the service holds
     * the strip.
     *
     * A `custom:` id goes down the same path as every other feature's script:
     * [playCustomAnimation]. That is what lets a Lua script be an audio
     * visualiser at all — it reads the same [AudioFrameFeed] singleton through
     * `glyph.audio`, and the service is what keeps the capture open underneath
     * it.
     *
     * The mode is read once per run rather than per frame: the service hands
     * the strip over in 1.5 s slices and starts a fresh run for each one, so a
     * change in the settings lands on the next slice instead of stuttering
     * mid-frame.
     */
    suspend fun playMusicVisualizerAnimation() {
        if (!isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return
        val device = profile ?: return

        val id = settingsRepository.getMusicVizAnimationId()
        if (ScriptAnimation.isCustomId(id)) {
            playCustomAnimation(id)
            return
        }

        val mode = MusicVisualizationMode.of(id) ?: MusicVisualizationMode.DEFAULT
        audioFeed.setGain(settingsRepository.getMusicVizSensitivity())

        // Through `AudioFrameFeed`, never a capture directly: which mechanism
        // is open is the service's decision, and the painter has to follow it
        // rather than pin one. Reading the wrong one hands every mode a silent
        // frame, and the strip then plays the idle animation for any setting.
        anim {
            runMusicVisualization(mode, device, nextFrame = { audioFeed.latest() })
        }
    }

    /**
     * Shows what a mode looks like, on made-up audio.
     *
     * Bounded, unlike the real thing: a preview that runs until the user closes
     * the dialog is a dialog that never closes. No service check either — this
     * is an explicit user action in the settings dialog, and the studio preview
     * already plays for real.
     */
    suspend fun previewMusicVisualizer(mode: MusicVisualizationMode) {
        if (!glyphManager.isNothingPhone()) return
        val device = profile ?: return

        renderer.start()
        try {
            renderer.turnOff()
            delay(CLEANUP_DELAY_MS.milliseconds)
            val track = SyntheticTrack()
            renderer.runMusicVisualization(
                mode,
                device,
                nextFrame = { track.next() },
                maxFrames = PREVIEW_FRAMES
            )
        } catch (e: Exception) {
            renderer.onError(e, "Music preview error")
        } finally {
            renderer.stop()
            renderer.turnOff()
        }
    }

    /**
     * A plausible spectrum for the settings preview.
     *
     * Built from two sine sweeps and a low bump travelling across the bands, so
     * every mode has something to react to. A flat frame would make six modes
     * look identical, and a real capture is not available in a dialog.
     */
    private class SyntheticTrack {
        private val bands = FloatArray(AudioFrame.BAND_COUNT)
        private var seq = 0L

        fun next(): AudioFrame {
            val t = seq / 30f
            for (i in bands.indices) {
                val x = i / (bands.size - 1f)
                val sweep = 0.5f + 0.5f * sin(TWO_PI * (x * 1.5f - t * 0.7f))
                val kick = exp(-((x - (t * 0.35f % 1f)) * (x - (t * 0.35f % 1f))) / 0.01f)
                bands[i] = (0.15f + 0.6f * sweep * sweep + 0.45f * kick).coerceIn(0f, 1f)
            }
            seq++
            return AudioFrame(
                seq = seq,
                bands = bands.copyOf(),
                bass = AudioAnalysis.bassOf(bands),
                mid = AudioAnalysis.midOf(bands),
                treble = AudioAnalysis.trebleOf(bands),
                rms = 0.55f,
                beat = seq % 30 == 0L,
                timestampMs = 0L
            )
        }

        private companion object {
            const val TWO_PI = 2f * Math.PI.toFloat()
        }
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
