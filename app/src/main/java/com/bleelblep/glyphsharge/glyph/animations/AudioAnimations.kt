package com.bleelblep.glyphsharge.glyph.animations

import com.bleelblep.glyphsharge.glyph.audio.AudioFrame
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import com.nothing.ketchum.GlyphFrame
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * The music visualiser: one loop, six ways of painting a spectrum.
 *
 * Every mode is a *continuous* animation, which is what sets it apart from the
 * sequences in `SequenceAnimations.kt` and `ParticleAnimations.kt`: those run to
 * an end and release the strip, this one holds it for as long as music plays
 * and checks [GlyphRenderer.isRunning] on every frame rather than every step.
 *
 * ### One loop, six painters
 *
 * The mode is a parameter rather than six loops, so pacing, the idle behaviour
 * and the per-frame bookkeeping live exactly once. [MusicSurface] carries the
 * state a painter needs between frames — the scrolling history, the matrix
 * heads, the beat flash — and is built per run, so a second run of the same
 * mode does not inherit the last one's leftovers.
 *
 * ### Phones with four segments
 *
 * Phone (1) has four channels in the C strip against Phone (3a)'s twenty. A
 * twenty-bar equaliser squeezed into four channels is four blinking dots, so
 * [resolveStrip] falls back to every channel on the phone: the visualiser is
 * coarse there, but it still reacts.
 */

/** Roughly 30 fps, which is as fast as a strip of LEDs can usefully change. */
private const val AUDIO_FRAME_MS = 33L

/** Below this many channels the C strip is too short to be worth graphing. */
internal const val MIN_GRAPH_CHANNELS = 8

/** How long a beat flash takes to fade, in frames. */
private const val BEAT_FLASH_FRAMES = 7

/** How bright the foot of a bar is, as a fraction of full. */
private const val DIM_FLOOR = 0.18f

/** Brightness of the idle travelling dot. */
private const val IDLE_BRIGHTNESS = 0.14f

/**
 * Segments per frame a matrix drop travels, at silence and as a multiple of the
 * band under its head.
 *
 * Below one, because the strip is short: a full segment every frame is a lap
 * in two thirds of a second on a twenty-channel ring, which is a blur rather
 * than rain. The fraction is carried between frames, so a slow drop still
 * creeps instead of moving in visible jumps.
 */
private const val MATRIX_STEP_MIN = 0.25f

/** How much of a segment per frame the loudest band adds to the step. */
private const val MATRIX_STEP_RANGE = 0.75f

/**
 * Runs the visualiser until something stops it.
 *
 * @param mode which painter to use
 * @param nextFrame the newest analysed audio, called once per frame; returning
 *   [AudioFrame.SILENT] draws the idle animation
 * @param maxFrames stop after this many frames; `0` means run until stopped.
 *   Only the settings preview bounds itself — the service has no reason to,
 *   and a visualiser that ended on its own would look like a crash.
 */
internal suspend fun GlyphRenderer.runMusicVisualization(
    mode: MusicVisualizationMode,
    profile: DeviceProfile,
    nextFrame: () -> AudioFrame,
    maxFrames: Int = 0,
) {
    val surface = MusicSurface(profile)
    var drawn = 0
    while (isRunning) {
        surface.draw(this, mode, nextFrame())
        drawn++
        if (maxFrames > 0 && drawn >= maxFrames) return
        delay(AUDIO_FRAME_MS.milliseconds)
    }
}

/** The C strip when it is long enough to graph, every channel otherwise. */
internal fun resolveStrip(profile: DeviceProfile): List<Int> =
    if (profile.c.size >= MIN_GRAPH_CHANNELS) profile.c else profile.all

/**
 * The channels a mode paints on, and the per-mode state that survives a frame.
 *
 * Not a singleton on purpose: two runs must not share a scrolling history, and
 * [runMusicVisualization] already builds one per run.
 */
private class MusicSurface(profile: DeviceProfile) {

    private val strip: List<Int> = resolveStrip(profile)
    private val all: List<Int> = profile.all
    private val pulse: List<Int> = profile.pulseSegments
    private val spiral: List<Int> = profile.spiralOrder.ifEmpty { profile.all }
    private val matrix = profile.matrixConfig

    /** The frame's bands, resampled to one value per segment. */
    private val bands = FloatArray(strip.size)

    /** Scrolling levels, newest first — the trace behind `WAVE`. */
    private val history = FloatArray(strip.size)

    /** Brightest writer per segment, so overlapping bars in `BARS` compose. */
    private val column = IntArray(strip.size)

    /** One travelling head per matrix drop, seeded once per run. */
    private val dropHeads: IntArray

    /** The fractional part of each drop's travel, carried between frames. */
    private val dropSteps: FloatArray

