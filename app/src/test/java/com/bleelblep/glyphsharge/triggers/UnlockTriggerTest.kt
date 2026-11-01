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
 * The "unlock" trigger: the keyguard was dismissed.
 *
 * `ACTION_USER_PRESENT` is the honest signal and fires at once when it comes.
 * Its fallback — the screen came on and no unlock followed — is what has to
 * work on a phone that never sends one, and it is built on the platform's
 * *state* rather than on the screen-on broadcast, for the reason
 * [ScreenOnTrigger] gives: the broadcast does not reach a background process
 * on every phone, and a fallback that never fires is the same as no trigger.
 *
 * These tests pin the fallback, because a fallback is exactly the kind of
 * thing that stops working silently: the rule stays attached, the host stays
 * alive, and the event simply never comes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnlockTriggerTest {

    private lateinit var powerManager: PowerManager

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        powerManager = context.getSystemService(PowerManager::class.java)
        shadowOf(powerManager).setIsInteractive(false)
    }

    private fun attach(): MutableList<TriggerEvent> {
        val events = mutableListOf<TriggerEvent>()
        val trigger = TriggerSources.create(context, TriggerType.UNLOCK)!!
        trigger.attach { events += it }
        return events
    }

    /** Lets the looper deliver a broadcast to the receivers that are waiting. */
    private fun deliver(intent: Intent) {
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a real unlock fires at once`() {
        val events = attach()

        deliver(Intent(Intent.ACTION_USER_PRESENT))

        assertEquals(
            "the honest signal did not reach the rule",
            1,
            events.size,
        )
        assertEquals(TriggerType.UNLOCK, events.first().type)
    }

    @Test
    fun `the fallback fires when the device becomes interactive and no unlock follows`() {
        val events = attach()

        // The screen comes on; the keyguard is never dismissed, so no
        // USER_PRESENT arrives — the case the fallback exists for.
        shadowOf(powerManager).setIsInteractive(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

        assertEquals(
            "the fallback did not fire: a phone that never sends USER_PRESENT " +
                "would never unlock anything",
            1,
            events.size,
        )
    }

    @Test
    fun `a real unlock inside the grace cancels the fallback`() {
        val events = attach()

        shadowOf(powerManager).setIsInteractive(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        deliver(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

        assertEquals(
            "the cycle fired twice: once for the unlock and once for the fallback",
            1,
            events.size,
        )
    }
}
