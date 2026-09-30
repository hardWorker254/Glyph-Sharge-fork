package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.sensor.SensorSnapshot
import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * `require("glyph.sensor")` — movement, for a script that reacts to the phone
 * being picked up or shaken.
 *
 * ```lua
 * local sensor = require("glyph.sensor")
 * while glyph.running do
 *   if sensor.shaken then
 *     glyph.flashAll(255, 80)
 *   else
 *     local tilt = util.clamp(sensor.magnitude, 0, 30) / 30
 *     glyph.setAll(tilt * glyph.MAX)
 *   end
 *   glyph.hold(20)
 * end
 * ```
 *
 * Every field is *live*, and for the same reason `glyph.net` is: the only
 * reason to ask about movement is to react to it, and a value frozen when the
 * module was built would answer a question about a phone that is no longer
 * moving. `x`, `y`, `z` and `magnitude` arrive in the script's own units —
 * m/s², gravity included — rather than as a normalised `-1..1`, because the
 * threshold that makes a shake is a real number of m/s² and a script that had
 * to convert back to get there would be guessing.
 *
 * Each field is read through one [SensorSnapshot], so the five numbers a
 * script sees were measured together. Two *different* fields are still two
 * samples, as they are for `glyph.net`; what the snapshot guarantees is that a
 * single value is internally consistent, which is what makes `magnitude` the
 * right thing to branch a tilt on.
 *
 * ### Why reading a field starts the sensor, and `require` does not
 *
 * Starting the listener in [build] would be the tidier-looking choice, and it
 * is the wrong one. `require` costs a script a table and nothing else: a
 * script may pull the module in at the top, pass it around, or `require` it
 * inside a branch it never takes, and on a phone none of those has asked for
 * acceleration. Registering there would make the *name* of the feature the
 * expensive thing — a 50 Hz listener and a thread of its own, held for the
 * whole run, on every animation that so much as mentions `glyph.sensor`.
 *
 * So the first `live` getter starts it instead. That is the first instant a
 * script has actually asked the question, it is the same instant the value it
 * asked for is about to be read, and it costs nothing to a script that required
 * the module and stopped there. The matching release is
 * [ScriptSession.close], which the engine calls on every exit path — including
 * the watchdog abort — so the listener cannot outlive the run that wanted it.
 */
internal object GlyphSensorModule {

    /** The name a script writes in `require`. */
    const val NAME = "glyph.sensor"

    fun build(session: ScriptSession): LuaTable =
        ModuleBuilder(NAME)
            .apply {
                // Widened to Double because LuaJ's `valueOf` has no Float
                // overload; Lua has one number type, so nothing is lost and it
                // is the same conversion `glyph.audio.level` already makes.
                live("x") { LuaValue.valueOf(reading(session).x.toDouble()) }
                live("y") { LuaValue.valueOf(reading(session).y.toDouble()) }
                live("z") { LuaValue.valueOf(reading(session).z.toDouble()) }
                live("magnitude") { LuaValue.valueOf(reading(session).magnitude.toDouble()) }
                // A boolean rather than a number, because `if sensor.shaken`
                // is the whole idiom and a bare number would be truthy for
                // every reading including a phone lying still at zero.
                live("shaken") { LuaValue.valueOf(reading(session).shaken) }
            }
            .build()

    /**
     * Starts the sensor if this run has not already, and returns a sample.
     *
     * [ScriptSession.startSensor] takes at most one reference per run however
     * often it is called, so the platform is only ever asked to register once
     * no matter how many times a draw loop asks for `magnitude`. The latch
     * lives in the session rather than here because it is the session's
     * [ScriptSession.close] that has to give the reference back, and only the
     * session knows whether it took one.
     */
    private fun reading(session: ScriptSession): SensorSnapshot {
        session.startSensor()
        return session.sensorSnapshot()
    }
}
