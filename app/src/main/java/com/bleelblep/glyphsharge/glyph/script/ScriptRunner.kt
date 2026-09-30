package com.bleelblep.glyphsharge.glyph.script

import android.content.Context
import com.bleelblep.glyphsharge.glyph.battery.BatteryStateReader
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.RunTrace
import com.bleelblep.glyphsharge.glyph.audio.AudioBand
import com.bleelblep.glyphsharge.glyph.audio.AudioFrameFeed
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import com.bleelblep.glyphsharge.glyph.net.NetworkSource
import com.bleelblep.glyphsharge.glyph.sensor.SensorSource
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs user scripts against the real glyph.
 *
 * This is the only place that joins the VM to the hardware. The split matters:
 * [LuaScriptEngine] is pure and testable, [CustomAnimationRepository] is pure
 * storage, and everything that touches `GlyphRenderer` or the battery lives
 * here.
 *
 * The VM thread blocks while a frame is being shown, so [run] hops to
 * [Dispatchers.Default] first — the renderer and the Nothing SDK must never be
 * touched from the main thread.
 */
@Singleton
class ScriptRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val renderer: GlyphRenderer,
    private val glyphManager: GlyphManager,
    private val runTrace: RunTrace,
    private val audioFeed: AudioFrameFeed,
    /**
     * Read by `glyph.net`, and passed by method reference rather than by
     * value: the source caches for a second on its own, and handing the
     * engine a `() -> NetworkSnapshot` keeps that decision on the side that
     * knows about the binder cost.
     */
    private val networkSource: NetworkSource,
    /**
     * Read and started by `glyph.sensor`, and the only reason the VM is ever
     * handed a way to open hardware.
     *
     * Both halves are passed down rather than wired into the host, because
     * they are not part of drawing: the source decides when a listener is
     * worth registering and reference counts the runs that want one, which is
     * knowledge the engine has no business holding. The same reasoning as
     * [networkSource] above, and the same care about the trailing lambda —
     * named arguments only from here on, since the engine takes three of them.
     */
    private val sensorSource: SensorSource
) {
    private companion object {
        const val TAG = "SCRIPT"
    }

    /** The run in progress, so [stop] can reach it from another thread. */
    @Volatile
    private var active: LuaScriptEngine? = null

    /**
     * Compiles and plays [source] for at most [durationMs].
     *
     * The whole [ScriptRunResult] comes back, not just its status: whatever
     * the script printed or logged while it was drawing is part of the
     * outcome, and it has already been read off the session by the time this
     * returns, so the studio has nothing left to go and ask for.
     *
     * @return why the run ended, and what the script said, for the studio
     *   console
     */
    suspend fun runScript(source: String, durationMs: Long): ScriptRunResult =
        withContext(Dispatchers.Default) {
            val profile = DeviceProfileFactory.forConnectedDevice()
                ?: return@withContext ScriptRunResult(
                    ScriptStatus.RUNTIME_ERROR,
                    "This phone's LED layout is unknown, so a script cannot run here."
                )

            val host = RendererHost(renderer, context, glyphManager, audioFeed)
            val engine = LuaScriptEngine(
                profile = profile,
                host = host,
                network = networkSource::snapshot,
                sensor = sensorSource::snapshot,
                sensorControl = sensorSource
            )
            active = engine
            Log.d(TAG, "Running script for ${durationMs}ms on ${profile.type}")

            try {
                val result = engine.run(source, durationMs)
                // A run that drew nothing means the frames never reached the
                // hardware — almost always a closed SDK session rather than a
                // broken script, and worth saying out loud.
                if (result.frames == 0 || host.dropped > 0) {
                    Log.w(
                        TAG,
                        "Script frames: ${host.emitted} shown, ${host.dropped} dropped. " +
                            "session=${glyphManager.isSessionActive} " +
                            "phone=${glyphManager.isNothingPhone()} " +
                            "serviceConnected=${glyphManager.isServiceConnected}"
                    )
                    runTrace.record(
                        "script",
                        "frames",
                        "shown=${host.emitted} dropped=${host.dropped} " +
                            "session=${glyphManager.isSessionActive} " +
                            "connected=${glyphManager.isServiceConnected}"
                    )
                }
                result
            } finally {
                active = null
            }
        }

    /** Interrupts the run in progress. Called when another animation takes the strip. */
    fun stop(reason: String = "stopped by another animation") {
        active?.stop(reason)
    }

    /**
     * Compiles without running, for the editor's Check button.
     *
     * A phone whose LED layout is unknown cannot be checked against, and
     * says so by returning [ScriptCheckResult.OK] — the same "nothing to
     * report" it returned before the result was typed, rather than a failure
     * the author could do nothing about.
     */
    fun check(source: String): ScriptCheckResult {
        val profile = DeviceProfileFactory.forConnectedDevice() ?: return ScriptCheckResult.OK
        val host = RendererHost(renderer, context, glyphManager, audioFeed)
        return LuaScriptEngine(
            profile = profile,
            host = host,
            network = networkSource::snapshot,
            sensor = sensorSource::snapshot,
            sensorControl = sensorSource
        )
            .validate(source)
    }

    /**
     * Adapts the suspend renderer API to the blocking host the VM expects.
     *
     * The renderer is written in coroutines because the built-in animations
     * are; a Lua script calls it from inside the VM, where there is no
     * suspending frame of reference left, so each call bridges with
     * [runBlocking]. The caller is already off the main thread.
     */
    private class RendererHost(
        private val renderer: GlyphRenderer,
        private val context: Context,
        private val glyphManager: GlyphManager,
        private val audioFeed: AudioFrameFeed,
    ) : GlyphScriptHost {

        /** Frames the SDK actually accepted, and frames it never saw. */
        var emitted = 0
            private set
        var dropped = 0
            private set

        override fun draw(channels: List<Int>, brightness: Int) {
            val shown = runBlocking { renderer.toggleChannels(channels, brightness) }
            if (shown) emitted++ else dropped++
        }

        override fun blank() {
            renderer.turnOff()
        }

        override fun batteryPercent(): Int = BatteryStateReader.read(context).percentage

        override fun isCharging(): Boolean = BatteryStateReader.read(context).isCharging

        // Reads the same feed the built-in modes do, so a Lua script and a mode
        // are looking at identical numbers — and through the same indirection,
        // so `glyph.audio.active` is true whenever *either* capture is open.
        // With no capture open — the studio, or any other feature — the frame
        // is SILENT and a script sees zeros, which it can detect through
        // `glyph.audio.active`.

        override fun isAudioActive(): Boolean = audioFeed.isActive

        override fun audioLevel(): Float = audioFeed.latest().rms

        override fun audioBand(band: AudioBand): Float = audioFeed.latest().let {
            when (band) {
                AudioBand.BASS -> it.bass
                AudioBand.MID -> it.mid
                AudioBand.TREBLE -> it.treble
            }
        }

        override fun audioBeat(): Boolean = audioFeed.latest().beat

        override fun audioBands(count: Int): FloatArray {
            val size = count.coerceAtLeast(0)
            if (size == 0) return FloatArray(0)
            return audioFeed.latest().bandsInto(FloatArray(size))
        }

        /** A one-line explanation of why the strip stayed dark, if it did. */
        fun failureSummary(): String? {
            if (dropped == 0) return null
            val reason = when {
                !glyphManager.isSessionActive -> "no glyph session"
                else -> "the SDK rejected every frame"
            }
            return "dropped=$dropped of ${emitted + dropped} ($reason)"
        }
    }
}

/** Convenience for callers that already have a profile, e.g. the studio preview. */
fun DeviceProfile.isSupported(): Boolean = all.isNotEmpty()
