package com.bleelblep.glyphsharge.glyph.device

import android.os.Build
import com.nothing.ketchum.Glyph

/**
 * The Nothing Phone (3a) Pro's `Build.MODEL` string.
 *
 * Local because the bundled SDK predates the phone: `Glyph` has no constant
 * for it, and inventing a `Glyph.DEVICE_…` name would only be a guess about
 * someone else's vocabulary. If a newer jar adds one, this is the single line
 * to replace.
 *
 * Nothing depends on this for *detection* — `A059P` contains the (3a)'s `A059`
 * — so the app works on the Pro either way. It only decides which id to offer
 * the system service *second*, when the (3a)'s own is refused.
 */
const val PHONE3A_PRO_MODEL = "A059P"

/**
 * Every Nothing Phone model the app supports, together with the single check
 * that identifies it.
 *
 * This enum is the **only** place that asks "which phone is this?", so adding
 * a model means adding one entry and nothing else.
 *
 * **Identification reads [Build.MODEL], not the SDK's `Common.is*` helpers.**
 * Those helpers do nothing more than the same comparison — each one is
 * `Build.MODEL.contains(Glyph.DEVICE_…)` in the SDK jar, verified by reading its
 * bytecode — so doing it here costs nothing and buys three things: the model
 * strings are named in this file instead of hiding behind a snapshot's idea of
 * which phones exist; the check is a pure function of a string, so it is
 * testable on a laptop; and a phone released *after* the SDK was packaged can
 * be supported without waiting for a new jar.
 *
 * A model that shares another's **layout** does not get an entry of its own.
 * Phone (3a) Pro reports `A059P`, which contains `A059`, and its strip is
 * identical to the (3a)'s: same channels, same rhythm, same arcs. A second
 * `DeviceType` for it would mean a second set of channel tables to keep in step
 * for no difference anyone could see, and would leave every `when (profile.type)`
 * in the animation code one branch short — including the battery bar's, which
 * would quietly stop drawing the 3a's particular accents. So the Pro is a
 * Phone (3a) here, and the only place it is named is registration below.
 *
 * A *variant* that the SDK does name — the 2a Plus as `A142P`, the Phone (2)
 * Global as `AIN065` — is likewise the same layout and the same entry, carrying
 * its own id in [variantIds] so the service is told the truth about which
 * handset it is talking to.
 */
enum class DeviceType(
    /** The channel-layout id handed to `GlyphManager.register(...)`. */
    val sdkId: String,
    private val matches: (String?) -> Boolean,
    /**
     * Further spellings this SDK names for the same layout, base id first.
     *
     * `A142P` for the (2a) Plus, `AIN065` for the Phone (2) Global. Both are
     * real constants the system service knows, so a device reporting one is
     * registered under that string rather than under its family's — see
     * [registrationIds].
     */
    private val variantIds: List<String> = emptyList(),
    /**
     * Model strings this SDK does *not* name, offered only after the base id is
     * refused.
     *
     * One entry today — the (3a) Pro, which shipped months after the bundled
     * jar — and the reason [registrationIds] returns a list rather than one id:
     * whether the service answers to the variant string is not derivable from
     * the layout, and guessing wrong draws channels the phone does not have.
     */
    private val unlistedModelIds: List<String> = emptyList(),
) {
    PHONE1(Glyph.DEVICE_20111, { it.containsModel(Glyph.DEVICE_20111) }),
    PHONE2(
        Glyph.DEVICE_22111,
        {
            it.containsModel(Glyph.DEVICE_22111) ||
                it.containsModel(Glyph.DEVICE_22111I)
        },
        // AIN065: the Phone (2) Global, same strip, more RAM and a different
        // SoC — which is precisely why it gets its own id rather than being
        // assumed to answer to A065.
        variantIds = listOf(Glyph.DEVICE_22111I),
    ),
    PHONE2A(
        Glyph.DEVICE_23111,
        {
            it.containsModel(Glyph.DEVICE_23111) ||
                it.containsModel(Glyph.DEVICE_23113)
        },
        variantIds = listOf(Glyph.DEVICE_23113),
    ),
    PHONE3A(
        Glyph.DEVICE_24111,
        { it.containsModel(Glyph.DEVICE_24111) },
        unlistedModelIds = listOf(PHONE3A_PRO_MODEL),
    );

    /** Every id this SDK knows for this layout, least specific first. */
    private val knownIds: List<String> = listOf(sdkId) + variantIds

    /**
     * Every id to try at registration, best guess first.
     *
     * The list, rather than one id, because "which string does the system Glyph
     * service want for this phone" is a question only the service can answer.
     * Two rules decide the order, and they differ because the two situations do:
     *
     *  * a variant **the SDK names** goes first, since the service is built
     *    alongside those constants and plainly knows them — the (2a) Plus wants
     *    `A142P`, not `A142`;
     *  * an **unlisted** model string goes last, because the service may not
     *    have heard of it at all — the (3a) Pro is offered the (3a)'s `A059`
     *    first and `A059P` only if that was refused.
     *
     * The caller keeps the first id that registers; a false answer from a guess
     * would leave the strip drawing channels the phone does not have, which
     * looks like broken hardware rather than like a mistake.
     */
    fun registrationIds(model: String? = Build.MODEL): List<String> {
        // Most specific first among the ids this SDK names: `A142P` before
        // `A142`, which it would otherwise match as a substring.
        val preferred = knownIds
            .filter { model.containsModel(it) }
            .maxByOrNull { it.length }
            ?: sdkId
        return listOf(preferred) +
            knownIds.filterNot { it == preferred } +
            unlistedModelIds.filterNot { it == preferred }
    }

    companion object {
        /**
         * The connected model, or `null` on hardware without a Glyph strip.
         *
         * First match wins, so an entry for a specific variant has to come
         * before the one it would otherwise be swallowed by — the same ordering
         * question as "PHONE2A before PHONE2", settled here by construction:
         * `A142P` matches only PHONE2A, `A059P` only PHONE3A, and `AIN065`
         * only PHONE2, because none of the other ids is a substring of it.
         */
        fun detect(): DeviceType? = detect(Build.MODEL)

        /**
         * The same answer for [model], a `Build.MODEL` string such as `A059P`.
         *
         * Split out so the table above can be exercised without a Nothing phone
         * attached — every entry is a string comparison, and a check that only
         * runs on the one device it was written for is a check nobody runs.
         */
        fun detect(model: String?): DeviceType? =
            entries.firstOrNull { spec -> model != null && spec.matches(model) }
    }
}

/**
 * Whether a [Build.MODEL] string carries [id] in it.
 *
 * Nullable receiver and a name of its own, because an extension called
 * `contains` here would resolve to itself at every call site inside it.
 */
private fun String?.containsModel(id: String): Boolean = this?.contains(id) == true