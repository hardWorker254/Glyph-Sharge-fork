package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.device.DnaConfig
import com.bleelblep.glyphsharge.glyph.device.FireworksConfig
import com.bleelblep.glyphsharge.glyph.net.NetworkSnapshot
import com.bleelblep.glyphsharge.glyph.sensor.SensorSnapshot
import com.bleelblep.glyphsharge.glyph.device.MatrixConfig
import com.bleelblep.glyphsharge.glyph.script.GlyphScriptHost
import com.bleelblep.glyphsharge.glyph.script.LuaScriptEngine
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Phone (3a) layout and a host that records instead of lighting LEDs.
 *
 * Shared by the module tests, which all want the same three things: a real
 * sandbox to run a real script in, a C strip long enough for `level()` to have
 * something to divide, and a battery it can read back. It is the same profile
 * `LuaScriptEngineTest` uses, so a passing module test is evidence about the
 * same device the other tests are about.
 */
internal fun testProfile(): DeviceProfile = DeviceProfile(
    type = DeviceType.PHONE3A,
    all = (0..19).toList(),
    a = (20..24).toList(),
    b = (25..27).toList(),
    c = (0..9).toList(),
    d = emptyList(),
    e = emptyList(),
    waveGroups = emptyList(),
    spiralOrder = (0..9).toList(),
    spiralStep = 60L,
    pulseSegments = listOf(3, 5, 7),
    c1SeqStep = 200L,
    c1SeqHold = 1000L,
    matrixConfig = MatrixConfig(1, 1, 1, 1, 1, 1),
    fireworksConfig = FireworksConfig(1, 1, 1, 1, 1, 1),
    dnaConfig = DnaConfig(1, 1, 1)
)

/** Records the frames a script would have drawn. */
internal class FakeHost(
    private val percent: Int = 64,
    private val charging: Boolean = true
) : GlyphScriptHost {
    val frames = mutableListOf<Pair<List<Int>, Int>>()

    override fun draw(channels: List<Int>, brightness: Int) {
        frames.add(channels to brightness)
    }

    override fun blank() = Unit

    override fun batteryPercent(): Int = percent

    override fun isCharging(): Boolean = charging
}

/**
 * An engine whose every run sees [hour]:[minute] on one January day,
 * [network] on every field access, and [sensor] on every field access.
 *
 * Three things are pinned because three modules are live-valued, and pinning
 * any of them means the engine has to let a test say which. `glyph.time`
 * branches on the time of day, so its test cannot wait for 22:00; `glyph.net`
 * branches on whether the phone is on a network, so its test cannot arrange
 * for the machine running it to be on a VPN; `glyph.sensor` branches on
 * whether the phone is moving, which is nobody's job to arrange on demand.
 *
 * All three default to the same "nothing is happening" answers the engine
 * itself defaults to, so a test that cares about none of them does not have
 * to say so.
 */
internal fun engineAt(
    hour: Int,
    minute: Int = 0,
    host: GlyphScriptHost = FakeHost(),
    network: NetworkSnapshot = NetworkSnapshot.DISCONNECTED,
    sensor: SensorSnapshot = SensorSnapshot.STILL
): LuaScriptEngine {
    val instant = ZonedDateTime.of(
        2024, 1, 15, hour, minute, 0, 0, ZoneId.systemDefault()
    )
    return LuaScriptEngine(
        profile = testProfile(),
        host = host,
        // All three named, and none of them trailing. The engine takes three
        // injectable lambdas, only the last of which can take a trailing
        // lambda, so writing one positionally binds it to whichever happens
        // to be declared last — silently rebinding a test's clock to its
        // network the day a parameter is added underneath it. This has
        // already happened once in this project, which is why it is a
        // comment on every construction site rather than only on the one that
        // was wrong.
        wallClock = { instant },
        network = { network },
        sensor = { sensor }
    )
}
