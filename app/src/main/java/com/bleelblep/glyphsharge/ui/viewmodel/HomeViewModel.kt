package com.bleelblep.glyphsharge.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.util.Log
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
    private val glyphManager: GlyphManager,
    /**
     * The manager the feature dialogs' "Test" buttons drive.
     *
     * Public because those dialogs are plain Composables: `hiltViewModel()`
     * resolves from inside a dialog as well as from a screen, which makes this
     * the injection point they can reach. Same singleton the feature services
     * hold.
     */
    val glyphAnimationManager: GlyphAnimationManager,
    private val serviceController: FeatureServiceController,
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
                features = GlyphFeature.entries.associateWith { feature ->
                    FeatureUiState(
                        feature = feature,
                        isEnabled = enabledByService[feature] ?: false,
                        isServiceActive = _isNothingPhone
                    )
                }
            )
        }
    }

    /** The Activity reports the real session state here; the SDK is the truth. */
    fun onSessionStateChanged(isActive: Boolean) {
        _uiState.update { it.copy(glyphServiceEnabled = isActive) }
    }

    fun toggleGlyphService(enabled: Boolean) {
        try {
            if (glyphManager.isSessionActive == enabled) {
                _uiState.update { it.copy(glyphServiceEnabled = enabled) }
                settingsRepository.saveGlyphServiceEnabled(enabled)
                // Reconcile the services even when the session was already in the
                // requested state: a feature switched on while the Glyph service
                // was off has a dead service, so its switch can read "on" while
                // nothing works.
                if (enabled) serviceController.startAllEnabled() else serviceController.stopAll()
                emit("Glyph service is already ${if (enabled) "enabled" else "disabled"}")
                return
            }

            glyphManager.toggleGlyphService()

            val newState = glyphManager.isSessionActive
            if (newState == enabled) {
                _uiState.update { it.copy(glyphServiceEnabled = newState) }
                settingsRepository.saveGlyphServiceEnabled(newState)
                emit(
                    context.getString(
                        if (newState) R.string.glyph_service_start
                        else R.string.glyph_service_stop
                    )
                )
                if (newState) serviceController.startAllEnabled() else serviceController.stopAll()
            } else {
                _uiState.update { it.copy(glyphServiceEnabled = glyphManager.isSessionActive) }
                emit(
                    context.getString(
                        if (enabled) R.string.glyph_service_fstart
                        else R.string.glyph_service_fstop
                    )
                )
                if (!enabled) settingsRepository.saveGlyphServiceEnabled(true) // roll back
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling glyph service", e)
            _uiState.update { it.copy(glyphServiceEnabled = glyphManager.isSessionActive) }
            emit("Error: ${e.message}")
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
        if (feature == GlyphFeature.NFC && enabled && !canUseNfc()) return

        // A feature cannot run without the master Glyph service: its service
        // shuts itself down on start and never registers its trigger. Refusing
        // here is what keeps the card honest — otherwise it sits there saying
        // "on" and nothing ever happens.
        if (enabled && !settingsRepository.getGlyphServiceEnabled()) {
            reject(context.getString(serviceOffMessageOf(feature)))
            return
        }

        serviceController.apply(feature, enabled)
        _uiState.update { state ->
            state.copy(
                features = state.features + (feature to
                    state.stateOf(feature).copy(isEnabled = enabled))
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
            ?: return reject("NFC is not available on this device")
        if (!adapter.isEnabled) {
            return reject("Please enable NFC in system settings first")
        }
        return true
    }

    private fun reject(message: String): Boolean {
        emit(message)
        return false
    }

    fun testFeature(feature: GlyphFeature) {
        emit(TOAST_BY_FEATURE[feature] ?: "Testing ${feature.name}")
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
     */
    fun onMusicCaptureResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            settingsRepository.saveMusicVizEnabled(false)
            emit(context.getString(R.string.music_viz_consent_denied))
            return
        }

        settingsRepository.saveMusicVizEnabled(true)
        serviceController.start(GlyphFeature.MUSIC_VISUALIZER, consent = resultCode to data)
        emit(context.getString(R.string.music_viz_toast))
    }

    fun emit(message: String) {
        viewModelScope.launch { _messages.send(message) }
    }

    private companion object {
        const val TAG = "HomeViewModel"

        val TOAST_BY_FEATURE = mapOf(
            GlyphFeature.POWER_PEEK to "Testing Power Peek",
            GlyphFeature.CHARGING_ANIMATION to "Testing Charging Animation",
            GlyphFeature.PULSE_LOCK to "Testing Pulse Lock",
            GlyphFeature.SCREEN_OFF to "Testing Screen Off",
            GlyphFeature.NFC to "Testing NFC",
            GlyphFeature.LOW_BATTERY to "Testing Low Battery Alert",
            GlyphFeature.VPN_CONNECTED to "Testing VPN Connected",
            GlyphFeature.MUSIC_VISUALIZER to "Testing Music Visualizer"
        )
    }
}
