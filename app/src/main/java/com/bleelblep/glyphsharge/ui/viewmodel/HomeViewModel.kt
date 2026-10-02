package com.bleelblep.glyphsharge.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.audio.PlaybackAudioSource
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.FeatureSpecs
import com.bleelblep.glyphsharge.services.GlyphServiceSwitch
import com.bleelblep.glyphsharge.tiles.TileStateBus
import com.bleelblep.glyphsharge.ui.state.FeatureUiState
import com.bleelblep.glyphsharge.ui.state.HomeUiState
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
     * The manager the feature dialogs' "Test" buttons drive.
     *
     * Public because those dialogs are plain Composables: they read this
     * through [com.bleelblep.glyphsharge.ui.theme.LocalHomeViewModel], which
     * hands back the instance this Activity drives. Same singleton the feature
     * services hold.
     */
    val glyphAnimationManager: GlyphAnimationManager,
    private val serviceController: FeatureServiceController,
    private val glyphServiceSwitch: GlyphServiceSwitch,
    private val tileStateBus: TileStateBus,
    private val playbackAudioSource: PlaybackAudioSource,
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
     * The Activity registers this because `enableForegroundDispatch` needs a
     * real `Activity`, which a ViewModel cannot hold.
     */
    fun setNfcDispatchHook(hook: (Boolean) -> Unit) {
        nfcDispatchHook = hook
    }

    private var nfcDispatchHook: ((Boolean) -> Unit)? = null

    private val _isNothingPhone = glyphManager.isNothingPhone()

    init {
        refreshFeatures()
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
    }

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
     * NFC is special-cased: the hardware check needs no Activity, but the
     * foreground dispatch that follows does, so the Activity registers
     * [setNfcDispatchHook] and is told when to enable or disable it.
     */
    fun setFeatureEnabled(feature: GlyphFeature, enabled: Boolean) {
        if ((feature == GlyphFeature.NFC) && (enabled && !canUseNfc())) return

        // A feature cannot run without the master Glyph service: its service
        // shuts itself down on start and never registers its trigger. Refusing
        // here is what keeps the card honest — otherwise it sits there saying
        // "on" and nothing ever happens.
        if (enabled && !settingsRepository.getGlyphServiceEnabled()) {
            reject(context.getString(serviceOffMessageOf(feature)))
            return
        }

        serviceController.apply(feature, enabled)
        // The shade mirrors these switches, and nothing else would tell a tile
        // sitting in it that the one it draws has moved.
        tileStateBus.notifyChanged()
        _uiState.update { state ->
            state.copy(
                features = state.features + (feature to
                    state.stateOf(feature).copy(isEnabled = enabled)),
            )
        }

        if (feature == GlyphFeature.NFC) nfcDispatchHook?.invoke(enabled)
    }

    /**
     * The toast each feature already shows when its dialog is opened while the
     * Glyph service is off. It lives in [FeatureSpecs] beside the rest of a
     * feature's wiring, because it is the same kind of fact — one per feature,
     * and a feature added without it has no message to show.
     */
    private fun serviceOffMessageOf(feature: GlyphFeature): Int =
        FeatureSpecs.of(feature).serviceOffMessage

    private fun canUseNfc(): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return reject(context.getString(R.string.nfc_not_available))
        if (!adapter.isEnabled) {
            return reject(context.getString(R.string.nfc_enable_in_settings))
        }
        return true
    }

    private fun reject(message: String): Boolean {
        emit(message)
        return false
    }

    /**
     * The toast the Test button shows, before the animation is asked for.
     *
     * Resolved through [TOAST_BY_FEATURE] so the wording follows the device
     * language. A feature with no entry of its own falls back to the generic
     * form with its enum name — a last resort, not the normal wording.
     */
    fun testFeature(feature: GlyphFeature) {
        val toast = TOAST_BY_FEATURE[feature]
        emit(
            if (toast != null) context.getString(toast)
            else context.getString(R.string.toast_testing_generic, feature.name),
        )
        viewModelScope.launch {
            when (feature) {
                GlyphFeature.POWER_PEEK ->
                    glyphAnimationManager.playPowerPeekAnimation(context) {}
                GlyphFeature.CHARGING_ANIMATION ->
                    glyphAnimationManager.playChargingAnimationAnimation(context) {}
                GlyphFeature.PULSE_LOCK -> glyphAnimationManager.playPulseLockAnimation()
                GlyphFeature.SCREEN_OFF -> glyphAnimationManager.playScreenOffAnimation()
                GlyphFeature.NFC -> glyphAnimationManager.playNfcAnimation()
                GlyphFeature.LOW_BATTERY -> glyphAnimationManager.playLowBatteryAnimation()
                GlyphFeature.VPN_CONNECTED -> glyphAnimationManager.playVpnConnectedAnimation()
                GlyphFeature.MUSIC_VISUALIZER ->
                    glyphAnimationManager.playMusicVisualizerAnimation()

                // Not reachable: `PREVIEW` is a strip participant, not a
                // feature — it has no card, no preference and no Test button,
                // and its toast is absent from `TOAST_BY_FEATURE` too. Named
                // rather than `else` so that adding a real feature cannot land
                // in the wrong branch by default.
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
     * happening; if the capture then fails, the service says why in its
     * notification rather than leaving a silent lie on screen.
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
            GlyphFeature.POWER_PEEK to R.string.toast_testing_power_peek,
            GlyphFeature.CHARGING_ANIMATION to R.string.toast_testing_charging_animation,
            GlyphFeature.PULSE_LOCK to R.string.toast_testing_pulse_lock,
            GlyphFeature.SCREEN_OFF to R.string.toast_testing_screen_off,
            GlyphFeature.NFC to R.string.toast_testing_nfc,
            GlyphFeature.LOW_BATTERY to R.string.toast_testing_low_battery_alert,
            GlyphFeature.VPN_CONNECTED to R.string.toast_testing_vpn_connected,
            GlyphFeature.MUSIC_VISUALIZER to R.string.toast_testing_music_visualizer,
        )
    }
}
