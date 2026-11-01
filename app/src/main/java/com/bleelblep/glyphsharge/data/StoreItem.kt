package com.bleelblep.glyphsharge.data

import com.bleelblep.glyphsharge.glyph.device.DeviceType
import java.util.Locale

/**
 * What kind of script a catalogue entry points at — the one fact that decides
 * what its card can do.
 *
 * An [ANIMATION] is a `.glyphlua`: the studio imports it, the strip plays it,
 * and "Test on glyph" and "Update" both mean something for it. A [SERVICE] is
 * a `.servicelua`: it cannot draw, so there is nothing to preview, and there is
 * no local animation for an update to overwrite — it installs through the
 * capability prompt instead, and stops there.
 *
 * Not derived at the call site from a file extension, because the screen, the
 * ViewModel and the parser all have to answer the same question and three
 * answers to one question is how a card ends up offering a button that does
 * nothing.
 */
enum class StoreItemKind {
    /** A `.glyphlua`: drawn directly by the strip. */
    ANIMATION,

    /** A `.servicelua`: fires animations from the background, draws nothing. */
    SERVICE,
    ;

    companion object {
        /**
         * Reads the catalogue's `type`, falling back to the file's extension.
         *
         * **The extension is a fallback for the `animation` answer too, not
         * only for `service`.** The index this build reads has no `type` field
         * at all, so today every entry arrives through the extension path — and
         * a catalogue that publishes an unrecognised `type` should degrade to
         * "whatever the file says" rather than to a hard-coded guess. Only
         * `.servicelua` names a kind; every other extension is an animation,
         * which is the shape of every entry published so far.
         *
         * The query and fragment are stripped first: a URL that hands out
         * `foo.servicelua?token=…` must not have its kind decided by the token.
         */
        fun parse(type: String?, url: String): StoreItemKind {
            when (type?.trim()?.lowercase(Locale.ROOT)) {
                "service" -> return SERVICE
                "animation" -> return ANIMATION
            }

            val fileName = url
                .substringAfterLast('/', missingDelimiterValue = "")
                .substringBefore('?')
                .substringBefore('#')
            return if (fileName.endsWith(EXTENSION_SERVICELUA, ignoreCase = true)) {
                SERVICE
            } else {
                ANIMATION
            }
        }

        private const val EXTENSION_SERVICELUA = ".servicelua"
    }
}

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
 *
 * [kind] is not defaulted: it decides which buttons a card offers, so a call
 * site that has not decided is a bug the compiler should find rather than one
 * a user finds when "Test on glyph" does nothing.
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
    val kind: StoreItemKind,
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