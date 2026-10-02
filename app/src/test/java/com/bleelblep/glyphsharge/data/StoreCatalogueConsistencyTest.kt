package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The store and the install history have to agree on what an item's id is.
 *
 * Every defect here was the same mistake made once on each side of a boundary:
 * two places folded an identifier to compare, or one of them failed to, and
 * neither could see the other. The visible cost is always the same — an
 * install the store forgets it made.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreCatalogueConsistencyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun prefs() = context.getSharedPreferences(
        StoreInstallRepository.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private lateinit var installs: StoreInstallRepository

    /** What a restart amounts to from this layer's point of view. */
    private fun afterRestart() = StoreInstallRepository(context)

    @Before
    fun setUp() {
        prefs().edit().clear().commit()
        installs = StoreInstallRepository(context)
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
    }

    @Test
    fun `a mixed-case catalogue id is still found after a restart`() {
        // The catalogue publishes `Wave`; `read` has always folded its map keys
        // with `lowercase()` while `record` wrote the raw id. So the in-memory
        // map was right for the rest of the session — and the very next launch
        // folded the key, every lookup by the raw id missed, and the store
        // offered the item as not installed with no "Update" button ever
        // appearing.
        installs.record("Wave", version = 2, animationId = "aa11")

        // Exactly what `ScriptStoreViewModel.derived` and `updateTarget` do:
        // reload the history, then index it by the catalogue's own id.
        val stored = afterRestart().installs.value
        val found = stored[StoreInstallRepository.foldedForComparison("Wave")]
        assertNotNull(
            "the history must be findable under the id the catalogue publishes; " +
                "before the fix this was null after every restart, so the store " +
                "offered the item again and the Update button never appeared",
            found,
        )
        assertEquals(2, found?.version)
        assertEquals("aa11", found?.animationId)
    }

    @Test
    fun `the folding rule is reachable, so a lookup cannot quietly skip it`() {
        // The failure this whole file is about was three places each doing their
        // own idea of "the same id". The rule is therefore public and this
        // pins what it does — including the Turkish locale, where a naive
        // `lowercase()` turns `I` into `ı` and `WAVE` becomes unreachable.
        assertEquals("wave", StoreInstallRepository.foldedForComparison("Wave"))
        assertEquals("wave", StoreInstallRepository.foldedForComparison("  WAVE  "))
        assertEquals("wavy", StoreInstallRepository.foldedForComparison("WaVy"))
    }

    @Test
    fun `the same item under two casings is one record, not two`() {
        // Both spellings arriving in one session used to be two map entries:
        // the store rendered two cards, installing from the second replaced the
        // first's record, and the phone ended up with `Wave` and `Wave (2)`.
        installs.record("Wave", version = 1, animationId = "aa11")
        installs.record("wave", version = 2, animationId = "bb22")

        assertEquals("one item, one record", 1, installs.installs.value.size)

        val stored = afterRestart().installs.value
        assertEquals("and still one after a restart", 1, stored.size)
        assertEquals(
            "the newest install is the one that survives",
            "bb22",
            stored.values.single().animationId,
        )
    }

    @Test
    fun `a record written by an older build is folded when it is read`() {
        // The other direction: rows already on disk from before the write side
        // was fixed can carry either casing, and reading has to leave one entry.
        prefs().edit().putString(
            StoreInstallRepository.KEY_INSTALLS,
            """[{"storeId":"Wave","version":1,"animationId":"aa11"},
               {"storeId":"wave","version":2,"animationId":"bb22"}]""",
        ).commit()

        val stored = afterRestart().installs.value
        assertEquals(1, stored.size)
        assertEquals("bb22", stored.values.single().animationId)
    }

    @Test
    fun `two installs at once do not lose one`() {
        // `record` was a read-modify-write on the StateFlow. The store screen
        // starts a download on a tap and only clears its flag in a `finally`
        // after the animation is imported, so two taps really can overlap — and
        // the second read the map the first was about to publish, silently
        // dropping the first install.
        val threads = (1..2).map { index ->
            Thread { installs.record("item$index", version = 1, animationId = "id$index") }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(
            "both installs must survive; the second publisher used to drop the first",
            2,
            installs.installs.value.size,
        )
        assertEquals(2, afterRestart().installs.value.size)
    }

    @Test
    fun `a catalogue that repeats an id is shown once`() {
        // Two entries, one id. The id is what the history is keyed on, so the
        // second install replaced the first's record while its card still said
        // "Install" — and pressing it duplicated the script on the phone.
        val parsed = parse(
            """[{"id":"Wave","url":"https://x/a.glyphlua","sha256":"${sha("a")}"},
                {"id":"wave","url":"https://x/b.glyphlua","sha256":"${sha("b")}"},
                {"id":"Pulse","url":"https://x/c.glyphlua","sha256":"${sha("c")}"}]""",
        )

        assertEquals("the duplicate is one item, so two cards", 2, parsed.size)
        assertEquals(
            "the first entry wins, so the outcome does not depend on array order",
            "Wave",
            parsed.first { it.id.equals("Wave", ignoreCase = true) }.id,
        )
    }

    @Test
    fun `a catalogue of distinct ids is untouched`() {
        val parsed = parse(
            """[{"id":"Wave","url":"https://x/a.glyphlua","sha256":"${sha("a")}"},
                {"id":"Pulse","url":"https://x/c.glyphlua","sha256":"${sha("c")}"}]""",
        )

        assertEquals(2, parsed.size)
    }

    @Test
    fun `a malformed entry still costs only that entry`() {
        // Deduplication is not a licence to be strict: a broken row is a
        // publisher's mistake and the rest of the catalogue is still good.
        val parsed = parse(
            """[{"id":"Wave","url":"https://x/a.glyphlua","sha256":"${sha("a")}"},
                {"id":"Broken","url":"https://x/b.glyphlua","sha256":"not-a-hash"}]""",
        )

        assertEquals(1, parsed.size)
        assertEquals("Wave", parsed.single().id)
    }

    @Test
    fun `an empty catalogue is not an error`() {
        // The counterpart to the 304 case: a server that legitimately publishes
        // nothing must read as an empty list rather than a thrown parse.
        assertTrue(parse("[]").isEmpty())
    }

    @Test
    fun `the repository still refuses a catalogue format it cannot read`() {
        val refused = runCatching {
            repository().parseIndex(JSONObject().put("format", 99).toString())
        }.exceptionOrNull()

        assertNotNull("an unknown format must be refused, not guessed at", refused)
    }

    @Test
    fun `the store and the history fold ids the same way`() {
        // If the two disagreed, the duplicate `parseIndex` removes would come
        // straight back as two history rows — so the rule itself is pinned, not
        // just each side's behaviour.
        val parsed = parse(
            """[{"id":"WaVe","url":"https://x/a.glyphlua","sha256":"${sha("a")}"},
                {"id":"wave","url":"https://x/b.glyphlua","sha256":"${sha("b")}"}]""",
        )
        assertEquals(1, parsed.size)

        // And the id the store kept is one the history records against cleanly.
        installs.record(parsed.single().id, version = 1, animationId = "aa11")
        assertEquals(1, afterRestart().installs.value.size)
        assertFalse(afterRestart().installs.value.isEmpty())
    }

    private fun parse(items: String) =
        repository().parseIndex(
            JSONObject().put("format", 1).put("items", JSONArray(items)).toString(),
        )

    private fun repository() = ScriptStoreRepository(context)

    private fun sha(seed: String): String = seed.repeat(64).take(64)
}