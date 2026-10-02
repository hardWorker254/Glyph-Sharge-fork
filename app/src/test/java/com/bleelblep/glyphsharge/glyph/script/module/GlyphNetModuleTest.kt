package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.net.NetworkSnapshot
import com.bleelblep.glyphsharge.glyph.script.LuaScriptEngine
import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for `require("glyph.net")`, against a pinned network state.
 *
 * Every field is live, so the only question these can answer honestly is what a
 * script is handed for a given network. The state is therefore injected rather
 * than read from the machine: a test that waited for a VPN to come up, or that
 * assumed the build server was on wifi, would pass on one host and fail on
 * every other.
 *
 * Each one goes through a real [LuaScriptEngine] and real Lua. The module is
 * never called from Kotlin, because half of what could be wrong with it is
 * invisible without the VM: a value bound as a function instead of a field, a
 * boolean that arrives as a truthy table, a live key the `__index` does not
 * resolve.
 */
class GlyphNetModuleTest {

    /** A home Wi-Fi connection: unmetered, not a VPN. */
    private val wifi = NetworkSnapshot(
        connected = true,
        wifi = true,
        metered = false,
        vpn = false,
    )

    /** A phone on cellular: the case where being metered actually matters. */
    private val cellular = NetworkSnapshot(
        connected = true,
        wifi = false,
        metered = true,
        vpn = false,
    )

    /** A tunnel up over whatever is underneath it. */
    private val tunnelled = NetworkSnapshot(
        connected = true,
        wifi = true,
        metered = false,
        vpn = true,
    )

    /** Runs [source] against [state], as a script would. */
    private fun at(state: NetworkSnapshot, source: String) {
        val result = engineAt(hour = 12, network = state).run(
            """
            local net = require("glyph.net")
            ${source.trimIndent()}
            """.trimIndent(),
        )
        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `a wifi connection reports every field`() {
        at(
            wifi, """
            assert(net.connected == true, 'the phone is online')
            assert(net.wifi == true, 'and it is over wifi')
            assert(net.metered == false, 'home wifi is not metered')
            assert(net.vpn == false, 'and no tunnel is up')
        """,
        )
    }

    @Test
    fun `a disconnected device reports nothing`() {
        // The state a session falls back to when it has not been wired to the
        // platform, so it has to be a state a script can act on rather than an
        // error or a nil field.
        at(
            NetworkSnapshot.DISCONNECTED, """
            assert(net.connected == false, 'nothing is online')
            assert(net.wifi == false, 'so no transport to name')
            assert(net.metered == false, 'and nothing to be charged for')
            assert(net.vpn == false, 'and no tunnel')
        """,
        )
    }

    @Test
    fun `a vpn transport is reported as vpn`() {
        // A VPN rides on top of whatever the phone was already using, so the
        // Wi-Fi underneath it stays true. Collapsing the two — reporting only
        // "some transport is up" — would break the script that wants to know
        // whether it is talking over the tunnel.
        at(
            tunnelled, """
            assert(net.vpn == true, 'the tunnel is up')
            assert(net.connected == true, 'and traffic is flowing over it')
            assert(net.wifi == true, 'while the wifi underneath carries it')
        """,
        )
    }

    @Test
    fun `metered is reported for a cellular connection`() {
        at(
            cellular, """
            assert(net.metered == true, 'the user is paying for these bytes')
            assert(net.wifi == false, 'cellular is not wifi')
            assert(net.connected == true, 'but it is still a connection')
        """,
        )
    }

    @Test
    fun `connected and metered are booleans`() {
        // `if not net.metered` is the whole point of the field, and in Lua a
        // bound function is truthy — so a getter that was not called would make
        // that expression silently wrong rather than loudly broken.
        at(
            wifi, """
            assert(type(net.connected) == 'boolean', 'connected must be a boolean')
            assert(type(net.metered) == 'boolean', 'metered must be a boolean')
            assert(type(net.wifi) == 'boolean', 'wifi must be a boolean')
            assert(type(net.vpn) == 'boolean', 'vpn must be a boolean')
            assert(not net.metered, 'a boolean false is falsey in an if')
        """,
        )
    }

    @Test
    fun `the state is read again on every access`() {
        // A module that read the network once while it was being built would
        // hand the same answer out for the whole run, and a script looping on
        // it — the only reason to ask — would never notice the phone leaving
        // Wi-Fi. A source that moves on each read is the only way to tell those
        // two apart, exactly as with the clock.
        var reads = 0
        val engine = LuaScriptEngine(
            profile = testProfile(),
            host = FakeHost(),
            network = {
                reads++
                if (reads < 2) NetworkSnapshot.DISCONNECTED else cellular
            },
        )

        val result = engine.run(
            """
            local net = require("glyph.net")
            local first = net.connected
            local second = net.connected
            assert(first == false and second == true, "each read must ask again")
            """.trimIndent(),
        )

        assertEquals(result.message, ScriptStatus.COMPLETED, result.status)
    }

    @Test
    fun `require returns the same table within a run`() {
        // A module built per access would make the obvious `net.vpn` in a loop
        // allocate a table every frame.
        at(
            wifi, """
            local again = require("glyph.net")
            assert(again == net, 'a second require must be the same table')
        """,
        )
    }
}
