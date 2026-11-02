package com.bleelblep.glyphsharge.triggers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The simple constructor's trigger palette.
 *
 * `POWER_CHANGED` remains a script event, but a simple rule must be built on
 * one edge: a charger rule pins a fixed animation, and a composite trigger
 * would make "plugged in" and "unplugged" share the same saved row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TriggerSourcesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the simple palette omits the combined charger trigger`() {
        val available = TriggerSources.available(context)

        assertFalse(TriggerType.POWER_CHANGED in available)
        assertTrue(TriggerType.POWER_CONNECTED in available)
        assertTrue(TriggerType.POWER_DISCONNECTED in available)
    }
}
