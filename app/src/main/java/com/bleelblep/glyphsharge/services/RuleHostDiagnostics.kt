package com.bleelblep.glyphsharge.services

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The rule host's live verdict on every reload: which rules attached, and
 * why one that should be able to fire is dark.
 *
 * The host publishes here from the place it already writes to logcat, so a
 * rule list can show the reason in the UI itself instead of asking the user to
 * pull a logcat they may not be able to read. The map is keyed by rule id and
 * holds the attach reason for that rule, or `null` when it attached fine;
 * rules that are off stay out, because a reload only runs for enabled rules
 * and the toggle row already answers for them.
 */
@Singleton
class RuleHostDiagnostics @Inject constructor() {

    private val state = MutableStateFlow<Map<String, String?>>(emptyMap())

    /** Every rule the host has a verdict on: id to reason, `null` when fine. */
    val reasons: StateFlow<Map<String, String?>> = state.asStateFlow()

    /** A rule attached: its slot opens again on the next reload. */
    fun markAttached(ruleId: String) {
        publish(state.value + (ruleId to null))
    }

    /** A rule could not attach: the slot keeps its reason until the next reload. */
    fun markNotAttached(ruleId: String, reason: String) {
        publish(state.value + (ruleId to reason))
    }

    /** Tears the verdicts down with the service; a fresh reload republishes. */
    fun clear() {
        publish(emptyMap())
    }

    private fun publish(value: Map<String, String?>) {
        state.value = value
    }
}
