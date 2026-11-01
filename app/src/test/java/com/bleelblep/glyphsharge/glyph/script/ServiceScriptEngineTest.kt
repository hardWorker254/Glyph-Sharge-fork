package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.script.module.FakeHost
import com.bleelblep.glyphsharge.glyph.script.module.testProfile
import com.bleelblep.glyphsharge.triggers.TriggerEvent
import com.bleelblep.glyphsharge.triggers.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The `.servicelua` runtime's core: a script files handlers, the host
 * dispatches events to them, and the only way the script can light anything is
 * `glyph.playAnimation()` — which reaches the app, never the strip.
 *
 * Robolectric because `ScriptSession.log` calls `android.util.Log`, and every
 * failing handler here goes through it.
 *
 * The drawing tests are the load-bearing ones: the whole design point of a
 * `.servicelua` is that it *cannot* draw, so `glyph.set` being nil is not an
 * implementation detail, it is the contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceScriptEngineTest {

    private fun engine(
        granted: (ScriptCapability) -> Boolean = { true },
        onFire: (Long?) -> Unit = {},
    ) = ServiceScriptEngine(
        profile = testProfile(),
        host = FakeHost(),
        granted = granted,
        onFireAnimation = onFire,
    )

    private fun event(type: TriggerType) = TriggerEvent(type, type.id)

    // region Registration → subscriptions

    @Test
    fun `glyph on files a handler and shows up in subscriptions`() {
        val e = engine()

        val result = e.load("""glyph.on("screen_off", function() end)""")

        assertTrue(result.isSuccess)
        assertEquals(setOf(TriggerType.SCREEN_OFF), e.subscriptions())
        e.stop()
    }

    @Test
    fun `the constructor starter survives decode and fires`() {
        // The exact chain a new advanced rule runs: the starter buffer is
        // parsed the way `importFrom` parses it (leading comments and all),
        // and the resulting source is loaded the way the host loads it.
        // Anything that breaks here breaks the rule the user just built.
        var fired: Long? = null
        val e = engine(onFire = { fired = it ?: -1L })
        val starter = """-- This service says WHEN; the animation itself is picked in the form.
-- glyph.on(trigger, fn) files a handler under a trigger name.
-- glyph.playAnimation() fires the animation of this rule.

glyph.on("screen_off", function()
  glyph.playAnimation()
end)"""

        val script = ServiceScriptFileFormat.decode(starter, "Rule service")
        val result = e.load(script.source)

        assertTrue(result.isSuccess)
        assertEquals(setOf(TriggerType.SCREEN_OFF), e.subscriptions())
        e.dispatch(event(TriggerType.SCREEN_OFF))
        assertEquals(-1L, fired)
        e.stop()
    }

    @Test
    fun `the Russian starter carries its comments through the engine`() {
        // Byte-for-byte the localized string resource: Cyrillic in the
        // leading comments must stay content the lexer skips, not a parse
        // error — the rule built from the RU starter has to fire too.
        val e = engine()
        val result = e.load(
            """-- Этот сервис говорит КОГДА; анимацию выбирает форма, а не скрипт.
-- glyph.on(триггер, fn) — обработчик на имя триггера.
-- glyph.playAnimation() запускает анимацию этого правила.

glyph.on("screen_off", function()
  glyph.playAnimation()
end)""",
        )

        assertTrue(result.isSuccess)
        assertEquals(setOf(TriggerType.SCREEN_OFF), e.subscriptions())
        e.stop()
    }

    @Test
    fun `several triggers can be subscribed at once`() {
        val e = engine()

        e.load(
            """
            glyph.on("screen_off", function() end)
            glyph.on("unlock", function() end)
            glyph.on("power_connected", function() end)
            """.trimIndent(),
        )

        assertEquals(
            setOf(TriggerType.SCREEN_OFF, TriggerType.UNLOCK, TriggerType.POWER_CONNECTED),
            e.subscriptions(),
        )
        e.stop()
    }

    @Test
    fun `an unknown trigger is refused at registration`() {
        val e = engine()

        val result = e.load("""glyph.on("nope", function() end)""")

        assertFalse(result.isSuccess)
        assertEquals(ScriptStatus.RUNTIME_ERROR, result.status)
        assertTrue(result.message!!.contains("unknown trigger"))
        e.stop()
    }

    @Test
    fun `a handler must be a function`() {
        val e = engine()

        val result = e.load("""glyph.on("screen_off", "not a function")""")

        assertFalse(result.isSuccess)
        assertTrue(result.message!!.contains("handler function"))
        e.stop()
    }

    @Test
    fun `a syntax error is reported as such`() {
        val e = engine()

        val result = e.load("""glyph.on("screen_off", function( end""")

        assertEquals(ScriptStatus.SYNTAX_ERROR, result.status)
        e.stop()
    }

    @Test
    fun `subscriptions are empty before anything is loaded`() {
        assertEquals(emptySet<TriggerType>(), engine().subscriptions())
    }

    // endregion

    // region Dispatch → playAnimation

    @Test
    fun `a dispatched event runs its handler and fires the animation`() {
        val fired = mutableListOf<Long?>()
        val e = engine { fired.add(it) }
        e.load("""glyph.on("unlock", function() glyph.playAnimation() end)""")

        e.dispatch(event(TriggerType.UNLOCK))

        assertEquals(listOf<Long?>(null), fired)
        e.stop()
    }

    @Test
    fun `playAnimation forwards an explicit duration`() {
        val fired = mutableListOf<Long?>()
        val e = engine { fired.add(it) }
        e.load("""glyph.on("unlock", function() glyph.playAnimation(1500) end)""")

        e.dispatch(event(TriggerType.UNLOCK))

        assertEquals(listOf<Long?>(1500L), fired)
        e.stop()
    }

    @Test
    fun `an event with no filed handler does nothing`() {
        val fired = mutableListOf<Long?>()
        val e = engine { fired.add(it) }
        e.load("""glyph.on("unlock", function() glyph.playAnimation() end)""")

        e.dispatch(event(TriggerType.SCREEN_OFF))

        assertTrue(fired.isEmpty())
        e.stop()
    }

    @Test
    fun `one broken handler does not stop the others`() {
        val fired = mutableListOf<Long?>()
        val e = engine { fired.add(it) }
        e.load(
            """
            glyph.on("unlock", function() error("boom") end)
            glyph.on("unlock", function() glyph.playAnimation() end)
            """.trimIndent(),
        )

        e.dispatch(event(TriggerType.UNLOCK))

        assertEquals(listOf<Long?>(null), fired)
        e.stop()
    }

    @Test
    fun `stop drops the handlers`() {
        val fired = mutableListOf<Long?>()
        val e = engine { fired.add(it) }
        e.load("""glyph.on("unlock", function() glyph.playAnimation() end)""")

        e.stop()
        e.dispatch(event(TriggerType.UNLOCK))

        assertTrue(fired.isEmpty())
        assertTrue(e.subscriptions().isEmpty())
    }

    // endregion

    // region The service surface: no drawing

    @Test
    fun `a service script cannot see the drawing half of the API`() {
        val e = engine()

        // `assert` raises, so a surviving load is proof that none of the
        // drawing names resolved — the whole contract of a `.servicelua`.
        val result = e.load(
            """
            assert(glyph.set == nil, "set")
            assert(glyph.hold == nil, "hold")
            assert(glyph.sweep == nil, "sweep")
            assert(glyph.ch == nil, "ch")
            assert(glyph.group == nil, "group")
            assert(glyph.audio == nil, "audio")
            glyph.on("screen_off", function() end)
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        e.stop()
    }

    @Test
    fun `reaching for a drawing call in a handler fails instead of drawing`() {
        val fired = mutableListOf<Long?>()
        val e = engine { fired.add(it) }
        e.load("""glyph.on("unlock", function() glyph.set({1}, 100) end)""")

        // glyph.set is nil, so the handler raises; dispatch swallows it and the
        // app is never asked to fire — the script had no way to light anything.
        e.dispatch(event(TriggerType.UNLOCK))

        assertTrue(fired.isEmpty())
        e.stop()
    }

    // endregion

    // region Context reads and the sandbox

    @Test
    fun `battery and charging are readable and live`() {
        val e = engine()

        val result = e.load(
            """
            assert(glyph.battery == 64, "battery")
            assert(glyph.charging == true, "charging")
            glyph.on("screen_off", function() end)
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        e.stop()
    }

    /**
     * The deny-by-default half of the capability contract.
     *
     * Without the grant the state reads are not *blocked*, they are absent —
     * the same `nil` a script gets for `glyph.set`, which it also cannot draw
     * with. That is the point: one shape of refusal for everything the service
     * surface does not hand out, grants included.
     */
    @Test
    fun `an ungranted capability reads as nil, not as a value`() {
        val e = engine(granted = { false })

        val result = e.load(
            """
            assert(glyph.battery == nil, "battery must be nil")
            assert(glyph.charging == nil, "charging must be nil")
            glyph.on("screen_off", function() end)
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        e.stop()
    }

    /** The grant is per capability: DEVICE_STATE alone unlocks both state reads. */
    @Test
    fun `the state grant is checked per capability, not as a blanket`() {
        val e = engine(granted = { it == ScriptCapability.DEVICE_STATE })

        val result = e.load(
            """
            assert(glyph.battery == 64, "battery")
            assert(glyph.charging == true, "charging")
            glyph.on("screen_off", function() end)
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        e.stop()
    }

    @Test
    fun `the banned globals are gone`() {
        val e = engine()

        val result = e.load(
            """
            assert(io == nil, "io")
            assert(os == nil, "os")
            assert(coroutine == nil, "coroutine")
            assert(load == nil, "load")
            assert(dofile == nil, "dofile")
            assert(luajava == nil, "luajava")
            assert(debug == nil, "debug")
            glyph.on("screen_off", function() end)
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        e.stop()
    }

    @Test
    fun `an infinite registration loop is stopped by the watchdog`() {
        val e = engine()

        val result = e.load("""while true do end""")

        assertFalse(result.isSuccess)
        e.stop()
    }

    @Test
    fun `loading twice without stopping is refused`() {
        val e = engine()
        e.load("""glyph.on("screen_off", function() end)""")

        val second = e.load("""glyph.on("unlock", function() end)""")

        assertEquals(ScriptStatus.RUNTIME_ERROR, second.status)
        assertTrue(second.message!!.contains("already loaded"))
        e.stop()
    }

    // endregion
}