    private var historyHead = 0
    private var flash = 0
    private var phase = 0

    init {
        val random = Random(System.nanoTime())
        val drops = matrix.drops.coerceAtMost(all.size.coerceAtLeast(1))
        dropHeads = IntArray(drops) { random.nextInt(all.size.coerceAtLeast(1)) }
        dropSteps = FloatArray(drops)
    }

    suspend fun draw(renderer: GlyphRenderer, mode: MusicVisualizationMode, frame: AudioFrame) {
        phase++

        // `strip` is drawn from `all` or from `c`, so no channels at all means
        // an unsupported phone, and there is nothing every mode could do.
        if (all.isEmpty()) return

        if (frame.isSilent) {
            drawIdle(renderer, mode)
            return
        }

        frame.bandsInto(bands)
        rememberLevel(frame)
        if (frame.beat) flash = BEAT_FLASH_FRAMES
        if (flash > 0) flash--

        when (mode) {
            MusicVisualizationMode.BARS -> drawBars(renderer, frame)
            MusicVisualizationMode.WAVE -> drawWave(renderer)
            MusicVisualizationMode.MIRROR -> drawMirror(renderer, frame)
            MusicVisualizationMode.BEAT -> drawBeat(renderer, frame)
            MusicVisualizationMode.MATRIX -> drawMatrix(renderer)
            MusicVisualizationMode.VORTEX -> drawVortex(renderer, frame)
        }
    }

    // region Painters

    /** An equaliser: each bar grows out of the bottom of the strip. */
    private suspend fun drawBars(renderer: GlyphRenderer, frame: AudioFrame) {
        val builder = renderer.builder() ?: return

        // Every bar is bottom-aligned, so on a one-dimensional strip they
        // overlap by construction: bass reaches nearly the whole thing and
        // treble only the first segment or two. Collapsing them with `max`
        // rather than "last writer wins" is what keeps a short treble bar from
        // erasing the tall bass bar underneath it — with the plain overwrite the
        // whole mode collapses into one dim block and stops looking like bars.
        column.fill(0)
        for (i in strip.indices) {
            val value = bands.getOrElse(i) { 0f }
            val lit = ceil(value * strip.size).toInt().coerceIn(0, strip.size)
            if (lit == 0) continue
            for (j in 0 until lit) {
                // Brighter towards the top, so a short bar is not merely a
                // dimmer long one and the strip has a shape.
                val gradient = (0.45f + 0.55f * (j + 1) / lit)
                val level = brightness(value * gradient)
                if (level > column[j]) column[j] = level
            }
        }
        for (j in strip.indices) {
            if (column[j] > 0) builder.buildChannel(strip[j], column[j])
        }

        if (flash > 0) lightAll(builder, frame, extra = 0.25f)
        renderer.toggle(builder, AUDIO_FRAME_MS)
    }

    /** A scrolling plot of the level over time, newest sample at the head. */
    private suspend fun drawWave(renderer: GlyphRenderer) {
        val builder = renderer.builder() ?: return
        // [history] is a ring, so it has to be read back from the write head.
        // Read straight from index 0 the trace is still drawn, but the head
        // stays where it was first written and the fade behind it is attached
        // to a fixed position instead of travelling with the newest sample.
        val size = history.size
        for (i in strip.indices) {
            val value = history.getOrElse((historyHead - i + size) % size) { 0f }
            // The head is the newest sample and so the brightest; the tail has
            // already been heard and dims away behind it.
            val age = 1f - i.toFloat() / size.coerceAtLeast(1)
            builder.buildChannel(strip[i], brightness(value * (DIM_FLOOR + age * 0.82f)))
        }
        renderer.toggle(builder, AUDIO_FRAME_MS)
    }

    /** Bars mirrored outwards from the middle of the strip. */
    private suspend fun drawMirror(renderer: GlyphRenderer, frame: AudioFrame) {
        val builder = renderer.builder() ?: return
        val centre = strip.size / 2
        val reach = (strip.size + 1) / 2

        for (k in 0 until reach) {
            val value = bands.getOrElse(k.coerceAtMost(bands.lastIndex)) { 0f }
            if (k >= ceil(value * reach).toInt()) continue

            val falloff = 1f - k.toFloat() / reach * 0.7f
            val level = brightness(value * falloff)

            val left = centre - k
            val right = centre + k
            if (left in strip.indices) builder.buildChannel(strip[left], level)
            if (right in strip.indices && right != left) builder.buildChannel(strip[right], level)
        }

        // The middle segment always burns a little, so the mode still reads as
        // symmetrical when the music is quiet.
        if (centre in strip.indices) {
            builder.buildChannel(strip[centre], brightness(DIM_FLOOR * frame.rms + 0.06f))
        }
        renderer.toggle(builder, AUDIO_FRAME_MS)
    }

