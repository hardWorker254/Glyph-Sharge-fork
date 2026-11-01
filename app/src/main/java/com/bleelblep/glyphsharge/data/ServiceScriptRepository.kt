package com.bleelblep.glyphsharge.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import com.bleelblep.glyphsharge.glyph.script.ServiceScriptFileFormat
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
 * Stores the `.servicelua` rules the user has installed.
 *
 * The same split [CustomAnimationRepository] settled on — **source in files,
 * index in preferences** — for the same reasons, applied to a leaner kind of
 * file: a service rule has no studio editor, no model claim and no in-place
 * update flow (yet), so what is left is import, list, read, delete, export.
 *
 * The directory is its own (`filesDir/service_scripts`) rather than a subfolder
 * of the animations': the two kinds have different suffixes and different
 * repositories, and a `.servicelua` dropped into `glyph_scripts` would be
 * adopted by the *animation* reload as a script that draws. Separation by
 * directory makes the wrong repository unable to even see the file.
 *
 * [reload] obeys the same three rules as the animation side: an index entry
 * with no file is dropped, a file with no index entry is adopted, a damaged
 * file falls back to the index copy.
 */
@Singleton
class ServiceScriptRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ServiceScripts"
        const val PREFS_NAME = "glyphsharge_service_scripts"
        const val KEY_INDEX = "index"
        const val SCRIPT_DIR = "service_scripts"
        const val FILE_SUFFIX = ".servicelua"

        /** Suffix of a not-yet-moved save; never mistaken for a script by [reload]. */
        private const val STAGING_SUFFIX = ".tmp"

        /** The shape an id has to have to become a file name. */
        private val SAFE_ID = Regex("[a-zA-Z0-9_-]{1,64}")
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _scripts = MutableStateFlow<List<ServiceScript>>(emptyList())

    /** Every installed service script, newest first. */
    val scripts: StateFlow<List<ServiceScript>> = _scripts.asStateFlow()

    init {
        reload()
    }

    private val scriptDir: File
        get() = File(context.filesDir, SCRIPT_DIR).apply { if (!exists()) mkdirs() }

    // region Reading

    /**
     * Rebuilds the list from the files and rewrites the index to match.
     *
     * The file decides, with the same three cases as
     * [CustomAnimationRepository.reload] — the reasoning there holds verbatim:
     * the file is what the host loads, so the file is what this list is.
     */
    fun reload() {
        val stored = runCatching { readIndex() }.getOrElse {
            Log.w(TAG, "Index unreadable, starting empty", it)
            emptyList()
        }

        val byId = LinkedHashMap<String, ServiceScript>()
        stored.forEach { entry ->
            if (!isSafeId(entry.id)) {
                Log.w(TAG, "Dropping an index entry whose id cannot name a file: ${entry.id}")
                return@forEach
            }
            readScript(entry.id, entry)?.let { byId[it.id] = it }
        }

        scriptDir.listFiles()?.forEach { file ->
            when {
                file.name.endsWith(STAGING_SUFFIX) -> file.delete()
                !file.name.endsWith(FILE_SUFFIX) -> Unit
                else -> {
                    val id = file.name.removeSuffix(FILE_SUFFIX)
                    when {
                        id in byId -> Unit
                        !isSafeId(id) -> Log.w(TAG, "Ignoring a file whose name cannot be an id: $file.name")
                        else -> readScript(id, null)?.let { byId[id] = it }
                    }
                }
            }
        }

        val next = byId.values.sortedByDescending { it.updatedAt }
        writeIndex(next)
        _scripts.value = next
    }

    private fun readScript(id: String, fallback: ServiceScript?): ServiceScript? {
        val file = scriptFile(id)
        if (!file.exists()) return null

        val text = runCatching { file.readText() }.getOrElse {
            Log.w(TAG, "Script file unreadable, keeping the index copy: $id", it)
            return fallback
        }

        val fallbackName = fallback?.name?.takeIf { it.isNotBlank() } ?: id
        val decoded = runCatching {
            ServiceScriptFileFormat.decode(text, fallbackName)
        }.getOrElse {
            Log.w(TAG, "Script file undecodable, keeping the index copy: $id", it)
            return fallback
        }

        // The file name is the id, for the same reason as on the animation side:
        // [scriptFile] keys off the name, and believing a header id would list a
        // script at an id whose file does not exist.
        return decoded.copy(id = id)
    }

    /**
     * Looks a script up by bare id or by its `service:` runtime form.
     *
     * The rule layer stores the runtime form; everything below normalises, so
     * both spellings answer here.
     */
    fun getById(id: String): ServiceScript? {
        val bare = ServiceScript.stripPrefix(id)
        return _scripts.value.firstOrNull { it.id == bare }
    }

    // endregion

    // region Writing

    fun delete(id: String) {
        val bare = ServiceScript.stripPrefix(id)
        // The ids in the list are the ones this class accepted at [reload], so
        // this is a guard on the argument — which is a path — rather than on
        // anything the app can currently reach.
        if (!isSafeId(bare)) {
            Log.w(TAG, "Refusing to delete with an id that cannot name a file: $id")
            return
        }
        scriptFile(bare).delete()
        publish(_scripts.value.filterNot { it.id == bare })
    }

    // endregion

    // region Import and export

    /**
     * Installs a `.servicelua` file.
     *
     * The id is always regenerated — an imported file that kept the original
     * id would silently overwrite the copy already on the phone.
     *
     * **Capabilities are not granted here.** Importing installs the file; the
     * prompt that asks about what it reaches for is a separate decision, taken
     * by the caller against [com.bleelblep.glyphsharge.data.ScriptPermissionStore].
     * Keeping the two apart is what lets an import be refused at the prompt
     * without a half-installed script holding grants it was never accepted for.
     */
    suspend fun importFrom(text: String, fallbackName: String): ServiceScript =
        withContext(Dispatchers.IO) {
            val parsed = ServiceScriptFileFormat.decode(text, fallbackName)
            val imported = parsed.copy(
                id = ServiceScript.newId(),
                name = uniqueName(parsed.name),
            )
            save(imported)
        }

    /**
     * Rewrites a script's source under its own id — the constructor's code
     * field saves back into the rule's own script rather than minting a new
     * one on every save. The id and the name survive (the grants hang off
     * the id, and the chips call the script by name); the body is whatever
     * the buffer holds, parsed exactly as an import would parse it, so a
     * pasted header is understood and a headerless buffer is kept whole.
     *
     * @throws ScriptFileFormat.InvalidScriptException when [text] has no Lua body
     */
    suspend fun replaceSource(id: String, text: String): ServiceScript =
        withContext(Dispatchers.IO) {
            val existing = getById(id)
                ?: throw IllegalArgumentException("No script is installed under $id.")
            val parsed = ServiceScriptFileFormat.decode(text, existing.name)
            save(
                parsed.copy(
                    id = existing.id,
                    name = existing.name,
                    createdAt = existing.createdAt,
                ),
            )
        }

    private fun save(script: ServiceScript): ServiceScript {
        val stamped = script.copy(updatedAt = System.currentTimeMillis())
        writeScript(stamped)
        upsert(stamped)
        return stamped
    }

    /** Reads a file the user picked. */
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalArgumentException("The chosen file could not be opened.")
    }

    /** Writes to a location the user picked through the system file picker. */
    suspend fun exportTo(uri: Uri, script: ServiceScript): Unit =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(ServiceScriptFileFormat.encode(script).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("The chosen location could not be written.")
        }

    /**
     * Drops the file into the public Downloads folder.
     *
     * No permission involved on API 29+, through the pending-write protocol.
     *
     * @return the resulting content uri, or `null` when the write failed
     */
    suspend fun exportToDownloads(script: ServiceScript): Uri? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, ServiceScriptFileFormat.suggestedFileName(script))
                put(MediaStore.Downloads.MIME_TYPE, ServiceScriptFileFormat.MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            val written = runCatching {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(ServiceScriptFileFormat.encode(script).toByteArray(Charsets.UTF_8))
                }
            }

            if (written.isFailure || (written.getOrNull() == null)) {
                resolver.delete(uri, null, null)
                return@withContext null
            }

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        }

    // endregion

    // region Internals

    private fun scriptFile(id: String) = File(scriptDir, "$id$FILE_SUFFIX")

    /** Replaces a script's file atomically — see [CustomAnimationRepository]'s twin. */
    private fun writeScript(script: ServiceScript) {
        val target = scriptFile(script.id)
        val staging = File(scriptDir, "${target.name}$STAGING_SUFFIX")
        staging.writeText(ServiceScriptFileFormat.encode(script))
        if (!staging.renameTo(target)) {
            staging.delete()
            throw IllegalStateException("The script ${script.id} could not be written.")
        }
    }

    private fun isSafeId(id: String) = SAFE_ID.matches(id)

    private fun upsert(script: ServiceScript) {
        publish(_scripts.value.filterNot { it.id == script.id } + script)
    }

    private fun publish(list: List<ServiceScript>) {
        val next = list.sortedByDescending { it.updatedAt }
        _scripts.value = next
        writeIndex(next)
    }

    private fun readIndex(): List<ServiceScript> {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ServiceScript(
                id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                name = item.optString("name", "Untitled"),
                // Only ever a fallback for a file that was already lost — see
                // [CustomAnimationRepository.readIndex] for the full reasoning.
                source = item.optString("source"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
    }

    private fun writeIndex(list: List<ServiceScript>) {
        val array = JSONArray()
        list.forEach { script ->
            array.put(
                JSONObject().apply {
                    put("id", script.id)
                    put("name", script.name)
                    // No `source`: the file holds it, and holding it here meant
                    // every save re-serialised every script into preferences.
                    put("createdAt", script.createdAt)
                    put("updatedAt", script.updatedAt)
                },
            )
        }
        prefs.edit { putString(KEY_INDEX, array.toString()) }
    }

    /** Two scripts with the same name are indistinguishable in a chip row. */
    private fun uniqueName(requested: String): String {
        val base = requested.trim().ifEmpty { "Rule" }
        val taken = _scripts.value.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base

        var suffix = 2
        while ("$base ($suffix)".lowercase() in taken) suffix++
        return "$base ($suffix)"
    }

    // endregion
}
