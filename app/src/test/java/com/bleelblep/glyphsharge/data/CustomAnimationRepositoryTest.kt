package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptFileFormat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The repository's persistence, and the one field that was easy to lose.
 *
 * A script is stored twice: as a file under `filesDir`, which is the source of
 * truth, and as an entry in the `SharedPreferences` index, which is the shape
 * that survives a file going missing. Those two copies can disagree, and when
 * they do the file is right — the index is only ever a cache, and since
 * [CustomAnimationRepository.reload] started reading the files it is not even
 * the one the app plays from.
 *
 * That is why [the declared models survive the index] is a test rather than a
 * comment. `devices` was added long after both copies existed, and an index
 * reader that did not learn the new field would report every model for every
 * script for as long as the process stayed alive. Nothing about the running app
 * would look wrong: the editor reads from `getById` on a fresh `reload()` and
 * gets the right answer. The loss would only surface after a restart, which is
 * the sort of bug that arrives as "sometimes the label is wrong".
 *
 * Robolectric because this is the one suite that needs a real
 * `SharedPreferences` and a real `filesDir`. The field under test is written by
 * `JSONObject` and read back by `JSONArray`, and a fake of either would prove
 * nothing about the pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomAnimationRepositoryTest {

    private lateinit var repository: CustomAnimationRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun prefs() = context.getSharedPreferences(
        CustomAnimationRepository.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        // A fresh index per test. `reload()` runs in the repository's own
        // `init`, so a leftover index from the previous test would be read
        // before this test had written anything.
        prefs().edit().clear().commit()
        // And fresh files: `reload()` adopts any script the index does not
        // name, so a file the previous test left behind would turn up in this
        // one's list regardless of anything this test wrote.
        File(context.filesDir, CustomAnimationRepository.SCRIPT_DIR).deleteRecursively()
        repository = CustomAnimationRepository(context)
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
    }

    private fun script(id: String, devices: Set<DeviceType>): ScriptAnimation =
        ScriptAnimation(
            id = id,
            name = "Script $id",
            source = "glyph.setAll(glyph.MAX)",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_500_000L,
            devices = devices,
        )

    /**
     * A second repository over the same preferences and filesDir is what a
     * process restart amounts to from this layer's point of view.
     */
    private fun afterRestart() = CustomAnimationRepository(context)

    @Test
    fun `the declared models survive the index`() {
        val narrow = setOf(DeviceType.PHONE1, DeviceType.PHONE2)
        repository.save(script("aa11", narrow))

        assertEquals(narrow, afterRestart().getById("aa11")?.devices)
    }

    @Test
    fun `every model is what an undeclared script reports`() {
        repository.save(script("bb22", ScriptAnimation.ALL_DEVICES))

        assertEquals(ScriptAnimation.ALL_DEVICES, afterRestart().getById("bb22")?.devices)
    }

    /**
     * The shape of an index written before `devices` existed: a JSON object
     * with no such key at all, which is not the same as an empty one.
     *
     * Reachable only when the file cannot be decoded: the file is what [reload]
     * asks first, and this is the copy that answers when it has nothing to say.
     * So the entry's file is broken as well as old, which is what an index from
     * that era would in fact be standing in for.
     */
    @Test
    fun `an index entry without the field claims every model`() {
        prefs().edit()
            .putString(
                CustomAnimationRepository.KEY_INDEX,
                """
                [{
                  "id": "cc33",
                  "name": "Written before the field",
                  "source": "glyph.setAll(glyph.MAX)",
                  "createdAt": 1700000000000,
                  "updatedAt": 1700000000000
                }]
                """.trimIndent(),
            )
            .commit()
        undecodableScriptFile("cc33")

        val loaded = afterRestart().getById("cc33")

        assertEquals(ScriptAnimation.ALL_DEVICES, loaded?.devices)
        // The row survives a file that says nothing, with the index copy behind
        // it — a damaged file costs the newer content and not the script.
        assertEquals("glyph.setAll(glyph.MAX)", loaded?.source)
    }

    /**
     * [decodeIndexDevices]'s narrowing rule: an unknown name next to known ones
     * is ignored rather than counted as a refusal. Same reachability as above —
     * the index only speaks when the file cannot.
     */
    @Test
    fun `a partly unknown list in the index narrows to what it named`() {
        prefs().edit()
            .putString(
                CustomAnimationRepository.KEY_INDEX,
                """
                [{
                  "id": "dd44",
                  "name": "Half known",
                  "source": "glyph.setAll(glyph.MAX)",
                  "createdAt": 1700000000000,
                  "updatedAt": 1700000000000,
                  "devices": ["PHONE1", "PHONE9", "PHONE2A"]
                }]
                """.trimIndent(),
            )
            .commit()
        undecodableScriptFile("dd44")

        assertEquals(
            setOf(DeviceType.PHONE1, DeviceType.PHONE2A),
            afterRestart().getById("dd44")?.devices,
        )
    }

    /**
     * The rule the whole class states, and the one the test above used to
     * contradict: the file is right.
     *
     * Both copies name the same script and both are complete, so a reader that
     * preferred the index would look exactly as correct — the disagreement is
     * the only thing that tells them apart.
     */
    @Test
    fun `the file wins when the index disagrees`() {
        prefs().edit()
            .putString(
                CustomAnimationRepository.KEY_INDEX,
                """
                [{
                  "id": "hh88",
                  "name": "Stale name",
                  "source": "-- stale source",
                  "createdAt": 1700000000000,
                  "updatedAt": 1700000000000,
                  "devices": ["PHONE1", "PHONE2A"]
                }]
                """.trimIndent(),
            )
            .commit()
        scriptFile("hh88").writeText(
            ScriptFileFormat.encode(script("hh88", setOf(DeviceType.PHONE1))),
        )

        val loaded = afterRestart().getById("hh88")

        assertEquals(setOf(DeviceType.PHONE1), loaded?.devices)
        assertEquals("glyph.setAll(glyph.MAX)", loaded?.source)
        assertEquals("Script hh88", loaded?.name)
    }

    /**
     * The other half of the file being the source of truth, and the one that
     * costs a user their work when it is missing.
     *
     * `writeIndex` goes through `apply()`, so a save whose flush never lands —
     * the process killed between the file write and the preference write —
     * leaves a script on disk with nothing pointing at it. A reader that only
     * walked the index then dropped it forever, while the file it had just
     * written sat in the directory being read by nothing.
     */
    @Test
    fun `a file the index never recorded is adopted on reload`() {
        // `setUp` already ran one `reload()`, which rewrites the index every
        // time — so the claim to check is that this script is not in it.
        val before = prefs().getString(CustomAnimationRepository.KEY_INDEX, null)
        assertNotNull(before)
        assertFalse(before!!.contains("ii99"))

        scriptFile("ii99").writeText(
            ScriptFileFormat.encode(script("ii99", ScriptAnimation.ALL_DEVICES)),
        )

        val loaded = afterRestart().getById("ii99")

        assertNotNull(loaded)
        assertEquals("glyph.setAll(glyph.MAX)", loaded?.source)
    }

    /**
     * The reason the file exists at all.
     *
     * A script "can grow past what a preference value should hold", and that is
     * only true if the Lua is not in the preferences — which it was, in every
     * entry, and re-serialised on every save of every other script.
     */
    @Test
    fun `the index never carries the script source`() {
        repository.save(script("jj10", ScriptAnimation.ALL_DEVICES))

        val raw = prefs().getString(CustomAnimationRepository.KEY_INDEX, null)

        assertNotNull(raw)
        assertFalse(raw!!.contains("glyph.setAll(glyph.MAX)"))
    }

    @Test
    fun `an imported script keeps the models the file declared`() {
        val file = """
            -- Glyph Sharge animation
            -- format: 1
            -- name: Narrow
            -- id: ee55
            -- devices: PHONE2A

            glyph.setAll(glyph.MAX)
        """.trimIndent()

        val imported = runBlocking { repository.importFrom(file, "fallback") }

        assertEquals(setOf(DeviceType.PHONE2A), imported.devices)
        // The id is always fresh, so the phone cannot overwrite the copy it
        // already has — which is also why the lookup below goes by the new id.
        assertTrue(imported.id != "ee55")
        assertNotNull(repository.getById(imported.id))
    }

    @Test
    fun `the written index is stable for the same set of models`() {
        repository.save(script("ff66", setOf(DeviceType.PHONE3A, DeviceType.PHONE1)))

        val raw = prefs().getString(CustomAnimationRepository.KEY_INDEX, null)

        assertNotNull(raw)
        // Declaration order, not `Set` iteration order, so two indexes for one
        // set of models compare equal.
        assertTrue(raw!!.contains("PHONE1"))
        assertTrue(raw.indexOf("PHONE1") < raw.indexOf("PHONE3A"))
    }

    @Test
    fun `an entry whose file vanished is dropped on reload`() {
        repository.save(script("gg77", ScriptAnimation.ALL_DEVICES))
        assertNotNull(repository.getById("gg77"))

        scriptFile("gg77").delete()

        assertNull(afterRestart().getById("gg77"))
    }

    /**
     * The directory is created lazily by the repository's own `scriptDir`
     * getter, which only runs once a script has been saved. A test that writes
     * a file directly has to create it, or the write fails on a directory that
     * does not exist yet.
     */
    private fun scriptFile(id: String): File {
        val dir = File(
            context.filesDir,
            CustomAnimationRepository.SCRIPT_DIR,
        ).apply { mkdirs() }
        return File(dir, "$id${CustomAnimationRepository.FILE_SUFFIX}")
    }

    /**
     * A file whose every line is a comment, so there is no Lua body for
     * `ScriptFileFormat.decode` to return and it throws instead. The case the
     * index copy exists to cover: the row is still there, with contents the
     * repository has to take from the preference it wrote earlier.
     */
    private fun undecodableScriptFile(id: String): File = scriptFile(id).apply {
        writeText(
            """
            -- Glyph Sharge animation
            -- format: 1
            -- no Lua body at all
            """.trimIndent(),
        )
    }
}