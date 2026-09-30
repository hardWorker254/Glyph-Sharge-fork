package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * `require("glyph.util")` — arithmetic a script would otherwise write out.
 *
 * ```lua
 * local util = require("glyph.util")
 * local fade = util.mapRange(glyph.audio.level, 0, 1, 0, glyph.MAX)
 * local spots = util.shuffle(glyph.ch.c)
 * ```
 *
 * Everything here is pure: no drawing, no sleeping, nothing that can end a run
 * early. That is what makes these safe to call inside a tight loop, and it is
 * why they are a module rather than more `glyph` functions — there is nothing
 * to configure and nothing to keep alive between calls.
 */
internal object GlyphUtilModule {

    /** The name a script writes in `require`. */
    const val NAME = "glyph.util"

    fun build(session: ScriptSession): LuaTable =
        ModuleBuilder(NAME)
            .apply {
                func("clamp") { args ->
                    LuaValue.valueOf(
                        clamp(
                            args.doubleOr(1),
                            args.doubleOr(2),
                            args.doubleOr(3)
                        )
                    )
                }
                func("lerp") { args ->
                    LuaValue.valueOf(lerp(args.doubleOr(1), args.doubleOr(2), args.doubleOr(3)))
                }
                func("mapRange") { args ->
                    LuaValue.valueOf(
                        mapRange(
                            args.doubleOr(1),
                            args.doubleOr(2),
                            args.doubleOr(3),
                            args.doubleOr(4),
                            args.doubleOr(5)
                        )
                    )
                }
                func("shuffle") { args -> shuffle(session, args) }
            }
            .build()

    /**
     * [value] held inside `[lo, hi]`.
     *
     * A reversed pair is swapped rather than rejected: `clamp(t, 1, 0)` in a
     * script is a mistake worth surviving, whereas the alternative — a
     * comparison that is always false — would return the input untouched and
     * hand the hardware a brightness it cannot show.
     */
    private fun clamp(value: Double, lo: Double, hi: Double): Double {
        val low = minOf(lo, hi)
        val high = maxOf(lo, hi)
        return value.coerceIn(low, high)
    }

    /**
     * How far along `from` → `to` [t] is.
     *
     * [t] is held to `0..1`. Extrapolation is a thing a shader can do and a
     * glyph cannot: past the end of the range this would ask the renderer for
     * a brightness the hardware clamps anyway, and the animation would look
     * stuck while the script believed it was still moving.
     */
    private fun lerp(from: Double, to: Double, t: Double): Double =
        from + (to - from) * t.coerceIn(0.0, 1.0)

    /**
     * [value] from one range onto another, e.g. audio `0..1` to `0..MAX`.
     *
     * Handles the two ways this is usually got wrong: a reversed input range
     * (mapping `0..1` onto `1..0` is a legitimate way to invert something, and
     * the negative span is what makes it work), and a degenerate one
     * (`inLo == inHi` has no slope at all, so the low end is the only answer
     * that does not divide by zero). Values outside the input range are pinned
     * to the output end for the same reason as [lerp].
     */
    private fun mapRange(
        value: Double,
        inLo: Double,
        inHi: Double,
        outLo: Double,
        outHi: Double
    ): Double {
        val span = inHi - inLo
        if (span == 0.0) return outLo
        val t = ((value - inLo) / span).coerceIn(0.0, 1.0)
        return outLo + (outHi - outLo) * t
    }

    /**
     * A shuffled copy of a Lua array, drawn with the session's own generator.
     *
     * A copy rather than a shuffle in place: the array a script shuffles is
     * usually a constant like `glyph.ch.c`, and shuffling it in place would
     * leave the next loop iteration — and the next `require` of the same
     * module — working on an order that has drifted.
     *
     * [args] rather than a typed parameter because the value comes straight out
     * of the VM; a non-table is an error the author needs to see, not an empty
     * result that quietly shuffles nothing.
     */
    private fun shuffle(session: ScriptSession, args: Array<LuaValue>): LuaValue {
        val source = args.raw(1)
            ?: throw IllegalArgumentException("expected a table of channels")
        if (!source.istable()) {
            throw IllegalArgumentException(
                "expected a table, got ${source.typename()}"
            )
        }

        val input = source.checktable()
        val values = (1..input.length()).map { input.get(it).toint() }
        val shuffled = values.toMutableList()
        // Fisher-Yates, walking downwards so every index is drawn from a range
        // that still has more than one element in it.
        for (i in shuffled.lastIndex downTo 1) {
            val j = session.randomInt(0, i)
            val swap = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = swap
        }

        return LuaTable().also { out ->
            shuffled.forEachIndexed { index, channel ->
                out.set(index + 1, LuaValue.valueOf(channel))
            }
        }
    }
}
