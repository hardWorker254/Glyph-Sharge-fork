package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The cache half of the store, and the pairing it depends on.
 *
 * ### Why this exists
 *
 * The repository stored the server's `ETag` and never stored the body. That
 * looked fine for exactly as long as the app was launched once: the first run
 * gets `200`, shows the catalogue, saves the validator. The *second* run sends
 * `If-None-Match`, the server answers `304`, and there is nothing to read — so
 * the store opens empty and reports success.
 *
 * Nothing about that failure is loud. It is not a crash, not an error state,
 * and not something a suite that only exercises [ScriptStoreRepository.parseIndex]
 * would ever notice, because the parser is not where the bug lived.
 *
 * So the assertion is on the *pairing*: an index that reached the disk has to
 * come back out of it, because the `ETag` that makes the next request cheap is
 * written on the same path and would otherwise be a promise with nothing behind
 * it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScriptStoreCacheTest {

    private lateinit var repository: ScriptStoreRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val storeDir: File
        get() = File(context.filesDir, "glyph_store")

    private val hash = "a".repeat(64)

    private val index = """
        {
          "format": 1,
          "updated": 1712345678000,
          "items": [
            {
              "id": "wave",
              "name": "Wave",
              "author": "someone",
              "license": "MIT",
              "description": "",
              "version": 1,
              "devices": ["PHONE1", "PHONE2", "PHONE2A", "PHONE3A"],
              "url": "https://example.invalid/wave.glyphlua",
              "sha256": "$hash"
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        storeDir.deleteRecursively()
        repository = ScriptStoreRepository(context)
    }

    @Test
    fun `a written index reads back`() {
        repository.writeCache(index)

        assertEquals(1, repository.parseIndex(File(storeDir, "index.json").readText()).size)
    }

    /**
     * The round trip through [ScriptStoreRepository.loadFromCache], which is
     * what the screen actually calls: the body has to reach the *published*
     * state, not merely the file.
     */
    @Test
    fun `a written index is what the screen would read`() = runBlocking {
        repository.writeCache(index)

        assertTrue("the cache reports itself present", repository.loadFromCache())
        assertEquals(listOf("wave"), repository.items.value.map { it.id })
    }

    @Test
    fun `an empty cache reports itself absent rather than empty`() = runBlocking {
        // The distinction the empty-store bug destroyed: "no cache" and
        // "a cache with nothing in it" have to be the same answer, and both
        // have to differ from "a cache with entries".
        assertFalse(repository.loadFromCache())
        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun `writing twice leaves the second one intact`() {
        repository.writeCache(index)
        val replacement = index
            .replace("\"Wave\"", "\"Pulse\"")
            .replace("\"wave\"", "\"pulse\"")

        repository.writeCache(replacement)

        assertEquals(
            listOf("pulse"),
            repository.parseIndex(File(storeDir, "index.json").readText()).map { it.id },
        )
    }

    /**
     * No temporary file left behind. The atomic write is what stops a crash
     * mid-write from leaving a truncated index that reports itself absent — so
     * a stray `.tmp` would mean the rename did not happen.
     */
    @Test
    fun `writing leaves no temporary file`() {
        repository.writeCache(index)

        assertFalse(
            "a leftover temp file means the rename failed",
            File(storeDir, "index.json.tmp").exists(),
        )
    }

    @Test
    fun `an unreadable cache is reported absent rather than throwing`() = runBlocking {
        // The directory has to exist before a file can be put in it: the
        // repository creates it lazily, and this test writes without going
        // through the repository at all.
        storeDir.mkdirs()
        File(storeDir, "index.json").writeText("<html>not json</html>")

        assertFalse(repository.loadFromCache())
        assertTrue(repository.items.value.isEmpty())
    }

    // region Validators

    @Test
    fun `a response carrying both validators stores both`() {
        repository.writeValidators("\"etag-1\"", "Mon, 01 Jan 2024 00:00:00 GMT")

        assertEquals("\"etag-1\"", File(storeDir, "index.etag").readText())
        assertEquals(
            "Mon, 01 Jan 2024 00:00:00 GMT",
            File(storeDir, "index.modified").readText(),
        )
    }

    /**
     * The mixed pair is the failure this guards against: a fresh ETag
     * stored beside the Last-Modified of an *earlier* catalogue makes
     * the next request answer `304` to a catalogue that did change.
     * Neither survives, so the next request is unconditional — one
     * extra download instead of the catalogue's freshness.
     */
    @Test
    fun `a response carrying only one validator stores neither`() {
        repository.writeValidators("\"etag-1\"", "Mon, 01 Jan 2024 00:00:00 GMT")
        repository.writeValidators("\"etag-2\"", null)

        assertFalse(
            "a stale Last-Modified must not survive",
            File(storeDir, "index.modified").exists(),
        )
        assertFalse(
            "nor may the ETag it can no longer be paired with",
            File(storeDir, "index.etag").exists(),
        )
    }

    @Test
    fun `a response carrying no validators clears the stored pair`() {
        repository.writeValidators("\"etag-1\"", "Mon, 01 Jan 2024 00:00:00 GMT")
        repository.writeValidators(null, null)

        assertFalse(File(storeDir, "index.etag").exists())
        assertFalse(File(storeDir, "index.modified").exists())
    }

    // endregion

    // region Painting the cache

    /**
     * The race on opening the store: `loadFromCache` and `refresh` run
     * concurrently, and a disk read that finishes *after* a successful
     * fetch must not paint the file over the catalogue the fetch just
     * published — the fetch's validators are already on disk, so every
     * later `304` would agree with the stale list until the next
     * launch.
     */
    @Test
    fun `a cache read leaves a catalogue already on screen alone`() = runBlocking {
        repository.writeCache(index)
        repository.loadFromCache()
        assertEquals(listOf("wave"), repository.items.value.map { it.id })

        // What a completed fetch leaves behind: a new file on disk and
        // a new list on screen. A read finishing after it must keep
        // the screen's answer.
        repository.writeCache(
            index.replace("\"Wave\"", "\"Pulse\"").replace("\"wave\"", "\"pulse\""),
        )
        repository.loadFromCache()

        assertEquals(
            "the catalogue already on screen is not replaced",
            listOf("wave"),
            repository.items.value.map { it.id },
        )
        assertTrue("the cache still reports itself present", repository.loadFromCache())
    }

    // endregion
}
