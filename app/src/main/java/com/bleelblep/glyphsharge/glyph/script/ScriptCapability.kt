package com.bleelblep.glyphsharge.glyph.script

import androidx.annotation.StringRes
import com.bleelblep.glyphsharge.R

/**
 * A privileged slice of the `.servicelua` API the *user* — not the app —
 * decides whether a script gets.
 *
 * The model is Android's runtime permissions, not a hard-coded blocklist:
 * instead of the app refusing to ever expose, say, sound to a service script,
 * the capability exists, the script that reaches for it *declares* that reach
 * by using its API, and the user is shown what the script wants before it is
 * installed. A decision is per script, never global, and is changeable later
 * in Settings → «Разрешения» — the app has no opinion beyond making the ask
 * honest and the enforcement structural.
 *
 * Enforcement is the same shape as the sandbox's banned globals: a capability
 * that has not been granted is simply **absent from the Lua table**, so an
 * ungranted `glyph.battery` reads as `nil` rather than as a value the script
 * was never allowed to see. There is no runtime check sprinkled through the
 * API — the table is built per grant set at load time, and a revoked grant
 * takes effect on the next reload.
 *
 * ### How a script declares a capability
 *
 * [detect] scans the source for the capability's [apiNames] on the `glyph`
 * table. This is the declaration: a script that says `glyph.battery` in its
 * source is asking to read the device state, exactly as an Android app names
 * a permission in its manifest before the system will show a prompt. A script
 * cannot use an API without naming it — Lua has no way to reach a table key
 * this scanner would miss without constructing the name dynamically, and a
 * script that does that gets `nil`, not a bypass.
 *
 * ### Adding a capability
 *
 * One enum entry with its API names and two strings. The import prompt, the
 * permission store and the engine's gating all read the same list, so there
 * is exactly one place to touch when a new privileged surface lands.
 */
enum class ScriptCapability(
    /** Stable stored id; survives renames of the display strings. */
    val id: String,

    /**
     * Names on the `glyph` table that mean "this script wants this".
     *
     * Matched as `glyph.<name>` in the source, whitespace-tolerant — the same
     * reading a Lua author would give it.
     */
    val apiNames: List<String>,

    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    /**
     * Reading the phone's physical state: battery level and charging status.
     *
     * Not dangerous in the permission sense, but privileged in the honest one:
     * it is the first thing a `.servicelua` can observe about the device
     * beyond the triggers that woke it, and it is the shape every future
     * capability (sound, sensors, …) will take.
     */
    DEVICE_STATE(
        id = "device_state",
        apiNames = listOf("battery", "charging"),
        labelRes = R.string.capability_device_state_label,
        descriptionRes = R.string.capability_device_state_description,
    ),
    ;

    /** Whether [source] reaches for this capability's API. */
    fun isRequestedIn(source: String): Boolean = apiNames.any { name ->
        Regex("""glyph\s*\.\s*${Regex.escape(name)}\b""").containsMatchIn(source)
    }

    companion object {
        /**
         * The capabilities [source] asks for — the set the import prompt shows
         * and the permission store records a decision against.
         */
        fun detect(source: String): Set<ScriptCapability> =
            entries.filter { it.isRequestedIn(source) }.toSet()

        /** Resolves a stored id, or `null` for one this build does not know. */
        fun ofId(raw: String): ScriptCapability? =
            entries.firstOrNull { it.id == raw }
    }
}
