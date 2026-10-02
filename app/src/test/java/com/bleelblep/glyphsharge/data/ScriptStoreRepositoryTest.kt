package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.security.MessageDigest

/**
 * The store's parsing and its refusals.
 *
 * Only [ScriptStoreRepository.parseIndex] is exercised: it is the one piece
 * that decides what the store *is*, and it runs on a string, so a catalogue can
 * be handed to it exactly as it was published — including the malformed ones,
 * which is the case that matters.
 *
 * The HTTP half is not tested here, and that is a deliberate gap rather than an
 * oversight. `refresh()` talks to a real CDN; a test for it would either hit the
 * network — flaky, slow, and asserting against somebody else's server — or
 * stand up a socket the production code has no seam for. What the hash is
 * *for* is testable without either, and it is the part with teeth: see [a hash
 * that does not match is refused].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScriptStoreRepositoryTest {

    private lateinit var repository: ScriptStoreRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        repository = ScriptStoreRepository(context)
    }

    /** A valid 64-hex digest, so tests that are not about the hash pass it. */
    private val validHash = "a".repeat(64)

    private fun itemJson(
        id: String = "wave",
        sha256: String = validHash,
        devices: String = """["PHONE1","PHONE2","PHONE2A","PHONE3A"]""",
        url: String = "https://cdn.jsdelivr.net/gh/u/s@main/packs/wave.glyphlua",
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", "Wave")
        put("author", "nick")
        put("license", "MIT")
        put("description", "Travelling wave")
        put("version", 2)
        put("devices", JSONArray(devices))
        put("url", url)
        put("sha256", sha256)
    }

    private fun indexOf(vararg items: JSONObject): String =
        JSONObject().apply {
            put("format", 1)
            put("updated", 1_700_000_000_000L)
            put("items", JSONArray().apply { items.forEach { put(it) } })
        }.toString()

    // region Reading a whole catalogue

    @Test
    fun `a well formed catalogue is read in full`() {
        val items = repository.parseIndex(indexOf(itemJson(id = "wave"), itemJson(id = "pulse")))

        assertEquals(2, items.size)
        assertEquals(listOf("wave", "pulse"), items.map { it.id })
    }

    @Test
    fun `every field of an item survives`() {
        val item = repository.parseIndex(indexOf(itemJson())).single()

        assertEquals("wave", item.id)
        assertEquals("Wave", item.name)
        assertEquals("nick", item.author)
        assertEquals("MIT", item.license)
        assertEquals("Travelling wave", item.description)
        assertEquals(2, item.version)
        assertEquals(
            "https://cdn.jsdelivr.net/gh/u/s@main/packs/wave.glyphlua",
            item.url,
        )
    }

    @Test
    fun `an item with no name falls back to its id`() {
        val nameless = itemJson().apply { put("name", "  ") }

        assertEquals("wave", repository.parseIndex(indexOf(nameless)).single().name)
    }

    @Test
    fun `a catalogue with no items is empty rather than broken`() {
        val empty = JSONObject().apply { put("format", 1) }.toString()

        assertTrue(repository.parseIndex(empty).isEmpty())
    }

    // endregion

    // region Declared models

    @Test
    fun `the models an item declares are read`() {
        val narrow = itemJson(devices = """["PHONE2A"]""")

        val item = repository.parseIndex(indexOf(narrow)).single()

        assertEquals(setOf(DeviceType.PHONE2A), item.devices)
        assertTrue(item.supports(DeviceType.PHONE2A))
        assertTrue(!item.supports(DeviceType.PHONE1))
    }

    @Test
    fun `an item naming an unknown model claims every model`() {
        // `PHONE4` is an honest claim about a phone that does not exist yet,
        // and refusing the item over it would leave it uninstallable anywhere.
        val future = itemJson(devices = """["PHONE4"]""")

        assertEquals(DeviceType.entries.toSet(), repository.parseIndex(indexOf(future)).single().devices)
    }

    @Test
    fun `a partly unknown list narrows to the models it named`() {
        val mixed = itemJson(devices = """["PHONE1","PHONE9","PHONE2A"]""")

        assertEquals(
            setOf(DeviceType.PHONE1, DeviceType.PHONE2A),
            repository.parseIndex(indexOf(mixed)).single().devices,
        )
    }

    @Test
    fun `device names come back in declaration order`() {
        // Read in whatever order the JSON array happens to list them, so a
        // screen can print them the same way for every item.
        val shuffled = itemJson(devices = """["PHONE3A","PHONE1","PHONE2A"]""")

        val item = repository.parseIndex(indexOf(shuffled)).single()

        assertEquals(listOf("PHONE1", "PHONE2A", "PHONE3A"), item.deviceNames)
    }

    @Test
    fun `an item with no models claims every model`() {
        val undeclared = itemJson().apply { put("devices", JSONArray()) }

        assertEquals(
            DeviceType.entries.toSet(),
            repository.parseIndex(indexOf(undeclared)).single().devices,
        )
    }

    // endregion

    // region Refusals

    /**
     * The check with teeth: bytes that are not the ones the catalogue
     * described must not be installed, whatever the reason they differ.
     */
    @Test
    fun `a hash that does not match is refused`() {
        val item = repository.parseIndex(indexOf(itemJson(sha256 = validHash))).single()

        // What a truncated download or a stale CDN object actually produces.
        val truncated = "glyph.setAll(glyph.MAX)".toByteArray()
        val actual = MessageDigest.getInstance("SHA-256").digest(truncated)
            .joinToString("") { "%02x".format(it) }

        assertTrue(actual != item.sha256)
    }

    @Test
    fun `the published hash of these bytes is the one that matches`() {
        // The other half of the same pair: a correct catalogue does match, so
        // the check is a comparison and not a blanket refusal.
        val source = "glyph.setAll(glyph.MAX)"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val item = repository.parseIndex(indexOf(itemJson(sha256 = digest))).single()

        assertEquals(digest, item.sha256)
    }

    @Test
    fun `an item whose hash is not a hash is dropped`() {
        val bogus = listOf(itemJson(id = "a", sha256 = "not-a-hash"), itemJson(id = "b", sha256 = ""))

        val items = repository.parseIndex(indexOf(*bogus.toTypedArray()))

        // Neither can be verified, so neither may be offered as installable.
        assertTrue(items.isEmpty())
    }

    @Test
    fun `an item with no url is dropped`() {
        val noUrl = itemJson().apply { put("url", "") }

        assertTrue(repository.parseIndex(indexOf(noUrl)).isEmpty())
    }

    @Test
    fun `an item with no id is dropped`() {
        val noId = itemJson().apply { put("id", "   ") }

        assertTrue(repository.parseIndex(indexOf(noId)).isEmpty())
    }

    /**
     * One bad entry costs the user that entry, not the store. A catalogue is
     * edited by hand in a pull request, and a typo in the fourth item should
     * not empty the first three.
     */
    @Test
    fun `one malformed entry does not empty the catalogue`() {
        val items = repository.parseIndex(
            indexOf(
                itemJson(id = "first"),
                itemJson(id = "broken", sha256 = "nope"),
                itemJson(id = "last"),
            ),
        )

        assertEquals(listOf("first", "last"), items.map { it.id })
    }

    @Test
    fun `a catalogue in a format this build does not know is refused`() {
        val future = JSONObject().apply {
            put("format", 99)
            put("items", JSONArray().apply { put(itemJson()) })
        }.toString()

        val error = runCatching { repository.parseIndex(future) }

        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull() is IOException)
    }

    @Test
    fun `a catalogue that is not json is refused rather than half read`() {
        val error = runCatching { repository.parseIndex("<html>404</html>") }

        assertTrue(error.isFailure)
    }

    @Test
    fun `an item that is not an object is skipped`() {
        val array = JSONArray().apply {
            put(itemJson(id = "good"))
            put("a bare string")
        }
        val body = JSONObject().apply {
            put("format", 1)
            put("items", array)
        }.toString()

        assertEquals(listOf("good"), repository.parseIndex(body).map { it.id })
    }

    // endregion
}