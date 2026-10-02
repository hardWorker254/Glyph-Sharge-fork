package com.bleelblep.glyphsharge.ui.viewmodel

import com.bleelblep.glyphsharge.data.StoreInstall
import com.bleelblep.glyphsharge.data.StoreInstallRepository
import com.bleelblep.glyphsharge.data.StoreItem
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a card offers "Update" in place of "Installed".
 *
 * The rule lives in the ViewModel's [outdatedItems] rather than in a composable
 * for the same reason the sort order does: it is part of what the state *means*,
 * so it can be asserted without a `Content` and without a device. Every input
 * is a plain value, which is why nothing here needs Robolectric.
 *
 * The cases are ordered by how much damage a false positive would do. Saying
 * "Installed" when there is an update costs a missed download; saying "Update"
 * for a script that is not really the one from the store costs the user their
 * own work.
 */
class StoreUpdateRulesTest {

    private fun item(id: String, name: String = id, version: Int = 1) = StoreItem(
        id = id,
        name = name,
        author = "someone",
        license = "MIT",
        description = "",
        url = "https://example.invalid/$id.glyphlua",
        sha256 = "a".repeat(64),
        devices = DeviceType.entries.toSet(),
        version = version,
    )

    /**
     * One record, keyed the way `StoreInstallRepository` keys it.
     *
     * The key goes through the repository's own fold because that is the
     * invariant the map carries: every entry in it was written by `record`, and
     * `record` folds. Hand-building an unfolded key here would assert against a
     * map that cannot exist.
     */
    private fun record(storeId: String, version: Int, animationId: String) =
        StoreInstallRepository.foldedForComparison(storeId) to
            StoreInstall(StoreInstallRepository.foldedForComparison(storeId), version, animationId)

    /** Folds names the way the ViewModel builds [ScriptStoreUiState.installed]. */
    private fun installed(vararg names: String): Set<String> =
        names.mapTo(mutableSetOf()) { matchKey(it) }

    private fun outdated(
        items: List<StoreItem>,
        installed: Set<String> = emptySet(),
        records: List<Pair<String, StoreInstall>> = emptyList(),
        live: Set<String> = emptySet(),
    ): Set<String> = outdatedItems(items, installed, records.toMap(), live)

    @Test
    fun `a catalogue version above the installed one is an update`() {
        val wave = item("wave", "Wave", version = 3)

        assertEquals(
            setOf("wave"),
            outdated(
                items = listOf(wave),
                installed = installed("Wave"),
                records = listOf(record("wave", version = 1, animationId = "aa11")),
                live = setOf("aa11"),
            ),
        )
    }

    /**
     * The ordinary case, and the one that has to stay quiet: the phone holds
     * what the catalogue publishes, so there is nothing to press.
     */
    @Test
    fun `the installed version itself is not an update`() {
        val wave = item("wave", "Wave", version = 2)

        assertTrue(
            outdated(
                items = listOf(wave),
                installed = installed("Wave"),
                records = listOf(record("wave", version = 2, animationId = "aa11")),
                live = setOf("aa11"),
            ).isEmpty(),
        )
    }

    /**
     * A phone holding something *newer* than the catalogue — a release
     * numbered down, a re-published entry — is not offered a "fix" that would
     * take it backwards.
     */
    @Test
    fun `a phone ahead of the catalogue is left alone`() {
        val wave = item("wave", "Wave", version = 2)

        assertTrue(
            outdated(
                items = listOf(wave),
                installed = installed("Wave"),
                records = listOf(record("wave", version = 5, animationId = "aa11")),
                live = setOf("aa11"),
            ).isEmpty(),
        )
    }

    /**
     * No record means no version to compare against — the user imported the
     * file by hand — so the card says "Installed" and the version line is the
     * only thing that tells them what they are running.
     */
    @Test
    fun `a script with no install record is never an update`() {
        val wave = item("wave", "Wave", version = 9)

        assertTrue(
            outdated(
                items = listOf(wave),
                installed = installed("Wave"),
                records = emptyList(),
                live = setOf("aa11"),
            ).isEmpty(),
        )
    }

