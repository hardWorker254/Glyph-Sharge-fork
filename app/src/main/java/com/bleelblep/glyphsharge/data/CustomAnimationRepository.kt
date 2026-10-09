package com.bleelblep.glyphsharge.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.device.DeviceType
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the animations the user wrote in the studio.
 *
 * **Source in files, index in preferences.** A script is edited often and can
 * grow past what a preference value should hold, so each one lives in its own
 * file under `filesDir/glyph_scripts`, and SharedPreferences keeps only the
 * small index that *describes* them — the name, the dates, the models, never
 * the Lua. Carrying the Lua as well is the thing the split exists to avoid, and
 * it used to: [reload] read the script out of the index and consulted the file
 * only to decide whether the row still existed, which left the file a
 * write-only artefact and the preference blob as the part that could grow.
 *
 * [reload] reads the files now, so the file decides in both directions: an
 * entry whose file is gone is dropped, and a file the index never recorded is
 * adopted rather than lost. The two can still disagree — only a damaged file
 * gets there — and then the index copy stands in, which is the one case where
 * the index is read as content rather than as shape.
 *
 * The list is exposed as a [StateFlow] because the animation pickers on the
 * feature cards are several screens away from the editor — saving a script has
 * to update the chips on Pulse Lock, Low Battery, NFC and Screen Off at once.
 */
