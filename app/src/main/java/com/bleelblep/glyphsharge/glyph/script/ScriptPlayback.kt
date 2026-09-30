package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.animations.AnimationRunner
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
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
        return playScript { scriptRunner.runScript(source, durationMs) }
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

        renderer.start()
        try {
            renderer.turnOff()
            delay(AnimationRunner.CLEANUP_DELAY_MS.milliseconds)
            return block()
        } catch (e: Exception) {
            renderer.onError(e, "Script error")
            return ScriptRunResult(ScriptStatus.RUNTIME_ERROR, e.message)
        } finally {
            renderer.stop()
            renderer.turnOff()
        }
    }
}
