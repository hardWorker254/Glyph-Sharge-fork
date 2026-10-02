package com.bleelblep.glyphsharge.glyph.script.module

import org.luaj.vm2.LuaValue

/**
 * Positional argument access for module functions.
 *
 * The same rule as the helpers in `GlyphLuaApi`: a missing slot is `nil` and
 * `nil` means "use the default", exactly as it does in Lua. Duplicated rather
 * than shared only because the `glyph` table's copies are file-private — the
 * alternative would be widening the sandbox file's internals for the sake of
 * four one-liners.
 *
 * Indices are Lua indices: argument 1 is the first one the script passed.
 */
internal fun Array<LuaValue>.raw(index: Int): LuaValue? = getOrNull(index - 1)

/** A number as an int, or [default] when the slot is missing or `nil`. */
internal fun Array<LuaValue>.intOr(index: Int, default: Int): Int {
    val value = raw(index) ?: return default
    if (value.isnil()) return default
    return value.toint()
}

/** A number as a double, or [default] when the slot is missing or `nil`. */
internal fun Array<LuaValue>.doubleOr(index: Int, default: Double = 0.0): Double {
    val value = raw(index) ?: return default
    if (value.isnil()) return default
    return value.todouble()
}

/** A string, or [default] when the slot is missing or `nil`. */
internal fun Array<LuaValue>.stringOr(index: Int, default: String = ""): String {
    val value = raw(index) ?: return default
    if (value.isnil()) return default
    return value.tojstring()
}

/** Every argument the script passed, joined the way `print` joins them. */
internal fun Array<LuaValue>.joinedText(): String = joinToString(" ") { it.tojstring() }