    @Test
    fun `an item that is not on the phone is not an update`() {
        val wave = item("wave", "Wave", version = 3)

        assertTrue(
            outdated(
                items = listOf(wave),
                installed = emptySet(),
                records = listOf(record("wave", version = 1, animationId = "aa11")),
                live = setOf("aa11"),
            ).isEmpty(),
        )
    }

    /**
     * The one every other test here avoided, by writing every id in lower case.
     *
     * `StoreInstallRepository` keys its map by the *folded* id, so a catalogue
     * entry published as `Wave` is stored under `wave`. This lookup used to be
     * indexed by the item's raw id, which came back `null` — so the badge stayed
     * dark and "Update" never appeared for exactly the entries a publisher would
     * naturally have capitalised. Installing an item, restarting, and being
     * offered it again with no way to update was the whole of it.
     */
    @Test
    fun `a catalogue id published with capitals still finds its record`() {
        val wave = item("Wave", "Wave", version = 3)

        assertEquals(
            setOf("Wave"),
            outdated(
                items = listOf(wave),
                installed = installed("Wave"),
                records = listOf(record("wave", version = 1, animationId = "aa11")),
                live = setOf("aa11"),
            ),
        )
    }

    /**
     * And the reverse: the record has to be found by the *catalogue's* casing
     * whatever the stored key happens to be, since one is written from the
     * catalogue and the other is read back from disk.
     */
    @Test
    fun `a record stored under a capitalised id is found by a lower-case catalogue`() {
        val wave = item("wave", "Wave", version = 3)

        assertEquals(
            setOf("wave"),
            outdated(
                items = listOf(wave),
                installed = installed("Wave"),
                records = listOf(record("Wave", version = 1, animationId = "aa11")),
                live = setOf("aa11"),
            ),
        )
    }

    /**
     * The rule with teeth. The store installed `Wave` as `aa11`; the user
     * deleted it and wrote a script of their own under the same name. Pressing
     * "Update" here would overwrite the new one with the old one, so the record
     * is treated as saying nothing about this phone.
     */
    @Test
    fun `a record whose script was deleted is not an update`() {
        val wave = item("wave", "Wave", version = 3)

        assertTrue(
            outdated(
                items = listOf(wave),
                installed = installed("Wave"),
                records = listOf(record("wave", version = 1, animationId = "aa11")),
                // The user's own `Wave` is on the phone; `aa11` is not.
                live = setOf("ff99"),
            ).isEmpty(),
        )
    }

    /**
     * The same fold as the badge: a catalogue saying `Wave` and a phone
     * holding `wave` is one animation, and the update belongs on that card.
     */
    @Test
    fun `a name that differs only in case still matches`() {
        val wave = item("wave", "Wave", version = 2)

        assertEquals(
            setOf("wave"),
            outdated(
                items = listOf(wave),
                installed = installed("  wave  "),
                records = listOf(record("wave", version = 1, animationId = "aa11")),
                live = setOf("aa11"),
            ),
        )
    }

    @Test
    fun `only the items behind their catalogue are marked`() {
        val wave = item("wave", "Wave", version = 2)
        val pulse = item("pulse", "Pulse", version = 1)
        val spiral = item("spiral", "Spiral", version = 4)

        assertEquals(
            setOf("wave", "spiral"),
            outdated(
                items = listOf(wave, pulse, spiral),
                installed = installed("Wave", "Pulse", "Spiral"),
                records = listOf(
                    record("wave", version = 1, animationId = "aa11"),
                    record("pulse", version = 1, animationId = "bb22"),
                    record("spiral", version = 3, animationId = "cc33"),
                ),
                live = setOf("aa11", "bb22", "cc33"),
            ),
        )
    }

    @Test
    fun `an empty store marks nothing`() {
        assertTrue(outdated(emptyList()).isEmpty())
    }
}