package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import com.bleelblep.glyphsharge.glyph.script.drawBatteryBar
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * `require("glyph.battery")` — the battery, in the shape an animation wants.
 *
 * ```lua
 * local battery = require("glyph.battery")
 * for i = 1, battery.level() do
 *   glyph.set({ i }, 1500)
 *   glyph.hold(40)
 * end
 * ```
 *
 * The flat `glyph.battery` value already carries the percentage. What a script
 * cannot do with it alone is *translate* — "64%" means a different number of
 * segments on a Phone (3a) than on a Phone (1), so a hard-coded count lights
 * the wrong number of LEDs on half the phones. [level] is that translation, and
 * it is why this module exists rather than two more fields on `glyph`.
 */
internal object GlyphBatteryModule {

    /** The name a script writes in `require`. */
    const val NAME = "glyph.battery"

    /**
     * The long central strip. The battery animation has always run down it,
     * and a count taken from anywhere else would not match what the user sees
     * while they are plugging in.
     */
    private const val BAR_GROUP = "c"

    /** Matches `glyph.batteryBar`, so the two behave identically when omitted. */
    private const val DEFAULT_BAR_MS = 2_000L

    private const val FULL_PERCENT = 100.0

    fun build(session: ScriptSession): LuaTable =
        ModuleBuilder(NAME)
            .apply {
                live("percent") { LuaValue.valueOf(session.batteryPercent()) }
                live("charging") { LuaValue.valueOf(session.isCharging()) }
                func("level") { LuaValue.valueOf(segmentCount(session, session.batteryPercent())) }
                func("bar") { args ->
                    session.drawBatteryBar(
                        session.batteryPercent(),
                        args.intOr(1, DEFAULT_BAR_MS.toInt()).toLong()
                    )
                    LuaValue.NIL
                }
            }
            .build()

    /**
     * How many segments of the bar the current charge is worth.
     *
     * Floored rather than rounded: a half-lit segment is a segment the user
     * reads as *not yet* charged, and the bar is meant to agree with the
     * percentage printed in the status bar.
     */
    private fun segmentCount(session: ScriptSession, percent: Int): Int {
        val segments = session.group(BAR_GROUP)?.size ?: return 0
        return (percent.coerceIn(0, FULL_PERCENT.toInt()) / FULL_PERCENT * segments).toInt()
    }
}
