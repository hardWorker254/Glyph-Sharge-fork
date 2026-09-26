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

        // ── Constants ────────────────────────────────────────────────────────
        glyph.set("MAX", LuaValue.valueOf(GLYPH_MAX_BRIGHTNESS))
        glyph.set("device", LuaValue.valueOf(session.deviceName()))

        // ── Channel groups ───────────────────────────────────────────────────
        val channels = LuaTable()
        session.groupNames().forEach { name ->
            channels.set(name, toLuaArray(session.group(name).orEmpty()))
        }
        glyph.set("ch", channels)

        // ── Live state ────────────────────────────────────────────────────────
        // These change while a script runs, so they cannot be plain table
        // fields: they are resolved on every read through the `__index`
        // metamethod installed at the bottom of this function.
        //
        // They are values, not functions, on purpose. A function value is
        // truthy in Lua, which would make `while glyph.running do` spin until
        // the duration limit instead of stopping when the user presses Stop.
        val live = LuaTable()
        live.set("__index", luaFunction("__index") { args ->
            // Called as __index(table, key): the key is the second argument.
            when (args.stringOr(2, "")) {
                "running" -> LuaValue.valueOf(session.isRunning())
                "battery" -> LuaValue.valueOf(session.batteryPercent())
                "charging" -> LuaValue.valueOf(session.isCharging())
                else -> LuaValue.NIL
            }
        })
        glyph.setmetatable(live)

        glyph.set("time", luaFunction("time") { LuaValue.valueOf(session.elapsedMs().toInt()) })
        glyph.set("frame", luaFunction("frame") { LuaValue.valueOf(session.frames) })

        // ── Drawing ──────────────────────────────────────────────────────────
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

        // ── Movement ─────────────────────────────────────────────────────────
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
                session.batteryBar(args.intOr(1, session.batteryPercent()), msOf(args, 2, 2_000L))
                LuaValue.NIL
            }
        )

        // ── Randomness and maths ─────────────────────────────────────────────
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

        // ── Control and output ───────────────────────────────────────────────
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

    /** A battery bar along the C strip, the same shape the charging animation uses. */
    private fun ScriptSession.batteryBar(percent: Int, durationMs: Long) {
        val bar = group("c").orEmpty()
        if (bar.isEmpty() || durationMs <= 0) return
        val target = (percent.coerceIn(0, 100) / 100f * bar.size).toInt()
        val perStep = (durationMs / bar.size).coerceAtLeast(1L)
        bar.indices.forEach { i ->
            draw(bar.take((i + 1).coerceAtMost(target)), GLYPH_MAX_BRIGHTNESS, perStep)
        }
        blank()
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

    // endregion

    /**
     * Wraps a Kotlin lambda as a Lua function.
     *
     * Errors become Lua errors so the script gets a message that points at the
     * offending call; a [ScriptAbortedError] is rethrown, because the watchdog
     * must not be catchable from inside a script.
     */
    fun luaFunction(name: String, block: (Array<LuaValue>) -> LuaValue): VarArgFunction =
        object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val values = Array(args.narg()) { args.arg(it + 1) }
                return try {
                    block(values)
                } catch (e: ScriptAbortedError) {
                    throw e
                } catch (e: Exception) {
                    LuaValue.error("glyph.$name: ${e.message ?: e::class.java.simpleName}")
                }
            }

            override fun toString(): String = "glyph.$name"
        }
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
