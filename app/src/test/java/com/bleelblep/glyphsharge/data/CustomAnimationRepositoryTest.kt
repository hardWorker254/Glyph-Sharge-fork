package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptFileFormat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * truth, and as an entry in the `SharedPreferences` index, which is what lets
 * the list be built without touching the disk. Those two copies can disagree,
 * and when they do the file is right — the index is only ever a cache.
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
        // The file has to exist too, or `reload()` drops the entry as vanished
        // and the assertion would pass for the wrong reason.
        scriptFile("cc33").writeText(
            ScriptFileFormat.encode(script("cc33", ScriptAnimation.ALL_DEVICES)),
        )

        assertEquals(ScriptAnimation.ALL_DEVICES, afterRestart().getById("cc33")?.devices)
    }

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
        scriptFile("dd44").writeText(
            ScriptFileFormat.encode(script("dd44", setOf(DeviceType.PHONE1))),
        )

        assertEquals(
            setOf(DeviceType.PHONE1, DeviceType.PHONE2A),
            afterRestart().getById("dd44")?.devices,
        )
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
}