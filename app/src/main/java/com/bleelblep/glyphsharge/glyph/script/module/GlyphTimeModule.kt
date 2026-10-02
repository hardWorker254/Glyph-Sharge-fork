package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.time.ZonedDateTime

/**
 * `require("glyph.time")` — the wall clock, for a script that changes with
 * the time of day.
 *
 * ```lua
 * local time = require("glyph.time")
 * local level = time.isNight and time.NIGHT or time.DAY
 * glyph.setAll(level)
 * ```
 *
 * Every field is *live*. A snapshot would be useless here: a script that
 * branches on the hour is almost always a loop that runs for minutes, and the
 * one thing it needs is the hour it is in right now. The clock comes from
 * [ScriptSession.wallClock], which the tests replace with a fixed instant so
 * the 22:00 boundary can be checked without waiting for 22:00.
 */
internal object GlyphTimeModule {

    /** The name a script writes in `require`. */
    const val NAME = "glyph.time"

    /**
     * Three brightness levels rather than a scale, because what a script
     * actually wants at dusk is "dimmer than day, brighter than night" and
     * nothing finer — a fourth level would be a guess about the user's taste.
     */
    private const val DUSK_FRACTION = 0.55

    /** Lights off from this hour to [NIGHT_END_HOUR]. */
    private const val NIGHT_START_HOUR = 22
    private const val NIGHT_END_HOUR = 6

    /** Minutes in an hour, for `minuteOfDay`. */
    private const val MINUTES_PER_HOUR = 60

    fun build(session: ScriptSession): LuaTable =
        ModuleBuilder(NAME)
            .apply {
                live("hour") { LuaValue.valueOf(session.wallClock().hour) }
                live("minute") { LuaValue.valueOf(session.wallClock().minute) }
                live("minuteOfDay") {
                    val now = session.wallClock()
                    LuaValue.valueOf((now.hour * MINUTES_PER_HOUR) + now.minute)
                }
                live("isNight") { LuaValue.valueOf(isNight(session.wallClock())) }
                int("DAY", GLYPH_MAX_BRIGHTNESS)
                int("DUSK", (GLYPH_MAX_BRIGHTNESS * DUSK_FRACTION).toInt())
                int("NIGHT", 0)
            }
            .build()

    /**
     * `true` from 22:00 up to 06:00, exclusive at both ends of the morning.
     *
     * "From 22:00 to 06:00" is a duration, not a pair of instants, so 22:00
     * itself is already night and 06:00 is already day — the same half-open
     * window a lamp on a timer gets. The pair of assertions either side of
     * both boundaries is what pins that down.
     */
    private fun isNight(now: ZonedDateTime): Boolean =
        (now.hour >= NIGHT_START_HOUR) || (now.hour < NIGHT_END_HOUR)
}
