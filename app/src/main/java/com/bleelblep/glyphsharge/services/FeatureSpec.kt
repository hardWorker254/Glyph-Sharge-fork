package com.bleelblep.glyphsharge.services

import android.app.Service
import androidx.annotation.StringRes
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature

/**
 * Everything the app has to know about one feature, in one record.
 *
 * The failure this prevents is structural rather than visible: with the
 * mapping spread across parallel `when` blocks, a feature could be added to
 * three of them and the fourth would still compile, because a `when` on an enum
 * only complains when a case is *missing*, never when a getter is paired with
 * the wrong setter. The service would start and stop correctly while reading
 * someone else's flag, and nothing would say so. As data, that pairing is
 * checked once, by the compiler, in a single list.
 *
 * The preference accessors are lambdas over [SettingsRepository] rather than
 * methods on it, so the mapping stays declarative and the repository keeps
 * having no idea that features exist as a group.
 */
data class FeatureSpec(
    val feature: GlyphFeature,
    val serviceClass: Class<out Service>,
    val stopAction: String,
    val isEnabled: (SettingsRepository) -> Boolean,
    val saveEnabled: (SettingsRepository, Boolean) -> Unit,

    /**
     * The toast this feature already shows when its dialog is opened while the
     * Glyph service is off. It is here because it is the same kind of fact —
     * one per feature, forgotten independently of the others — and because
     * reusing each feature's existing wording beats adding a second, slightly
     * different message next to it.
     */
    @StringRes val serviceOffMessage: Int,
) {
    /**
     * Whether this feature is allowed to do anything at all.
     *
     * Every feature service asks the same two questions before it acts: is my
     * own switch on, and is the master Glyph service on. The second is not a
     * detail of this feature — it is the app-wide precondition, and a service
     * that starts while it is off shuts itself down, registers no trigger and
     * leaves the feature card claiming to be on with nothing behind it.
     *
     * Answering it once here is what stops the two halves from drifting: it is
     * the same gate for `onStartCommand`, for `onTaskRemoved` and for the event
     * handler, so a feature cannot pass one and fail another.
     */
    fun isRunnable(settings: SettingsRepository): Boolean =
        isEnabled(settings) && settings.getGlyphServiceEnabled()
}

/**
 * The one place a feature is wired to its service and its preference.
 *
 * Adding a feature means adding one entry here and one value to
 * [GlyphFeature]; nothing else in the app has to know the service exists.
 *
 * The order matches [GlyphFeature.entries] so the list can be read against the
 * enum, and [init] fails the build-up if the two ever disagree — an entry
 * missing from a `when` compiles, an entry missing from a `Map` lookup does
 * not.
 */
object FeatureSpecs {

    val all: List<FeatureSpec> = listOf(
        FeatureSpec(
            feature = GlyphFeature.PULSE_LOCK,
            serviceClass = PulseLockService::class.java,
            stopAction = PulseLockService.ACTION_STOP,
            isEnabled = { it.isPulseLockEnabled() },
            saveEnabled = { repo, enabled -> repo.savePulseLockEnabled(enabled) },
            serviceOffMessage = R.string.pulse_lock_toast
        ),
        FeatureSpec(
            feature = GlyphFeature.POWER_PEEK,
            serviceClass = PowerPeekService::class.java,
            stopAction = PowerPeekService.ACTION_STOP,
            isEnabled = { it.isPowerPeekEnabled() },
            saveEnabled = { repo, enabled -> repo.savePowerPeekEnabled(enabled) },
            serviceOffMessage = R.string.power_peek_toast
        ),
        FeatureSpec(
            feature = GlyphFeature.LOW_BATTERY,
            serviceClass = LowBatteryAlertService::class.java,
            stopAction = LowBatteryAlertService.ACTION_STOP,
            isEnabled = { it.isLowBatteryEnabled() },
            saveEnabled = { repo, enabled -> repo.saveLowBatteryEnabled(enabled) },
            serviceOffMessage = R.string.low_battery_alert_toast
        ),
        FeatureSpec(
            feature = GlyphFeature.SCREEN_OFF,
            serviceClass = ScreenOffGlyphService::class.java,
            stopAction = ScreenOffGlyphService.ACTION_STOP,
            isEnabled = { it.isScreenOffFeatureEnabled() },
            saveEnabled = { repo, enabled -> repo.saveScreenOffFeatureEnabled(enabled) },
            serviceOffMessage = R.string.screen_off_toast
        ),
        FeatureSpec(
            feature = GlyphFeature.NFC,
            serviceClass = NfcGlyphService::class.java,
            stopAction = NfcGlyphService.ACTION_STOP,
            isEnabled = { it.isNfcFeatureEnabled() },
            saveEnabled = { repo, enabled -> repo.saveNfcFeatureEnabled(enabled) },
            serviceOffMessage = R.string.nfc_glyph_toast
        ),
        FeatureSpec(
            feature = GlyphFeature.CHARGING_ANIMATION,
            serviceClass = ChargingAnimationService::class.java,
            stopAction = ChargingAnimationService.ACTION_STOP,
            isEnabled = { it.isChargingAnimationEnabled() },
            saveEnabled = { repo, enabled -> repo.saveChargingAnimationEnabled(enabled) },
            serviceOffMessage = R.string.charging_animation_toast
        ),
        FeatureSpec(
            feature = GlyphFeature.MUSIC_VISUALIZER,
            serviceClass = MusicVisualizerService::class.java,
            stopAction = MusicVisualizerService.ACTION_STOP,
            isEnabled = { it.isMusicVizEnabled() },
            saveEnabled = { repo, enabled -> repo.saveMusicVizEnabled(enabled) },
            serviceOffMessage = R.string.music_viz_toast
        ),
        FeatureSpec(
            feature = GlyphFeature.VPN_CONNECTED,
            serviceClass = VpnConnectedService::class.java,
            stopAction = VpnConnectedService.ACTION_STOP,
            isEnabled = { it.isVpnConnectedEnabled() },
            saveEnabled = { repo, enabled -> repo.saveVpnConnectedEnabled(enabled) },
            serviceOffMessage = R.string.vpn_connected_toast
        )
    )

    private val byFeature: Map<GlyphFeature, FeatureSpec> = all.associateBy { it.feature }

    init {
        // Fail here rather than at the first lookup. Every caller of `of` is
        // driven by a [GlyphFeature] value, and a feature with no spec has no
        // service to start and no preference to read — so without this check the
        // mistake would surface as a crash deep inside a start intent, or worse,
        // as a feature that silently never runs.
        val missing = GlyphFeature.entries.filterNot { it in byFeature }
        check(missing.isEmpty()) {
            "FeatureSpecs has no entry for ${missing.joinToString { it.name }}"
        }
    }

    fun of(feature: GlyphFeature): FeatureSpec =
        byFeature[feature] ?: error(
            "No FeatureSpec for ${feature.name}: a feature with no spec has no " +
                "service to start and no preference to read"
        )

    fun allFor(features: Iterable<GlyphFeature>): List<FeatureSpec> =
        features.map { of(it) }
}
