package com.bleelblep.glyphsharge.glyph.engine

import android.util.Log
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.nothing.ketchum.GlyphFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/** Brightness used by every animation unless it deliberately dims a channel. */
const val GLYPH_MAX_BRIGHTNESS = 4000

/**
 * One animation's claim on the renderer.
 *
 * Identity, not state: there is nothing to read and nothing to set, only to be
 * handed to [GlyphRenderer.stop] and compared. Two leases are never equal even
 * when their [owner] names match, which is exactly what lets a displaced
 * animation's teardown be recognised as stale.
 */
class RenderLease internal constructor(val owner: String)

/**
 * Thin, failure-tolerant wrapper around the SDK frame API, and the only place
 * in the app that talks to `GlyphManager.mGM` directly.
 *
 * Animations are written as `suspend` extensions on this class, so the three
 * concerns that every animation shares live here exactly once:
 *
 *  * **error handling** — a failing frame is logged, never propagated;
 *  * **cancellation** — [isRunning] is checked between steps, and
 *    [stop] is what [com.bleelblep.glyphsharge.glyph.GlyphAnimationManager]
 *    flips to abort a sequence mid-flight;
 *  * **rhythm** — [pulse] is the "on, off, wait" primitive every animation
 *    is ultimately built from.
 *
 * ### Why a lease rather than a flag
 *
 * This used to be one `@Volatile Boolean` that anybody could set and anybody
 * could clear, and three unrelated call sites did exactly that. Under
 * [com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator] the strip is held
 * by one feature at a time, so the flag is only *read* by the current owner and
 * the obvious design looks adequate.
 *
 * It is not, because cancellation is cooperative. When one feature preempts
 * another, the preempted animation unwinds through its own `finally` — and it
 * may well do so *after* the preempting one has already called `start()`. The
 * stale `renderer.stop()` then cleared the flag out from under the new owner,
 * whose `while (isRunning)` loop exited at the next step and the animation died
 * halfway with nothing in any log.
 *
 * So stopping requires proving you are the owner: [start] hands back a
 * [RenderLease] and [stop] only clears the flag for the token inside it.
 * A late teardown is then a no-op, which is the only correct outcome — that
 * animation is already gone.
 */
@Singleton
class GlyphRenderer @Inject constructor(
    private val glyphManager: GlyphManager,
) {
    private companion object {
        const val TAG = "GlyphRenderer"
    }

    /**
     * Who holds the strip, or `null`.
     *
     * An atomic reference rather than a volatile boolean because [start] and
     * [stop] have to read it, decide and write it as one step — two animations
     * unwinding at once would otherwise interleave a read and a write.
     */
    private val holder = AtomicReference<RenderLease?>(null)

    /**
     * `true` while a sequence plays. Animations must check it inside every
     * loop so that [stop] takes effect promptly instead of after the last step.
     */
    val isRunning: Boolean get() = holder.get() != null

    

    /**
     * Claims the renderer for [owner] and returns the lease that releases it.
     *
     * Unconditional, which is deliberate and matches what this always did: the
     * coordinator is what serialises owners, so the only question this has to
     * answer is "is anyone still drawing", and a new animation means yes. The
     * lease is what keeps a *previous* owner's late teardown from saying
     * otherwise.
     */
    fun start(owner: String): RenderLease =
        RenderLease(owner).also { holder.set(it) }

    /**
     * Releases [lease] — but only if it is still the current one.
     *
     * A lease that was already displaced is quietly ignored, which is the whole
     * point: its animation is over, and clearing the flag now would stop
     * whichever animation took over.
     */
    fun stop(lease: RenderLease) {
        holder.compareAndSet(lease, null)
    }

    /**
     * Stops whatever is running, whoever it is.
     *
     * For the preempting side only — [com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator.acquireNow]
     * interrupting the owner it is about to replace. A holder's own teardown
     * must use [stop] with its lease, or it will take the next owner down with it.
     */
    fun stopAll() {
        holder.set(null)
    }

    /** Turns every LED off, swallowing (but logging) failures. */
    fun turnOff() {
        runCatching { glyphManager.turnOffAll() }
            .onFailure { Log.e(TAG, "turnOffAll failed", it) }
    }

    /**
     * A fresh frame builder with [channels] lit at [brightness], or `null` if
     * there is nothing to draw or the SDK is unavailable.
     */
    fun frame(
        channels: Collection<Int>,
        brightness: Int = GLYPH_MAX_BRIGHTNESS,
    ): GlyphFrame.Builder? {
        if (channels.isEmpty()) return null
        return runCatching {
            glyphManager.mGM?.glyphFrameBuilder?.apply {
                channels.forEach { buildChannel(it, brightness) }
            }
        }.onFailure { Log.e(TAG, "frame error", it) }.getOrNull()
    }

    /**
     * An empty builder for animations that need a different brightness per
     * channel, or `null` if the SDK is unavailable.
     */
    fun builder(): GlyphFrame.Builder? =
        runCatching { glyphManager.mGM?.glyphFrameBuilder }
            .onFailure { Log.e(TAG, "builder error", it) }
            .getOrNull()

    /**
     * Displays [builder] and optionally waits.
     *
     * @param builder the frame to show, or `null` to skip the step
     * @return `true` when the frame was actually handed to the SDK
     */
    suspend fun toggle(builder: GlyphFrame.Builder?, delayMs: Long = 0L): Boolean {
        if (builder == null) return false
        return try {
            // `mGM?.` is the whole problem: with a closed or never-opened
            // session the call is skipped, nothing is drawn, and a caller that
            // only counts frames reports a perfect run over a dark strip.
            val manager = glyphManager.mGM ?: return false
            manager.toggle(builder.build())
            if (delayMs > 0) delay(delayMs.milliseconds)
            true
        } catch (e: Exception) {
            onError(e, "toggle error", delayMs)
            false
        }
    }

    /** Lights [channels] at [brightness] and optionally waits. */
    suspend fun toggleChannels(
        channels: Collection<Int>,
        brightness: Int = GLYPH_MAX_BRIGHTNESS,
        delayMs: Long = 0L,
    ): Boolean = toggle(frame(channels, brightness), delayMs)

    /**
     * Shows [channels] for [onMs], turns everything off, then waits [offMs].
     * This is the building block almost every animation is made of.
     */
    suspend fun pulse(
        channels: Collection<Int>,
        onMs: Long,
        offMs: Long = 0L,
        brightness: Int = GLYPH_MAX_BRIGHTNESS,
    ) {
        toggleChannels(channels, brightness, onMs)
        turnOff()
        if (offMs > 0) delay(offMs.milliseconds)
    }

    /**
     * Reports a failed animation step. Keeps the rhythm going instead of
     * aborting the whole sequence, but never swallows coroutine cancellation.
     */
    suspend fun onError(e: Exception, message: String, retryDelayMs: Long = 0L) {
        if (e !is CancellationException) {
            Log.e(TAG, message, e)
            if (retryDelayMs > 0) delay(retryDelayMs.milliseconds)
        } else {
            throw e
        }
    }
}
