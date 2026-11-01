package com.bleelblep.glyphsharge.rules

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.di.GlyphPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's list of [GlyphRule]s.
 *
 * A single JSON array under one preference key, the same storage shape the
 * animation library already uses for its index. Rules are small — no Lua
 * source lives here, only a [GlyphRule.scriptId] pointing at a stored
 * `.servicelua` — so one key keeps the whole list atomic: a save is one write,
 * and a torn write cannot leave two half-lists disagreeing about which rule is
 * enabled.
 *
 * Reads and writes are serialised on an internal lock because the host writes
 * (a rule's cooldown state, a toggle) from the main thread while the UI reads
 * the list for every recomposition.
 */
@Singleton
class GlyphRuleRepository @Inject constructor(
    @param:GlyphPrefs private val prefs: SharedPreferences,
) {

    private val lock = Any()

    private val _rules = MutableStateFlow<List<GlyphRule>>(emptyList())

    /**
     * Every rule, as a flow.
     *
     * Published rather than polled because a reader wants to *react* to a
     * change, not merely see the current state: `MainActivity` watches it to
     * know whether any enabled rule listens for an NFC event, and the window
     * registration that follows has to follow a switch flipped in another
     * screen — which happens without this Activity ever being resumed.
     */
    val rules: StateFlow<List<GlyphRule>> = _rules.asStateFlow()

    /** Every rule, in the order they were last saved. */
    fun getAll(): List<GlyphRule> = synchronized(lock) { read() }

    fun getById(id: String): GlyphRule? = getAll().firstOrNull { it.id == id }

    /** Inserts or replaces the rule with the same [GlyphRule.id]. */
    fun save(rule: GlyphRule) = synchronized(lock) {
        write(read().filterNot { it.id == rule.id } + rule)
    }

    fun saveAll(rules: List<GlyphRule>) = synchronized(lock) { write(rules) }

    fun delete(id: String) = synchronized(lock) {
        write(read().filterNot { it.id == id })
    }

    /** Flips one rule's switch without disturbing the others. */
    fun setEnabled(id: String, enabled: Boolean) = synchronized(lock) {
        write(read().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    private fun read(): List<GlyphRule> {
        val raw = prefs.getString(KEY_RULES, null)
        val rules = if (raw == null) {
            emptyList()
        } else {
            runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { GlyphRule.fromJson(array.getJSONObject(it)) }
            }
                // A corrupt list degrades to empty rather than throwing: the user
                // loses their rules but the app keeps running, and the next save
                // overwrites the unreadable bytes.
                .getOrDefault(emptyList())
        }
        // The rules the app used to preinstall, as projections of its features.
        //
        // They are gone from the app — the app ships the user's own rules and
        // nothing else — so they have to go from the storage too. Left on
        // disk they would come back looking exactly like the user's own: a
        // row named "Glow Gate" in the rules list, with a switch, that the
        // user never made and cannot get rid of, because the app would
        // re-derive it on the next start.
        val kept = rules.filterNot { it.id.startsWith(RETIRED_DERIVED_PREFIX) }
        if (kept.size != rules.size) {
            write(kept)
        }
        _rules.value = kept
        return kept
    }

    private fun write(rules: List<GlyphRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    private companion object {
        const val KEY_RULES = "glyph_rules_json"

        /**
         * The prefix the preinstalled feature rules were written under.
         *
         * History now: the features became rules and then went away with their
         * rules, and this is the name their rules were stored under. The
         * repository has to recognise them to delete them, and a name written
         * down once is a name that cannot drift.
         */
        const val RETIRED_DERIVED_PREFIX = "feature:"
    }
}