    /** A dim wash that snaps to full brightness on every kick. */
    private suspend fun drawBeat(renderer: GlyphRenderer, frame: AudioFrame) {
        val builder = renderer.builder() ?: return
        val punch = if (flash > 0) flash.toFloat() / BEAT_FLASH_FRAMES else 0f

        lightAll(builder, frame, extra = 0.35f + 0.65f * punch)

        // The pulse segments — the round dots and short strips — carry the low
        // end, because that is where a kick lives and they read as impacts.
        val level = brightness(DIM_FLOOR * frame.bass + 0.75f * punch)
        pulse.forEach { builder.buildChannel(it, level) }

        renderer.toggle(builder, AUDIO_FRAME_MS)
    }

    /** Matrix rain, with every drop's length driven by the band it is over. */
    private suspend fun drawMatrix(renderer: GlyphRenderer) {
        if (all.isEmpty()) return
        val builder = renderer.builder() ?: return

        for (k in dropHeads.indices) {
            // The head has to travel or this is a fixed pattern that only grows
            // and shrinks, which is not rain. The speed is read from the band
            // the head is currently over, so a loud passage runs faster.
            val under = bands.getOrElse(dropHeads[k] % bands.size.coerceAtLeast(1)) { 0f }
            dropSteps[k] += MATRIX_STEP_MIN + MATRIX_STEP_RANGE * under
            val advance = floor(dropSteps[k])
            dropSteps[k] -= advance
            val head = (dropHeads[k] + advance.toInt()) % all.size
            dropHeads[k] = head

            val value = bands.getOrElse(head % bands.size.coerceAtLeast(1)) { 0f }
            val length = (1 + value * matrix.maxLength).roundToInt().coerceIn(1, all.size)
            for (j in 0 until length) {
                val fade = 1f - j.toFloat() / length * 0.8f
                builder.buildChannel(
                    all[(head + j) % all.size],
                    brightness(value * fade + 0.05f),
                )
            }
        }
        renderer.toggle(builder, AUDIO_FRAME_MS)
    }

    /** Two rings travelling around the spiral in opposite directions. */
    private suspend fun drawVortex(renderer: GlyphRenderer, frame: AudioFrame) {
        if (spiral.isEmpty()) return
        val builder = renderer.builder() ?: return

        val size = spiral.size
        val spin = 1 + (frame.bass * 3f).roundToInt()
        val ringSize = (size * (0.12f + 0.28f * frame.rms)).roundToInt().coerceIn(2, size)

        lightRing(builder, phase * spin, ringSize)
        lightRing(builder, phase * spin + size / 2, ringSize)

        renderer.toggle(builder, AUDIO_FRAME_MS)
    }

    private fun lightRing(builder: GlyphFrame.Builder, head: Int, ringSize: Int) {
        for (j in 0 until ringSize) {
            // Brightest at the head, fading round the back of the ring.
            val fade = 1f - j.toFloat() / ringSize
            builder.buildChannel(spiral[(head + j) % spiral.size], brightness(0.25f + 0.75f * fade))
        }
    }

    // endregion

    // region Shared

    /**
     * A slow travelling dot, so a paused track leaves the phone looking alive
     * rather than broken.
     *
     * Only the modes that can look composed without data get one: a bar chart
     * with nothing to plot is honest, and faking it would be worse than a pause.
     */
    private suspend fun drawIdle(renderer: GlyphRenderer, mode: MusicVisualizationMode) {
        if (strip.isEmpty() || !mode.isIdleFriendly) return
        val builder = renderer.builder() ?: return

        val index = (phase / 3) % strip.size
        builder.buildChannel(strip[index], brightness(IDLE_BRIGHTNESS))
        builder.buildChannel(
            strip[(index - 1 + strip.size) % strip.size],
            brightness(IDLE_BRIGHTNESS * 0.5f),
        )
        renderer.toggle(builder, AUDIO_FRAME_MS)
    }

    /** Records the current level for the scrolling [history] trace. */
    private fun rememberLevel(frame: AudioFrame) {
        if (history.isEmpty()) return
        historyHead = (historyHead + 1) % history.size
        history[historyHead] = frame.mid
    }

    /** The whole strip, dimmer with the level and brighter on a beat. */
    private fun lightAll(builder: GlyphFrame.Builder, frame: AudioFrame, extra: Float) {
        val level = brightness(DIM_FLOOR + 0.55f * frame.rms + extra)
        all.forEach { builder.buildChannel(it, level) }
    }

    private fun brightness(value: Float): Int =
        (GLYPH_MAX_BRIGHTNESS * value.coerceIn(0f, 1f)).toInt()

    // endregion
}
