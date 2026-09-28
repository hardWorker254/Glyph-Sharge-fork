package com.bleelblep.glyphsharge.glyph.audio

import androidx.annotation.StringRes
import com.bleelblep.glyphsharge.R
import java.util.Locale

/**
 * The ways the visualiser can paint the spectrum on the strip.
 *
 * Separate from [com.bleelblep.glyphsharge.glyph.GlyphAnimationId] on purpose.
 * That enum is a flat set of unrelated names — `PULSE`, `WAVE`, `MATRIX` mean
 * one thing there and another here — and a `custom:<uuid>` id from the studio
 * has to be resolved in the same `when` as these, so mixing the two would make
 * `WAVE` ambiguous between "a travelling wave" and "an oscilloscope".
 */
enum class MusicVisualizationMode(
    val id: String,
    @param:StringRes val displayNameRes: Int,
) {
    /** A classic equaliser: one bar per band, height = energy. */
    BARS("BARS", R.string.music_viz_mode_bars),

    /** An oscilloscope: the waveform itself, head bright, tail fading. */
    WAVE("WAVE", R.string.music_viz_mode_wave),

    /** Bars mirrored outwards from the centre of the strip. */
    MIRROR("MIRROR", R.string.music_viz_mode_mirror),

    /** Everything flashes on a detected kick, scaled by the low end. */
    BEAT("BEAT", R.string.music_viz_mode_beat),

    /** Matrix rain whose drops are driven by the bands. */
    MATRIX("MATRIX", R.string.music_viz_mode_matrix),

    /** Two counter-rotating rings, the bass turning them. */
    VORTEX("VORTEX", R.string.music_viz_mode_vortex);

    /** Shown when the visualiser runs without music. */
    val isIdleFriendly: Boolean
        get() = when (this) {
            // BARS and MIRROR are graphs of a value: with nothing to plot they
            // are a row of dark segments. The rest look composed when empty.
            BARS, MIRROR -> false
            WAVE, BEAT, MATRIX, VORTEX -> true
        }

    companion object {
        /** The id a fresh install starts on. */
        val DEFAULT = BARS

        /**
         * Resolves a stored id, or `null` when it is unknown.
         *
         * `null` rather than a silent fallback: the caller has to decide between
         * a custom script id and a mode, and a mode it cannot resolve is a
         * setting to repair, not one to guess at.
         */
        fun of(raw: String): MusicVisualizationMode? =
            entries.firstOrNull { it.id == raw.trim().uppercase(Locale.ROOT) }
    }
}
