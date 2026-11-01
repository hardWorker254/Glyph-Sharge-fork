package com.bleelblep.glyphsharge.rules

import org.json.JSONObject

/**
 * One user-defined automation: *when* something happens, *what* lights up.
 *
 * This is the unit the constructor creates and the generic rule host runs. It
 * replaces the fixed set of feature services with a list the user curates —
 * the same idea the eight hard-coded features expressed, but as data, so a new
 * combination costs no code.
 *
 * ## Simple vs advanced
 *
 * A rule is one of two things, told apart by [scriptId]:
 *
 *  * **Simple** — [scriptId] is `null`. The rule fires on [triggerId] (one of
 *    the [com.bleelblep.glyphsharge.triggers.TriggerType]s) and plays
 *    [animationId]. The trigger is chosen in the constructor's simple mode.
 *  * **Advanced** — [scriptId] names a `.servicelua` script. The script decides
 *    *when* to fire by calling `glyph.playAnimation()`; [triggerId] is then
 *    only the trigger the host attaches as a convenience default and is not the
 *    whole story. The animation played is still [animationId] — the script never
 *    names it, the app resolves it from what the user picked.
 *
 * Either way [animationId] is either a built-in `GlyphAnimationId` name or a
 * `custom:<uuid>` id of an installed `.glyphlua` animation.
 *
 * @param cooldownMs the minimum gap between two firings of this rule, so a
 *   chatty trigger (a battery broadcast, a shaky sensor) cannot machine-gun the
 *   strip.
 * @param lowBatteryThreshold the battery percentage at or below which a
 *   [com.bleelblep.glyphsharge.triggers.TriggerType.LOW_BATTERY] rule fires.
 *   Only meaningful for that trigger — every other trigger ignores it — but it
 *   lives on the rule because the threshold is per-rule, not a global: one rule
 *   can warn at 30%, another at 10%. Carried as data so the low-battery
 *   listener stays a plain trigger with no repository to reach for.
 * @param shakeThreshold the horizontal jolt, in m/s², at which a
 *   [com.bleelblep.glyphsharge.triggers.TriggerType.SHAKE] rule fires — same
 *   deal as [lowBatteryThreshold]: per-rule data, ignored by every other
 *   trigger, so one rule answers a hard knock and another a barely-there tap.
 *   The default mirrors `GlyphServiceSettings.SHAKE_MEDIUM`, the sensitivity
 *   Power Peek has shipped with.
 * @param preempt how the rule meets a strip somebody else is holding. `true`
 *   (the default) interrupts — a user rule fires because of something the user
 *   chose to be interrupted for; `false` skips instead, which is how the
 *   features this refactor migrates behaved (Charging, Power Peek and Low
 *   Battery all tolerate a busy strip) and which is preserved rather than
 *   "upgraded" to match the default.
 * @param applyScriptCapMs whether the effective run length is resolved through
 *   the animation's natural length when it is shorter than [durationMs].
 *   `false` runs the raw configured duration, which is what NFC and Low Battery
 *   have always done.
 * @param acquireWakeLock whether a firing holds the CPU for the run. Only
 *   makes sense for a trigger that can arrive while dozing — a battery
 *   threshold or a tap on a dark screen — so it defaults to `false` and a
 *   migrated feature sets it to what it used to do itself.
 */
