package com.bleelblep.glyphsharge.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptScope
import com.bleelblep.glyphsharge.glyph.script.isVisibleIn
import com.bleelblep.glyphsharge.ui.viewmodel.CustomAnimationsViewModel

/**
 * The catalogue every animation picker renders.
 *
 * Built-in animations are a fixed list, but the user can add their own in the
 * studio, so a picker never renders [list] on its own: it calls
 * [withCustomAnimations] with whatever the repository currently holds. That
 * keeps a new script visible on Pulse Lock, Low Battery, NFC and Screen Off
 * without those four screens knowing anything about the studio.
 */
object GlyphAnimations {
    data class GlyphAnim(
        val id: String,
        val displayName: String,
        @param:DrawableRes val iconRes: Int,
        /** `true` for a Lua animation, which the duration setting bounds. */
        val isCustom: Boolean = false,
    )

    val list = listOf(
        GlyphAnim("C1", "C1 Sequential", R.drawable.su),
        GlyphAnim("WAVE", "Wave", R.drawable._78),
        GlyphAnim("BEEDAH", "Beedah", R.drawable._78),
        GlyphAnim("PULSE", "Pulse", R.drawable._44),
        GlyphAnim("LOCK", "Padlock Sweep", R.drawable._23_24px),
        GlyphAnim("SPIRAL", "Spiral", R.drawable._78),
        GlyphAnim("HEARTBEAT", "Heartbeat", R.drawable._44),
        GlyphAnim("MATRIX", "Matrix Rain", R.drawable._78),
        GlyphAnim("FIREWORKS", "Fireworks", R.drawable._44),
        GlyphAnim("DNA", "DNA Helix", R.drawable._23_24px),
    )

    /** Icon reused for every script: nothing in the app art matches "custom". */
    private val customIcon = R.drawable._23_24px

    /**
     * Built-ins first, then the user's scripts, so a picker keeps a stable
     * layout and the part that changes is always at the end of the chip row.
     *
     * [scope] is the picker being filled. The four trigger features pass
     * [ScriptScope.TRIGGER] and therefore never see a script that declared
     * `glyph.target = "music"`; the visualiser passes [ScriptScope.MUSIC] and
     * sees everything, because a script that named no target means "anywhere".
     */
    fun withCustomAnimations(
        custom: List<ScriptAnimation>,
        scope: ScriptScope = ScriptScope.TRIGGER,
    ): List<GlyphAnim> {
        if (custom.isEmpty()) return list
        return list + custom.asSequence().filter { it.isVisibleIn(scope) }.map { animation ->
            GlyphAnim(
                id = animation.runtimeId,
                displayName = animation.name,
                iconRes = customIcon,
                isCustom = true,
            )
        }
    }

    /**
     * Resolves a stored id against the list actually on screen.
     *
     * A custom id that no longer resolves — the script was deleted, or the
     * picker was handed a stale list — falls back to the first entry, so the
     * feature still has something valid selected.
     */
    fun getById(id: String, options: List<GlyphAnim> = list): GlyphAnim =
        options.firstOrNull { it.id == id } ?: list.first()
}

/**
 * The animations a picker should offer, rebuilt whenever the studio saves.
 *
 * The list is a `StateFlow` on purpose: a script saved in the studio has to
 * appear on all four feature cards at once, including the one already open
 * behind it. The pickers are plain Composables, so they read it through
 * [CustomAnimationsViewModel] rather than reaching for the store themselves.
 */
@Composable
fun rememberAnimationOptions(scope: ScriptScope = ScriptScope.TRIGGER): List<GlyphAnimations.GlyphAnim> {
    val viewModel: CustomAnimationsViewModel = hiltViewModel()
    val custom by viewModel.animations.collectAsStateWithLifecycle()
    return remember(custom, scope) { GlyphAnimations.withCustomAnimations(custom, scope) }
}
