package com.bleelblep.glyphsharge.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts, stops and reads features through the [FeatureSpecs] registry.
 *
 * The feature -> service -> stop action -> preference mapping is data in
 * [FeatureSpec], so this class only decides *what* to do with a feature, never
 * *which* service or preference that is.
 */
@Singleton
class FeatureServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private companion object {
        const val TAG = "FeatureServices"
    }

    fun isEnabled(feature: GlyphFeature): Boolean =
        FeatureSpecs.of(feature).isEnabled(settingsRepository)

    fun saveEnabled(feature: GlyphFeature, enabled: Boolean) =
        FeatureSpecs.of(feature).saveEnabled(settingsRepository, enabled)

    /** Reads every feature's current preference in one pass. */
    fun readAll(): Map<GlyphFeature, Boolean> =
        FeatureSpecs.all.associate { it.feature to it.isEnabled(settingsRepository) }

    /**
     * Starts a feature's service.
     *
     * Refused while the master Glyph service is off: every one of these
     * services checks the same thing in `onStartCommand` and calls `shutDown()`,
     * so starting one anyway produced a service that lived for a few
     * milliseconds, registered no trigger, and left the card claiming to be on
     * while nothing could ever happen.
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
        val spec = FeatureSpecs.of(feature)
        val intent = Intent(context, spec.serviceClass)
        if (consent != null && feature == GlyphFeature.MUSIC_VISUALIZER) {
            intent.putExtra(MusicVisualizerService.EXTRA_CONSENT_RESULT_CODE, consent.first)
            consent.second?.let { intent.putExtra(MusicVisualizerService.EXTRA_CONSENT_DATA, it) }
        }
        context.startForegroundService(intent)
    }

    fun stop(feature: GlyphFeature) {
        val spec = FeatureSpecs.of(feature)
        runCatching {
            context.startService(
                Intent(context, spec.serviceClass).apply {
                    action = spec.stopAction
                }
            )
        }
        runCatching { context.stopService(Intent(context, spec.serviceClass)) }
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

    /**
     * Starts the service that keeps the app in Quick Settings "Active apps".
     *
     * Not a feature, so not in [startAllEnabled]: it is the master switch's
     * own foreground service, and it is one of the two things [stopAll]
     * stops. Starting it is part of turning the Glyph service on — a master
     * switch that saved a flag and opened a session but left this down would
     * be killed by the system without anything having drawn.
     */
    fun startPersistentGlyphService() {
        runCatching { context.startForegroundService(Intent(context, GlyphForegroundService::class.java)) }
            .onFailure { Log.w(TAG, "GlyphForegroundService did not start: ${it.message}") }
    }

    /** Stops every feature service. Used when the master glyph service goes off. */
    fun stopAll() {
        GlyphFeature.entries.forEach { stop(it) }
        runCatching { context.stopService(Intent(context, GlyphForegroundService::class.java)) }
        runCatching { context.stopService(Intent(context, QuietHoursService::class.java)) }
    }
}
