package com.bleelblep.glyphsharge.glyph.script

import android.util.Log
import com.bleelblep.glyphsharge.glyph.audio.AudioBand
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.net.NetworkSnapshot
import com.bleelblep.glyphsharge.glyph.sensor.SensorControl
import com.bleelblep.glyphsharge.glyph.sensor.SensorSnapshot
import com.bleelblep.glyphsharge.glyph.script.module.ModuleRegistry
import com.bleelblep.glyphsharge.glyph.script.module.ModuleSourceScan
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaThread
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.jse.JsePlatform
import java.time.ZonedDateTime

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
    val target: ScriptTarget? = null,
    /**
     * What the script wrote — every `print` and every `glyph.log` — in the
     * order it wrote it and at the severity it wrote it.
     *
     * On the result rather than fetched from the session afterwards, because
     * the session is closed in the `finally` of [LuaScriptEngine.run] and
     * released from the engine's active slot on every path out. A caller that
     * asked for the lines once [run] had returned would be racing that
     * teardown, and would get an empty list on the runs that mattered most —
     * the ones that failed halfway through logging.
     *
     * Defaults to empty rather than being nullable so the many construction
     * sites that never run a script (no LED layout, no glyph on this phone)
     * do not each have to invent a value.
     */
    val logLines: List<ScriptLogLine> = emptyList(),
) {
    val isSuccess: Boolean get() = status == ScriptStatus.COMPLETED
}

/**
 * How bad a script is, as far as compiling it can tell.
 *
 * Split from [ScriptStatus] because the two answer different questions:
 * [ScriptStatus] is what a *run* did, while this is what a file *is*. The one
 * case worth separating is [MISSING_MODULE], which is a valid Lua file that
 * names something this build does not ship — reporting that to an author as a
 * syntax error sends them looking for a missing bracket that was never there.
 */
enum class ScriptCheckStatus {
    /** Compiles, and every `require` in it resolves to a module we ship. */
    OK,

    /** The file does not parse, so nothing else about it can be trusted yet. */
    SYNTAX_ERROR,

    /** Parses, but requires a module that is not in the registry. */
    MISSING_MODULE
}

/**
 * What [LuaScriptEngine.validate] found: the case, and the text to show for it.
 *
 * Shaped like [ScriptRunResult] — an enum plus an optional message — because
 * the studio needs to say something *different* for each case, and a bare
 * `String?` cannot tell it which of its messages to use. [message] is `null`
 * only for [ScriptCheckStatus.OK].
 */