data class GlyphRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val triggerId: String,
    val animationId: String,
    val durationMs: Long,
    val scriptId: String? = null,
    val cooldownMs: Long = 0L,
    val lowBatteryThreshold: Int = DEFAULT_LOW_BATTERY_THRESHOLD,
    val shakeThreshold: Float = DEFAULT_SHAKE_THRESHOLD,
    val preempt: Boolean = true,
    val applyScriptCapMs: Boolean = true,
    val acquireWakeLock: Boolean = false,
) {
    /** Whether this is an advanced rule driven by a `.servicelua` script. */
    val isScripted: Boolean get() = scriptId != null

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_ENABLED, enabled)
        put(KEY_TRIGGER, triggerId)
        put(KEY_ANIMATION, animationId)
        put(KEY_DURATION, durationMs)
        scriptId?.let { put(KEY_SCRIPT, it) }
        put(KEY_COOLDOWN, cooldownMs)
        put(KEY_LOW_BATTERY_THRESHOLD, lowBatteryThreshold)
        put(KEY_SHAKE_THRESHOLD, shakeThreshold)
        put(KEY_PREEMPT, preempt)
        put(KEY_APPLY_SCRIPT_CAP, applyScriptCapMs)
        put(KEY_ACQUIRE_WAKE_LOCK, acquireWakeLock)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TRIGGER = "trigger"
        private const val KEY_ANIMATION = "animation"
        private const val KEY_DURATION = "durationMs"
        private const val KEY_SCRIPT = "scriptId"
        private const val KEY_COOLDOWN = "cooldownMs"
        private const val KEY_LOW_BATTERY_THRESHOLD = "lowBatteryThreshold"
        private const val KEY_SHAKE_THRESHOLD = "shakeThreshold"
        private const val KEY_PREEMPT = "preempt"
        private const val KEY_APPLY_SCRIPT_CAP = "applyScriptCapMs"
        private const val KEY_ACQUIRE_WAKE_LOCK = "acquireWakeLock"

        /**
         * Reads a rule written by [toJson].
         *
         * A rule missing its animation is dropped by the caller rather than
         * defaulted here: a rule that would light up an arbitrary animation is
         * worse than a rule that does not exist. Everything else has a
         * defensible default so a hand-edited or partially-written file still
         * loads.
         */
        fun fromJson(json: JSONObject): GlyphRule? {
            val id = json.optString(KEY_ID).takeIf { it.isNotBlank() } ?: return null
            val animationId = json.optString(KEY_ANIMATION).takeIf { it.isNotBlank() } ?: return null
            return GlyphRule(
                id = id,
                name = json.optString(KEY_NAME, id),
                enabled = json.optBoolean(KEY_ENABLED, true),
                triggerId = json.optString(KEY_TRIGGER),
                animationId = animationId,
                durationMs = json.optLong(KEY_DURATION, DEFAULT_DURATION_MS),
                scriptId = json.optString(KEY_SCRIPT).takeIf { it.isNotBlank() },
                cooldownMs = json.optLong(KEY_COOLDOWN, 0L),
                lowBatteryThreshold = json.optInt(KEY_LOW_BATTERY_THRESHOLD, DEFAULT_LOW_BATTERY_THRESHOLD),
                shakeThreshold = json.optDouble(KEY_SHAKE_THRESHOLD, DEFAULT_SHAKE_THRESHOLD.toDouble()).toFloat(),
                preempt = json.optBoolean(KEY_PREEMPT, true),
                applyScriptCapMs = json.optBoolean(KEY_APPLY_SCRIPT_CAP, true),
                acquireWakeLock = json.optBoolean(KEY_ACQUIRE_WAKE_LOCK, false),
            )
        }

        /** A fresh unique id for a new rule. */
        fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

        /** The duration a rule with no explicit one uses. */
        const val DEFAULT_DURATION_MS = 3_000L

        /** The battery percentage a low-battery rule with no explicit one uses. */
        const val DEFAULT_LOW_BATTERY_THRESHOLD = 20

        /**
         * The jolt a shake rule with no explicit threshold uses, in m/s².
         *
         * Deliberately the same number as `GlyphServiceSettings.SHAKE_MEDIUM`
         * — the sensitivity Power Peek has always shipped with — so a rule
         * built in the constructor shakes exactly as hard as the feature it
         * replaced.
         */
        const val DEFAULT_SHAKE_THRESHOLD = 18.0f
    }
}

/**
 * Whether any rule in this list is switched on and listens for [triggerId].
 *
 * A plain question with one answer, asked in one place: the app used to have a
 * feature for NFC, and the window registration that lets a tag reach the app
 * followed *that* feature's switch. The features are gone, and a rule is what
 * asks for a tag now — so the question belongs to the rules.
 *
 * It is a function rather than an inline check because the answer decides
 * whether the app holds a window registration, and that is worth a test.
 */
fun List<GlyphRule>.hasEnabledTrigger(triggerId: String): Boolean =
    any { it.enabled && it.triggerId == triggerId }
