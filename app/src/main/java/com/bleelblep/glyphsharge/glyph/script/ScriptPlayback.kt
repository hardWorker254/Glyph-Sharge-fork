package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.animations.AnimationRunner
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Plays a user's Lua animation on the glyph, under the same guards as a
 * built-in one.
 *
 * The split from [ScriptRunner] is where the drawing stops and the hosting
 * starts: the runner knows how to execute Lua and reach the renderer, and this
 * class decides whether the strip should be handed to it at all, blanks the
 * strip around it, and turns a thrown exception into a result the studio can
 * print instead of a crash a feature service has to catch.
 */
@Singleton
class ScriptPlayback @Inject constructor(
    private val runner: AnimationRunner,
    private val renderer: GlyphRenderer,
    private val glyphManager: GlyphManager,
    private val customAnimationRepository: CustomAnimationRepository,
    private val scriptRunner: ScriptRunner,
    /**
     * Lazy, and that is not incidental.
     *
     * The coordinator holds [com.bleelblep.glyphsharge.glyph.GlyphAnimationManager]
     * so it can interrupt whoever owns the strip, and the manager holds this
     * class — so a direct dependency would close a construction cycle and Dagger
     * would refuse the graph. A [Provider] defers the lookup to the moment a
     * preview actually runs, which is also the only time it is needed: the
     * feature path is already inside `withStrip` and never comes near here.
     */
    private val coordinator: Provider<GlyphFeatureCoordinator>,
) {

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
     * [com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator.acquireNow] — or
     * the safety cap in [ScriptAnimation.SAFETY_CAP_MS] for a script that never
     * finishes.
     *
     * @param runtimeId a `custom:` id as stored in the feature settings
     * @return why the run ended, so the studio can show it
     */
    suspend fun playCustomAnimation(runtimeId: String): ScriptRunResult {
        if (!runner.isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) {
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
     *
     * The script's own output rides along on the result — nothing is fetched
     * from the runner afterwards, because there is nothing left to fetch it
     * from. The two results built here, for a phone with no glyph, are the
     * exception: no script ran, so they have no lines and say so by being
     * empty.
     */
    suspend fun previewScript(source: String, durationMs: Long): ScriptRunResult {
        if (!glyphManager.isNothingPhone()) {
            return ScriptRunResult(ScriptStatus.STOPPED, "Glyph is not available on this device.")
        }

        // Through the coordinator, like every other thing that draws.
        //
        // This is the one caller that used to skip it, and skipping meant the
        // preview interleaved with whatever a feature service was playing: both
        // drew, the LEDs carried both at once, and the preview's teardown cleared
        // the flag the service's own `while (isRunning)` loop was watching — so
        // the service's animation stopped halfway and nothing said so.
        //
        // `preempt = false`, so a preview never interrupts a feature: it takes
        // the strip only if it is free, and gives up otherwise. A preview is
        // the user checking something out, and interrupting a charging animation
        // for that is worse than the preview not appearing.
        val played = coordinator.get().withStrip(
            owner = GlyphFeature.PREVIEW,
            preempt = false,
        ) {
            playScript { scriptRunner.runScript(source, durationMs) }
        }

        // `null` is "the strip was busy", which is a real answer rather than a
        // failure to report as one — and saying so beats leaving the user
        // staring at an editor that appeared to do nothing.
        return played ?: ScriptRunResult(
            ScriptStatus.STOPPED,
            "The glyph is busy with another animation.",
        )
    }

    /**
     * Compiles without drawing anything, for the editor's Check button.
     *
     * The typed result is passed through untouched: a missing module and a
     * syntax error are different findings and the studio has different things
     * to say about them, so the distinction has to survive the hop.
     */
    fun checkScript(source: String): ScriptCheckResult = scriptRunner.check(source)

    /**
     * Runs a script under the same guards as a built-in animation: the strip is
     * blanked before and after, and a failure never escapes into the caller.
     */
    private suspend fun playScript(block: suspend () -> ScriptRunResult): ScriptRunResult {
        if (runner.profile == null) {
            return ScriptRunResult(ScriptStatus.STOPPED, "This phone's LED layout is unknown.")
        }

        val lease = renderer.start("script")
        return try {
            renderer.turnOff()
            delay(AnimationRunner.CLEANUP_DELAY_MS.milliseconds)
            block()
        } catch (e: Exception) {
            renderer.onError(e, "Script error")
            ScriptRunResult(ScriptStatus.RUNTIME_ERROR, e.message)
        } finally {
            renderer.stop(lease)
            renderer.turnOff()
        }
    }
}
