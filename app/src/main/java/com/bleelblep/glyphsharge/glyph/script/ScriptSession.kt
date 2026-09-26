package com.bleelblep.glyphsharge.glyph.script

import android.util.Log
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import org.luaj.vm2.Varargs
import java.util.Random

/**
 * One execution of one script: the state the watchdog reads, the state the
 * `glyph` API writes, and the mapping from "how did it end" to a
 * [ScriptRunResult].
 *
 * Timing lives here rather than in the Lua bindings because *every* wait must
 * be interruptible. A plain `Thread.sleep` inside a glyph call would keep the
 * strip lit for the full duration after the user has already stopped the
 * animation, so [sleep] wakes up every [CANCEL_POLL_MS] and re-checks the
 * abort flag.
 */
internal class ScriptSession(
    private val profile: DeviceProfile,
    private val host: GlyphScriptHost,
    private val maxDurationMs: Long,
    private val nowMs: () -> Long
) {
    private companion object {
        const val TAG = "ScriptSession"

        /** A single `glyph.hold` may not exceed this, whatever the script asks for. */
        const val MAX_HOLD_MS = 60_000L

        /** How often a blocking wait wakes up to notice a stop request. */
        const val CANCEL_POLL_MS = 16L

        /** Seed for scripts that want a repeatable pattern instead of noise. */
        const val DEFAULT_SEED = 0x5EEDL

        /** Hard ceiling on executed bytecode, counted in watchdog ticks. */
        const val INSTRUCTION_BUDGET = 200_000_000L
        const val INSTRUCTION_INTERVAL = 20_000L

        /** Bounded so a script inside a loop cannot grow the log forever. */
        const val MAX_LOG_LINES = 200
    }

    private enum class AbortKind { STOPPED, TIMED_OUT, INSTRUCTION_LIMIT }

    private val startedAt = nowMs()
    @Volatile
    private var random: Random = Random(DEFAULT_SEED)
    private val messages = mutableListOf<String>()

    /** Number of frames handed to the glyph, reported back in the result. */
    var frames: Int = 0
        private set

    /** `true` after `glyph.exit()`, which is a normal ending rather than a kill. */
    var scriptFinished: Boolean = false
        private set

    @Volatile
    private var externalStop: String? = null

    private var abortKind: AbortKind? = null
    private var watchdogTicks = 0L

    // region Watchdog

    /**
     * Called by the engine's count hook every [INSTRUCTION_INTERVAL] VM
     * instructions. Returns the reason the script must be killed, or `null`.
     */
    fun watchdogReason(): String? {
        externalStop?.let { return it }

        val elapsed = nowMs() - startedAt
        if (maxDurationMs > 0 && elapsed >= maxDurationMs) {
            abortKind = AbortKind.TIMED_OUT
            return "Duration limit of ${maxDurationMs}ms reached (${elapsed}ms elapsed)"
        }

        if (++watchdogTicks * INSTRUCTION_INTERVAL >= INSTRUCTION_BUDGET) {
            abortKind = AbortKind.INSTRUCTION_LIMIT
            return "Instruction budget of $INSTRUCTION_BUDGET reached"
        }
        return null
    }

    /** Called from any thread when another animation wants the strip. */
    fun requestExternalStop(reason: String) {
        if (externalStop == null) externalStop = reason
    }

    /** Throws if the watchdog has decided this run is over. */
    fun checkAbort() {
        watchdogReason()?.let { throw ScriptAbortedError(it) }
    }

    /**
     * What `glyph.running` reports. Unlike [watchdogReason] this has no side
     * effects, because a script may call it every iteration of its own loop.
     */
    fun isRunning(): Boolean {
        if (scriptFinished || externalStop != null) return false
        if (maxDurationMs <= 0) return true
        return nowMs() - startedAt < maxDurationMs
    }

    // endregion

    // region Drawing

    /** Lights [channels], then waits [holdMs] in interruptible slices. */
    fun draw(channels: List<Int>, brightness: Int, holdMs: Long) {
        checkAbort()
        if (channels.isEmpty()) {
            host.blank()
        } else {
            frames++
            host.draw(channels, brightness.coerceIn(0, GLYPH_MAX_BRIGHTNESS))
        }
        if (holdMs > 0) sleep(holdMs)
    }

    fun blank() {
        checkAbort()
        host.blank()
    }

    fun batteryPercent(): Int = host.batteryPercent()

    fun isCharging(): Boolean = host.isCharging()

    fun elapsedMs(): Long = nowMs() - startedAt

    /**
     * Waits [ms] without blocking cancellation.
     *
     * The strip keeps whatever was last drawn, which is what makes
     * `glyph.set(...)` + `glyph.hold(...)` behave like the Kotlin
     * `toggleChannels`/`delay` pair the built-in animations use.
     */
    fun sleep(ms: Long) {
        val remaining = ms.coerceIn(0L, MAX_HOLD_MS)
        var left = remaining
        while (left > 0) {
            checkAbort()
            val slice = minOf(CANCEL_POLL_MS, left)
            try {
                Thread.sleep(slice)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ScriptAbortedError("interrupted")
            }
            left -= slice
        }
    }

    fun exit() {
        scriptFinished = true
        throw ScriptAbortedError("script called glyph.exit()")
    }

    // endregion

    // region Utilities

    fun randomInt(from: Int, to: Int): Int {
        val low = minOf(from, to)
        val high = maxOf(from, to)
        val span = high - low + 1
        return if (span <= 1) low else low + random.nextInt(span)
    }

    fun randomDouble(): Double = random.nextDouble()

    /** Makes a pattern repeatable, which is what a seeded script is for. */
    fun seed(value: Long) {
        random = Random(value)
    }

    /** Named channel groups, so a script never hard-codes a channel number. */
    fun group(name: String): List<Int>? = when (name.lowercase()) {
        "all" -> profile.all
        "a" -> profile.a
        "b" -> profile.b
        "c" -> profile.c
        "d" -> profile.d
        "e" -> profile.e
        "nonc" -> profile.nonC
        "spiral" -> profile.spiralOrder.ifEmpty { profile.all }
        "pulse" -> profile.pulseSegments
        else -> null
    }

    fun groupNames(): List<String> =
        listOf("all", "a", "b", "c", "d", "e", "nonC", "spiral", "pulse")

    fun deviceName(): String = profile.type.name

    fun log(message: String) {
        val line = message.trim()
        if (line.isEmpty()) return
        // Bounded: a script in a `for` loop must not grow the list without end.
        if (messages.size < MAX_LOG_LINES) messages.add(line)
        Log.d(TAG, "[script] $line")
    }

    // endregion

    /**
     * Maps whatever ended the run onto a result the studio can show.
     *
     * @param outcome what `LuaThread.resume` returned: `false, message` when
     *   the script raised, and a truthy value on a clean finish.
     */
    fun result(outcome: Varargs?, elapsedMs: Long): ScriptRunResult {
        watchdogReason()?.let { reason ->
            return when (abortKind ?: AbortKind.STOPPED) {
                AbortKind.TIMED_OUT, AbortKind.INSTRUCTION_LIMIT ->
                    ScriptRunResult(ScriptStatus.TIMED_OUT, reason, frames, elapsedMs)

                AbortKind.STOPPED ->
                    ScriptRunResult(ScriptStatus.STOPPED, reason, frames, elapsedMs)
            }
        }

        if (scriptFinished) {
            return ScriptRunResult(ScriptStatus.COMPLETED, null, frames, elapsedMs)
        }

        if (outcome != null && outcome.arg1().isboolean() && !outcome.arg1().toboolean()) {
            val message = outcome.arg(2).tojstring().ifBlank { "unknown Lua error" }
            return ScriptRunResult(ScriptStatus.RUNTIME_ERROR, message, frames, elapsedMs)
        }

        return ScriptRunResult(ScriptStatus.COMPLETED, null, frames, elapsedMs)
    }
}
