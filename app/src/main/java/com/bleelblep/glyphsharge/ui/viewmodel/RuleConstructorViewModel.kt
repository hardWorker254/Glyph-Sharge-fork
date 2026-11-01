package com.bleelblep.glyphsharge.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.ScriptPermissionStore
import com.bleelblep.glyphsharge.data.ServiceScriptRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import com.bleelblep.glyphsharge.glyph.script.ServiceScriptFileFormat
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.rules.GlyphRuleRepository
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.FeatureSpecs
import com.bleelblep.glyphsharge.services.RuleHostDiagnostics
import com.bleelblep.glyphsharge.tiles.TileStateBus
import dagger.Lazy
import com.bleelblep.glyphsharge.ui.state.RuleUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the rule constructor writes: one rule, one import, one host re-sync.
 *
 * Its own ViewModel rather than a second [HomeViewModel]. The one-owner rule
 * that `HomeViewModelOwnershipTest` pins is not a quirk of that class — two
 * instances of a state holder nobody coordinates is how a screen ends up
 * showing what nobody wrote — and `RuleConstructorActivity` is a second
 * screen, so it gets a second state holder of its own instead of a duplicate
 * of someone else's. Everything this needs is a `@Singleton` behind it, so
 * what it writes is visible to the home list the moment that Activity
 * resumes and [com.bleelblep.glyphsharge.MainActivity] refreshes it.
 *
 * The functions here are the ones that used to sit in the rules region of
 * `HomeViewModel`, moved rather than copied. The constructor now fronts its
 * own list — the studio's arrangement, list first and form behind it — so it
 * also owns a list's refresh, toggles, delete and export. That is still not a
 * second [HomeViewModel]: nothing here publishes home state, the rules flow
 * below is this screen's own view of the repository, and both lists agree
 * because they read the same `@Singleton` behind them rather than because
 * they share one state holder. `HomeViewModelOwnershipTest` guards the other
 * half of that — no screen resolving *its* HomeViewModel.
 */
