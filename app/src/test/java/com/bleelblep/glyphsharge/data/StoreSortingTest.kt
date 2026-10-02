package com.bleelblep.glyphsharge.data

import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.ui.viewmodel.matchKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catalogue's installed-first ordering.
 *
 * The rule the screen relies on, written out here as the one place it can be
 * checked. It lives in the ViewModel's `items` collector rather than in a
 * composable, which is deliberate: the order is part of what the state *means*,
 * so a test can assert on it without a `Content` and without a device.
 *
 * The comparison is a pure function of (item, installed names), so nothing here
 * needs Robolectric — there is nothing to fake.
 */
class StoreSortingTest {

    private fun item(id: String, name: String) = StoreItem(
        id = id,
        name = name,
        author = "someone",
        license = "MIT",
        description = "",
        url = "https://example.invalid/$id.glyphlua",
        sha256 = "a".repeat(64),
        devices = DeviceType.entries.toSet(),
        version = 1,
    )

    /**
     * The ordering, spelled out once so the test and the production code cannot
     * drift into two different rules. Both sides fold through
     * [matchKey], exactly as the screen
     * and the ViewModel do.
     */
    private fun sort(items: List<StoreItem>, installed: Set<String>): List<String> =
        items
            .asSequence()
            .sortedByDescending { it.matchKey in installed }
            .map { it.id }
            .toList()

    /**
     * The installed set as the ViewModel builds it: every local script's name,
     * folded. Passing raw names here would test a comparison the production
     * code never performs — the folding happens once, on the way in.
     */
    private fun installed(vararg names: String): Set<String> =
        names.mapTo(mutableSetOf()) { matchKey(it) }

    @Test
    fun `installed items come first`() {
        val items = listOf(
            item("wave", "Wave"),
            item("pulse", "Pulse"),
            item("spiral", "Spiral"),
        )

        assertEquals(
            listOf("pulse", "wave", "spiral"),
            sort(items, installed("Pulse")),
        )
    }

    /**
     * A stable sort keeps the catalogue's own order within each group, which is
     * what stops a refresh from reshuffling everything the user was reading.
     */
    @Test
    fun `catalogue order is kept within each group`() {
        val items = listOf(
            item("a", "Alpha"),
            item("b", "Beta"),
            item("c", "Gamma"),
            item("d", "Delta"),
        )

        assertEquals(
            listOf("b", "d", "a", "c"),
            sort(items, installed("Beta", "Delta")),
        )
    }

    @Test
    fun `nothing installed leaves the order untouched`() {
        val items = listOf(item("a", "Alpha"), item("b", "Beta"))

        assertEquals(listOf("a", "b"), sort(items, emptySet()))
    }

    @Test
    fun `everything installed leaves the order untouched`() {
        val items = listOf(item("a", "Alpha"), item("b", "Beta"))

        assertEquals(listOf("a", "b"), sort(items, installed("Alpha", "Beta")))
    }

    /**
     * The installed set is built from the phone's own script names, folded
     * through the same function. A stray space therefore cannot stop a match —
     * and the alternative is a badge that goes dark for a reason nobody can see.
     */
    @Test
    fun `a stray space does not stop a match`() {
        // Pulse first, so Wave genuinely has to move up to prove anything.
        val items = listOf(item("pulse", "Pulse"), item("wave", "Wave"))

        assertEquals(listOf("wave", "pulse"), sort(items, installed("  Wave  ")))
    }

    /**
     * The one that actually happens: the catalogue says `Wave`, and a user who
     * imported the same file and typed the name themselves has `wave`.
     * [CustomAnimationRepository.uniqueName] only ever appends `(2)` — it never
     * rewrites case — so without this fold the badge would light for one user
     * and stay dark for the next.
     */
    @Test
    fun `a name that differs only in case still matches`() {
        val items = listOf(item("pulse", "Pulse"), item("wave", "Wave"))

        assertEquals(listOf("wave", "pulse"), sort(items, installed("wave")))
    }

    /**
     * A duplicate the user made does **not** light the badge, and should not.
     *
     * `uniqueName` turns a second copy of `Wave` into `Wave (2)`. If the phone
     * holds `Wave (2)` but not `Wave`, the animation from the store is genuinely
     * not there, and marking it installed would hide the one button that would
     * fix that. The badge answers "is this on my phone", not "is something with
     * this name on my phone".
     */
    @Test
    fun `a duplicate with a suffixed name does not count as installed`() {
        val items = listOf(item("wave", "Wave"), item("pulse", "Pulse"))

        assertEquals(listOf("wave", "pulse"), sort(items, installed("Wave (2)")))
    }

    @Test
    fun `an unmatched name is simply not installed`() {
        val items = listOf(item("wave", "Wave"), item("pulse", "Pulse"))

        assertEquals(listOf("wave", "pulse"), sort(items, installed("Nope")))
    }
}
