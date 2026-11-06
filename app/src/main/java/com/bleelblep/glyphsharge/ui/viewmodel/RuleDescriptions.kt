package com.bleelblep.glyphsharge.ui.viewmodel

import android.content.Context
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.ServiceScriptRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationId
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.triggers.TriggerType
import com.bleelblep.glyphsharge.ui.components.GlyphAnimations
import com.bleelblep.glyphsharge.ui.state.RuleUiState

/**
 * Projects a stored rule onto the row the home list and the constructor's own
 * list both render.
 *
 * One mapper rather than two: the two screens show the same rule and their
 * rows have to read the same, and a description that drifts between them is
 * the kind of difference a user reads as two different rules sharing a name.
 * It lives in the ViewModel package because that is the only layer both
 * owners are in — it needs strings and the installed-script lookup, so neither
 * the state package below nor the screens above could hold it without
 * pretending to be something they are not.
 */
fun GlyphRule.toRuleUiState(
    context: Context,
    serviceScriptRepository: ServiceScriptRepository,
): RuleUiState = RuleUiState(
    id = id,
    name = name,
    description = describe(context, serviceScriptRepository),
    isEnabled = enabled,
    // Only the constructor's list acts on it, but the fact belongs to the
    // rule rather than to the screen: a rule whose script is gone still has
    // a scriptId, and the export that would fail is exactly the one a row
    // should not offer.
    canExportScript = scriptId?.let { serviceScriptRepository.getById(it) != null } ?: false,
)

/**
 * "Screen off → Spiral": the two facts a rule row has to show at a glance.
 *
 * The trigger is spelled out by its [TriggerType.labelRes] rather than
 * derived from the id — "VPN connected" and "Low battery", not "Vpn
 * connected". A low-battery rule appends its threshold, since the number is
 * the difference between two rules that would otherwise read the same.
 *
 * An advanced rule leads with its *script* rather than a trigger: the
 * trigger field is only a fallback there, and "Warn at 15% → Spiral" is
 * what the user actually built. A script whose file is gone degrades to
 * the generic word rather than a blank — the rule is still real, it just
 * cannot fire until the script comes back.
 */
private fun GlyphRule.describe(
    context: Context,
    serviceScriptRepository: ServiceScriptRepository,
): String {
    val triggerText = if (isScripted) {
        val scriptName = serviceScriptRepository.getById(scriptId!!)?.name
            ?: context.getString(R.string.rules_custom_script)
        context.getString(R.string.rules_script_prefix, scriptName)
    } else {
        val type = TriggerType.fromId(triggerId)
        val trigger = type?.let { context.getString(it.labelRes) } ?: triggerId
        when (type) {
            // The number is the difference between two rules that would
            // otherwise read the same — for both parameterised triggers.
            TriggerType.LOW_BATTERY -> "$trigger (≤$lowBatteryThreshold%)"
            TriggerType.SHAKE -> "$trigger (${shakeThreshold.toInt()})"
            else -> trigger
        }
    }
    val animation = GlyphAnimations.list.firstOrNull { it.id == animationId }?.displayName
        ?: if (ScriptAnimation.isCustomId(animationId)) {
            context.getString(R.string.rules_custom_script)
        } else {
            animationId
        }
    // The arc is appended only when it is not the default, for the same
    // reason the thresholds print their number: two battery rules that differ
    // only in which strip they light would otherwise read the same. Only the
    // default is left off because on every other phone the choice does not
    // exist — a "(C)" there would describe a control the user was never
    // offered.
    val arcSuffix = if ((animationId == GlyphAnimationId.BATTERY.id) &&
        (batteryArc != GlyphRule.DEFAULT_BATTERY_ARC)
    ) {
        " ($batteryArc)"
    } else {
        ""
    }
    return "$triggerText → $animation$arcSuffix"
}
