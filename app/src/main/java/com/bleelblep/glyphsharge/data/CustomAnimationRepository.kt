package com.bleelblep.glyphsharge.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptFileFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the animations the user wrote in the studio.
 *
 * **Source in files, index in preferences.** A script is edited often and can
 * grow past what a preference value should hold, so each one lives in its own
 * file under `filesDir/glyph_scripts`, and SharedPreferences only keeps the
 * small index needed to list them without touching the disk.
 *
 * The list is exposed as a [StateFlow] because the animation pickers on the
 * feature cards are several screens away from the editor — saving a script has
 * to update the chips on Pulse Lock, Low Battery, NFC and Screen Off at once.
 */
@Singleton
class CustomAnimationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CustomAnimations"
        const val PREFS_NAME = "glyphsharge_custom_animations"
        const val KEY_INDEX = "index"
        const val SCRIPT_DIR = "glyph_scripts"
        const val FILE_SUFFIX = ".glyphlua"
    }

    /**
     * What a brand new animation starts with.
     *
     * A localized string rather than a Kotlin constant because the first thing
     * a user reads is the comment header, and it has to say the same thing in
     * both languages. The Lua itself is identical in every locale.
     */
    private fun starterScript(): String = context.getString(R.string.studio_starter_script)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _animations = MutableStateFlow<List<ScriptAnimation>>(emptyList())

    /** Every stored animation, newest first. */
    val animations: StateFlow<List<ScriptAnimation>> = _animations.asStateFlow()

    init {
        reload()
    }

    private val scriptDir: File
        get() = File(context.filesDir, SCRIPT_DIR).apply { if (!exists()) mkdirs() }

    // region Reading

    /** Re-reads the index from disk and drops entries whose file vanished. */
    fun reload() {
        val stored = runCatching { readIndex() }.getOrElse {
            Log.w(TAG, "Index unreadable, starting empty", it)
            emptyList()
        }

        val alive = stored.filter { scriptFile(it.id).exists() }
        if (alive.size != stored.size) writeIndex(alive)

        _animations.value = alive.sortedByDescending { it.updatedAt }
    }

    fun getById(id: String): ScriptAnimation? {
        val bare = ScriptAnimation.stripPrefix(id)
        return _animations.value.firstOrNull { it.id == bare }
    }

    fun findByRuntimeId(runtimeId: String): ScriptAnimation? =
        if (!ScriptAnimation.isCustomId(runtimeId)) null else getById(runtimeId)

    // endregion

    // region Writing

    /** Creates an empty animation from [template] and returns it, unsaved. */
    fun draft(template: String = starterScript()): ScriptAnimation = ScriptAnimation(
        id = ScriptAnimation.newId(),
        name = uniqueName(context.getString(R.string.studio_default_name)),
        source = template
    )

    fun save(animation: ScriptAnimation): ScriptAnimation {
        val stamped = animation.copy(updatedAt = System.currentTimeMillis())
        scriptFile(stamped.id).writeText(ScriptFileFormat.encode(stamped))
        upsert(stamped)
        return stamped
    }

    fun rename(id: String, name: String): ScriptAnimation? {
        val existing = getById(id) ?: return null
        return save(existing.copy(name = uniqueName(name, exceptId = existing.id)))
    }
    fun delete(id: String) {
        scriptFile(id).delete()
        publish(_animations.value.filterNot { it.id == id })
    }

    /** A copy under a free name, so a built-in-looking script can be tweaked safely. */
    fun duplicate(id: String): ScriptAnimation? {
        val original = getById(id) ?: return null
        val copy = original.copy(
            id = ScriptAnimation.newId(),
            name = uniqueName("${original.name} ${context.getString(R.string.studio_copy_suffix)}")
        )
        return save(copy)
    }

    // endregion

    // region Import and export

    /**
     * Imports a `.glyphlua` file.
     *
     * The id is always regenerated: an imported file that kept the original id
     * would silently overwrite the copy already on the phone.
     */
    suspend fun importFrom(text: String, fallbackName: String): ScriptAnimation =
        withContext(Dispatchers.IO) {
            val parsed = ScriptFileFormat.decode(text, fallbackName)
            val imported = parsed.copy(
                id = ScriptAnimation.newId(),
                name = uniqueName(parsed.name)
            )
            save(imported)
        }

    /** Reads a file the user picked. */
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalArgumentException("The chosen file could not be opened.")
    }

    /** Writes to a location the user picked through the system file picker. */
    suspend fun exportTo(uri: Uri, animation: ScriptAnimation): Unit =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(ScriptFileFormat.encode(animation).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("The chosen location could not be written.")
        }

    /**
     * Drops the file straight into the public Downloads folder.
     *
     * No permission is involved: on API 29+ an app may add its own files to
     * `MediaStore.Downloads` through the pending-write protocol.
     *
     * @return the resulting content uri, or `null` when the write failed
     */
    suspend fun exportToDownloads(animation: ScriptAnimation): Uri? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, ScriptFileFormat.suggestedFileName(animation))
                put(MediaStore.Downloads.MIME_TYPE, ScriptFileFormat.MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            val written = runCatching {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(ScriptFileFormat.encode(animation).toByteArray(Charsets.UTF_8))
                }
            }

            if (written.isFailure || written.getOrNull() == null) {
                resolver.delete(uri, null, null)
                return@withContext null
            }

            // Clears IS_PENDING, which is what publishes the file to the user.
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            uri
        }

    // endregion

    // region Internals

    private fun scriptFile(id: String) = File(scriptDir, "$id$FILE_SUFFIX")

    private fun upsert(animation: ScriptAnimation) {
        publish(_animations.value.filterNot { it.id == animation.id } + animation)
    }

    /** Publishes a new list to the UI and mirrors it into the index. */
    private fun publish(list: List<ScriptAnimation>) {
        val next = list.sortedByDescending { it.updatedAt }
        _animations.value = next
        writeIndex(next)
    }

    private fun readIndex(): List<ScriptAnimation> {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ScriptAnimation(
                id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                name = item.optString("name", "Untitled"),
                source = item.optString("source"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }

    private fun writeIndex(list: List<ScriptAnimation>) {
        val array = JSONArray()
        list.forEach { animation ->
            array.put(
                JSONObject().apply {
                    put("id", animation.id)
                    put("name", animation.name)
                    put("source", animation.source)
                    put("createdAt", animation.createdAt)
                    put("updatedAt", animation.updatedAt)
                }
            )
        }
        prefs.edit { putString(KEY_INDEX, array.toString()) }
    }

    /** Two scripts with the same name are indistinguishable in a chip row. */
    private fun uniqueName(requested: String, exceptId: String? = null): String {
        val base = requested.trim().ifEmpty { context.getString(R.string.studio_untitled) }
        val taken = _animations.value
            .filter { it.id != exceptId }
            .map { it.name.lowercase() }
            .toSet()
        if (base.lowercase() !in taken) return base

        var suffix = 2
        while ("$base ($suffix)".lowercase() in taken) suffix++
        return "$base ($suffix)"
    }
}
