package com.bleelblep.glyphsharge.glyph.script

import java.util.Locale
import java.util.UUID

/**
 * One installed `.servicelua` rule: a name, the Lua source, and the identity
 * the rules layer stores.
 *
 * The near-twin of [ScriptAnimation], deliberately not its subclass or a
 * shared supertype: an animation and a service rule are stored, listed,
 * imported and deleted through different flows, and a shared base would have
 * to pretend the differences — the `devices` claim, the runtime id namespace —
 * were configuration rather than kind.
 *
 * The id namespace is what keeps the two apart where a string is all there is:
 * a rule's `scriptId` is `service:<id>` just as an animation is `custom:<id>`,
 * so a hand-edited rule file cannot point an advanced rule at an animation
 * and get a script that draws.
 */
data class ServiceScript(
    val id: String,
    val name: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    /** The string a [com.bleelblep.glyphsharge.rules.GlyphRule] stores. */
    val runtimeId: String get() = runtimeIdOf(id)

    companion object {
        /**
         * Namespace for service-script ids, the mirror of [ScriptAnimation.ID_PREFIX].
         *
         * `service:` rather than a second meaning for `custom:`: the two kinds
         * resolve through different repositories, and one prefix per kind is
         * what makes "which repository owns this id?" answerable without a lookup.
         */
        const val ID_PREFIX = "service:"

        /** A short, collision-free id. Only the first block of the UUID is used. */
        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
            .take(12).lowercase(Locale.ROOT)

        fun runtimeIdOf(id: String): String =
            if (id.startsWith(ID_PREFIX)) id else ID_PREFIX + id

        fun isServiceId(raw: String): Boolean = raw.trim().startsWith(ID_PREFIX)

        fun stripPrefix(raw: String): String = raw.trim().removePrefix(ID_PREFIX)
    }
}
