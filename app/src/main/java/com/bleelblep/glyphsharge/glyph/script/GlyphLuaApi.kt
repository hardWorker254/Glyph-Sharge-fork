package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The `glyph` table: everything a script can reach.
 *
 * This is the entire user-facing language surface, and it is deliberately
 * small. A script can light channels, wait, and read the battery — it cannot
 * touch the file system, the network, or the app. The channel *groups*
 * (`glyph.ch.c`, `glyph.ch.nonC`, …) come from the device profile, so the same
 * script works on a Phone (3a) and a Phone (1) without naming a channel id.
 *
 * ```lua
 * local C = glyph.ch.c
 * for i, ch in ipairs(C) do
 *   glyph.set({ ch }, glyph.MAX)
 *   glyph.hold(60)
 * end
 * glyph.off()
 * ```
 *
 * State that changes while a script runs — `glyph.running`, `glyph.battery`,
 * `glyph.charging` — is exposed as a *value*, resolved on each read through a
 * `__index` metamethod. Reading them as `glyph.running()` would always yield a
 * function, which is truthy in Lua, and a `while glyph.running do` loop would
 * never end on its own.
 */
internal object GlyphLuaApi {

    /**
     * Publishes the table into [globals].
     *
     * @param session the run this API is bound to
     */
    fun install(globals: Globals, session: ScriptSession) {
        val glyph = LuaTable()

        // Constants
        glyph.set("MAX", LuaValue.valueOf(GLYPH_MAX_BRIGHTNESS))
        glyph.set("device", LuaValue.valueOf(session.deviceName()))

        // Channel groups
        val channels = LuaTable()
        session.groupNames().forEach { name ->
            channels.set(name, toLuaArray(session.group(name).orEmpty()))
        }
        glyph.set("ch", channels)

        // Live state
        // These change while a script runs, so they cannot be plain table
        // fields: they are resolved on every read by [liveValueOf], which the
        // single `__index` metamethod installed at the bottom of this function
        // calls before it looks at what the script declared.
        //
        // They are values, not functions, on purpose. A function value is
        // truthy in Lua, which would make `while glyph.running do` spin until
        // the duration limit instead of stopping when the user presses Stop.
        //
        // A plain function rather than a table of its own because a table
        // carries exactly one metatable: `glyph.setmetatable(live)` would be
        // overwritten by the `setmetatable(meta)` at the bottom of this
        // function, and nothing would read `live`, so every live value would
        // resolve to nil — `glyph.running` always falsy and `glyph.batteryBar()`
        // with no battery to draw.
        fun liveValueOf(key: String): LuaValue = when (key) {
            "running" -> LuaValue.valueOf(session.isRunning())
            "battery" -> LuaValue.valueOf(session.batteryPercent())
            "charging" -> LuaValue.valueOf(session.isCharging())
            else -> LuaValue.NIL
        }

        // Audio
        // The music visualiser's input. A separate table rather than more
        // fields on `glyph`, because a script that reads it has to be able to
        // tell "no music is playing" (every value 0, `active` false) from "this
        // key does not exist".
        val audio = LuaTable()
        audio.set("__index", luaFunction("__index") { args ->
            when (args.stringOr(2, "")) {
                "active" -> LuaValue.valueOf(session.isAudioActive())
                "level" -> LuaValue.valueOf(session.audioLevel().toDouble())
                "bass" -> LuaValue.valueOf(session.audioBass().toDouble())
                "mid" -> LuaValue.valueOf(session.audioMid().toDouble())
                "treble" -> LuaValue.valueOf(session.audioTreble().toDouble())
                "beat" -> LuaValue.valueOf(session.audioBeat())
                else -> LuaValue.NIL
            }
        })
        // `bands` is a real key, not a live value: it takes an argument, so
        // `__index` is never consulted for it.
        audio.set(
            "bands",
            luaFunction("bands") { args ->
                val count = args.intOr(1, 8).coerceIn(1, 256)
                toLuaArray(session.audioBands(count))
            }
        )
        audio.setmetatable(audio)
        glyph.set("audio", audio)

        // Milliseconds since this run started. Named `elapsed` because the
        // `glyph.time` module now owns wall-clock time, and two things called
        // "time" on one table is a name an author cannot hold in their head.
        //
        // `glyph.time` stays as an alias because these scripts are saved to the
        // user's storage and nothing is going to re-save them. The flag is
        // local to this `install`, which runs once per run, so a script that
        // calls it every frame is told once and not sixty times a second.
        var timeAliasWarned = false
        val elapsed: (Array<LuaValue>) -> LuaValue = {
            LuaValue.valueOf(session.elapsedMs().toInt())
        }
        glyph.set("elapsed", luaFunction("elapsed") { elapsed(it) })
        glyph.set(
            "time",
            luaFunction("time") { args ->
                if (!timeAliasWarned) {
                    timeAliasWarned = true
                    session.log(
                        "glyph.time() is deprecated — use glyph.elapsed() instead",
                        LogLevel.WARN
                    )
                }
                elapsed(args)
            }
        )
        glyph.set("frame", luaFunction("frame") { LuaValue.valueOf(session.frames) })

        // Drawing
        glyph.set(
            "set",
            luaFunction("set") { args ->
                session.draw(session.channelsOf(args, 1), brightnessOf(args, 2), holdOf(args, 3))
                LuaValue.NIL
            }
        )
        glyph.set(
            "setAll",
            luaFunction("setAll") { args ->
                session.draw(
                    session.group("all").orEmpty(),
                    brightnessOf(args, 1),
                    holdOf(args, 2)
                )
                LuaValue.NIL
            }
        )
        glyph.set(
            "off",
            luaFunction("off") { args ->
                session.blank()
                val wait = holdOf(args, 1)
                if (wait > 0) session.sleep(wait)
                LuaValue.NIL
            }
        )
        glyph.set(
            "hold",
            luaFunction("hold") { args ->
                session.sleep(msOf(args, 1, 0L))
                LuaValue.NIL
            }
        )
        glyph.set(
            "pulse",
            luaFunction("pulse") { args ->
                val channels = session.channelsOf(args, 1)
                val brightness = brightnessOf(args, 4)
                session.draw(channels, brightness, msOf(args, 2, 200L))
                session.blank()
                session.sleep(msOf(args, 3, 100L))
                LuaValue.NIL
            }
        )
        glyph.set(
            "blinkAll",
            luaFunction("blinkAll") { args ->
                val all = session.group("all").orEmpty()
                repeat(args.intOr(4, 1).coerceAtLeast(1)) {
                    session.draw(all, brightnessOf(args, 3), msOf(args, 1, 150L))
                    session.blank()
                    session.sleep(msOf(args, 2, 150L))
                }
                LuaValue.NIL
            }
        )

        // Movement
        glyph.set(
            "sweep",
            luaFunction("sweep") { args ->
                val channels = session.channelsOf(args, 1)
                session.sweep(channels, msOf(args, 2, 80L), brightnessOf(args, 3), args.boolOr(4, false))
                LuaValue.NIL
            }
        )
        glyph.set(
            "wave",
            luaFunction("wave") { args ->
                val channels = session.channelsOf(args, 1)
                session.wave(channels, msOf(args, 2, 80L), brightnessOf(args, 3), args.intOr(4, 0))
                LuaValue.NIL
            }
        )
        glyph.set(
            "spiral",
            luaFunction("spiral") { args ->
                val cycles = args.intOr(1, 1).coerceAtLeast(1)
                val stepMs = msOf(args, 2, 60L)
                val brightness = brightnessOf(args, 3)
                repeat(cycles) { session.spiral(stepMs, brightness) }
                LuaValue.NIL
            }
        )
        glyph.set(
            "heartbeat",
            luaFunction("heartbeat") { args ->
                session.heartbeat(
                    cycles = args.intOr(1, 3),
                    beatMs = msOf(args, 2, 150L),
                    gapMs = msOf(args, 3, 120L),
                    brightness = brightnessOf(args, 4)
                )
                LuaValue.NIL
            }
        )
        glyph.set(
            "batteryBar",
            luaFunction("batteryBar") { args ->
                session.drawBatteryBar(args.intOr(1, session.batteryPercent()), msOf(args, 2, 2_000L))
                LuaValue.NIL
            }
        )

        // Randomness and maths
        glyph.set(
            "rnd",
            luaFunction("rnd") { args ->
                val from = args.intOr(1, 0)
                LuaValue.valueOf(session.randomInt(from, args.intOr(2, from)))
            }
        )
        glyph.set(
            "rndFloat",
            luaFunction("rndFloat") { LuaValue.valueOf(session.randomDouble()) }
        )
        glyph.set(
            "seed",
            luaFunction("seed") { args ->
                session.seed(msOf(args, 1, 0L))
                LuaValue.NIL
            }
        )
        glyph.set(
            "ease",
            luaFunction("ease") { args ->
                LuaValue.valueOf(ease(args.doubleOr(1, 0.0), args.stringOr(2, "inout")))
            }
        )
        glyph.set(
            "group",
            luaFunction("group") { args ->
                val name = args.stringOr(1, "")
                val group = session.group(name)
                if (group == null) {
                    LuaValue.error("glyph.group: unknown channel group '$name'")
                    return@luaFunction LuaValue.NIL
                }
                toLuaArray(group)
            }
        )

        // Control and output
        glyph.set(
            "exit",
            luaFunction("exit") {
                session.exit()
                LuaValue.NIL
            }
        )
        glyph.set(
            "log",
            luaFunction("log") { args ->
                session.log(args.joinToString(" ") { it.tojstring() })
                LuaValue.NIL
            }
        )

        // Which service this script is for
        // `glyph.target = "music"` is a declaration, not a setting: it says
        // which picker should offer the script. `__newindex` fires only for a
        // key that is not already there, which is exactly the once-at-the-top
        // shape a declaration takes — and it means an unknown key set on `glyph`
        // is still stored rather than silently dropped.
        //
        // The value lands in `declared`, and `__index` reads it back from there,
        // so a script can look at what it declared.
        val declared = LuaTable()
        val meta = LuaTable()
        meta.set("__newindex", luaFunction("__newindex") { args ->
            // Called as __newindex(table, key, value).
            val key = args.stringOr(2, "")
            val value = args.stringOr(3, "")
            if (key == "target") {
                // An error the script can see, rather than a target silently
                // ignored: a misspelled one would otherwise make the script
                // vanish from every picker with nothing to explain why.
                session.declareTarget(value)?.let { LuaValue.error(it) }
            }
            declared.set(key, value)
            LuaValue.NIL
        })
        // The one `__index` for `glyph`, and therefore the only place a key
        // that is not a real field is resolved. Both halves live here because
        // a table can carry only one metatable: live state first, then the
        // script's own declarations.
        //
        // LuaJ calls a function-valued `__index` as __index(table, key), so the
        // key is argument *2*. Reading argument 1 instead looks the `glyph`
        // table itself up in `declared`, which is why reading `glyph.target`
        // back after declaring it would come back nil.
        //
        // Live state is consulted first so a declaration cannot shadow a
        // built-in: a script that assigns `glyph.running` must not be able to
        // turn `while glyph.running do` into a test that is always true.
        meta.set("__index", luaFunction("__index") { args ->
            val key = args.stringOr(2, "")
            val live = liveValueOf(key)
            if (live.isnil()) declared.get(LuaValue.valueOf(key)) else live
        })
        glyph.setmetatable(meta)

        globals.set("glyph", glyph)
    }

