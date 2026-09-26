package com.bleelblep.glyphsharge.glyph.engine

import android.util.Log
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.nothing.ketchum.GlyphFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/** Brightness used by every animation unless it deliberately dims a channel. */
const val GLYPH_MAX_BRIGHTNESS = 4000

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
 */
@Singleton
class GlyphRenderer @Inject constructor(
    private val glyphManager: GlyphManager,
) {
    private companion object {
        const val TAG = "GlyphRenderer"
    }

    @Volatile
    private var running = false

    /**
     * `true` while a sequence plays. Animations must check it inside every
     * loop so that [stop] takes effect promptly instead of after the last step.
     */
    val isRunning: Boolean get() = running

    /** Marks the start of a sequence. */
    fun start() {
        running = true
    }

    /** Asks every running sequence to stop at its next step. */
    fun stop() {
        running = false
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
        brightness: Int = GLYPH_MAX_BRIGHTNESS
    ): GlyphFrame.Builder? {
        if (channels.isEmpty()) return null
        return runCatching {
            glyphManager.mGM?.getGlyphFrameBuilder()?.apply {
                channels.forEach { buildChannel(it, brightness) }
            }
        }.onFailure { Log.e(TAG, "frame error", it) }.getOrNull()
    }

    /**
     * An empty builder for animations that need a different brightness per
     * channel, or `null` if the SDK is unavailable.
     */
    fun builder(): GlyphFrame.Builder? =
        runCatching { glyphManager.mGM?.getGlyphFrameBuilder() }
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
            if (delayMs > 0) delay(delayMs)
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
        delayMs: Long = 0L
    ): Boolean = toggle(frame(channels, brightness), delayMs)

    /**
     * Shows [channels] for [onMs], turns everything off, then waits [offMs].
     * This is the building block almost every animation is made of.
     */
    suspend fun pulse(
        channels: Collection<Int>,
        onMs: Long,
        offMs: Long = 0L,
        brightness: Int = GLYPH_MAX_BRIGHTNESS
    ) {
        toggleChannels(channels, brightness, onMs)
        turnOff()
        if (offMs > 0) delay(offMs)
    }

    /**
     * Reports a failed animation step. Keeps the rhythm going instead of
     * aborting the whole sequence, but never swallows coroutine cancellation.
     */
    suspend fun onError(e: Exception, message: String, retryDelayMs: Long = 0L) {
        if (e is CancellationException) throw e
        Log.e(TAG, message, e)
        if (retryDelayMs > 0) delay(retryDelayMs)
    }
}
