package com.bleelblep.glyphsharge.glyph.script

import android.util.Log
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.script.module.GlyphTimeModule
import com.bleelblep.glyphsharge.triggers.TriggerEvent
import com.bleelblep.glyphsharge.triggers.TriggerType
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.jse.JsePlatform

/** Whether a [ServiceScriptEngine.load] succeeded, and if not, why not. */
data class ServiceLoadResult(
    val status: ScriptStatus,
    val message: String? = null,
) {
    val isSuccess: Boolean get() = status == ScriptStatus.COMPLETED
}

/**
 * Runs a `.servicelua` script: a long-lived rule that says *when* to fire and
 * lets the app decide *what* lights up.
 *
 * This is the Phase E runtime the rule docs have been pointing at. It is a
 * separate engine from [LuaScriptEngine] on purpose, for two reasons that are
 * really one:
 *
 *  1. **A different lifetime.** [LuaScriptEngine.run] is built around one
 *     bounded animation — it blocks on the calling thread for a capped
 *     duration and, in a `finally`, blanks the strip and closes the session. A
 *     service script has no such shape: it loads *once* and then answers events
 *     for as long as its rule is on. So the model here is **registration**:
 *     [load] compiles the source, runs it as an initialization chunk that
 *     calls `glyph.on(...)` to file handlers and returns, and the globals stay
 *     alive afterwards. [dispatch] invokes a filed handler when its trigger
 *     fires, and [stop] tears the whole thing down. Nothing ever blocks waiting
 *     for an event — that is what keeps it clear of the watchdog's duration
 *     rule, of the banned `coroutine` global and of holding the strip while
 *     idle.
 *
 *  2. **A smaller surface.** A `.servicelua` is forbidden from drawing: it
 *     cannot call `glyph.set` or any of its siblings. The whole point is that
 *     the *app* resolves which animation plays (from the rule's `animationId`),
 *     so the script's only way to light anything is [glyphPlayAnimation]. That
 *     is enforced structurally, not by a check — [installServiceApi] simply
 *     never installs the drawing half of the API, so `glyph.set` reads as `nil`
 *     and a script that reaches for it gets "attempt to call a nil value"
 *     rather than a strip it should not be driving.
 *
 * ### The service `glyph` surface
 *
 * | Added | Why |
 * |-------|-----|
 * | `glyph.on(triggerId, fn)` | file a handler for a [TriggerType.id] |
 * | `glyph.playAnimation([ms])` | fire the owning rule's animation |
 * | `glyph.log(msg)`, `print` | output |
 * | `glyph.device`, `glyph.elapsed()` | orientation |
 * | `glyph.battery`, `glyph.charging` | read-only, live via `__index`; **[ScriptCapability.DEVICE_STATE]** |
 * | `glyph.hour`, `glyph.isNight`, `glyph.DAY` | the wall clock, read-only and live |
 *
 * The clock is the one piece of read-only context a service gets for free:
 * it needs no capability, because `require` needed none either — and unlike
 * `require`, a flat field is available here at all, since `require` is banned
 * in this sandbox. A service that wanted the hour used to have no way to ask
 * for it.
 *
 * Everything the animation API can *do to the strip* — `set`, `hold`, `sweep`,
 * `ch`, `group`, `audio`, sensor and net modules — is absent. The sandbox
 * (banned globals, string-metatable isolation, instruction watchdog) mirrors
 * [LuaScriptEngine] and is kept in step with it: both are copy-pastes of the
 * same security list, and a change to one belongs in the other.
 *
 * ### Capabilities
 *
 * The state-reading half is not unconditionally present: it is behind
 * [ScriptCapability.DEVICE_STATE], and [granted] answers whether the user
 * allowed it for *this* script. The gating is structural, like everything
 * else here — the `__index` branches for `battery` and `charging` are simply
 * not installed when the grant is missing, so an ungranted script reads `nil`
 * and never sees a check it could argue with. Deny-by-default: [granted]
 * defaults to "no", and every production caller passes the user's decisions.
 *
 * ### Not wired yet
 *
 * Storage for `.servicelua` files, the constructor's advanced mode and the
 * [RuleHostService] attach/dispatch loop are later slices. This is the core:
 * load a script, learn what it subscribes to, dispatch events to it, stop it —
 * each independently testable without a device.
 */
