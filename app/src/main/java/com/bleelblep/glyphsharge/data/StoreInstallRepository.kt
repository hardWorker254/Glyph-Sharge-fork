package com.bleelblep.glyphsharge.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One catalogue entry as it sits on the phone: which version of it, and which
 * local animation it became.
 *
 * [version] is the catalogue's [StoreItem.version] at the moment of the
 * install. It is the only copy of that number anywhere on the device — see
 * [StoreInstallRepository] for why there has to be one.
 */
data class StoreInstall(
    val storeId: String,
    val version: Int,
    val animationId: String,
    val installedAt: Long = System.currentTimeMillis(),
)

/**
 * What the phone remembers about the store items it has installed.
 *
 * **The version is the whole reason this file exists.** The catalogue carries
 * a version and the phone's own scripts carry none: an installed animation is
 * a `.glyphlua` with a name, some Lua and two timestamps. So "is there a newer
 * one?" cannot be answered from either side alone, and without an answer the
 * card can only ever say "Installed" — which is the one thing a user opening
 * the store after a week away most wants to be told otherwise about.
 *
 * ### Keyed by catalogue id, not by name
 *
 * [ScriptStoreRepository] strips nothing and the studio's
 * [CustomAnimationRepository.uniqueName] renames duplicates, so a name is not
 * a stable identity on either side. The id is: the catalogue fixes it at
 * publish time and a version bump does not change it.
 *
 * ### Why the local animation id is stored as well
 *
 * Because an update has to overwrite *one specific script* rather than
 * whatever happens to be called the same thing. A user who installs `Wave` and
 * then writes their own `Wave` in the studio holds two files; the one the store
 * installed is the one an update may replace, and only its id says which. The
 * record is therefore dropped as soon as that id is gone from the phone —
 * see the ViewModel's `derived`, which is where that judgement lives because
 * the phone's script list is not this class's to read.
 *
 * ### Not the script's own file
 *
 * Provenance deliberately does not travel in the `.glyphlua` header. That file
 * is a plain Lua script a user can export, edit in any text editor and re-import
 * by hand, and a field only this app understands would be an unknown line to
 * everything else that reads it.
 */
