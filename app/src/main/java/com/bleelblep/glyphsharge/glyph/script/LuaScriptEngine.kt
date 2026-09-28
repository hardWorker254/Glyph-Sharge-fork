package com.bleelblep.glyphsharge.glyph.script

import android.util.Log
import com.bleelblep.glyphsharge.glyph.audio.AudioBand
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaThread
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.jse.JsePlatform

/** Why a script run ended. */
enum class ScriptStatus {
    /** The script ran to its end, or called `glyph.exit()`. */
    COMPLETED,

    /** Another animation or a feature took the strip. */
    STOPPED,

    /** The feature's Duration setting elapsed. */
    TIMED_OUT,

    /** The file did not parse — nothing was drawn. */
    SYNTAX_ERROR,

    /** A Lua error, or a glyph API call with bad arguments. */
    RUNTIME_ERROR
}

/** Outcome of one [LuaScriptEngine.run] call, for the studio's console. */
data class ScriptRunResult(
    val status: ScriptStatus,
    val message: String? = null,
    val frames: Int = 0,
    val elapsedMs: Long = 0L,
    /**
     * What the script declared through `glyph.target`, or `null` when it
     * declared nothing.
     *
     * Reported rather than acted on: the run has already happened by the time
     * it is known, so this is for the studio console and for the animation
     * list to catch up on, not for routing the run.
     */
    val target: ScriptTarget? = null
) {
    val isSuccess: Boolean get() = status == ScriptStatus.COMPLETED
}

/**
 * The hardware surface a script is allowed to touch.
 *
 * The engine never sees [com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer]
 * itself — it gets this narrow interface, which keeps the engine testable and
 * makes it obvious what a script can actually do: draw, blank, and read the
 * battery. Nothing else exists on the other side.
 */
interface GlyphScriptHost {
    /**
     * Lights [channels] at [brightness] and leaves them lit.
     *
     * Timing is deliberately *not* a parameter: the session owns every wait, so
     * that a stop request is noticed within milliseconds instead of at the end
     * of a long hold.
     */
    fun draw(channels: List<Int>, brightness: Int)

    /** Turns every LED off. */
    fun blank()

    fun batteryPercent(): Int

    fun isCharging(): Boolean

    // region Audio
    //
    // Every one of these has a default that reads as "no music": the studio
    // runs scripts with no capture open, and a script must not have to ask
    // whether the service is there before it can be tested.

    /** Whether a capture is currently feeding [audioBands]. */
    fun isAudioActive(): Boolean = false

    /** Overall level, `0..1`. */
    fun audioLevel(): Float = 0f

    /** Low, middle or high spectrum energy, `0..1`. */
    fun audioBand(band: AudioBand): Float = 0f

    /** `true` on the single frame a beat was detected. */
    fun audioBeat(): Boolean = false

    /** The spectrum resampled to [count] values, `0..1` each. */
    fun audioBands(count: Int): FloatArray = FloatArray(count.coerceAtLeast(0))

    // endregion
}

/**
 * Raised inside the VM when a script must stop: the user pressed stop, the
 * duration elapsed, or `glyph.exit()` was called.
 *
 * A [Error] rather than an [Exception] on purpose — LuaJ converts it at the
 * `LuaThread.resume` boundary, so a script cannot swallow it with `pcall` and
 * keep burning CPU.
 */
internal class ScriptAbortedError(message: String) : Error(message)

/**
 * Runs a user-written Lua script against the glyph.
 *
 * ### The sandbox
 *
 * Lua scripts arrive from outside the app, so the environment is built by
 * subtraction: the standard LuaJ globals are loaded and then every capability
 * that could touch the file system, the process, the Java heap or the host's
 * own instruction hook is removed.
 *
 * | Removed | Why |
 * |---------|-----|
 * | `io`, `os` | file system, process control |
 * | `luajava` | full reflection back into the app — the real escape hatch |
 * | `load`, `loadstring`, `dofile`, `loadfile`, `require` | compiles or loads code at runtime |
 * | `package` | module loading |
 * | `coroutine` | hook state lives on the thread, so a fresh coroutine would run unchecked |
 * | `collectgarbage` | lets a script stall the VM |
 * | `debug` | a script could call `debug.sethook()` and remove the watchdog |
 *
 * `DebugLib` itself *is* loaded, because without it no hook can fire at all.
 * The library is installed, the watchdog hook is attached, and the table is
 * then set to `nil` so the script cannot see or remove it.
 *
 * ### The watchdog
 *
 * A script is arbitrary code driving hardware from a foreground service, so an
 * accidental `while true do end` must not wedge the glyph forever. Two limits
 * are enforced, and both are checked from a `debug.sethook` count hook rather
 * than from the script's own code:
 *
 *  * **wall clock** — the feature's duration, or the studio's preview limit;
 *  * **instructions** — a ceiling on total executed bytecode, which also stops
 *    a script that burns CPU without ever touching the clock.
 *
 * The count hook is the only mechanism that interrupts a tight loop: call,
 * return and line events are never generated by one.
 */
