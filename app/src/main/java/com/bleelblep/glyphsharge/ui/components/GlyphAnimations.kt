package com.bleelblep.glyphsharge.ui.components

import androidx.annotation.DrawableRes
import com.bleelblep.glyphsharge.R

object GlyphAnimations {
    data class GlyphAnim(
        val id: String,
        val displayName: String,
        @DrawableRes val iconRes: Int
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
        GlyphAnim("DNA", "DNA Helix", R.drawable._23_24px)
    )

    fun getById(id: String): GlyphAnim =
        list.firstOrNull { it.id == id } ?: list.first()
}