package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.LogLevel
import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * `require("glyph.log")` — the console, with severities.
 *
 * ```lua
 * local log = require("glyph.log")
 * log.info("starting")
 * log.warn("battery is " .. battery.percent)
 * log.error("nothing to animate")   -- stops the run
 * ```
 *
 * `print` and `glyph.log` stay exactly as they were; this is the version that
 * says *how* bad it is. [error] is the only one that raises, and it does so
 * because a script that has decided it cannot continue should stop rather than
 * spend the rest of its duration drawing a frame it knows is wrong.
 */
internal object GlyphLogModule {

    /** The name a script writes in `require`. */
    const val NAME = "glyph.log"

    fun build(session: ScriptSession): LuaTable =
        ModuleBuilder(NAME)
            .apply {
                func("info") { args ->
                    session.log(args.joinedText(), LogLevel.INFO)
                    LuaValue.NIL
                }
                func("warn") { args ->
                    session.log(args.joinedText(), LogLevel.WARN)
                    LuaValue.NIL
                }
                func("error") { args -> raise(session, args.joinedText()) }
            }
            .build()

    /**
     * Writes the line and then stops the run.
     *
     * Thrown as a plain exception rather than with `LuaValue.error` so that
     * [ModuleBuilder.func]'s wrapper turns it into exactly one Lua error with
     * one prefix. Raising it directly would be caught by that same wrapper —
     * `LuaError` is a `RuntimeException` — and the author would read
     * `glyph.log.error: glyph.log.error: …`.
     */
    private fun raise(session: ScriptSession, text: String): LuaValue {
        session.log(text, LogLevel.ERROR)
        throw IllegalStateException(text)
    }
}
