package com.bleelblep.glyphsharge.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The null half of [getSetting]'s contract, which had no test and no
 * implementation.
 *
 * A caller may pass a null default to mean "tell me whether this was ever
 * written" — [FeatureSettings.getMusicVizAnimationId] does exactly that, so
 * that it can answer `MusicVisualizationMode.DEFAULT.id` itself. The read
 * substituted the caller's default for a cleared value, but the substitution
 * was written as `default as String`: for a null default that cast throws
 * "null cannot be cast to non-null type kotlin.String", before the caller's
 * own elvis ever got to run. The fallback the caller had written was
 * unreachable, and the path was only ever missed because first-run defaults
 * pre-write the key on a normal install — clear the store, or clear just that
 * value, and the read throws.
 *
 * Robolectric for a real `SharedPreferences`: the branch under test is
 * `getString`'s return for an absent key, and a fake that returned anything
 * else would not be testing the same question.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPrefsTest {

    private lateinit var prefs: SharedPreferences

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        prefs = context.getSharedPreferences("settings_prefs_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `absent key with null default comes back null for the caller to answer`() {
        assertNull(prefs.getSetting<String?>(KEY, null))
    }

    @Test
    fun `explicitly cleared string with null default comes back null`() {
        prefs.edit().putString(KEY, null).commit()

        assertNull(prefs.getSetting<String?>(KEY, null))
    }

    @Test
    fun `written string wins over a null default`() {
        prefs.edit().putString(KEY, "pulse").commit()

        assertEquals("pulse", prefs.getSetting<String?>(KEY, null))
    }

    @Test
    fun `absent key with a non-null default hands back that default`() {
        assertEquals("fallback", prefs.getSetting(KEY, "fallback"))
    }

    @Test
    fun `cleared string with a non-null default hands back that default`() {
        prefs.edit().putString(KEY, null).commit()

        assertEquals("fallback", prefs.getSetting(KEY, "fallback"))
    }

    @Test
    fun `written string wins over a non-null default`() {
        prefs.edit().putString(KEY, "pulse").commit()

        assertEquals("pulse", prefs.getSetting(KEY, "fallback"))
    }

    @Test
    fun `boolean default is never substituted by a missing key`() {
        assertEquals(true, prefs.getSetting(KEY, true))
    }

    @Test
    fun `int default round-trips through a write`() {
        prefs.putSetting(KEY, 7)

        // The type argument is spelled out on purpose: without it, the
        // surrounding `assertEquals(long, long)` overload pulls `T` up to
        // `Long`, the literal `0` becomes a Long, and the read asks for
        // `getLong` from a value written with `putInt`.
        assertEquals(7, prefs.getSetting<Int>(KEY, 0))
    }

    private companion object {
        const val KEY = "music_viz_animation_id"
    }
}
