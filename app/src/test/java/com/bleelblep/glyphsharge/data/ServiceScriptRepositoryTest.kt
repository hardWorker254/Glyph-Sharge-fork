package com.bleelblep.glyphsharge.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import com.bleelblep.glyphsharge.glyph.script.ServiceScriptFileFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * Installation of `.servicelua` files, and the rules the file system enforces.
 *
 * Robolectric because the repository is real files in a real `filesDir` behind
 * a real `SharedPreferences` index — the split *is* the behaviour under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceScriptRepositoryTest {

    private lateinit var repository: ServiceScriptRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val scriptDir: File
        get() = File(context.filesDir, ServiceScriptRepository.SCRIPT_DIR)

    private val source = """glyph.on("low_battery", function() glyph.playAnimation() end)"""

    @Before
    fun setUp() {
        repository = ServiceScriptRepository(context)
    }

    @Test
    fun `import regenerates the id and keeps the source`() = runBlocking {
        val installed = repository.importFrom(
            ServiceScriptFileFormat.encode(
                ServiceScript(id = "original12345", name = "Warn at 15%", source = source),
            ),
            fallbackName = "Fallback",
        )

        assertNotEquals("original12345", installed.id)
        assertEquals("Warn at 15%", installed.name)
        assertEquals(source, installed.source)
        // The file exists under the file name the id became.
        assertTrue(File(scriptDir, "${installed.id}.servicelua").exists())
    }

    @Test
    fun `a rule finds its script by bare id and by runtime id`() = runBlocking {
        val installed = repository.importFrom(source, "Warn at 15%")

        assertEquals(installed, repository.getById(installed.id))
        assertEquals(installed, repository.getById("service:${installed.id}"))
    }

    @Test
    fun `a second import of the same name does not overwrite the first`() = runBlocking {
        val first = repository.importFrom(source, "Warn")
        val second = repository.importFrom(source, "Warn")

        assertNotEquals(first.id, second.id)
        assertEquals("Warn (2)", second.name)
        assertNotNull(repository.getById(first.id))
    }

    @Test
    fun `reload adopts a file the index never saw`() {
        // A process killed between the file write and the index flush leaves
        // exactly this: a file, no entry. The file decides, so it is adopted.
        val orphan = ServiceScript(
            id = "orphan123456",
            name = "Orphan",
            source = source,
        )
        scriptDir.mkdirs()
        File(scriptDir, "orphan123456.servicelua")
            .writeText(ServiceScriptFileFormat.encode(orphan))

        val fresh = ServiceScriptRepository(context)

        assertNotNull(fresh.getById("orphan123456"))
    }

    @Test
    fun `reload drops an index entry whose file is gone`() = runBlocking {
        val installed = repository.importFrom(source, "Warn")
        File(scriptDir, "${installed.id}.servicelua").delete()

        val fresh = ServiceScriptRepository(context)

        assertNull(fresh.getById(installed.id))
    }

    @Test
    fun `delete removes the file, not just the row`() = runBlocking {
        val installed = repository.importFrom(source, "Warn")

        repository.delete(installed.id)

        assertNull(repository.getById(installed.id))
        assertTrue(!File(scriptDir, "${installed.id}.servicelua").exists())
    }

    @Test
    fun `an animation file dropped here is invisible to this repository`() {
        // Directory separation is the guard: `.glyphlua` files live elsewhere,
        // and this reload only ever walks its own directory.
        val wrongKind = File(scriptDir, "lookslikeananimation.glyphlua")
        scriptDir.mkdirs()
        wrongKind.writeText("glyph.set(glyph.ch.c, glyph.MAX)")

        repository.reload()

        assertTrue(repository.scripts.value.isEmpty())
    }

    @Test
    fun `replaceSource rewrites the body under the same id and name`() = runBlocking {
        // The constructor's code field saves back into the rule's own script:
        // same id (so the grants and the rule's link survive), same name (so
        // the chips do not start calling it something else), new body.
        val installed = repository.importFrom(source, "Warn at 15%")
        val edited = """glyph.on("screen_off", function() glyph.playAnimation() end)"""

        val updated = repository.replaceSource(installed.id, edited)

        assertEquals(installed.id, updated.id)
        assertEquals("Warn at 15%", updated.name)
        assertEquals(edited, updated.source)
        // The one file was overwritten, not joined by a second one.
        assertEquals(1, scriptDir.listFiles()?.size ?: 0)
    }
}