    // region Session drawing helpers

    private fun ScriptSession.sweep(
        channels: List<Int>,
        stepMs: Long,
        brightness: Int,
        reverse: Boolean
    ) {
        if (channels.isEmpty()) return
        val order = if (reverse) channels.reversed() else channels
        for (i in order.indices) {
            draw(order.take(i + 1), brightness, stepMs)
        }
        blank()
    }

    /**
     * A travelling wave: [trail] segments behind the head stay lit at a
     * fraction of [brightness], which is what gives a wave its motion.
     */
    private fun ScriptSession.wave(
        channels: List<Int>,
        stepMs: Long,
        brightness: Int,
        trail: Int
    ) {
        if (channels.isEmpty()) return
        val tail = trail.coerceIn(0, channels.size)
        for (i in channels.indices) {
            val start = (i - tail).coerceAtLeast(0)
            for (j in start..i) {
                val head = i - j
                val level = (brightness * (1.0 - 0.75 * head / (tail + 1))).toInt()
                draw(listOf(channels[j]), level, 0L)
            }
            blank()
            sleep(stepMs)
        }
    }

    private fun ScriptSession.spiral(stepMs: Long, brightness: Int) {
        val order = group("spiral").orEmpty()
        if (order.isEmpty()) return
        order.indices.forEach { i -> draw(order.take(i + 1), brightness, stepMs) }
        blank()
        order.indices.reversed().forEach { i -> draw(order.take(i + 1), brightness, stepMs) }
        blank()
    }