data class ScriptCheckResult(
    val status: ScriptCheckStatus,
    val message: String? = null,
) {
    val isOk: Boolean get() = status == ScriptCheckStatus.OK

    companion object {
        /**
         * Nothing to report.
         *
         * Named rather than a constructor call at each site so the "all good"
         * case is impossible to spell wrong, and so the callers that cannot
         * check anything at all — a phone whose LED layout is unknown — can
         * hand back the same honest "no complaint" answer they always did.
         */
        val OK = ScriptCheckResult(ScriptCheckStatus.OK)
    }
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
 * | `load`, `loadstring`, `dofile`, `loadfile` | compiles or loads code at runtime |
 * | `require` | Lua's own, which searches the file system. Replaced immediately after by a `require` that resolves six in-memory modules and nothing else — see [ModuleRegistry] |
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
    private val host: GlyphScriptHost,
    /**
     * The wall clock every run sees, injectable so `glyph.time` can be tested
     * at a chosen hour. Defaults to the real one; nothing in the app passes
     * anything else.
     */
    private val wallClock: () -> ZonedDateTime = { ZonedDateTime.now() },
    /**
     * The network state every run sees, injectable so `glyph.net` can be tested
     * without arranging for the test machine to be on a VPN. Same reasoning as
     * [wallClock] above; defaults to the real one, and nothing in the app passes
     * anything else.
     */
    private val network: () -> NetworkSnapshot = { NetworkSnapshot.DISCONNECTED },
    /**
     * The accelerometer reading every run sees, injectable so `glyph.sensor`
     * can be tested without asking anyone to shake the machine. Same
     * reasoning as [wallClock] above; defaults to a still phone.
     */
    private val sensor: () -> SensorSnapshot = { SensorSnapshot.STILL },
    /**
     * What opens and closes the accelerometer for a run.
     *
     * A separate parameter from [sensor] for the reason given on
     * [ScriptSession.sensorControl]: reading a value and owning a listener are
     * different jobs, and a test that wants the first must not be made to
     * provide the second. Defaults to doing nothing, which is what every run
     * in this file's own tests gets.
     */
    private val sensorControl: SensorControl = SensorControl.NONE,
) {
    private companion object {
        const val TAG = "LuaScriptEngine"

        /** How often the watchdog runs, in VM instructions. */
        const val INSTRUCTION_INTERVAL = 20_000

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
         *
         * `require` is on this list and is then deliberately replaced by a
         * sandboxed one, so the list stays an honest record of what Lua's own
         * `require` can reach: the file system, via `package.path`. Leaving it
         * out would read as "scripts cannot require", which is no longer true.
         */
        val BANNED_GLOBALS = arrayOf(
            "io", "os", "package", "require", "module",
            "dofile", "loadfile", "load", "loadstring",
            "luajava", "coroutine", "collectgarbage", "newproxy",
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
        // Every argument named, not just for readability: this session takes
        // three injectable lambdas and only the last of them could ever take
        // a trailing lambda, so writing one positionally would bind it to
        // whichever happens to be declared last — and would keep compiling
        // after a parameter was added underneath it.
        val current = ScriptSession(
            profile = profile,
            host = host,
            maxDurationMs = maxDurationMs,
            nowMs = ::now,
            clock = wallClock,
            network = network,
            sensor = sensor,
            sensorControl = sensorControl,
        )
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
                e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Syntax error",
            )
        }

        val thread = LuaThread(env.globals, chunk)
        installWatchdog(thread, current)

        return try {
            val outcome = thread.resume(LuaValue.NIL)
            current.result(outcome, now() - startedAt).withScriptLog(current)
        } catch (_: ScriptAbortedError) {
            current.result(null, now() - startedAt).withScriptLog(current)
        } catch (e: Exception) {
            Log.e(TAG, "Script run failed", e)
            ScriptRunResult(
                ScriptStatus.RUNTIME_ERROR,
                describe(e),
                current.frames,
                now() - startedAt,
                current.target(),
                current.logLines(),
            )
        } catch (e: Error) {
            // The `Errors`, not just the `Exceptions`.
            //
            // The memory ceiling in `ScriptSession` is meant to stop a runaway
            // allocation before this happens, but it is a poll — a script can
            // outrun it between two watchdog ticks, and a deeply recursive
            // script can reach the stack limit without allocating a table at
            // all. Neither is caught by `catch (e: Exception)`, so before this
            // branch an `OutOfMemoryError` from a store-downloaded script
            // propagated out of a feature's foreground service and killed the
            // process, taking an unrelated feature with it.
            //
            // Reported as a runtime error so the studio shows the author what
            // happened, rather than the service dying where it stands.
            Log.e(TAG, "Script exhausted a VM resource", e)
            ScriptRunResult(
                ScriptStatus.RUNTIME_ERROR,
                describeError(e),
                current.frames,
                now() - startedAt,
                current.target(),
                current.logLines(),
            )
        } finally {
            activeSession = null
            // Whatever happened, the strip must not be left half-lit, and the
            // sensor listener must not outlive the run that asked for it. Both
            // on every path out of the try, including the watchdog abort: a
            // run killed by its instruction budget has usually read the sensor
            // many times, and an un-unregistered 50Hz listener on a phone whose
            // feature set is about battery is the exact failure this pairing
            // exists to prevent.
            runCatching { host.blank() }
            runCatching { current.close() }
        }
    }

    /**
     * Puts the script's own output on [this], read from [session] right now.
     *
     * Called on the way out of [run] and nowhere else, because this is the
     * last moment the session's log is reachable: the `finally` immediately
     * after it closes the session and clears the engine's active slot, and
     * the caller has never held a reference to either.
     */
    private fun ScriptRunResult.withScriptLog(session: ScriptSession): ScriptRunResult =
        copy(logLines = session.logLines())

    /**
     * Compiles [source] without running it. Backs the editor's Check button.
     *
     * Two findings, in a fixed order, and a typed answer for each:
     *
     *  1. **It does not parse.** Reported first, and alone. A file that is not
     *     valid Lua has no meaningful `require` in it — the names inside it
     *     are noise, and telling the author about a module before telling them
     *     the file cannot be read sends them looking in the wrong place.
     *  2. **A `require` names a module that is not registered.** Reported
     *     here rather than at the first `require` because the two are so far
     *     apart in the author's workflow that only one of them is any use:
     *     this is the check they press before saving, and a typo found ten
     *     minutes later on a phone is a typo found too late.
     *
     * Kept apart from [ScriptStatus.SYNTAX_ERROR] so the studio can tell the
     * author which of the two it was — see [ScriptCheckStatus].
     *
     * The throwaway session built here is never closed and does not need to
     * be: no script runs, so no `live` getter ever fires, and the sensor is
     * only ever started by a getter.
     */
    fun validate(source: String): ScriptCheckResult {
        val syntaxError = try {
            val check = ScriptSession(
                profile = profile,
                host = host,
                maxDurationMs = 0L,
                nowMs = ::now,
                clock = wallClock,
                network = network,
                sensor = sensor,
                sensorControl = sensorControl,
            )
            val env = buildGlobals(check)
            env.globals.load(WRAPPER_OPEN + source + "\n" + WRAPPER_CLOSE, "=check")
            null
        } catch (e: Exception) {
            e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Syntax error"
        }
        return if (syntaxError != null) {
            ScriptCheckResult(ScriptCheckStatus.SYNTAX_ERROR, syntaxError)
        } else {
            val missing = ModuleSourceScan.missingModules(source).firstOrNull()
                ?: return ScriptCheckResult.OK
            // The registry's own wording, verbatim: it is the same string the
            // script itself will trip over on the phone, so the author reads the
            // Check output and the failure as the same sentence.
            ScriptCheckResult(
                ScriptCheckStatus.MISSING_MODULE,
                ModuleRegistry.unknownModuleMessage(missing),
            )
        }
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

        BANNED_GLOBALS.forEach { globals[it] = LuaValue.NIL }

        GlyphLuaApi.install(globals, current)

        // Our own `require`, installed over the blank the loop above left: it
        // resolves registry entries and nothing else, so there is no file
        // system, no search path and no `package` behind it. `dofile`, `load`
        // and `loadstring` stay nil, which is what the sandbox is for — this
        // adds a front door to four known rooms, not a way out of the house.
        globals["require"] = ModuleRegistry.requireFor(current)

        // `print` is the one output channel a script gets, and it goes to the
        // studio console rather than to stdout.
        globals["print"] =
            GlyphLuaApi.luaFunction("print") { args ->
                current.log(args.joinToString(" ") { it.tojstring() })
                LuaValue.NIL
            }

        // Used, then hidden: a script must not be able to clear the hook.
        globals["debug"] = LuaValue.NIL
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

    /**
     * The same, for a [Throwable] that is not an [Exception].
     *
     * There is no wrapper to unwrap for an `OutOfMemoryError` thrown by the VM
     * itself, so this reads the message directly.
     */
    private fun describeError(e: Error): String =
        e.message ?: e::class.java.simpleName
}