@HiltViewModel
class RuleConstructorViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ruleRepository: GlyphRuleRepository,
    private val serviceController: FeatureServiceController,
    /**
     * The animation stack, reached only by [testRule].
     *
     * Lazy because nothing else on this screen touches it, and this screen is
     * mostly text: a form that cannot save has to be testable without standing
     * up the renderer, the script runner and the audio pipeline first.
     */
    private val glyphAnimationManager: Lazy<GlyphAnimationManager>,
    private val tileStateBus: TileStateBus,
    private val serviceScriptRepository: ServiceScriptRepository,
    private val scriptPermissionStore: ScriptPermissionStore,
    private val ruleHostDiagnostics: RuleHostDiagnostics,
) : ViewModel() {

    /**
     * One-shot complaints: a read that failed, an import that did not parse.
     *
     * A channel rather than state for the same reason `HomeViewModel` uses
     * one — two identical failures are two messages, and a `StateFlow` would
     * keep only the last.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * The installed `.servicelua` scripts, for the advanced tab.
     *
     * The repository's own flow — one source of truth, so an import anywhere
     * shows up everywhere, including on the permissions screen.
     */
    val serviceScripts: StateFlow<List<ServiceScript>> get() = serviceScriptRepository.scripts

    /**
     * The rules the constructor's own list draws, in repository order.
     *
     * Everything, projections included: a rule the constructor cannot show is a
     * rule it also cannot edit, and this list is where editing lives. The
     * duplicates that were the reason for filtering them — a card on the home
     * screen switching the same preference — belong to the home list, which
     * still filters them and keeps its cards.
     */
    private val _rules = MutableStateFlow<List<RuleUiState>>(emptyList())

    val rules: StateFlow<List<RuleUiState>> get() = _rules

    /**
     * The host's verdict on every rule, keyed by rule id and valued with the
     * reason the host could not attach it, or `null` when the rule attached
     * fine.
     *
     * The same source the host writes through — the constructor just reads it,
     * so the list can show why a rule is dark the moment the host says why. A
     * rule whose entry is present is a rule whose reload came back with a
     * complaint; the row renders the value, `null` means it attached cleanly.
     */
    val attachFailures: StateFlow<Map<String, String?>> get() = ruleHostDiagnostics.reasons

    init {
        refreshRules()
    }

    /** The full rule behind a list row, for the constructor to edit. */
    fun ruleFor(id: String): GlyphRule? = ruleRepository.getById(id)

    // region The list

    /**
     * Re-reads the repository into [rules].
     *
     * Called after every write this ViewModel makes: the form saves and then
     * pops back to the list, and a list still showing what the save replaced
     * would be the one screen not noticing the change.
     */
    fun refreshRules() {
        _rules.value = ruleRepository.getAll()
            .map { it.toRuleUiState(context, serviceScriptRepository) }
    }

    /**
     * Enables or disables one rule and re-syncs the host to match.
     *
     * The rule's own switch, and the host is told straight away: a rule that
     * is on but not attached is a row that says "on" and does nothing.
     */
    fun setRuleEnabled(id: String, enabled: Boolean) {
        ruleRepository.setEnabled(id, enabled)
        serviceController.syncRuleHost()
        tileStateBus.notifyChanged()
        refreshRules()
    }

    /**
     * Removes a rule and re-syncs the host so its trigger is detached.
     */
    fun deleteRule(id: String) {
        ruleRepository.delete(id)
        serviceController.syncRuleHost()
        tileStateBus.notifyChanged()
        refreshRules()
    }

    /**
     * The installed script behind a rule — what both exports write.
     *
     * `null` means the rule stopped being scripted or its file is gone since
     * the menu was drawn. The export says so rather than writing a file it no
     * longer has.
     */
    private fun scriptFor(ruleId: String): ServiceScript? =
        ruleFor(ruleId)?.scriptId?.let { serviceScriptRepository.getById(it) }

    /** The name the "Export to a file" picker opens with. */
    fun suggestedExportName(ruleId: String): String? =
        scriptFor(ruleId)?.let { ServiceScriptFileFormat.suggestedFileName(it) }

    /** Writes the rule's `.servicelua` to a location the user picked. */
    fun exportScriptTo(uri: Uri, ruleId: String) {
        viewModelScope.launch {
            val script = scriptFor(ruleId)
            if (script == null) {
                emit(context.getString(R.string.rules_msg_script_missing))
                return@launch
            }
            runCatching { serviceScriptRepository.exportTo(uri, script) }
                .onSuccess { emit(context.getString(R.string.studio_msg_exported, script.name)) }
                .onFailure {
                    emit(context.getString(R.string.studio_msg_export_failed, it.message.orEmpty()))
                }
        }
    }

    /** One-tap export into the public Downloads folder. */
    fun exportScriptToDownloads(ruleId: String) {
        viewModelScope.launch {
            val script = scriptFor(ruleId)
            if (script == null) {
                emit(context.getString(R.string.rules_msg_script_missing))
                return@launch
            }
            val uri = serviceScriptRepository.exportToDownloads(script)
            emit(
                if (uri != null) {
                    context.getString(
                        R.string.studio_msg_exported_downloads,
                        uri.lastPathSegment.orEmpty(),
                    )
                } else {
                    context.getString(R.string.studio_msg_downloads_failed)
                },
            )
        }
    }

    // endregion

    /**
     * Plays a rule's animation once, without waiting for its trigger.
     *
     * The same call the host makes for a real firing
     * ([com.bleelblep.glyphsharge.glyph.GlyphAnimationManager.playAnimationById]),
     * so a test shows what the fire will show — including for a feature's rule,
     * whose locked battery indicators resolve from the id they carry.
     */
    fun testRule(id: String) {
        val rule = ruleRepository.getById(id) ?: return
        emit(context.getString(R.string.toast_testing_generic, rule.name))
        viewModelScope.launch {
            glyphAnimationManager.get()
                .playAnimationById(rule.animationId, rule.durationMs)
        }
    }

    /**
     * Reads a picked `.servicelua` so the constructor can show what it asks
     * for *before* anything is installed — the prompt has to see the same
     * bytes the import will, and this is those bytes.
     */
    suspend fun readServiceScriptText(uri: Uri): String = serviceScriptRepository.readText(uri)

    /**
     * Saves a rule: new when [existingId] is null, an in-place update
     * otherwise. Either way the host is re-synced so a changed trigger or
     * animation takes effect on the next fire, and the constructor's own
     * list is re-read before the form is gone.
     *
     * [scriptId] is the advanced mode's one extra fact: non-null makes the rule
     * a `.servicelua` rule whose script decides when to fire, null keeps it the
     * simple trigger chain. Editing a scripted rule back to simple mode passes
     * null and clears the id — the mode toggle is the whole truth about which
     * shape the rule is.
     *
     * [code] is the advanced mode's code buffer, and it lands *before* the
     * rule: a rule pointing at code that never reached disk cannot fire, so a
     * failed write complains and leaves the rule exactly as it was.
     * [accepted] is what the prompt allowed above this save, intersected with
     * what the buffer declares now — the same intersection, taken the same
     * way, as an import.
     */
    fun saveRule(
        existingId: String?,
        name: String,
        triggerId: String,
        animationId: String,
        durationMs: Long,
        lowBatteryThreshold: Int,
        shakeThreshold: Float = GlyphRule.DEFAULT_SHAKE_THRESHOLD,
        scriptId: String? = null,
        code: String? = null,
        accepted: Set<ScriptCapability> = emptySet(),
    ) {
        viewModelScope.launch {
            val effectiveScriptId = if (code == null) {
                scriptId
            } else {
                val persisted = runCatching { persistRuleScript(scriptId, name, code, accepted) }
                if (persisted.isFailure) {
                    emit(context.getString(R.string.rules_msg_script_save_failed))
                    return@launch
                }
                persisted.getOrThrow()
            }

            val updated = existingId?.let { ruleRepository.getById(it) }
            val rule = if (updated != null) {
                // An edit keeps the id (and so the rule's identity and enabled
                // state) and replaces only the facts the constructor owns.
                updated.copy(
                    name = name.ifBlank { updated.name },
                    triggerId = triggerId,
                    animationId = animationId,
                    durationMs = durationMs,
                    lowBatteryThreshold = lowBatteryThreshold,
                    shakeThreshold = shakeThreshold,
                    scriptId = effectiveScriptId,
                )
            } else {
                GlyphRule(
                    id = GlyphRule.newId(),
                    name = name.ifBlank { "Rule" },
                    enabled = true,
                    triggerId = triggerId,
                    animationId = animationId,
                    durationMs = durationMs,
                    lowBatteryThreshold = lowBatteryThreshold,
                    shakeThreshold = shakeThreshold,
                    scriptId = effectiveScriptId,
                )
            }
            ruleRepository.save(rule)
            serviceController.syncRuleHost()
            tileStateBus.notifyChanged()
            // The form is about to pop back to the list this owns; the list is
            // re-read here rather than on resume so the new rule is there the
            // moment the form is gone.
            refreshRules()

            // Said out loud, because a save that worked and a save that did
            // not look the same from the form: the rule is written last, after
            // the seven projected ones, which on a phone puts it a screen and a
            // half below where the user is looking. Read back rather than
            // assumed — the repository is the whole claim, and a write that
            // silently did nothing is the case worth naming.
            val written = ruleRepository.getById(rule.id)
            emit(
                if (written != null) {
                    context.getString(R.string.rules_msg_saved, written.name)
                } else {
                    context.getString(R.string.rules_msg_not_saved)
                },
            )
        }
    }

    /**
     * What the prompt has already allowed for [scriptId] — the form's gate on
     * asking again, so a rule resaved with code that was already allowed does
     * not re-ask.
     */
    fun grantedFor(scriptId: String): Set<ScriptCapability> =
        scriptPermissionStore.grantedFor(scriptId)

    /**
     * Writes the constructor's code buffer into a script the rule can point at.
     *
     * The rule's own script is rewritten in place — the model here is "the
     * rule is its service", so editing the rule edits that script. A rule
     * with no script yet (or one whose script is gone) mints a new one,
     * named after the rule. Grants are recorded here, against whichever
     * script resulted, so an accepted prompt and an installed script never
     * part ways.
     *
     * @return the `service:` runtime id the rule stores
     */
    private suspend fun persistRuleScript(
        scriptId: String?,
        ruleName: String,
        code: String,
        accepted: Set<ScriptCapability>,
    ): String {
        val script = if (scriptId != null && serviceScriptRepository.getById(scriptId) != null) {
            serviceScriptRepository.replaceSource(scriptId, code)
        } else {
            serviceScriptRepository.importFrom(
                code,
                ruleName.trim().ifBlank { context.getString(R.string.rules_script_name_fallback) },
            )
        }
        val granted = ScriptCapability.detect(code) intersect accepted
        if (granted.isNotEmpty()) scriptPermissionStore.grant(script.id, granted)
        return script.runtimeId
    }

    /**
     * Installs a picked `.servicelua` and records the accepted capabilities
     * against the freshly installed copy.
     *
     * Detection runs on *this* read, not on the text the prompt was built
     * from: [accepted] is intersected with what the file declares now, so a
     * file swapped between the prompt and the import cannot carry home a
     * grant it never showed. Nothing is installed when the read fails; the
     * one-shot [messages] channel gets the complaint, same as every other
     * failing action here.
     */
    fun importServiceScript(
        uri: Uri,
        accepted: Set<ScriptCapability>,
        onInstalled: (ServiceScript) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val text = serviceScriptRepository.readText(uri)
                val requested = ScriptCapability.detect(text)
                val installed = serviceScriptRepository.importFrom(text, fileNameFor(uri))
                val granted = requested intersect accepted
                if (granted.isNotEmpty()) scriptPermissionStore.grant(installed.id, granted)
                installed
            }.onSuccess { installed ->
                onInstalled(installed)
                // The form shows the new chip appear; the list shows nothing
                // at all, so the confirmation has to come from here for both.
                emit(context.getString(R.string.studio_msg_imported, installed.name))
            }
                .onFailure { emit(context.getString(R.string.rules_import_failed)) }
        }
    }

    private fun fileNameFor(uri: Uri): String = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.removeSuffix(".${ServiceScriptFileFormat.EXTENSION}")
        ?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.rules_custom_script)

    private fun emit(message: String) {
        viewModelScope.launch { _messages.send(message) }
    }
}
