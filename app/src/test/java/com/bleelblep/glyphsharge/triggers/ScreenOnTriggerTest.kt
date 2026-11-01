package com.bleelblep.glyphsharge.triggers

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * "The screen came on" — the event a rule points at with the
 * [TriggerType.SCREEN_ON] trigger.
 *
 * The point of these tests is the path that does *not* involve a broadcast.
 *
 * `ACTION_SCREEN_ON` is delivered to a receiver registered by a background
 * process only when the platform decides to deliver it, and on the phone this
 * app was written for it did not: the rule host is a foreground *service*, not
 * a foreground app, so the screen-on broadcast never reached the receiver that
 * was registered, waiting, and saying nothing. A rule that waited for it
 * waited forever, and no amount of reading the code would have shown why —
 * every line of it was correct.
 *
 * So the trigger asks the platform's state instead of waiting for the
 * broadcast, and these tests pin that: the event has to arrive when the
 * *device* becomes interactive, with no broadcast anywhere.
 *
 * Robolectric because the fact under test is the platform's own: the power
 * manager's interactive state, which the shadow lets a test move.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenOnTriggerTest {

    private lateinit var powerManager: PowerManager

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        powerManager = context.getSystemService(PowerManager::class.java)
        shadowOf(powerManager).setIsInteractive(false)
    }

    private fun attach(): MutableList<TriggerEvent> {
        val events = mutableListOf<TriggerEvent>()
        val trigger = TriggerSources.create(context, TriggerType.SCREEN_ON)!!
        trigger.attach { events += it }
        return events
    }

    /** Moves the looper past one watcher tick. */
    private fun tick() {
        shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(InteractiveStateWatcher.POLL_MS + 50))
    }

    @Test
    fun `the event arrives when the device becomes interactive, with no broadcast`() {
        val events = attach()

        // The screen comes on. Nothing is broadcast; the platform's own state
        // is what changed, and that is what the rule waits for.
        shadowOf(powerManager).setIsInteractive(true)
        tick()

        assertEquals(
            "the rule's trigger did not fire when the device became interactive; " +
                "this is the whole of what 'Экран включён' means",
            1,
            events.size,
        )
        assertEquals(TriggerType.SCREEN_ON, events.first().type)
    }

    @Test
    fun `the broadcast is the fast path, not a second event`() {
        val events = attach()
        shadowOf(powerManager).setIsInteractive(true)

        // The platform's own broadcast arrives...
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        tick()

        assertEquals(
            "one screen-on is one event, however many paths noticed it",
            1,
            events.size,
        )
    }

    @Test
    fun `the broadcast alone reaches the trigger`() {
        val events = attach()
        shadowOf(powerManager).setIsInteractive(true)

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "the platform's own broadcast did not reach the trigger: a receiver " +
                "that is registered in a way the platform drops is the silent " +
                "failure this whole trigger was rebuilt to avoid",
            1,
            events.size,
        )
    }

    @Test
    fun `a device that stays interactive reports nothing`() {
        val events = attach()

        shadowOf(powerManager).setIsInteractive(true)
        tick()
        tick()

        assertEquals(
            "the trigger fired for a screen that was already on",
            1,
            events.size,
        )
    }

    @Test
    fun `a trigger attached while the screen is on does not report the past`() {
        shadowOf(powerManager).setIsInteractive(true)

        val events = attach()
        tick()

        assertEquals(
            "the watcher reported a transition that happened before it was attached",
            0,
            events.size,
        )
    }

    @Test
    fun `a screen-off that suspends before the next tick does not swallow the next screen-on`() {
        // The real sequence on a phone: the screen goes off, and the device
        // suspends before the watcher's one-second tick can run. If the tick
        // is the only thing that ever notices "not interactive", the latch
        // stays true across the whole sleep and the next screen-on is not an
        // edge at all. The screen-off broadcast is delivered before the
        // suspend, so it has to be what clears the latch.
        shadowOf(powerManager).setIsInteractive(true)
        val events = attach()

        // Screen off. No tick: the device is asleep.
        shadowOf(powerManager).setIsInteractive(false)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()

        // Screen on, and the broadcast fast path notices it.
        shadowOf(powerManager).setIsInteractive(true)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "the screen-on after a real suspend was swallowed by a stale latch; " +
                "this is the screen-on rule that never fires after the phone has been off a while",
            1,
            events.size,
        )
    }

    @Test
    fun `a detached trigger stops reporting`() {
        val events = mutableListOf<TriggerEvent>()
        val trigger = TriggerSources.create(context, TriggerType.SCREEN_ON)!!
        trigger.attach { events += it }
        trigger.detach()

        shadowOf(powerManager).setIsInteractive(true)
        tick()

        assertEquals(
            "a detached trigger still fired",
            0,
            events.size,
        )
    }
}
