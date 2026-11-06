package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.time.ZonedDateTime

/**
 * The wall clock, for a script that changes with the time of day.
 *
 * ```lua
 * local level = glyph.isNight and glyph.NIGHT or glyph.DAY
 * glyph.setAll(level)
 * ```
 *
 * The fields live flat on `glyph`, so reading the hour costs no `require` —
 * which matters most in a service, where `require` is banned outright and a
 * flat field is the only road to the clock at all.
 *
 * [build] still serves `require("glyph.time")` for the scripts that were
 * written when that was the way, and both surfaces read [liveFields] and
 * [constants], so the two cannot disagree: a field added to one and not the
 * other would be a name that answers on one path and reads nil on the other.
 *
 * Every moving field is *live*. A snapshot would be useless here: a script
 * that branches on the hour is almost always a loop that runs for minutes, and
 * the one thing it needs is the hour it is in right now. The clock comes from
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

    /**
     * The fields that move, keyed by the name a script writes.
     *
     * Shared with the flat `glyph.hour` surface on purpose: one getter per
     * field, so a fix to the 06:00 boundary lands on both roads at once.
     */
    fun liveFields(session: ScriptSession): Map<String, () -> LuaValue> = mapOf(
        "hour" to { LuaValue.valueOf(session.wallClock().hour) },
        "minute" to { LuaValue.valueOf(session.wallClock().minute) },
        "minuteOfDay" to {
            val now = session.wallClock()
            LuaValue.valueOf((now.hour * MINUTES_PER_HOUR) + now.minute)
        },
        "isNight" to { LuaValue.valueOf(isNight(session.wallClock())) },
    )

    /** The three brightness levels, likewise shared with `glyph.DAY` and kin. */
    fun constants(): Map<String, Int> = mapOf(
        "DAY" to GLYPH_MAX_BRIGHTNESS,
        "DUSK" to (GLYPH_MAX_BRIGHTNESS * DUSK_FRACTION).toInt(),
        "NIGHT" to 0,
    )

    fun build(session: ScriptSession): LuaTable =
        ModuleBuilder(NAME)
            .apply {
                liveFields(session).forEach { (key, get) -> live(key, get) }
                constants().forEach { (key, value) -> int(key, value) }
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
