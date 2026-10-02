package com.bleelblep.glyphsharge.glyph.audio

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
    /**
     * Always English, on purpose: a mode is a technical name, and every
     * other animation picker in the app names its built-ins in English
     * whatever the locale. Translated chips here were the only
     * localized animation names in the app.
     */
    val displayName: String,
) {
    /** A classic equaliser: one bar per band, height = energy. */
    BARS("BARS", "Bars"),

    /** An oscilloscope: the waveform itself, head bright, tail fading. */
    WAVE("WAVE", "Wave"),

    /** Bars mirrored outwards from the centre of the strip. */
    MIRROR("MIRROR", "Mirror"),

    /** Everything flashes on a detected kick, scaled by the low end. */
    BEAT("BEAT", "Beat"),

    /** Matrix rain whose drops are driven by the bands. */
    MATRIX("MATRIX", "Matrix"),

    /** Two counter-rotating rings, the bass turning them. */
    VORTEX("VORTEX", "Vortex");

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