class ServiceScriptEngine(
    private val profile: DeviceProfile,
    private val host: GlyphScriptHost,
    /**
     * The user's per-script capability decisions, consulted once at [load].
     *
     * A capability not granted here is not installed into the Lua table at
     * all — see the class KDoc. Deny-by-default; the host passes the answers
     * from [com.bleelblep.glyphsharge.data.ScriptPermissionStore], tests pass
     * `{ true }` or a fixed set.
     */
    private val granted: (ScriptCapability) -> Boolean = { false },
    /**
     * What [glyphPlayAnimation] calls when the script fires. The engine never
     * picks an animation: the *app* resolves it from the owning rule, which is
     * why the Lua call carries no id. Injected so a test can record the fire
     * instead of lighting hardware.
     *
     * [durationMs] is the script's optional override, or `null` to use the
     * rule's own duration.
     */
    private val onFireAnimation: (durationMs: Long?) -> Unit,
) {
    private companion object {
        const val TAG = "ServiceScriptEngine"

        /** How often the watchdog runs, in VM instructions. */
        const val INSTRUCTION_INTERVAL = 20_000

        /** Chosen as a string a script has no use for; the value is never read. */
        const val STRING_METATABLE_LOCK = "glyph.string.protected"

        const val WRAPPER_OPEN = "local function __glyph_main()\n"
        const val WRAPPER_CLOSE = "\nend\n__glyph_main()\n"

        /**
         * Names blanked out of the globals table, exactly as
         * [LuaScriptEngine] blanks them. Duplicated rather than shared so a
         * change to the animation engine cannot silently reach this one (or
         * vice versa) — but the two lists mean the same thing and must move
         * together.
         */
        val BANNED_GLOBALS = arrayOf(
            "io", "os", "package", "require", "module",
            "dofile", "loadfile", "load", "loadstring",
            "luajava", "coroutine", "collectgarbage", "newproxy",
        )
    }

    /** The live session behind the current script, or `null` when stopped. */
    private var session: ScriptSession? = null

    /** The sandbox that outlives [load], so handlers can still run. */
    private var globals: Globals? = null

    /** Handlers filed by `glyph.on`, keyed by [TriggerType.id]. */
    private val handlers = mutableMapOf<String, MutableList<LuaValue>>()

    // region Lifecycle

    /**
     * Compiles [source], runs it once as a registration chunk, and keeps the
     * sandbox alive for [dispatch].
     *
     * Must be followed by [stop] when the owning rule goes away, or the
     * session (and anything it registered, such as a sensor reference) is held
     * for the life of the process.
     */
    fun load(source: String): ServiceLoadResult {
        if (session != null) {
            return ServiceLoadResult(
                ScriptStatus.RUNTIME_ERROR,
                "a script is already loaded; call stop() first",
            )
        }

        val current = ScriptSession(
            profile = profile,
            host = host,
            // No wall-clock limit: a service lives as long as its rule, not for
            // a fixed cap. The instruction and heap watchdogs still apply.
            maxDurationMs = 0L,
            nowMs = ::now,
        )

        val env = try {
            buildServiceGlobals(current)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot create the Lua environment", e)
            current.close()
            return ServiceLoadResult(ScriptStatus.RUNTIME_ERROR, e.message ?: "Lua setup failed")
        }

        val chunk = try {
            env.load(WRAPPER_OPEN + source + "\n" + WRAPPER_CLOSE, "=glyph-service")
        } catch (e: Exception) {
            Log.w(TAG, "Service script rejected by the parser: ${e.message}")
            current.close()
            return ServiceLoadResult(
                ScriptStatus.SYNTAX_ERROR,
                e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Syntax error",
            )
        }

        installWatchdog(env, current)
        session = current
        globals = env

        return try {
            // The registration chunk runs here, on the calling thread, and is
            // expected to return: it files `glyph.on` handlers and stops. A
            // script that never returns would trip the instruction watchdog.
            chunk.call()
            ServiceLoadResult(ScriptStatus.COMPLETED)
        } catch (e: ScriptAbortedError) {
            stop()
            ServiceLoadResult(ScriptStatus.STOPPED, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "Service script registration failed", e)
            stop()
            ServiceLoadResult(ScriptStatus.RUNTIME_ERROR, describe(e))
        } catch (e: Error) {
            Log.e(TAG, "Service script exhausted a VM resource", e)
            stop()
            ServiceLoadResult(ScriptStatus.RUNTIME_ERROR, e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * The triggers this script filed handlers for — the set a caller must
     * attach through [com.bleelblep.glyphsharge.triggers.TriggerSources] for
     * [dispatch] to ever be reached.
     *
     * Empty before [load]. A trigger the palette cannot build will simply never
     * be dispatched; the host decides whether that is worth a warning.
     */
    fun subscriptions(): Set<TriggerType> =
        handlers.keys.mapNotNull { TriggerType.fromId(it) }.toSet()

    /**
     * Runs the handlers filed for [event]'s trigger.
     *
     * Called by the host when a [TriggerSource][com.bleelblep.glyphsharge.triggers.TriggerSource]
     * fires. A handler that raises is logged and skipped so one broken rule
     * cannot take the others down; a [ScriptAbortedError] still propagates,
     * because it means the session itself was told to stop.
     *
     * A handler typically calls `glyph.playAnimation()`, which re-enters
     * [onFireAnimation] synchronously on this thread.
     */
    fun dispatch(event: TriggerEvent) {
        val current = session ?: return
        val targets = handlers[event.type.id]?.toList() ?: return
        for (handler in targets) {
            try {
                handler.call()
            } catch (e: ScriptAbortedError) {
                throw e
            } catch (e: Exception) {
                current.log(
                    "handler for '${event.type.id}' failed: ${describe(e)}",
                    LogLevel.ERROR,
                )
            } catch (e: Error) {
                current.log(
                    "handler for '${event.type.id}' failed: ${e.message ?: e::class.java.simpleName}",
                    LogLevel.ERROR,
                )
            }
        }
    }

    /** Drops the handlers and closes the session. Safe to call twice. */
    fun stop() {
        handlers.clear()
        session?.close()
        session = null
        globals = null
    }

    // endregion

    // region Sandbox

    /**
     * Assembles the sandbox: standard globals, pruned to [BANNED_GLOBALS],
     * string metatable isolated, and the reduced service `glyph` installed.
     *
     * `DebugLib` first, exactly as [LuaScriptEngine] does it — the VM only
     * consults a count hook while `Globals.debuglib` is set.
     */
    private fun buildServiceGlobals(current: ScriptSession): Globals {
        val globals = JsePlatform.standardGlobals()
        globals.load(DebugLib())

        BANNED_GLOBALS.forEach { globals[it] = LuaValue.NIL }

        isolateStringMetatable(globals)

        installServiceApi(globals, current)

        globals["print"] = GlyphLuaApi.luaFunction("print") { args ->
            current.log(args.joinToString(" ") { it.tojstring() })
            LuaValue.NIL
        }

        globals["debug"] = LuaValue.NIL
        return globals
    }

    /**
     * Publishes the service `glyph` table: subscribe, fire, and read-only
     * context — and nothing that touches the strip.
     */
    private fun installServiceApi(globals: Globals, current: ScriptSession) {
        val glyph = LuaTable()

        glyph["device"] = LuaValue.valueOf(current.deviceName())
        glyph["elapsed"] = GlyphLuaApi.luaFunction("elapsed") {
            LuaValue.valueOf(current.elapsedMs().toInt())
        }
        glyph["log"] = GlyphLuaApi.luaFunction("log") { args ->
            current.log(args.getOrNull(0)?.tojstring() ?: "")
            LuaValue.NIL
        }

        // glyph.on(triggerId, fn): file a handler under a known trigger id.
        glyph["on"] = GlyphLuaApi.luaFunction("on") { args ->
            val name = args.getOrNull(0)
            if (name == null || !name.isstring()) {
                throw IllegalArgumentException(
                    "expected a trigger name, e.g. glyph.on(\"screen_off\", fn)",
                )
            }
            val type = TriggerType.fromId(name.tojstring())
                ?: throw IllegalArgumentException("unknown trigger '${name.tojstring()}'")
            val handler = args.getOrNull(1)
            if (handler == null || !handler.isfunction()) {
                throw IllegalArgumentException("expected a handler function for '${type.id}'")
            }
            handlers.getOrPut(type.id) { mutableListOf() }.add(handler)
            LuaValue.NIL
        }

        // glyph.playAnimation([durationMs]): fire, with no id — the app picks
        // the animation from the rule that owns this script.
        glyph["playAnimation"] = GlyphLuaApi.luaFunction("playAnimation") { args ->
            val duration = args.getOrNull(0)
            val durationMs = if (duration != null && duration.isnumber()) duration.tolong() else null
            onFireAnimation(durationMs)
            LuaValue.NIL
        }

        // The three levels of the day, same values an animation gets. Plain
        // fields: they do not move, so nothing here has to consult the clock.
        GlyphTimeModule.constants().forEach { (key, value) ->
            glyph[key] = LuaValue.valueOf(value)
        }

        // `battery` and `charging` are live, so they resolve through __index
        // rather than being frozen at load — and only when the user granted
        // DEVICE_STATE for this script: the branches are decided here, at
        // table-build time, so an ungranted key reads as nil with no check in
        // the hot path to bypass.
        //
        // The wall clock shares this path because it moves too, but takes no
        // grant: an hour is not the state the capability exists to guard, and
        // `require("glyph.time")` never asked for one either. A key this does
        // not know — `set`, `hold`, anything — falls through to nil, which is
        // the whole point.
        val canReadState = granted(ScriptCapability.DEVICE_STATE)
        val clock = GlyphTimeModule.liveFields(current)
        val metatable = LuaTable()
        metatable[LuaValue.INDEX] = GlyphLuaApi.luaFunction("__index") { args ->
            val key = args.getOrNull(1)?.tojstring() ?: ""
            when (key) {
                "battery" -> if (canReadState) LuaValue.valueOf(current.batteryPercent()) else LuaValue.NIL
                "charging" -> if (canReadState) LuaValue.valueOf(current.isCharging()) else LuaValue.NIL
                else -> clock[key]?.invoke() ?: LuaValue.NIL
            }
        }
        glyph.setmetatable(metatable)

        globals["glyph"] = glyph
    }

    /**
     * Arms the watchdog on [env]'s running thread — the same mechanism
     * [LuaScriptEngine] uses, so an infinite loop in a registration chunk or
     * inside a handler still trips the instruction budget and the heap ceiling.
     */
    private fun installWatchdog(env: Globals, current: ScriptSession) {
        val state = env.running.state
        state.hookfunc = GlyphLuaApi.luaFunction("watchdog") {
            current.watchdogReason()?.let { throw ScriptAbortedError(it) }
            LuaValue.NIL
        }
        state.hookcount = INSTRUCTION_INTERVAL
    }

    /**
     * Takes the process-wide string metatable away from the script.
     *
     * Byte-for-byte the same defence [LuaScriptEngine] applies: LuaJ keeps one
     * `LuaString.s_metatable` static for every sandbox in the process, so a
     * service script that reached it could vandalise the animations' strings
     * too. See that engine's KDoc for the full reasoning.
     */
    private fun isolateStringMetatable(globals: Globals) {
        val index = LuaTable()
        val library = globals["string"] as? LuaTable
        if (library != null) {
            for (key in library.keys()) index[key] = library[key]
        }

        val metatable = LuaTable()
        metatable[LuaValue.INDEX] = index
        metatable[LuaValue.METATABLE] = LuaValue.valueOf(STRING_METATABLE_LOCK)
        LuaString.s_metatable = metatable
    }

    // endregion

    private fun now(): Long = System.nanoTime() / 1_000_000L

    /** LuaJ wraps a thrown Java exception in a `LuaError`, one level down. */
    private fun describe(e: Exception): String =
        e.cause?.message ?: e.message ?: e::class.java.simpleName
}
