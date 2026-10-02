package com.bleelblep.glyphsharge.glyph.script

import com.bleelblep.glyphsharge.glyph.device.DeviceType
import java.util.Locale
import java.util.UUID

/**
 * One user-written animation: a name, a Lua source, and the identity the
 * settings layer stores.
 *
 * Custom animations live side by side with the built-in ones in
 * `SettingsRepository`, which is why they need a distinguishable id. The
 * built-in ids (`C1`, `WAVE`, …) are plain words understood by
 * `GlyphAnimationId`, so a custom one is stored as `custom:<uuid>` and
 * [isCustomId] tells the two apart without a lookup table.
 */
data class ScriptAnimation(
    val id: String,
    val name: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,

    /**
     * The models this script claims to work on, as written in the file header.
     *
     * **Every model by default, and that default is the honest answer.** A
     * script written against the named channel groups — `glyph.ch.c` and the
     * rest — reads its own layout from [ScriptSession.group]
     * and so behaves correctly on a four-segment Phone (1) and a twenty-segment
     * Phone (3a) alike. Declaring three models is therefore a statement about
     * what the author chose to support, not a description of what the code
     * needs.
     *
     * The default rather than an empty set is also what keeps every script
     * already on a phone working: none of them has this field, and none of
     * them is broken by not having it.
     */
    val devices: Set<DeviceType> = ALL_DEVICES,
) {
    /** The string handed to the settings layer and the animation pickers. */
    val runtimeId: String get() = runtimeIdOf(id)

    /** Whether this script is claimed to work on [type]. */
    fun supports(type: DeviceType?): Boolean = (type != null) && (type in devices)

    companion object {
        /** Every model, the default value of [devices]. */
        val ALL_DEVICES: Set<DeviceType> = DeviceType.entries.toSet()
        /**
         * Namespace for ids that only this app can resolve. Anything without it
         * is handed to [com.bleelblep.glyphsharge.glyph.GlyphAnimationId].
         */
        const val ID_PREFIX = "custom:"

        /**
         * How long a script may run before the watchdog stops it anyway.
         *
         * A script has no duration: it is a program, and it ends when its code
         * ends. This only exists so that a script which never finishes — an
         * endless loop, a wait that never returns — cannot hold the glyph
         * forever with no way to tell why. Nothing in normal use comes close.
         */
        const val SAFETY_CAP_MS = 30_000L

        /** A short, collision-free id. Only the first block of the UUID is used. */
        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
            .take(12).lowercase(Locale.ROOT)

        fun runtimeIdOf(id: String): String =
            if (id.startsWith(ID_PREFIX)) id else ID_PREFIX + id

        fun isCustomId(raw: String): Boolean = raw.trim().startsWith(ID_PREFIX)

        fun stripPrefix(raw: String): String = raw.trim().removePrefix(ID_PREFIX)
    }
}
