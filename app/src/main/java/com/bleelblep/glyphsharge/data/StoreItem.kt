package com.bleelblep.glyphsharge.data

import com.bleelblep.glyphsharge.glyph.device.DeviceType
import java.util.Locale

/**
 * One entry in the animation store's index.
 *
 * A shop item rather than a script: it is what the catalogue says exists, not
 * what the phone has. Nothing here has been downloaded, and [url] points at a
 * file that may have changed since the index was written — which is why
 * [sha256] is part of the entry and not an afterthought. A script is verified
 * against it before it is saved, so a catalogue that disagrees with its own
 * files cannot install something it did not describe.
 *
 * [devices] is a copy of what the script's own header says. The catalogue
 * generates its index from those headers, so this is a second reading of the
 * same line rather than a second source of truth: if the two ever disagree,
 * the file wins and the hash check is what notices.
 */
data class StoreItem(
    val id: String,
    val name: String,
    val author: String,
    val license: String,
    val description: String,
    val url: String,
    val sha256: String,
    val devices: Set<DeviceType>,
    val version: Int,
) {
    /** The models this item claims to work on, in declaration order. */
    val deviceNames: List<String>
        get() = DeviceType.entries.asSequence().filter { it in devices }.map { it.name }.toList()

    /** Whether this item is claimed to work on [type]. */
    fun supports(type: DeviceType?): Boolean = (type != null) && (type in devices)

    /**
     * The name this item is matched on against the phone's own scripts.
     *
     * **Case-folded and trimmed, because the two sides are written by different
     * people.** A catalogue says `Wave`; a user who imported the same file into
     * the studio and typed the name themselves ends up with `wave`, and
     * `CustomAnimationRepository.uniqueName` only ever appends `(2)` — it never
     * rewrites case. Comparing the raw strings would light the badge for one
     * user and not for the next, for no reason either could see.
     *
     * Not a comparison by id: an installed script gets a fresh local id on
     * import by design, so the name is the only value both sides keep.
     */
    val matchKey: String get() = name.trim().lowercase(Locale.ROOT)

    /**
     * A catalogue entry that names no model at all.
     *
     * An unrecognised or absent `devices` list is treated as "every model",
     * for the same reason [com.bleelblep.glyphsharge.glyph.script.ScriptFileFormat]
     * treats it that way in a header: a script written against the named groups
     * really does run everywhere, and `PHONE4` from a future release is an
     * honest claim rather than a mistake worth refusing the item over.
     */
    companion object {
        fun parseDevices(raw: String?): Set<DeviceType> {
            val named = raw
                ?.split(',', ';')
                ?.map { it.trim().uppercase(Locale.ROOT) }
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .asSequence()
                .mapNotNull { name -> DeviceType.entries.firstOrNull { it.name == name } }
                .toSet()

            return named.ifEmpty { DeviceType.entries.toSet() }
        }
    }
}