@Singleton
class StoreInstallRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "StoreInstalls"

        const val PREFS_NAME = "glyphsharge_store_installs"
        const val KEY_INSTALLS = "installs"

        private const val KEY_STORE_ID = "storeId"
        private const val KEY_VERSION = "version"
        private const val KEY_ANIMATION_ID = "animationId"
        private const val KEY_INSTALLED_AT = "installedAt"

        /**
         * The one place a catalogue id becomes a key.
         *
         * Public because three things have to agree on it and only this class
         * can see the other two: [record] writes the key, [read] rebuilds it, and
         * the store screen *looks the record up* by the catalogue's own id. When
         * the write side folded and the read side folded but the lookup did
         * not, the history was written and read correctly and still came back
         * `null` — so the store offered an already-installed item again and the
         * "Update" button never appeared.
         *
         * Callers holding a raw catalogue id should index `installs` by
         * `foldedForComparison(id)` rather than by `id`.
         *
         * [Locale.ROOT] rather than the default locale: in Turkish
         * `I`.lowercase() is `ı`, which would fold `WAVE` to something no entry
         * for `Wave` could match — the same bug as above, for one language only.
         */
        fun foldedForComparison(storeId: String): String =
            storeId.trim().lowercase(Locale.ROOT)
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _installs = MutableStateFlow(read())

    /** Every installed item, keyed by catalogue id. */
    val installs: StateFlow<Map<String, StoreInstall>> = _installs.asStateFlow()

    /**
     * Records that `animationId` now holds `version` of `storeId`.
     *
     * An update and a first install call this alike, which is what stops the
     * two paths from being told apart by anything on the write side: the store
     * id and the local id are the same pair either way, and only the version
     * changes.
     */
    /**
     * Records that `animationId` now holds `version` of `storeId`.
     *
     * An update and a first install call this alike, which is what stops the
     * two paths from being told apart by anything on the write side: the store
     * id and the local id are the same pair either way, and only the version
     * changes.
     *
     * ### The id is folded here, exactly as [read] folds it
     *
     * [read] has always keyed its map by `storeId.lowercase()`, so a catalogue
     * entry published as `Wave` came back as `wave` and every lookup keyed by
     * the raw id missed. [record] wrote the raw id, so the *in-memory* map was
     * right until the process restarted and the disk read folded it — and then
     * the history vanished. Installing an item, restarting, and finding the
     * store offering it again with no "Update" button ever appearing is the
     * whole of that bug.
     *
     * Folding on both sides is what stops the map holding `Wave` and `wave` as
     * two entries, which it otherwise did within a single session and then
     * silently collapsed to one on the next read — the count changing across a
     * restart, and which record survived depending on array order.
     */
    fun record(storeId: String, version: Int, animationId: String) {
        val key = foldedForComparison(storeId)

        // `update` rather than read-modify-write. Two installs can overlap — the
        // store screen starts the download on a tap and only clears the flag in
        // a `finally` after the animation is imported — and a plain read of
        // `_installs.value` gave both the same map, so whichever published
        // second silently dropped the first. The second install of a second item
        // then looked like it had never happened.
        var next: Map<String, StoreInstall> = emptyMap()
        _installs.update { current ->
            next = current + (key to StoreInstall(key, version, animationId))
            next
        }
        persist(next)
    }


    /**
     * Writes the history out, in a form that is byte-for-byte stable.
     *
     * Sorted by key so the stored string can be diffed, in the same spirit as
     * the animation index's device lists.
     *
     * Separate from the flow update because the two have different failure
     * modes: the flow is what the screen reads and must reflect the install
     * even if the disk write does not survive, whereas the disk is what the
     * *next* launch reads and is the only thing that makes an install outlive
     * the process.
     */
    private fun persist(installs: Map<String, StoreInstall>) {
        val array = JSONArray()
        installs.values.sortedBy { it.storeId }.forEach { install ->
            array.put(
                JSONObject().apply {
                    put(KEY_STORE_ID, install.storeId)
                    put(KEY_VERSION, install.version)
                    put(KEY_ANIMATION_ID, install.animationId)
                    put(KEY_INSTALLED_AT, install.installedAt)
                },
            )
        }
        prefs.edit { putString(KEY_INSTALLS, array.toString()) }
    }

    /**
     * The records as they were left on disk, skipping any entry missing either
     * id.
     *
     * A record without a catalogue id cannot be looked up and one without an
     * animation id has nothing to update, so neither is a state the store could
     * act on — they are dropped rather than surfaced as an update that would
     * then fail at the press.
     *
     * Unreadable preferences are treated as none, for the same reason
     * [CustomAnimationRepository] does: losing the history costs the user a few
     * "Обновить" buttons, refusing to start over it costs them the store.
     */
    private fun read(): Map<String, StoreInstall> = runCatching {
        val raw = prefs.getString(KEY_INSTALLS, null) ?: return emptyMap()
        val array = JSONArray(raw)
        (0 until array.length()).asSequence().mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val storeId = foldedForComparison(item.optString(KEY_STORE_ID))
            val animationId = item.optString(KEY_ANIMATION_ID).trim()
            if (storeId.isBlank() || animationId.isBlank()) return@mapNotNull null

            StoreInstall(
                storeId = storeId,
                // A record written before the version was tracked reads as the
                // first version, which is the honest reading: an install this
                // build has no number for was certainly not made from a
                // catalogue entry numbered higher than the format's start.
                version = item.optInt(KEY_VERSION, 1).coerceAtLeast(1),
                animationId = animationId,
                installedAt = item.optLong(KEY_INSTALLED_AT, 0L),
            )
        // Already folded by `foldId` above, and folding again would be a no-op
        // at best — `associateBy` is what collapses a history written before the
        // write side was fixed, where two rows for `Wave` and `wave` can both be
        // on disk. Last one wins, which is the later install.
        }.associateBy { it.storeId }
    }.onFailure { Log.w(TAG, "Install history unreadable, treating as empty", it) }
        .getOrDefault(emptyMap())
}