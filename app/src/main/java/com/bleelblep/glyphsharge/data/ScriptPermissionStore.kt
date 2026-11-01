package com.bleelblep.glyphsharge.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which capabilities the user has granted to which script — the app's half of
 * the runtime-permissions model, and the only place those decisions live.
 *
 * **No entry means no grant.** A script that was never prompted (imported
 * before this store existed, hand-placed in the directory, or granted and
 * later had its file replaced out of band) runs with nothing, and the APIs it
 * did not get read as `nil` — the same answer a script gets for any API the
 * service surface never had. Prompting at import is what *creates* an entry;
 * silence is denial, which is the only safe way for the two to disagree.
 *
 * Keyed by the script's **bare** id, never the `service:` runtime form: a
 * decision belongs to the file on disk, and normalising at the edge here means
 * a caller cannot record a grant that outlives its own prefix spelling.
 */
@Singleton
class ScriptPermissionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ScriptPermissions"
        const val PREFS_NAME = "glyphsharge_script_permissions"
        private const val KEY_GRANTS = "grants"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Read once, held in memory, written through on every change.
     *
     * Small by construction — script ids and capability ids only — so there is
     * no reason to re-parse the JSON on every lookup the engine makes.
     */
    private var grants: Map<String, Set<ScriptCapability>> = load()

    /** The capabilities granted to [scriptId] (bare or `service:`-prefixed). */
    fun grantedFor(scriptId: String): Set<ScriptCapability> =
        grants[ServiceScript.stripPrefix(scriptId)].orEmpty()

    /** Whether one capability is granted. The engine asks this at load time. */
    fun isGranted(scriptId: String, capability: ScriptCapability): Boolean =
        capability in grantedFor(scriptId)

    /**
     * Records the user's accept for every capability in [capabilities].
     *
     * Takes the grant *set* rather than one at a time because the import prompt
     * answers for all of what a script asked in one decision — the Android
     * dialog is per install, not per permission, and so is this.
     */
    fun grant(scriptId: String, capabilities: Set<ScriptCapability>) {
        if (capabilities.isEmpty()) return
        val bare = ServiceScript.stripPrefix(scriptId)
        grants = grants + (bare to (grantedFor(bare) + capabilities))
        persist()
    }

    /**
     * Withdraws capabilities. Revocation takes effect on the script's next
     * load — the engine builds its API table once, at load, on purpose.
     */
    fun revoke(scriptId: String, capabilities: Set<ScriptCapability>) {
        if (capabilities.isEmpty()) return
        val bare = ServiceScript.stripPrefix(scriptId)
        val remaining = grantedFor(bare) - capabilities
        grants = if (remaining.isEmpty()) grants - bare else grants + (bare to remaining)
        persist()
    }

    /**
     * Every decision, for the Settings screen.
     *
     * Scripts with no entry are deliberately absent rather than present with an
     * empty set: a script the user was never asked about is not a script that
     * was denied, it is one that has not been asked yet.
     */
    fun grantsByScript(): Map<String, Set<ScriptCapability>> = grants

    private fun load(): Map<String, Set<ScriptCapability>> {
        val raw = prefs.getString(KEY_GRANTS, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().associate { scriptId ->
                val array = root.optJSONArray(scriptId) ?: JSONArray()
                val caps = (0 until array.length()).mapNotNull { index ->
                    // `ofId` dropping an unknown id is the forward-compat rule:
                    // a grant written by a newer build is preserved as… not
                    // here, but the script simply loses that one grant rather
                    // than the file failing to parse.
                    ScriptCapability.ofId(array.optString(index))
                }.toSet()
                scriptId to caps
            }
        }.getOrElse {
            Log.w(TAG, "Grants unreadable, starting empty", it)
            emptyMap()
        }
    }

    private fun persist() {
        val root = JSONObject()
        grants.forEach { (scriptId, caps) ->
            root.put(
                scriptId,
                JSONArray().apply {
                    // Enum declaration order: one script's list is byte-stable.
                    ScriptCapability.entries.filter { it in caps }.forEach { put(it.id) }
                },
            )
        }
        prefs.edit { putString(KEY_GRANTS, root.toString()) }
    }
}
