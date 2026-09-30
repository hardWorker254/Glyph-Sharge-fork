package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.GlyphLuaApi
import com.bleelblep.glyphsharge.glyph.script.ScriptAbortedError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.VarArgFunction

/**
 * Assembles one module table for [ModuleRegistry] to hand to a script.
 *
 * A module is a plain Lua table, so without a helper each one repeats the same
 * `LuaValue.valueOf` wrapping and — worse — is a place to forget the part that
 * matters. Two mistakes are easy here and both are invisible until a script is
 * already running on a phone:
 *
 *  * a value that changes during the run stored as a plain field, so a script
 *    that loops on it sees whatever it was when the module was built;
 *  * a function bound without a prefix, so an error inside it blames
 *    `glyph.clamp` when the author called `glyph.util.clamp`.
 *
 * [live] and [func] exist to make the first and the second hard to write.
 *
 * ```kotlin
 * internal fun build(session: ScriptSession): LuaTable =
 *     ModuleBuilder("glyph.time")
 *         .apply { live("hour") { LuaValue.valueOf(session.wallClock().hour) } }
 *         .apply { int("DAY", GLYPH_MAX_BRIGHTNESS) }
 *         .build()
 * ```
 *
 * @param name the name the script asked for, e.g. `glyph.time`. Used verbatim
 *   as the error prefix, so a message reads `glyph.time.foo: …` and the author
 *   can tell which `require` in a multi-line script is at fault.
 */
internal class ModuleBuilder(private val name: String) {

    /** The module as the script sees it. Live keys are resolved out of [live]. */
    private val table = LuaTable()

    /**
     * Keys that change while the run lasts, resolved on every read.
     *
     * A getter rather than a value because a value would freeze whatever the
     * getter returned at build time, and these modules exist precisely for
     * scripts that branch on something that moves — the hour, the battery.
     */
    private val live = mutableMapOf<String, () -> LuaValue>()

    /** A value that is already a Lua value, e.g. a table handed straight through. */
    fun value(key: String, v: LuaValue) {
        table.set(key, v)
    }

    fun int(key: String, v: Int) {
        table.set(key, LuaValue.valueOf(v))
    }

    fun number(key: String, v: Double) {
        table.set(key, LuaValue.valueOf(v))
    }

    fun bool(key: String, v: Boolean) {
        table.set(key, LuaValue.valueOf(v))
    }

    /**
     * A key resolved afresh on every read, for anything that moves.
     *
     * Resolved through an `__index` metamethod rather than stored, so
     * `while glyph.time.hour == hour do` behaves instead of spinning on a
     * snapshot. Reading it is cheap — the getter is a field read or a system
     * clock call — and scripts call it once per frame at most.
     */
    fun live(key: String, get: () -> LuaValue) {
        live[key] = get
    }

    /**
     * A method on the module.
     *
     * Bound through [GlyphLuaApi.luaFunction] with this module's own name as
     * the prefix, so a failure is reported as `glyph.util.clamp: …`. The
     * wrapper also rethrows a [ScriptAbortedError] untouched, which is what
     * keeps a module function from making the watchdog catchable.
     */
    fun func(key: String, f: (Array<LuaValue>) -> LuaValue) {
        table.set(key, GlyphLuaApi.luaFunction(key, name, f))
    }

    /**
     * The finished module, ready for [ModuleRegistry] to cache.
     *
     * Live keys get a metatable only when there are any: a module made purely
     * of constants and methods is then an ordinary table with nothing to run on
     * every single field read.
     */
    fun build(): LuaTable {
        if (live.isNotEmpty()) {
            val meta = LuaTable()
            meta.set("__index", liveIndex())
            table.setmetatable(meta)
        }
        return table
    }

    /**
     * The one `__index` for a module, matching the shape `glyph.audio` uses.
     *
     * LuaJ calls a function-valued `__index` as `__index(table, key)`, so the
     * key is argument *2* — reading argument 1 looks the module table itself up
     * and every live value comes back nil.
     *
     * The live map is consulted first so a constant can never shadow a moving
     * value: a script that assigns `t.hour = 99` gets a field of its own, but
     * a module that also declared `hour` statically must not hand out the
     * stale one. `rawget` is used rather than `get` for the same reason — `get`
     * would re-enter this very metamethod.
     */
    private fun liveIndex(): VarArgFunction = GlyphLuaApi.luaFunction(
        "__index",
        name,
        { args ->
            val key = args.stringOr(2, "")
            live[key]?.invoke() ?: table.rawget(key)
        }
    )

    override fun toString(): String = "ModuleBuilder($name)"
}