class LuaScriptEngine(
    private val profile: DeviceProfile,
    private val host: GlyphScriptHost
) {
    private companion object {
        const val TAG = "LuaScriptEngine"

        /** How often the watchdog runs, in VM instructions. */
        const val INSTRUCTION_INTERVAL = 20_000

        /** Hard ceiling on executed instructions for a single run. */
        const val INSTRUCTION_BUDGET = 200_000_000L

        /** Fallback run length when no duration was supplied. */
        const val DEFAULT_DURATION_MS = 5_000L

        /**
         * The user's source runs as the body of a function, so `local`
         * declarations behave the way a script author expects and a top-level
         * `return` is legal. The wrapper calls it immediately, so the file has
         * no required structure at all.
         */
        const val WRAPPER_OPEN = "local function __glyph_main()\n"
        const val WRAPPER_CLOSE = "\nend\n__glyph_main()\n"

        /**
         * Names blanked out of the globals table before the script sees it.
         * See the class KDoc for why each one goes.
         */
        val BANNED_GLOBALS = arrayOf(
            "io", "os", "package", "require", "module",
            "dofile", "loadfile", "load", "loadstring",
            "luajava", "coroutine", "collectgarbage", "newproxy"
        )
    }

    /** The run in progress, so [stop] can reach it from another thread. */
    @Volatile
    private var activeSession: ScriptSession? = null

    /**
     * Compiles and runs [source] once, on the calling thread.
     *
     * The caller is expected to be on a background dispatcher, because
     * [GlyphScriptHost] implementations block while the glyph is being driven.
     *
     * @param maxDurationMs wall-clock limit; the run also ends earlier if the
     *   script returns on its own, calls `glyph.exit()`, or [stop] is called.
     */
    fun run(source: String, maxDurationMs: Long = DEFAULT_DURATION_MS): ScriptRunResult {
        val startedAt = now()
        val current = ScriptSession(profile, host, maxDurationMs, ::now)
        activeSession = current

        val env = try {
            buildGlobals(current)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot create the Lua environment", e)
            activeSession = null
            return ScriptRunResult(ScriptStatus.RUNTIME_ERROR, e.message ?: "Lua setup failed")
        }

        val wrapped = WRAPPER_OPEN + source + "\n" + WRAPPER_CLOSE
        val chunk = try {
            // `Globals.load` compiles and returns the closure without running
            // it, which is what lets the watchdog hook be attached to the
            // thread before a single instruction executes.
            env.globals.load(wrapped, "=glyph-animation")
        } catch (e: Exception) {
            Log.w(TAG, "Script rejected by the parser: ${e.message}")
            activeSession = null
            return ScriptRunResult(
                ScriptStatus.SYNTAX_ERROR,
                e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Syntax error"
            )
        }

        val thread = LuaThread(env.globals, chunk)
        installWatchdog(thread, current)

        return try {
            val outcome = thread.resume(LuaValue.NIL)
            current.result(outcome, now() - startedAt)
        } catch (e: ScriptAbortedError) {
            current.result(null, now() - startedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Script run failed", e)
            ScriptRunResult(
                ScriptStatus.RUNTIME_ERROR,
                describe(e),
                current.frames,
                now() - startedAt,
                current.target()
            )
        } finally {
            activeSession = null
            // Whatever happened, the strip must not be left half-lit.
            runCatching { host.blank() }
        }
    }

    /** Compiles [source] without running it. Backs the editor's Check button. */
    fun validate(source: String): String? = try {
        val env = buildGlobals(ScriptSession(profile, host, 0L, ::now))
        env.globals.load(WRAPPER_OPEN + source + "\n" + WRAPPER_CLOSE, "=check")
        null
    } catch (e: Exception) {
        e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Syntax error"
    }

    /** Stops the current run. Safe to call from the main thread at any time. */
    fun stop(reason: String = "another animation took the glyph") {
        activeSession?.requestExternalStop(reason)
    }

    // region Sandbox

    /** A prepared sandbox: the globals table, ready to run a chunk. */
    private class LuaEnvironment(val globals: Globals)

    /**
     * Assembles the sandbox.
     *
     * Order matters. `DebugLib` is loaded **first**: the VM only consults a
     * hook while `Globals.debuglib` is set, and loading it needs parts of the
     * standard globals that are about to be removed. The `debug` table itself
     * is blanked afterwards, so a script can neither see nor clear the hook.
     */
    private fun buildGlobals(current: ScriptSession): LuaEnvironment {
        val globals = JsePlatform.standardGlobals()

        // Installs Globals.debuglib, which is what makes the VM call the
        // watchdog at all. Must happen before the globals are pruned.
        globals.load(DebugLib())

        BANNED_GLOBALS.forEach { globals.set(it, LuaValue.NIL) }

        GlyphLuaApi.install(globals, current)

        // `print` is the one output channel a script gets, and it goes to the
        // studio console rather than to stdout.
        globals.set(
            "print",
            GlyphLuaApi.luaFunction("print") { args ->
                current.log(args.joinToString(" ") { it.tojstring() })
                LuaValue.NIL
            }
        )

        // Used, then hidden: a script must not be able to clear the hook.
        globals.set("debug", LuaValue.NIL)
        return LuaEnvironment(globals)
    }

    /**
     * Arms the watchdog on [thread].
     *
     * The hook state is written straight onto the thread rather than through
     * `debug.sethook`, because that table has just been hidden from the script
     * — and because the VM reads `hookfunc`/`hookcount` from the running
     * thread's state on every instruction anyway.
     *
     * The hook throws a [ScriptAbortedError] rather than a Lua error: a Lua
     * error is catchable from inside the script, so `pcall` would swallow it
     * and the loop would keep running.
     */
    private fun installWatchdog(thread: LuaThread, current: ScriptSession) {
        thread.state.hookfunc = GlyphLuaApi.luaFunction("watchdog") {
            current.watchdogReason()?.let { throw ScriptAbortedError(it) }
            LuaValue.NIL
        }
        thread.state.hookcount = INSTRUCTION_INTERVAL
    }

    // endregion

    private fun now(): Long = System.nanoTime() / 1_000_000L

    /**
     * LuaJ wraps a Java exception thrown by a native function in a
     * `LuaError`, so the useful text is one level down.
     */
    private fun describe(e: Exception): String =
        e.cause?.message ?: e.message ?: e::class.java.simpleName
}
