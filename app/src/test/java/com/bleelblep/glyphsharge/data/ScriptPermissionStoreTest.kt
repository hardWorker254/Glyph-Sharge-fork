package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user's per-script decisions, and the rule that silence is denial.
 *
 * Robolectric because the store's contract includes persistence — a decision
 * survives the process, which is the whole point of asking once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScriptPermissionStoreTest {

    private lateinit var store: ScriptPermissionStore

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        store = ScriptPermissionStore(context)
    }

    @Test
    fun `a script that was never asked has nothing`() {
        assertFalse(store.isGranted("neverasked1234", ScriptCapability.DEVICE_STATE))
        assertTrue(store.grantedFor("neverasked1234").isEmpty())
    }

    @Test
    fun `a grant is recorded and read back`() {
        store.grant("script1", setOf(ScriptCapability.DEVICE_STATE))

        assertTrue(store.isGranted("script1", ScriptCapability.DEVICE_STATE))
    }

    @Test
    fun `the runtime prefix spells the same decision`() {
        // The rule layer stores `service:<id>`; the file layer uses the bare
        // id. Both must read — and write — the same entry.
        store.grant("service:script1", setOf(ScriptCapability.DEVICE_STATE))

        assertTrue(store.isGranted("script1", ScriptCapability.DEVICE_STATE))
        assertEquals(setOf("script1"), store.grantsByScript().keys)
    }

    @Test
    fun `a decision survives a new store instance`() {
        store.grant("script1", setOf(ScriptCapability.DEVICE_STATE))

        val reopened = ScriptPermissionStore(context)

        assertTrue(reopened.isGranted("script1", ScriptCapability.DEVICE_STATE))
    }

    @Test
    fun `revoking the last capability removes the entry entirely`() {
        store.grant("script1", setOf(ScriptCapability.DEVICE_STATE))

        store.revoke("script1", setOf(ScriptCapability.DEVICE_STATE))

        assertFalse(store.isGranted("script1", ScriptCapability.DEVICE_STATE))
        // Not present with an empty set — "was asked and denied" and "has not
        // been asked" are different states, and only the second one prompts.
        assertFalse(store.grantsByScript().containsKey("script1"))
    }

    @Test
    fun `an unknown capability id from a newer build is dropped, not fatal`() {
        val prefs = context.getSharedPreferences(
            ScriptPermissionStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        prefs.edit().putString("grants", """{"script1":["from_the_future"]}""").apply()

        val reopened = ScriptPermissionStore(context)

        assertTrue(reopened.grantedFor("script1").isEmpty())
    }
}
