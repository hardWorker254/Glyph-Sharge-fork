package com.bleelblep.glyphsharge.services

import android.os.PowerManager
import android.util.Log
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.utils.LoggingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place the phrase "a trigger fired, now draw" is written.
 *
 * Every event-driven feature service — Screen Off, Glow Gate, VPN, NFC, Low
 * Battery — used to open-code the same chain by hand inside its own
 * `playXxxSequence()`:
 *
 * ```kotlin
 * animationScope.launch {
 *     if (!spec.isRunnable(settings)) return@launch
 *     featureCoordinator.withStrip(owner, preempt) {
 *         val duration = glyphAnimationManager.runCapMs(id, configured)
 *         wakeLock.acquire(duration + N)
 *         glyphAnimationManager.runCapped(duration) { playXxxAnimation() }
 *     }
 * }
 * ```
 *
 * The five copies had already drifted in small, invisible ways — whether the
 * WakeLock was taken at all, whether the cap went through [GlyphAnimationManager.runCapMs]
 * or used the raw configured duration, whether the strip was preempted or a
 * busy strip was tolerated. None of those differences is a *behaviour* the
 * features chose; four of the five are accidents of who copied whom. Encoding
 * them as named fields on [Request] turns each into a decision that is visible
 * at the call site, and makes the next feature unable to get them subtly
 * wrong because there is no copy left to get wrong.
 *
 * What stays in the service is only what is genuinely its own: *what* its
 * trigger is, and *which* animation id and duration it is configured with.
 * What moves here is the machinery that is identical for everyone and was the
 * part that kept drifting.
 */
