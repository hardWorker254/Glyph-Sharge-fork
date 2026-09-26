package com.bleelblep.glyphsharge.glyph

import java.util.Locale

/**
 * The animation ids understood by the settings layer.
 *
 * The strings here are the ones stored in `SettingsRepository` and shown by the
 * UI catalogue [com.bleelblep.glyphsharge.ui.components.GlyphAnimations], which
 * mirrors the same list. Adding an animation means adding an entry here, one
 * branch in `GlyphAnimationManager.playAnimation`, and one entry there.
 */
enum class GlyphAnimationId(val id: String) {
    C1("C1"),
    WAVE("WAVE"),
    BEEDAH("BEEDAH"),
    PULSE("PULSE"),
    LOCK("LOCK"),
    SPIRAL("SPIRAL"),
    HEARTBEAT("HEARTBEAT"),
    MATRIX("MATRIX"),
    FIREWORKS("FIREWORKS"),
    DNA("DNA");

    companion object {
        /**
         * Resolves a stored id, or `null` when it is unknown. Callers treat
         * `null` and [PULSE] the same way: a plain blink.
         */
        fun of(raw: String): GlyphAnimationId? =
            entries.firstOrNull { it.id == raw.trim().uppercase(Locale.ROOT) }
    }
}
