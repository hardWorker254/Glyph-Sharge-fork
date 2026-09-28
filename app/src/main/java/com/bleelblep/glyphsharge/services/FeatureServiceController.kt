package com.bleelblep.glyphsharge.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.state.GlyphFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single place that knows which foreground service backs which feature.
 *
 * The feature -> service -> preference mapping was previously written out
 * four times: once in `MainActivity.initializeServices`, once per enable and
 * once per disable in the toggle handlers, and again in
 * `syncServicesAfterToggle`. Adding a feature meant touching all of them.
 * Here it is declared once, and both the Activity and the ViewModel drive
 * services through the same object.
 */
@Singleton
class FeatureServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private companion object {
        const val TAG = "FeatureServices"
    }

    private fun serviceOf(feature: GlyphFeature): Class<out Service> = when (feature) {
        GlyphFeature.CHARGING_ANIMATION -> ChargingAnimationService::class.java
        GlyphFeature.POWER_PEEK -> PowerPeekService::class.java
        GlyphFeature.PULSE_LOCK -> PulseLockService::class.java
        GlyphFeature.SCREEN_OFF -> ScreenOffGlyphService::class.java
        GlyphFeature.NFC -> NfcGlyphService::class.java
        GlyphFeature.LOW_BATTERY -> LowBatteryAlertService::class.java
        GlyphFeature.MUSIC_VISUALIZER -> MusicVisualizerService::class.java
    }

    /** The stop action a feature's service understands, if it has one. */
    private fun stopActionOf(feature: GlyphFeature): String = when (feature) {
        GlyphFeature.POWER_PEEK -> PowerPeekService.ACTION_STOP
        GlyphFeature.PULSE_LOCK -> PulseLockService.ACTION_STOP
        GlyphFeature.SCREEN_OFF -> ScreenOffGlyphService.ACTION_STOP
        GlyphFeature.NFC -> NfcGlyphService.ACTION_STOP
        GlyphFeature.CHARGING_ANIMATION -> ChargingAnimationService.ACTION_STOP
        GlyphFeature.LOW_BATTERY -> LowBatteryAlertService.ACTION_STOP
        GlyphFeature.MUSIC_VISUALIZER -> MusicVisualizerService.ACTION_STOP
    }

    fun isEnabled(feature: GlyphFeature): Boolean = when (feature) {
        GlyphFeature.CHARGING_ANIMATION -> settingsRepository.isChargingAnimationEnabled()
        GlyphFeature.POWER_PEEK -> settingsRepository.isPowerPeekEnabled()
        GlyphFeature.PULSE_LOCK -> settingsRepository.isPulseLockEnabled()
        GlyphFeature.SCREEN_OFF -> settingsRepository.isScreenOffFeatureEnabled()
        GlyphFeature.NFC -> settingsRepository.isNfcFeatureEnabled()
        GlyphFeature.LOW_BATTERY -> settingsRepository.isLowBatteryEnabled()
        GlyphFeature.MUSIC_VISUALIZER -> settingsRepository.isMusicVizEnabled()
    }

    fun saveEnabled(feature: GlyphFeature, enabled: Boolean) {
        when (feature) {
            GlyphFeature.CHARGING_ANIMATION -> settingsRepository.saveChargingAnimationEnabled(enabled)
            GlyphFeature.POWER_PEEK -> settingsRepository.savePowerPeekEnabled(enabled)
            GlyphFeature.PULSE_LOCK -> settingsRepository.savePulseLockEnabled(enabled)
            GlyphFeature.SCREEN_OFF -> settingsRepository.saveScreenOffFeatureEnabled(enabled)
            GlyphFeature.NFC -> settingsRepository.saveNfcFeatureEnabled(enabled)
            GlyphFeature.LOW_BATTERY -> settingsRepository.saveLowBatteryEnabled(enabled)
            GlyphFeature.MUSIC_VISUALIZER -> settingsRepository.saveMusicVizEnabled(enabled)
        }
    }

    /** Reads every feature's current preference in one pass. */
    fun readAll(): Map<GlyphFeature, Boolean> =
        GlyphFeature.entries.associateWith { isEnabled(it) }

    /**
     * Starts a feature's service.
     *
     * Refused while the master Glyph service is off. Every one of these
     * services checks the same thing in `onStartCommand` and calls `shutDown()`
     * — so starting one anyway produced a service that lived for a few
     * milliseconds, registered no trigger, and left the card claiming to be on
     * while nothing could ever happen. The Screen Off service survived that
     * only because it had been started while the Glyph service was still on.
     */
    fun start(feature: GlyphFeature) {
        start(feature, null)
    }

    /**
     * Starts a feature's service, optionally carrying a capture consent.
     *
     * [consent] exists for the music visualiser alone. Android 14 refuses to
     * hand out a `MediaProjection` token until a foreground service of the
     * matching type is already running, so the consent has to travel *with*
     * the start intent and be claimed by the service after `startForeground` —
     * not be redeemed by whoever asked for it.
     *
     * @param consent the Activity result code and the consent `Intent`
     */
    fun start(feature: GlyphFeature, consent: Pair<Int, Intent?>?) {
        if (!settingsRepository.getGlyphServiceEnabled()) {
            Log.w(TAG, "Not starting ${feature.name}: the Glyph service is off")
            return
        }
        val intent = Intent(context, serviceOf(feature))
        if (consent != null && feature == GlyphFeature.MUSIC_VISUALIZER) {
            intent.putExtra(MusicVisualizerService.EXTRA_CONSENT_RESULT_CODE, consent.first)
            consent.second?.let { intent.putExtra(MusicVisualizerService.EXTRA_CONSENT_DATA, it) }
        }
        context.startForegroundService(intent)
    }

    fun stop(feature: GlyphFeature) {
        runCatching {
            context.startService(
                Intent(context, serviceOf(feature)).apply {
                    action = stopActionOf(feature)
                }
            )
        }
        runCatching { context.stopService(Intent(context, serviceOf(feature))) }
    }

    /**
     * Applies a toggle: persists the preference and starts or stops the
     * backing service to match.
     */
    fun apply(feature: GlyphFeature, enabled: Boolean) {
        saveEnabled(feature, enabled)
        if (enabled) start(feature) else stop(feature)
    }

    /** Starts every feature the user has switched on. Used at app start. */
    fun startAllEnabled() {
        GlyphFeature.entries.forEach { feature ->
            if (isEnabled(feature)) start(feature)
        }
    }

    /** Stops every feature service. Used when the master glyph service goes off. */
    fun stopAll() {
        GlyphFeature.entries.forEach { stop(it) }
        runCatching { context.stopService(Intent(context, GlyphForegroundService::class.java)) }
        runCatching { context.stopService(Intent(context, QuietHoursService::class.java)) }
    }
}