@Singleton
class FeatureTriggerPlayer @Inject constructor(
    private val featureCoordinator: GlyphFeatureCoordinator,
    private val glyphAnimationManager: GlyphAnimationManager,
) {

    /**
     * Everything about one trigger firing that differs between features.
     *
     * @param owner the strip participant this run claims to be, so the
     *   coordinator can arbitrate and report who holds the LEDs.
     * @param eventLabel a short human description of what fired — "screen off",
     *   "payment (AID=…)" — used verbatim in the log lines so a busy-strip skip
     *   can be traced back to the event that caused it.
     * @param animationId the settings id to play. A built-in id, or a
     *   `custom:<uuid>` script id; both go through the same dispatch.
     * @param configuredDurationMs the feature's own duration preference, in
     *   milliseconds.
     * @param applyScriptCapMs whether the effective cap is resolved through
     *   [GlyphAnimationManager.runCapMs] (which shortens a run to a script's
     *   natural length) rather than the raw configured duration. Screen Off,
     *   Glow Gate and VPN do; NFC and Low Battery have always used the raw
     *   duration, and that is preserved here rather than "fixed", because this
     *   refactor is behaviour-preserving by contract.
     * @param preempt `true` for an event the user just caused (an unlock, a
     *   screen-off, a VPN connect), which interrupts whatever is still playing;
     *   `false` skips on a busy strip instead.
     * @param acquireWakeLock whether to hold the CPU for the run. `false` for
     *   anything that fires with the phone in the foreground; `true` for a
     *   trigger that can arrive while dozing (NFC).
     * @param wakeLockBufferMs extra time on top of the cap, covering the strip
     *   blanking that happens *after* the cap fires.
     */
    data class Request(
        val owner: GlyphFeature,
        val eventLabel: String,
        val animationId: String,
        val configuredDurationMs: Long,
        val applyScriptCapMs: Boolean = true,
        val preempt: Boolean = false,
        val acquireWakeLock: Boolean = false,
        val wakeLockBufferMs: Long = DEFAULT_WAKE_LOCK_BUFFER_MS,
    ) {
        companion object {
            /** The strip is still being blanked when the cap hits, so the lock outlives it. */
            const val DEFAULT_WAKE_LOCK_BUFFER_MS = 3_000L
        }
    }

    /**
     * Runs [block]'s whole trigger chain on [scope], once, for one [request].
     *
     * The order of the three teardown-relevant steps is the whole point and is
     * not caller-configurable:
     *
     *  1. the WakeLock is taken *inside* the strip block, so a busy strip never
     *     leaves the CPU held for an animation that did not run;
     *  2. the animation is wrapped in [GlyphAnimationManager.runCapped], which
     *     joins the drawing job before it blanks the strip, so frames cannot be
     *     drawn over a strip the watchdog has already cleared;
     *  3. the WakeLock is released from `withStrip`'s `onRelease`, which runs
     *     *after* the strip is handed back — the only ordering that guarantees
     *     the CPU cannot sleep between "strip released" and "lock released".
     *
     * @param scope the caller's animation scope; the launch happens here so a
     *   feature does not open-code it, but the scope itself stays the service's
     *   to own and cancel.
     * @param isRunnable the feature's run gate, re-checked at fire time because
     *   a trigger can be delivered long after the service was started.
     * @param onDisabled runs when the fire-time gate is closed, for a feature
     *   that traces its own skips (Glow Gate's `RunTrace`).
     * @param onStarted runs once the strip is held and the cap is known — for a
     *   log line or a `RunTrace` record.
     * @param onTimeout runs after the cap fired and the strip has been stopped,
     *   for teardown the player does not know about (Low Battery's audio).
     * @param onBusy runs when the strip could not be taken; the animation is
     *   skipped rather than queued.
     * @param onFailed runs if the chain threw, with the exception.
     * @param playAnimation the innermost draw, normally a single
     *   [GlyphAnimationManager] call for the configured id.
     */
    fun playOnTrigger(
        scope: CoroutineScope,
        isRunnable: () -> Boolean,
        request: Request,
        wakeLock: PowerManager.WakeLock? = null,
        onDisabled: () -> Unit = {},
        onStarted: (capMs: Long) -> Unit = {},
        onTimeout: () -> Unit = {},
        onBusy: () -> Unit = {},
        onFailed: (Exception) -> Unit = {},
        playAnimation: suspend () -> Unit,
    ) {
        val tag = request.owner.name

        scope.launch {
            // Re-check at fire time, not just at service start: a broadcast can
            // be delivered long after the switch was flipped off.
            if (!isRunnable()) {
                LoggingManager.log(TAG, "[$tag] disabled – skipping ${request.eventLabel}")
                onDisabled()
                return@launch
            }

            try {
                // `preempt` decides interrupt-vs-tolerate; the default timeouts
                // are the coordinator's own, so a feature does not restate them.
                val played = featureCoordinator.withStrip(
                    owner = request.owner,
                    preempt = request.preempt,
                    // Released after the strip is handed back, and only for a
                    // run that actually took the strip: a busy strip leaves the
                    // WakeLock untouched because it was never acquired.
                    onRelease = {
                        if (request.acquireWakeLock) releaseQuietly(wakeLock, tag)
                    },
                ) {
                    val capMs = if (request.applyScriptCapMs) {
                        glyphAnimationManager.runCapMs(
                            request.animationId,
                            request.configuredDurationMs,
                        )
                    } else {
                        request.configuredDurationMs
                    }

                    LoggingManager.log(
                        TAG,
                        "[$tag] start – event=${request.eventLabel} " +
                            "anim=${request.animationId} cap=${capMs}ms",
                    )
                    onStarted(capMs)

                    if (request.acquireWakeLock) {
                        acquireQuietly(wakeLock, capMs + request.wakeLockBufferMs, tag)
                    }

                    glyphAnimationManager.runCapped(
                        capMs = capMs,
                        onTimeout = onTimeout,
                    ) { playAnimation() }
                }

                if (played == null) {
                    LoggingManager.log(
                        TAG,
                        "[$tag] strip busy (owner: ${featureCoordinator.currentOwner.value}) " +
                            "– skipping ${request.eventLabel}",
                    )
                    onBusy()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$tag] Error in ${request.eventLabel} sequence", e)
                LoggingManager.log(TAG, "[$tag] error in ${request.eventLabel}: ${e.message}")
                onFailed(e)
            }
        }
    }

    private fun acquireQuietly(
        wakeLock: PowerManager.WakeLock?,
        timeoutMs: Long,
        tag: String,
    ) {
        if (wakeLock == null) return
        try {
            wakeLock.acquire(timeoutMs)
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseQuietly(wakeLock: PowerManager.WakeLock?, tag: String) {
        if (wakeLock == null) return
        try {
            if (wakeLock.isHeld) wakeLock.release()
        } catch (e: Exception) {
            Log.w(tag, "Failed to release WakeLock: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "FeatureTriggerPlayer"
    }
}