    private fun ScriptSession.heartbeat(cycles: Int, beatMs: Long, gapMs: Long, brightness: Int) {
        val all = group("all").orEmpty()
        repeat(cycles.coerceAtLeast(1)) {
            draw(all, brightness, beatMs)
            blank()
            sleep(gapMs)
            draw(all, brightness, beatMs)
            blank()
            sleep(beatMs + gapMs)
        }
    }

    // endregion

    // region Argument helpers

    private fun ease(t: Double, curve: String): Double {
        val x = t.coerceIn(0.0, 1.0)
        return when (curve) {
            "linear" -> x
            "in" -> x * x
            "out" -> 1.0 - (1.0 - x).pow(2)
            "inout" -> if (x < 0.5) 2 * x * x else 1.0 - (-2 * x + 2).pow(2) / 2
            "bounce" -> {
                val n = 7.5625
                val d = 2.75
                when {
                    x < 1 / d -> n * x * x
                    x < 2 / d -> n * (x - 1.5 / d).pow(2) + 0.75
                    x < 2.5 / d -> n * (x - 2.25 / d).pow(2) + 0.9375
                    else -> n * (x - 2.625 / d).pow(2) + 0.984375
                }
            }
            "wave" -> (1 - cos(x * 2 * Math.PI)) / 2
            "pulse" -> abs(sin(x * Math.PI))
            else -> x
        }
    }