@Singleton
class CustomAnimationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "CustomAnimations"
        const val PREFS_NAME = "glyphsharge_custom_animations"
        const val KEY_INDEX = "index"
        const val SCRIPT_DIR = "glyph_scripts"
        const val FILE_SUFFIX = ".glyphlua"

        /**
         * Suffix of the file a save writes before moving it into place — see
         * [writeScript]. Never a `.glyphlua` on its own, so a leftover of an
         * interrupted save can never be mistaken for a script by [reload].
         */
        private const val STAGING_SUFFIX = ".tmp"

        /** The shape an id has to have to become a file name. See [isSafeId]. */
        private val SAFE_ID = Regex("[a-zA-Z0-9_-]{1,64}")

        /** Index key holding the models each script claims to work on. */
        private const val KEY_DEVICES = "devices"
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

    /**
     * Rebuilds the list from the files, and rewrites the index to match.
     *
     * The file decides. This used to check only that each file *existed* and
     * then take the script's contents out of the index, so the two copies could
     * disagree with the index winning — the opposite of the rule this class
     * states, and the reason an edit made to a `.glyphlua` out of band was
     * never picked up.
     *
     * Three cases, and only one of them loses anything:
     *
     *  * **An index entry with no file** is dropped, as it always was.
     *  * **A file with no index entry** is adopted. [writeIndex] goes through
     *    `apply()`, so a process killed between the file write and the flush
     *    leaves the file behind and the entry missing; a reader that only
     *    walked the index then lost the user's script while it sat in the
     *    directory. Adoption is what makes the file authoritative rather than
     *    authoritative when convenient.
     *  * **A file that cannot be read or decoded** keeps the index copy, so a
     *    damaged file costs the newer content and not the row. This is the only
     *    place the index is read as content: every index written since it
     *    stopped carrying `source` holds metadata alone.
     *
     * Also clears out the staging files an interrupted [writeScript] leaves
     * behind.
     */
    fun reload() {
        val stored = runCatching { readIndex() }.getOrElse {
            Log.w(TAG, "Index unreadable, starting empty", it)
            emptyList()
        }

        val byId = LinkedHashMap<String, ScriptAnimation>()
        stored.forEach { entry ->
            if (!isSafeId(entry.id)) {
                Log.w(TAG, "Dropping an index entry whose id cannot name a file: ${entry.id}")
                return@forEach
            }
            readScript(entry.id, entry)?.let { byId[it.id] = it }
        }

        scriptDir.listFiles()?.forEach { file ->
            when {
                // A write that never reached its rename. Recreated on the next
                // save, so there is nothing here to salvage.
                file.name.endsWith(STAGING_SUFFIX) -> file.delete()

                !file.name.endsWith(FILE_SUFFIX) -> Unit

                else -> {
                    val id = file.name.removeSuffix(FILE_SUFFIX)
                    when {
                        id in byId -> Unit
                        !isSafeId(id) -> Log.w(TAG, "Ignoring a file whose name cannot be an id: $file.name")
                        // `null` fallback: there is no index copy to fall back
                        // to, so an undecodable orphan is skipped rather than
                        // listed as an empty script.
                        else -> readScript(id, null)?.let { byId[id] = it }
                    }
                }
            }
        }

        val next = byId.values.sortedByDescending { it.updatedAt }
        // Every time, not only when the shape changed. The index stopped
        // carrying `source` when this method started reading the files, so the
        // first reload after that upgrade has to rewrite every entry that still
        // does — otherwise the Lua stays in preferences for good.
        writeIndex(next)
        _animations.value = next
    }

    /**
     * What [id] really holds: the file's contents, or [fallback] when the file
     * is gone or damaged, or `null` when it is gone and there is nothing to fall
     * back to.
     */
    private fun readScript(id: String, fallback: ScriptAnimation?): ScriptAnimation? {
        val file = scriptFile(id)
        if (!file.exists()) return null

        val text = runCatching { file.readText() }.getOrElse {
            Log.w(TAG, "Script file unreadable, keeping the index copy: $id", it)
            return fallback
        }

        // A file with no `name:` header keeps the name it has always gone by in
        // the list: the one in its file name. Not the localized "My animation"
        // [draft] uses — that names something the user has just created, and
        // this names something that was already there. It also keeps a resource
        // lookup out of the path every script takes at startup.
        val fallbackName = fallback?.name?.takeIf { it.isNotBlank() } ?: id

        val decoded = runCatching {
            ScriptFileFormat.decode(text, fallbackName)
        }.getOrElse {
            Log.w(TAG, "Script file undecodable, keeping the index copy: $id", it)
            return fallback
        }

        // The file name is the id, and the header's own `id` is not consulted.
        // [scriptFile], [delete] and [getById] all key off the name, and
        // `decode` invents a fresh id for a header that carries none — believing
        // that one would list a script at an id whose file does not exist, so
        // the next delete would remove nothing.
        return decoded.copy(id = id)
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
        name = uniqueName(defaultName()),
        source = template,
    )

    fun save(animation: ScriptAnimation): ScriptAnimation {
        val stamped = animation.copy(updatedAt = System.currentTimeMillis())
        writeScript(stamped)
        upsert(stamped)
        return stamped
    }

    fun delete(id: String) {
        // The ids in the list are the ones this class accepted at [reload], so
        // this is a guard on the argument rather than on anything the app can
        // currently reach — and the argument is a path, so it earns one.
        if (!isSafeId(id)) {
            Log.w(TAG, "Refusing to delete with an id that cannot name a file: $id")
            return
        }
        scriptFile(id).delete()
        publish(_animations.value.filterNot { it.id == id })
    }

    /** A copy under a free name, so a built-in-looking script can be tweaked safely. */
    fun duplicate(id: String): ScriptAnimation? {
        val original = getById(id) ?: return null
        val copy = original.copy(
            id = ScriptAnimation.newId(),
            name = uniqueName("${original.name} ${context.getString(R.string.studio_copy_suffix)}"),
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
                name = uniqueName(parsed.name),
            )
            save(imported)
        }

    /**
     * Replaces the source of an animation already on the phone, in place.
     *
     * **The id, the name and the creation date are kept; everything else is
     * the new file's.** An update is the same animation in a newer version, so
     * it has to land on the same script: a second file under a fresh id would
     * leave the user holding two copies with no way to tell which one their
     * features point at — and, because [importFrom] runs every name through
     * [uniqueName], the leftover would be called `Wave (2)` while the one in
     * use stayed stale.
     *
     * The name is kept rather than taken from the file for the same reason: it
     * is the user's script, and the store's card is only ever offered when the
     * name still matches what the catalogue publishes.
     *
     * The models are *not* kept. Those come from the new file, because the
     * author is the one saying which phones this version works on and a release
     * that added a model should say so rather than inherit the old claim.
     *
     * @throws IllegalArgumentException when no such script is on the phone
     */
    suspend fun updateFrom(id: String, text: String): ScriptAnimation =
        withContext(Dispatchers.IO) {
            val existing = getById(id)
                ?: throw IllegalArgumentException("No animation with the id $id is stored.")
            val parsed = ScriptFileFormat.decode(text, existing.name)
            save(
                parsed.copy(
                    id = existing.id,
                    name = existing.name,
                    createdAt = existing.createdAt,
                ),
            )
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

            if (written.isFailure || (written.getOrNull() == null)) {
                resolver.delete(uri, null, null)
                return@withContext null
            }

            // Clears IS_PENDING, which is what publishes the file to the user.
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

    /**
     * Replaces a script's file in one step.
     *
     * [File.writeText] truncates before it writes, so a process killed in
     * between leaves a half-written file. That cost nothing while the index
     * held the only copy the app ever read; [reload] reads the file now, so the
     * new content goes to a sibling and is moved into place — the one operation
     * the filesystem will not let a reader observe half done.
     *
     * @throws IllegalStateException when the move itself fails, so the caller
     *   does not go on to index a script whose file is not there.
     */
    private fun writeScript(animation: ScriptAnimation) {
        val target = scriptFile(animation.id)
        val staging = File(scriptDir, "${target.name}$STAGING_SUFFIX")
        staging.writeText(ScriptFileFormat.encode(animation))
        if (!staging.renameTo(target)) {
            staging.delete()
            throw IllegalStateException("The script ${animation.id} could not be written.")
        }
    }

    /**
     * Whether [id] may be used as a file name.
     *
     * Every id this class generates is a hex UUID and every id a file header
     * carries has to match the same shape, so this is not the answer to an
     * attack anyone has performed: it is the guard on the one input here that
     * this class does not produce itself, the index. An id carrying a path
     * separator would build a file outside `glyph_scripts`, which is a write
     * outside the directory on save and a delete outside it in [delete].
     */
    private fun isSafeId(id: String) = SAFE_ID.matches(id)

    private fun defaultName() = context.getString(R.string.studio_default_name)

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
                // Read only to stand in for a file [reload] could not decode —
                // see the third case there. Written indexes stopped carrying
                // this field when the file became what [reload] reads, so an
                // entry with no `source` at all is the normal case and `""` is
                // only ever the fallback for a file that was already lost.
                source = item.optString("source"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                // Absent from every index written before the field existed,
                // which reads as every model — the same answer the file header
                // gives for a script that never declared one.
                devices = decodeIndexDevices(item.optJSONArray(KEY_DEVICES)),
            )
        }
    }

    /**
     * Models named in the index, or every model when none are named.
     *
     * Duplicates [ScriptFileFormat.decode]'s rule on purpose: the file is the
     * authority and the index is a copy, so a value the file would refuse has
     * to be refused here too. Reading the two differently would let an index
     * claim a narrower set of models than the script it points at — and since
     * [reload] asks the file first, this only speaks for a file that could not
     * answer, which is exactly when the copy has to be as strict as the thing
     * it stands in for.
     */
    private fun decodeIndexDevices(array: JSONArray?): Set<DeviceType> {
        val named = (0 until (array?.length() ?: 0)).asSequence().mapNotNull { index ->
            val name = array?.optString(index)?.trim()?.uppercase(Locale.ROOT).orEmpty()
            if (name.isBlank()) return@mapNotNull null
            DeviceType.entries.firstOrNull { it.name == name }
        }.toSet()

        return named.ifEmpty { ScriptAnimation.ALL_DEVICES }
    }

    private fun writeIndex(list: List<ScriptAnimation>) {
        val array = JSONArray()
        list.forEach { animation ->
            array.put(
                JSONObject().apply {
                    put("id", animation.id)
                    put("name", animation.name)
                    // No `source`. The file holds it, and holding it here meant
                    // every save re-serialised every script's full text into
                    // SharedPreferences — the exact growth the file split was
                    // introduced to stop, and the reason `reload` reads files.
                    put("createdAt", animation.createdAt)
                    put("updatedAt", animation.updatedAt)
                    // Enum declaration order, so the index of one script is
                    // byte-for-byte stable and can be diffed between releases.
                    put(
                        KEY_DEVICES,
                        JSONArray().apply {
                            DeviceType.entries
                                .filter { it in animation.devices }
                                .forEach { put(it.name) }
                        },
                    )
                },
            )
        }
        prefs.edit { putString(KEY_INDEX, array.toString()) }
    }

    /** Two scripts with the same name are indistinguishable in a chip row. */
    private fun uniqueName(requested: String, exceptId: String? = null): String {
        val base = requested.trim().ifEmpty { context.getString(R.string.studio_untitled) }
        val taken = _animations.value
            .asSequence()
            .filter { it.id != exceptId }
            .map { it.name.lowercase() }
            .toSet()
        if (base.lowercase() !in taken) return base

        var suffix = 2
        while ("$base ($suffix)".lowercase() in taken) suffix++
        return "$base ($suffix)"
    }
}
