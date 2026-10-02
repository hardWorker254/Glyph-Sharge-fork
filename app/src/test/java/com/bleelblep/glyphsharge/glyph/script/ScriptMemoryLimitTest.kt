package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.device.DnaConfig
import com.bleelblep.glyphsharge.glyph.device.FireworksConfig
import com.bleelblep.glyphsharge.glyph.device.MatrixConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The memory half of the sandbox's ceiling.
 *
 * The sandbox bounded wall-clock time and executed instructions, and bounded
 * memory not at all. A script whose only statement is `t[#t+1] = 1` on an
 * unbounded table allocates for the whole of the 200 M-instruction budget —
 * measured at around ten seconds and 390 MB of live heap before that budget
 * trips. On ART with a 256 MB heap that is an `OutOfMemoryError` *before* the
 * budget is reached, and an `OutOfMemoryError` is an `Error`: it passes under
 * every `catch (e: Exception)` between the script and the feature's foreground
 * service, so a script downloaded from the store could kill a feature service
 * that had nothing to do with it.
 *
 * These are the two halves of that problem — the ceiling stops the allocation
 * loop, and the engine reports an `Error` rather than throwing one at the
 * service.
 */
class ScriptMemoryLimitTest {

    private fun profile() = DeviceProfile(
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
        dnaConfig = DnaConfig(1, 1, 1),
    )

    /** Records frames instead of lighting LEDs, as the studio's preview does. */
    private class FakeHost : GlyphScriptHost {
        override fun draw(channels: List<Int>, brightness: Int) = Unit
        override fun blank() = Unit
        override fun batteryPercent(): Int = 64
        override fun isCharging(): Boolean = true
    }

    @Test
    fun `an allocation loop is stopped by the memory ceiling, not the instruction budget`() {
        val engine = LuaScriptEngine(profile(), FakeHost())

        // No duration cap: the only thing that can end this run is a watchdog
        // ceiling, and the message says which one it was.
        val result = engine.run(UNBOUNDED_TABLE, maxDurationMs = 0L)

        assertEquals(
            "the memory ceiling should be what stops this run, got: ${result.message}",
            ScriptStatus.TIMED_OUT,
            result.status,
        )
        assertTrue(
            "expected the memory ceiling to fire, got: ${result.message}",
            result.message?.contains("Memory limit") == true,
        )
    }

    @Test
    fun `an ordinary allocating script still runs to completion`() {
        val engine = LuaScriptEngine(profile(), FakeHost())

        val result = engine.run(
            """
            local t = {}
            for i = 1, 500 do t[i] = i end
            local sum = 0
            for i = 1, #t do sum = sum + t[i] end
            glyph.setAll(sum > 0 and 200 or 0)
            """.trimIndent(),
            maxDurationMs = 5_000L,
        )

        assertEquals(
            "a script that allocates normally must be unaffected (${result.message})",
            ScriptStatus.COMPLETED,
            result.status,
        )
        assertTrue("it should have drawn", result.frames > 0)
    }

    @Test
    fun `run always returns a result rather than throwing a VM error`() {
        val engine = LuaScriptEngine(profile(), FakeHost())

        // Reaching this line at all is the contract: an `OutOfMemoryError` or
        // a `StackOverflowError` from the Lua VM must be reported as a result
        // the studio can show, not thrown out into a foreground service.
        val result = engine.run(UNBOUNDED_TABLE, maxDurationMs = 0L)

        assertTrue(
            "run() must always return one of the known statuses, got ${result.status}",
            (result.status in ScriptStatus.entries),
        )
    }

    private companion object {
        /**
         * The smallest script that allocates without bound.
         *
         * No `glyph.*` call on purpose: the runaway is the allocation itself,
         * and a script that also drew would let the renderer be blamed for the
         * growth instead of the table.
         */
        const val UNBOUNDED_TABLE = "local t = {} while true do t[#t + 1] = 1 end"
    }
}
