package com.bleelblep.glyphsharge.glyph.script

import android.util.Log
import com.bleelblep.glyphsharge.glyph.audio.AudioBand
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.glyph.net.NetworkSnapshot
import com.bleelblep.glyphsharge.glyph.sensor.SensorControl
import com.bleelblep.glyphsharge.glyph.sensor.SensorSnapshot
import org.luaj.vm2.Varargs
import java.time.ZonedDateTime
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
    private val nowMs: () -> Long,
    /**
     * The wall clock, injectable for the same reason [nowMs] is: `glyph.time`
     * branches on the hour, and a test for its 22:00 boundary cannot wait for
     * 22:00 or assert anything useful about the rest of the day.
     */
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
    /**
     * The network state, injectable for the same reason [clock] is:
     * `glyph.net` branches on whether the phone is connected, and a test that
     * waited for a VPN to come up would be a test that never ran.
     *
     * Defaults to [NetworkSnapshot.DISCONNECTED] rather than to a real read, so
     * a session built by something that has not wired the platform in still
     * answers a question a script can act on. "Offline" is the safe answer —
     * it is the state in which an animation should be doing nothing.
     */
    private val network: () -> NetworkSnapshot = { NetworkSnapshot.DISCONNECTED },
    /**
     * The accelerometer reading, injectable for the same reason [clock] and
     * [network] are: `glyph.sensor` branches on whether the phone is moving,
     * and a test that waited for someone to shake the test machine would be a
     * test that never ran.
     *
     * Defaults to [SensorSnapshot.STILL] rather than to a real read, so a
     * session that has not been wired to the platform still answers a question
     * a script can act on — a still phone is a state an animation can simply
     * not react to.
     */
    private val sensor: () -> SensorSnapshot = { SensorSnapshot.STILL },
    /**
     * What opens and closes the accelerometer, injected separately from
     * [sensor] so the reader and the lifecycle are not the same object.
     *
     * A session given only a reading could express "here is the answer" but
     * not "do not start the sensor", so every session would have to be built
     * with one or the other. With the pair split, a test hands in a recording
     * fake and asserts that a `require` did — or did not — start anything,
     * which is the only evidence available without a device in the room.
     *
     * Defaults to [SensorControl.NONE] rather than to a real sensor, so a
     * session that was never given a control can still be asked for a reading
     * and cannot accidentally open hardware.
     */
    private val sensorControl: SensorControl = SensorControl.NONE
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
    /** Log lines with the severity they were written at, in the order written. */
    private val messages = mutableListOf<ScriptLogLine>()

    /** Number of frames handed to the glyph, reported back in the result. */
    var frames: Int = 0
        private set

    /** `true` after `glyph.exit()`, which is a normal ending rather than a kill. */
    var scriptFinished: Boolean = false
        private set

    @Volatile
    private var externalStop: String? = null

    /** Set by `glyph.target`, read back in [result]. */
    @Volatile
    private var declaredTarget: ScriptTarget? = null

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

    // region Audio

    /**
     * `true` while something is feeding [audioBands].
     *
     * A script that loops on `glyph.audio.level` without checking this spins at
     * full speed forever drawing nothing; with it, the script can idle instead.
     */
    fun isAudioActive(): Boolean = host.isAudioActive()

    fun audioLevel(): Float = host.audioLevel()

    fun audioBass(): Float = host.audioBand(AudioBand.BASS)

    fun audioMid(): Float = host.audioBand(AudioBand.MID)

    fun audioTreble(): Float = host.audioBand(AudioBand.TREBLE)

    /** `true` on the frame a beat was detected; it is true for a single read. */
    fun audioBeat(): Boolean = host.audioBeat()

    /** The spectrum resampled to [count] values, for a strip of that length. */
    fun audioBands(count: Int): FloatArray = host.audioBands(count)

    // endregion

    // region Target

    /**
     * Records which service this script is written for.
     *
     * Honoured only before the first frame. A declaration that arrives after the
     * script has already drawn is refused rather than accepted quietly: by then
     * the script is running in whichever service happened to start it, and
     * quietly re-filing it would claim a guarantee that was never true.
     *
     * @return `null` when accepted, or the reason it was refused
     */
    fun declareTarget(raw: String): String? {
        val target = ScriptTarget.fromName(raw)
            ?: return "Unknown target '$raw'. Use \"${ScriptTarget.MUSIC_ID}\"."

        if (frames > 0) {
            return "glyph.target must be set before the first glyph.set(); " +
                "it was ignored and the script keeps running as ${ScriptTarget.ANY}."
        }

        declaredTarget = target
        return null
    }

    /** What the script declared, or `null` when it declared nothing. */
    fun target(): ScriptTarget? = declaredTarget

    // endregion

    fun elapsedMs(): Long = nowMs() - startedAt

    /**
     * Now, in the device's own time zone.
     *
     * Local, not UTC, because the only question a script is asking is whether
     * it is evening *where the phone is* — a user in CET whose phone is on UTC
     * would otherwise get a sunrise animation at 8pm.
     */
    fun wallClock(): ZonedDateTime = clock()

    /**
     * What `glyph.net` reads, afresh, on every field access.
     *
     * One reading answers all four fields at once. Returning a [NetworkSnapshot]
     * rather than four separate queries is what keeps a script that checks
     * `net.vpn` and then `net.metered` from seeing two different instants — see
     * `NetworkSnapshot` for the handover that would otherwise be observable.
     */
    fun networkSnapshot(): NetworkSnapshot = network()

    // region Sensor

    /**
     * Whether this run has already given its sensor back.
     *
     * Volatile because a run may be abandoned by a stop request from another
     * thread while the interpreter is somewhere else entirely, and the
     * release must happen exactly once whichever thread gets there first.
     */
    @Volatile
    private var sensorClosed = false

    /**
     * Whether this run is the one currently holding the reference.
     *
     * The session's half of a two-level count, and the half that is easy to
     * get wrong. [SensorControl.start] counts *across* runs, because two
     * services can be running scripts at once and whichever finished first
     * must not unregister a listener the other is still reading. This flag
     * counts *within* one: a script in a draw loop reads `sensor.magnitude`
     * sixty times a second, and a reference taken per read is a reference no
     * single [close] can ever give back — the listener would outlive the run
     * by an unbounded amount, which is the very failure the count exists to
     * prevent. Latched here, so a run takes at most one and always returns
     * exactly what it took.
     */
    @Volatile
    private var sensorHeld = false

    /**
     * What `glyph.sensor` reads, afresh, on every field access.
     *
     * The reader is asked for the *whole* snapshot, never for one axis, and
     * this is what makes that possible: acceleration arrives as a single event
     * and a script that could pull x at one instant and y at another would be
     * reading a smear rather than a vector. See `SensorSnapshot`.
     *
     * Does not start the sensor by itself — the source starts it on the first
     * read as well, so a caller that never came through [startSensor] still
     * gets an answer instead of a permanently still phone. The pairing that
     * matters is [startSensor] with [close].
     */
    fun sensorSnapshot(): SensorSnapshot = sensor()

    /**
     * Asks for accelerometer samples for the rest of this run.
     *
     * Takes at most one reference however many times it is called, which is
     * what lets `glyph.sensor` call it from every field getter: a draw loop
     * asking for `magnitude` sixty times a second still leaves exactly one
     * reference for [close] to return.
     */
    fun startSensor() {
        // Refused after the run is over: the matching release has already
        // happened, and a late acquire would be a reference nothing can bring
        // back to zero.
        if (sensorClosed || sensorHeld) return
        sensorHeld = true
        sensorControl.start()
    }

    /**
     * Gives up the reference this run took, if it took one.
     *
     * Guarded rather than blind for the same reason [startSensor] is latched.
     * A run that never read the sensor holds nothing, and releasing on its
     * behalf would decrement a count it never incremented — and when the count
     * belongs to *another* run, that is not an underflow that gets clamped
     * away, it is a live listener being torn out from under a script that is
     * still reading it.
     */
    fun stopSensor() {
        if (!sensorHeld) return
        sensorHeld = false
        sensorControl.stop()
    }

    /**
     * Releases whatever this run took, and ends it.
     *
     * Idempotent because the engine calls it from a `finally` on every exit
     * path, including the watchdog abort: a second call must not be a second
     * release. Latching the flag as well as testing it means a close on a run
     * that already closed is a no-op rather than a count driven one lower.
     *
     * Not a general teardown: the strip is blanked by the host, which the
     * engine owns, and the sensor is the only thing this run can have
     * registered and therefore the only thing it has to unregister.
     */
    fun close() {
        if (sensorClosed) return
        sensorClosed = true
        stopSensor()
    }

    // endregion

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

    /**
     * Records a line for the studio console, at [level].
     *
     * The severity goes into logcat with the matching call rather than being
     * folded into the text: a warning the author has to notice is worth
     * nothing if it comes out at the same priority as the progress notes
     * around it.
     */
    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val line = message.trim()
        if (line.isEmpty()) return
        // Bounded: a script in a `for` loop must not grow the list without end.
        if (messages.size < MAX_LOG_LINES) messages.add(ScriptLogLine(line, level))
        val tagged = "[script] $line"
        when (level) {
            LogLevel.INFO -> Log.d(TAG, tagged)
            LogLevel.WARN -> Log.w(TAG, tagged)
            LogLevel.ERROR -> Log.e(TAG, tagged)
        }
    }

    /** What the script wrote, for the studio console. A snapshot, not the live list. */
    fun logLines(): List<ScriptLogLine> = messages.toList()

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
                    ScriptRunResult(ScriptStatus.TIMED_OUT, reason, frames, elapsedMs, declaredTarget)

                AbortKind.STOPPED ->
                    ScriptRunResult(ScriptStatus.STOPPED, reason, frames, elapsedMs, declaredTarget)
            }
        }

        if (scriptFinished) {
            return ScriptRunResult(ScriptStatus.COMPLETED, null, frames, elapsedMs, declaredTarget)
        }

        if (outcome != null && outcome.arg1().isboolean() && !outcome.arg1().toboolean()) {
            val message = outcome.arg(2).tojstring().ifBlank { "unknown Lua error" }
            return ScriptRunResult(ScriptStatus.RUNTIME_ERROR, message, frames, elapsedMs, declaredTarget)
        }

        return ScriptRunResult(ScriptStatus.COMPLETED, null, frames, elapsedMs, declaredTarget)
    }
}
