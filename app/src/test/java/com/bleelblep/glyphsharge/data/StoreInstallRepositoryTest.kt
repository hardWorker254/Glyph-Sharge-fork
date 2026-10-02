package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The install history's persistence — the one record that says *which* version
 * a phone holds.
 *
 * Everything about the update button rests on this number surviving a restart.
 * Read back as the wrong version and every installed card either offers an
 * update the phone does not need or hides one it does; and because the store
 * only ever checks against the catalogue when it is opened, the failure would
 * not show up until the day someone published a new release.
 *
 * Robolectric because this is the one suite that needs a real
 * `SharedPreferences`: the field under test is written by `JSONObject` and read
 * back by `JSONArray`, and a fake of either would prove nothing about the pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreInstallRepositoryTest {

    private lateinit var repository: StoreInstallRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun prefs() = context.getSharedPreferences(
        StoreInstallRepository.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        // A fresh history per test. It is read in the repository's own `init`,
        // so a leftover from the previous test would be in place before this
        // one had written anything.
        prefs().edit().clear().commit()
        repository = StoreInstallRepository(context)
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
    }

    /**
     * A second repository over the same preferences is what a process restart
     * amounts to from this layer's point of view.
     */
    private fun afterRestart() = StoreInstallRepository(context)

    @Test
    fun `an install survives a restart`() {
        repository.record("wave", version = 3, animationId = "aa11")

        val stored = afterRestart().installs.value["wave"]
        assertEquals(3, stored?.version)
        assertEquals("aa11", stored?.animationId)
    }

    @Test
    fun `an update replaces the record rather than adding one`() {
        repository.record("wave", version = 1, animationId = "aa11")
        repository.record("wave", version = 2, animationId = "aa11")

        val installs = afterRestart().installs.value
        // One entry, and the newest version: a second row for the same id
        // would leave the answer to "which version is installed" up to map
        // order.
        assertEquals(1, installs.size)
        assertEquals(2, installs["wave"]?.version)
    }

    @Test
    fun `an update keeps pointing at the same animation`() {
        repository.record("wave", version = 1, animationId = "aa11")
        repository.record("wave", version = 2, animationId = "aa11")

        assertEquals("aa11", afterRestart().installs.value["wave"]?.animationId)
    }

    @Test
    fun `items are recorded separately`() {
        repository.record("wave", version = 1, animationId = "aa11")
        repository.record("pulse", version = 4, animationId = "bb22")

        val installs = afterRestart().installs.value
        assertEquals(setOf("wave", "pulse"), installs.keys)
        assertEquals(4, installs["pulse"]?.version)
    }

    @Test
    fun `a record with no animation behind it is not offered`() {
        repository.record("wave", version = 1, animationId = "aa11")
        assertNull(afterRestart().installs.value["spiral"])
    }

    /**
     * The shape of a row written before a field existed — here `animationId`,
     * without which there is nothing an update could overwrite. Such a row
     * describes no action the store could take, so it is dropped rather than
     * surfaced.
     */
    @Test
    fun `an entry without an animation id is dropped`() {
        prefs().edit()
            .putString(
                StoreInstallRepository.KEY_INSTALLS,
                """[{"storeId":"wave","version":2}]""",
            )
            .commit()

        assertTrue(StoreInstallRepository(context).installs.value.isEmpty())
    }

    @Test
    fun `an entry without a catalogue id is dropped`() {
        prefs().edit()
            .putString(
                StoreInstallRepository.KEY_INSTALLS,
                """[{"version":2,"animationId":"aa11"}]""",
            )
            .commit()

        assertTrue(StoreInstallRepository(context).installs.value.isEmpty())
    }

    /**
     * Unreadable preferences cost the user a few "Update" buttons; refusing to
     * start over it would cost them the store.
     */
    @Test
    fun `unreadable preferences read as no history rather than a crash`() {
        prefs().edit().putString(StoreInstallRepository.KEY_INSTALLS, "not json").commit()

        assertTrue(StoreInstallRepository(context).installs.value.isEmpty())
    }

    @Test
    fun `a record starts at the first version`() {
        prefs().edit()
            .putString(
                StoreInstallRepository.KEY_INSTALLS,
                """[{"storeId":"wave","animationId":"aa11"}]""",
            )
            .commit()

        // An install this build has no number for cannot have been made from a
        // catalogue entry numbered above the format's start.
        assertEquals(1, StoreInstallRepository(context).installs.value["wave"]?.version)
    }
}