package com.bleelblep.glyphsharge.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.ScriptPermissionStore
import com.bleelblep.glyphsharge.data.ServiceScriptRepository
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphDeviceReport
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.audio.PlaybackAudioSource
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.FeatureSpecs
import com.bleelblep.glyphsharge.services.GlyphServiceSwitch
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.rules.GlyphRuleRepository
import com.bleelblep.glyphsharge.tiles.TileStateBus
import com.bleelblep.glyphsharge.ui.state.FeatureUiState
import com.bleelblep.glyphsharge.ui.state.HomeUiState
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the home screen: the master glyph switch and the six feature
 * toggles.
 *
 * Transient strings still need a Toast, so they are pushed through [messages]
 * as one-shot events rather than held in state.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    glyphManager: GlyphManager,
    /**
     * The manager the visualiser's "Test" button and a rule row's ▶ drive.
     *
     * Public and lazy because those dialogs are plain Composables: they read it
     * through [com.bleelblep.glyphsharge.ui.theme.LocalHomeViewModel], which
     * hands back the instance this Activity drives. Lazy because nothing else on
     * this screen reaches the animation stack — a list of switches and rules
     * should not have to stand up a renderer to be tested, and the one thing
     * this screen most often does is re-read its own state.
     */
    val glyphAnimationManager: Lazy<GlyphAnimationManager>,
    private val serviceController: FeatureServiceController,
    private val ruleRepository: GlyphRuleRepository,
    private val glyphServiceSwitch: GlyphServiceSwitch,
    private val tileStateBus: TileStateBus,
    private val playbackAudioSource: PlaybackAudioSource,
    private val serviceScriptRepository: ServiceScriptRepository,
    private val scriptPermissionStore: ScriptPermissionStore,
    private val deviceReport: GlyphDeviceReport,
) : ViewModel() {

    /**
     * The capture singleton, for the one card that has to ask it whether a
     * projection token is already live.
     *
     * A `LazyListScope` cannot inject for itself, so the card list reads this
     * from the ViewModel it is already given. `PlaybackAudioSource` is a
     * `@Singleton`, so this is the object the visualiser service captures
     * through.
     */
    val musicCaptureSource: PlaybackAudioSource get() = playbackAudioSource

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * "Switch the visualiser on, and do whatever it takes."
     *
     * A request rather than an action, because the two things it needs can
     * only come from an Activity — the microphone permission and the capture
     * confirmation — and a ViewModel cannot ask for either. The card's switch
     * and the Quick Settings tile both raise the same request and let
     * `MainActivity` answer it, which is why there is one chain rather than
     * two that could answer differently.
     *
     * Both ends of that chain now hold the same instance: `MainActivity`
     * drains it, and the card raises on it through `LocalHomeViewModel`. When
     * the screen resolved its own `HomeViewModel` the card's request went into
     * a channel nobody was reading — it filled up, and nothing happened, with
     * no error to show for it.
     *
     * Buffered on purpose: the visualiser card is the last one in the home
     * list, so a request raised while the app is still starting would find
     * nobody collecting it yet.
     */
    private val _musicCaptureRequests = Channel<Unit>(Channel.BUFFERED)
    val musicCaptureRequests: Flow<Unit> = _musicCaptureRequests.receiveAsFlow()

    fun requestMusicCapture() {
        _musicCaptureRequests.trySend(Unit)
    }

    /**
     * The visualiser card's switch, and what the tile means by "turn it on".
     *
     * Off goes straight through, as it always did: nothing about stopping it
     * needs an Activity.
     */
    fun toggleMusicVisualizer(enabled: Boolean) {
        if (enabled) requestMusicCapture()
        else setFeatureEnabled(GlyphFeature.MUSIC_VISUALIZER, enabled = false)
    }

    /**
     * There is no hook here any more.
     *
     * `enableForegroundDispatch` needs a real `Activity`, which a ViewModel
     * cannot hold — so this used to hand `MainActivity` a callback to be told
     * through. That only worked while one screen could switch NFC: a hook is
     * installed by a screen and dropped with it, and the feature's switch is
     * now reachable from a rule row on this screen *and* from the
     * constructor's list. `MainActivity` watches
     * [FeatureServiceController.featureStates] instead, so the preference is
     * followed by whichever reader needs it and nothing has to be installed.
     */

    private val _isNothingPhone = glyphManager.isNothingPhone()

    init {
        refreshFeatures()
        watchDeviceVerdict()
    }

    /**
     * Says out loud a phone the app recognised and the system refused.
     *
     * The only failure in the app that produces a perfectly working screen and
     * a dead strip: every toggle is where it should be, every service starts,
     * and nothing lights, because registration never succeeded. There is no
     * logcat on the phone to pull, so the reason is said once, in the user's
     * words, naming the model — which is also the one thing worth asking a bug
     * report for.
     *
     * Once per refusal rather than once per collection: the verdict is held in
     * a singleton that outlives this ViewModel, so a rotation would otherwise
     * repeat the same message, and a second identical toast teaches people to
     * stop reading toasts.
     */
    private fun watchDeviceVerdict() {
        viewModelScope.launch {
            var lastRefusal: String? = null
            deviceReport.verdict.collect { verdict ->
                if (verdict == null || verdict.registered) return@collect
                val refusal = "${verdict.model}|${verdict.offeredIds.joinToString()}"
                if (refusal == lastRefusal) return@collect
                lastRefusal = refusal
                emit(
                    context.getString(
                        R.string.glyph_registration_refused,
                        verdict.model,
                        verdict.offeredIds.joinToString(),
                    ),
                )
            }
        }
    }

    /**
     * Reloads every feature toggle from preferences. Called on creation and
     * whenever the screen returns to the foreground, so a change made inside
     * a feature's own settings dialog is reflected on the card.
     */
    fun refreshFeatures() {
        val enabledByService = serviceController.readAll()
        _uiState.update { state ->
            state.copy(
                // From the registry, not the enum. `GlyphFeature.PREVIEW` is a
                // strip participant with no service and no preference, so it has
                // no spec and no card — iterating the enum put a row on the home
                // screen for a setting that does not exist, and asked
                // `readAll` about a key it will never hold.
                features = FeatureSpecs.all.associate { spec ->
                    val feature = spec.feature
                    feature to FeatureUiState(
                        feature = feature,
                        isEnabled = enabledByService[feature] ?: false,
                        isServiceActive = _isNothingPhone,
                    )
                },
            )
        }
        refreshRules()
    }

    // region Rules

    /**
     * The installed `.servicelua` scripts, for the permissions screen. The
     * repository's own flow — one source of truth, so an import anywhere
     * shows up everywhere. The constructor holds the same flow itself now
     * that it is its own Activity, against its own holder.
     */
    val serviceScripts: StateFlow<List<ServiceScript>> get() = serviceScriptRepository.scripts

    /**
     * Reloads the user's rule list from the repository.
     *
     * Projections included, except for the features that still have a card in
     * [HOME_CARD_FEATURES] — those two would otherwise be switched from two
     * places on one screen, and one of the two switches would be the feature's
     * card, the other its rule, over one preference. `HomeRuleVisibilityTest`
     * fails if that set and the cards on the screen ever disagree, because both
     * mistakes of it are bad in opposite directions: listed twice the user sees
     * two switches for one thing, listed nowhere they cannot reach the feature
     * at all.
     */
    fun refreshRules() {
        val rules = ruleRepository.getAll()
            .map { it.toRuleUiState(context, serviceScriptRepository) }
        _uiState.update { it.copy(rules = rules) }
    }

    /**
     * Enables or disables one rule and re-syncs the host to match.
     *
     * A feature's rule goes through the feature's own switch instead. That
     * preference is what the projection is derived from and what its rule is
     * re-derived from, so writing the rule alone would leave the two
     * disagreeing until the next sync — which is when the switch would jump
     * back on its own.
     */
    fun setRuleEnabled(id: String, enabled: Boolean) {
        ruleRepository.setEnabled(id, enabled)
        serviceController.syncRuleHost()
        tileStateBus.notifyChanged()
        refreshRules()
    }

    /**
     * Removes a rule and re-syncs the host so its trigger is detached.
     *
     * A feature's rule is not removed: it comes back on the next sync, because
     * it is re-derived from the feature's settings. Its row does not offer
     * Delete for the same reason, and this refuses the one path that does not
     * go through a row.
     */
    fun deleteRule(id: String) {
        ruleRepository.delete(id)
        serviceController.syncRuleHost()
        tileStateBus.notifyChanged()
        refreshRules()
    }

    /**
     * Plays a rule's animation once, without waiting for its trigger.
     *
     * The same entry the host uses for a real firing — [GlyphAnimationManager.playAnimationById]
     * — rather than the per-feature "Test" methods this screen used to call
     * from the feature cards. One path means a test shows what a fire will show:
     * the rule's own animation and the rule's own duration, with the locked
     * battery indicators resolved by the id they carry. Those per-feature
     * methods read the settings directly, so a test could differ from the fire
     * the moment the rule and the settings disagreed — which, before
     * [FeatureSpec.applyRule] existed, they could.
     */
    fun testRule(id: String) {
        val rule = ruleRepository.getById(id) ?: return
        emit(context.getString(R.string.toast_testing_generic, rule.name))
        viewModelScope.launch {
            glyphAnimationManager.get()
                .playAnimationById(rule.animationId, rule.durationMs, rule.batteryArc)
        }
    }

    /** Every script's current grants, for the Settings permissions screen. */
    fun scriptGrants(): Map<String, Set<ScriptCapability>> = scriptPermissionStore.grantsByScript()

    /**
     * One toggle on the permissions screen.
     *
     * The host is re-synced immediately: revocation would otherwise wait for
     * the next reload to mean anything, and a switch that lies until reboot
     * is the kind of gap the whole permission model exists to close.
     */
    fun setScriptCapability(scriptId: String, capability: ScriptCapability, granted: Boolean) {
        if (granted) {
            scriptPermissionStore.grant(scriptId, setOf(capability))
        } else {
            scriptPermissionStore.revoke(scriptId, setOf(capability))
        }
        serviceController.syncRuleHost()
    }

    // The row shape and its description live in RuleDescriptions.kt: the
    // constructor's own list renders the same rows and has to say the same
    // words about them.

    // endregion

    /** The Activity reports the real session state here; the SDK is the truth. */
    fun onSessionStateChanged(isActive: Boolean) {
        _uiState.update { it.copy(glyphServiceEnabled = isActive) }
    }

    fun toggleGlyphService(enabled: Boolean) {
        // Off the main thread and through the one switch that knows the order:
        // the SDK needs a bound service before a session opens, and the tile
        // in the shade needs the same thing the card does.
        viewModelScope.launch {
            val outcome = glyphServiceSwitch.apply(enabled)
            // The SDK is the truth, so the card follows the session rather
            // than the request — including when the request was refused.
            _uiState.update { it.copy(glyphServiceEnabled = outcome.isActive) }
            when {
                outcome.error != null -> emit(
                    context.getString(
                        if (enabled) R.string.glyph_service_fstart else R.string.glyph_service_fstop,
                    ),
                )
                !outcome.changed ->
                    emit(
                        context.getString(
                            if (enabled) R.string.glyph_service_already_enabled
                            else R.string.glyph_service_already_disabled,
                        ),
                    )
                outcome.isActive -> emit(context.getString(R.string.glyph_service_start))
                else -> emit(context.getString(R.string.glyph_service_stop))
            }
        }
    }

    /**
     * Persists a feature toggle and starts or stops its service.
     *
     * Every refusal lives in [FeatureServiceController.setFeatureEnabled] —
     * the master switch being off, and NFC's hardware — because a rule row in
     * either list reaches a feature's switch too, and a guard that only the
     * card knew about would be a switch that lies on one screen and not the
     * other. What is left here is the card's own bookkeeping: the state its
     * switch draws, and the tile.
     */
    fun setFeatureEnabled(feature: GlyphFeature, enabled: Boolean) {
        serviceController.setFeatureEnabled(feature, enabled)?.let { refusal ->
            emit(refusal)
            return
        }
        // The shade mirrors these switches, and nothing else would tell a tile
        // sitting in it that the one it draws has moved.
        tileStateBus.notifyChanged()
        _uiState.update { state ->
            state.copy(
                features = state.features + (feature to
                    state.stateOf(feature).copy(isEnabled = enabled)),
            )
        }
    }

    private fun reject(message: String): Boolean {
        emit(message)
        return false
    }

    /**
     * Plays the visualiser's animation once, to show what it will look like.
     *
     * The last caller of what used to cover eight features. Each of the others
     * is a rule now, tested by the ▶ on its rule row through
     * [RuleConstructorViewModel.testRule] — and that path is the better one
     * anyway: it plays the rule as the host will, with the rule's own duration
     * and arcs, which a per-feature entry reading settings could not promise
     * once the rule became editable. What is left here is the one thing a rule
     * cannot be: a feature whose start is driven by an Activity.
     *
     * The toast is resolved through [TOAST_BY_FEATURE] so the wording follows
     * the device language. One entry, because one feature can reach here; a
     * second card would add its own line rather than fall through to the
     * generic form below, which names the feature by its enum name and is
     * there so nothing has no message at all.
     */
    fun testFeature(feature: GlyphFeature) {
        val toast = TOAST_BY_FEATURE[feature]
        emit(
            if (toast != null) context.getString(toast)
            else context.getString(R.string.toast_testing_generic, feature.name),
        )
        viewModelScope.launch {
            when (feature) {
                GlyphFeature.MUSIC_VISUALIZER ->
                    glyphAnimationManager.get().playMusicVisualizerAnimation()

                // Not reachable: neither `RULE_HOST` nor `PREVIEW` is a single
                // feature — they are strip participants with no card, no
                // preference and no Test button, and neither's toast is in
                // `TOAST_BY_FEATURE`. Named rather than `else` so that adding a
                // real feature cannot land in the wrong branch by default.
                GlyphFeature.RULE_HOST -> Unit
                GlyphFeature.PREVIEW -> Unit
            }
        }
    }

    /**
     * Hands the system capture result to the service and turns the feature on.
     *
     * The token is *not* claimed here: `getMediaProjection` refuses to return
     * one unless a foreground service of type `mediaProjection` is already
     * running, so the consent rides in the start intent and is claimed by
     * `MusicVisualizerService` right after `startForeground`. The obvious way
     * fails with "Media projections require a foreground service".
     *
     * The flag is set before the service is asked, so the card matches what is
     * happening; if the capture then fails, the service logs why and stops
     * rather than leaving a silent lie on screen — there is no notification to
     * say it in, because the app never asks for POST_NOTIFICATIONS.
     *
     * @return `true` when the visualiser is now on, which is what lets the
     *   Activity that only opened to ask close itself again.
     */
    fun onMusicCaptureResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK) {
            settingsRepository.saveMusicVizEnabled(enabled = false)
            tileStateBus.notifyChanged()
            emit(context.getString(R.string.music_viz_consent_denied))
            return false
        }

        settingsRepository.saveMusicVizEnabled(enabled = true)
        serviceController.start(GlyphFeature.MUSIC_VISUALIZER, consent = resultCode to data)
        tileStateBus.notifyChanged()
        emit(context.getString(R.string.music_viz_toast))
        return true
    }

    fun emit(message: String) {
        viewModelScope.launch { _messages.send(message) }
    }

    private companion object {
        /**
         * The toast each feature shows when its Test button is pressed, held as
         * resource ids so it can be resolved against the device language.
         */
        val TOAST_BY_FEATURE = mapOf(
            GlyphFeature.MUSIC_VISUALIZER to R.string.toast_testing_music_visualizer,
        )
    }
}