    private fun toLuaArray(values: List<Int>): LuaTable {
        val table = LuaTable()
        values.forEachIndexed { index, value -> table.set(index + 1, LuaValue.valueOf(value)) }
        return table
    }

    private fun toLuaArray(values: FloatArray): LuaTable {
        val table = LuaTable()
        values.forEachIndexed { index, value ->
            table.set(index + 1, LuaValue.valueOf(value.toDouble()))
        }
        return table
    }

    // endregion

    /**
     * Wraps a Kotlin lambda as a Lua function.
     *
     * Errors become Lua errors so the script gets a message that points at the
     * offending call; a [ScriptAbortedError] is rethrown, because the watchdog
     * must not be catchable from inside a script.
     *
     * @param prefix what the function is called *through*, so a failure is
     *   reported as the author wrote it. `glyph.set` keeps the default; a
     *   module passes its own name and gets `glyph.util.clamp: …` rather than
     *   a bare `glyph.clamp`, which would point at an `__index` nobody called.
     *
     *   Ahead of [block] rather than after it: Kotlin binds a trailing lambda
     *   to the *last* parameter, so a third parameter in last place would stop
     *   every `luaFunction("set") { … }` in this file from compiling.
     */
    fun luaFunction(
        name: String,
        prefix: String = "glyph",
        block: (Array<LuaValue>) -> LuaValue
    ): VarArgFunction =
        object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val values = Array(args.narg()) { args.arg(it + 1) }
                return try {
                    block(values)
                } catch (e: ScriptAbortedError) {
                    throw e
                } catch (e: Exception) {
                    error("$prefix.$name: ${e.message ?: e::class.java.simpleName}")
                }
            }

            override fun toString(): String = "$prefix.$name"
        }
}

/**
 * A battery bar along the C strip, the same shape the charging animation uses.
 *
 * Top level rather than a member of [GlyphLuaApi] because `glyph.battery.bar`
 * has to draw exactly what `glyph.batteryBar` draws, and two copies of this
 * loop would be two places for the two to drift apart.
 */
internal fun ScriptSession.drawBatteryBar(percent: Int, durationMs: Long) {
    val bar = group("c").orEmpty()
    if (bar.isEmpty() || durationMs <= 0) return
    val target = (percent.coerceIn(0, 100) / 100f * bar.size).toInt()
    val perStep = (durationMs / bar.size).coerceAtLeast(1L)
    bar.indices.forEach { i ->
        draw(bar.take((i + 1).coerceAtMost(target)), GLYPH_MAX_BRIGHTNESS, perStep)
    }
    blank()
}

/**
 * Reaches for a channel list in several shapes, so scripts stay readable:
 * `{ 1, 2, 3 }`, a bare channel number, or a group name such as `"c"`.
 */
private fun ScriptSession.channelsOf(args: Array<LuaValue>, index: Int): List<Int> {
    val value = args.raw(index) ?: return emptyList()

    if (value.isnil()) return emptyList()
    if (value.isint()) return listOf(value.toint())
    if (value.istable()) {
        val table = value.checktable()
        return (1..table.length().toInt()).map { table.get(it).toint() }
    }
    if (value.isstring()) {
        val group = group(value.tojstring())
        if (group == null) {
            LuaValue.error("unknown channel group '${value.tojstring()}'")
            return emptyList()
        }
        return group
    }

    LuaValue.error("expected a channel list, a channel number or a group name")
    return emptyList()
}

private fun brightnessOf(args: Array<LuaValue>, index: Int): Int {
    val value = args.getOrNull(index - 1) ?: return GLYPH_MAX_BRIGHTNESS
    if (value.isnil()) return GLYPH_MAX_BRIGHTNESS
    return value.toint().coerceIn(0, GLYPH_MAX_BRIGHTNESS)
}

private fun holdOf(args: Array<LuaValue>, index: Int): Long = msOf(args, index, 0L)

private fun msOf(args: Array<LuaValue>, index: Int, default: Long): Long {
    val value = args.raw(index) ?: return default
    if (value.isnil()) return default
    return value.tolong()
}

/** Positional argument access that treats a missing slot as `nil`, like Lua. */
private fun Array<LuaValue>.raw(index: Int): LuaValue? = getOrNull(index - 1)

private fun Array<LuaValue>.intOr(index: Int, default: Int): Int {
    val value = raw(index) ?: return default
    if (value.isnil()) return default
    return value.toint()
}

private fun Array<LuaValue>.boolOr(index: Int, default: Boolean): Boolean {
    val value = raw(index) ?: return default
    if (value.isnil()) return default
    return value.toboolean()
}

private fun Array<LuaValue>.doubleOr(index: Int, default: Double): Double {
    val value = raw(index) ?: return default
    if (value.isnil()) return default
    return value.todouble()
}

private fun Array<LuaValue>.stringOr(index: Int, default: String): String {
    val value = raw(index) ?: return default
    if (value.isnil()) return default
    return value.tojstring()
}
