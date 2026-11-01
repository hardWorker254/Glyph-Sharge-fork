package com.bleelblep.glyphsharge.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.rules.GlyphRuleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val settingsRepository: SettingsRepository,
    private val ruleRepository: GlyphRuleRepository,
) {
    private companion object {
        const val TAG = "FeatureServices"
    }

    fun isEnabled(feature: GlyphFeature): Boolean =
        FeatureSpecs.of(feature).isEnabled(settingsRepository)

    fun saveEnabled(feature: GlyphFeature, enabled: Boolean) =
        FeatureSpecs.of(feature).saveEnabled(settingsRepository, enabled)

    /**
     * Every feature's switch, republished on each write through [setFeatureEnabled].
     *
     * Published rather than pushed: one fact (the preference) with more than one
     * reader, and the reader that needs an Activity — `MainActivity`'s NFC
     * foreground dispatch — cannot be *told* by whichever screen happened to
     * flip the switch. A switch a rule row and a card can both reach used to be
     * the one thing that had to be pushed, through a hook installed by one
     * screen and dropped with it; now both write here and every reader follows
     * the preference itself.
     */
    private val _featureStates = MutableStateFlow(readAll())

    val featureStates: StateFlow<Map<GlyphFeature, Boolean>> get() = _featureStates.asStateFlow()

    /**
     * The one way the UI switches a feature, with the two refusals that belong to
     * every one of them.
     *
     *  * **The master Glyph service is off.** A feature's trigger cannot fire
     *    without it, so a switch that said "on" would be a switch lying.
     *  * **NFC needs hardware that is not there, or is off in the system.**
     *    Android hands tag intents to an Activity and nowhere else, so on a
     *    device without NFC — or with NFC switched off — the feature could
     *    never fire, and the refusal is what says so in the user's words
     *    rather than leaving a toggle that does nothing.
     *
     * @return `null` when the switch was applied, or the localized reason it was
     *   refused. The reason is returned rather than shown because the caller owns
     *   the toast channel, and both screens have one.
     */
    fun setFeatureEnabled(feature: GlyphFeature, enabled: Boolean): String? {
        if (enabled && !settingsRepository.getGlyphServiceEnabled()) {
            return context.getString(FeatureSpecs.of(feature).serviceOffMessage)
        }
        apply(feature, enabled)
        _featureStates.value = _featureStates.value + (feature to enabled)
        return null
    }

    /** Reads every feature's current preference in one pass. */
    fun readAll(): Map<GlyphFeature, Boolean> =
        FeatureSpecs.all.associateBy({ it.feature }) { it.isEnabled(settingsRepository) }

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
        if ((consent != null) && (feature == GlyphFeature.MUSIC_VISUALIZER)) {
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
                },
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

    /**
     * Starts every feature the user has switched on. Used at app start.
     *
     * Each start is independent. They used to sit in one `forEach`, so the
     * first feature that threw — `startForegroundService` raises
     * `ForegroundServiceStartNotAllowedException` from the background, which a
     * Quick Settings tap is — aborted the iteration and left every feature
     * after it silently not started, while their cards said "on".
     *
     * [reloadRuleHost] is false for the app-start path: `MainActivity.onStart`
     * runs this every time, including the start that a fingerprint unlock
     * triggers, and a reload there detaches the screen-on watcher in the
     * middle of the transition it is watching for. The master-switch path
     * leaves it true, because turning the Glyph service back on may follow a
     * rule edit made while it was off.
     */
    fun startAllEnabled(reloadRuleHost: Boolean = true) {
        // [FeatureSpecs.all], not [GlyphFeature.entries] — and that distinction
        // was a launch crash.
        //
        // The enum carries [GlyphFeature.PREVIEW], a strip participant with no
        // service and no preference, so it has no entry here. Iterating the enum
        // reached `isEnabled(PREVIEW)`, `FeatureSpecs.of` threw, and it threw
        // out of `MainActivity.onCreate` before a single frame was drawn.
        //
        // The registry is the right thing to iterate in any case: it is the list
        // of things that *have* a service, which is what this function starts.
        // `readAll` below already worked that way; these two did not.
        FeatureSpecs.all.forEach { spec ->
            if (!spec.isEnabled(settingsRepository)) return@forEach
            runCatching { start(spec.feature) }
                .onFailure { Log.w(TAG, "${spec.feature} did not start", it) }
        }

        // The rule host is not in the registry — it has no single feature's
        // preference — so it is synced here rather than started in the loop.
        //
        // Ensure, not reload, on the app-start path: this runs on every
        // `MainActivity.onStart`, and a running host must keep its triggers
        // attached. Detaching and re-attaching the screen-on trigger in the
        // middle of the screen-on transition is how the fingerprint-only
        // unlock lost its event — the new watcher starts with the latch
        // already at "interactive", so the transition it was supposed to
        // report is gone.
        syncRuleHost(reload = reloadRuleHost)
    }

    /**
     * Starts, reloads or stops the generic rule host to match the current rule list.
     *
     * Called on the master on/off path (via [startAllEnabled] and [stopAll])
     * and after any change to a rule. The host runs only while the master Glyph
     * service is on **and** at least one rule is enabled: an idle foreground
     * service with nothing to fire would hold a notification slot and a process
     * for nothing.
     *
     * [reload] is the difference between "make sure the host is running" and
     * "the rules changed, re-read them". The start action alone is enough for
     * the first; the reload action is what detaches and re-attaches triggers.
     * Keeping them apart matters because a trigger can hold state that a
     * detach destroys — the screen-on watcher's edge latch is the one that
     * made a fingerprint unlock disappear.
     */
    fun syncRuleHost(reload: Boolean = true) {
        val masterOn = settingsRepository.getGlyphServiceEnabled()
        val hasRules = ruleRepository.getAll().any { it.enabled }
        if (masterOn && hasRules) {
            runCatching {
                context.startForegroundService(
                    Intent(context, RuleHostService::class.java).apply {
                        action = if (reload) {
                            RuleHostService.ACTION_RELOAD
                        } else {
                            RuleHostService.ACTION_START
                        }
                    },
                )
            }.onFailure { Log.w(TAG, "RuleHostService did not start", it) }
        } else {
            stopRuleHost()
        }
    }

    /** Stops the rule host. Safe to call when it is not running. */
    fun stopRuleHost() {
        runCatching {
            context.startService(
                Intent(context, RuleHostService::class.java).apply {
                    action = RuleHostService.ACTION_STOP
                },
            )
        }
        runCatching { context.stopService(Intent(context, RuleHostService::class.java)) }
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
        // The registry, for the same reason as [startAllEnabled]: a strip
        // participant with no service has nothing to stop, and asking would throw.
        FeatureSpecs.all.forEach { stop(it.feature) }
        stopRuleHost()
        runCatching { context.stopService(Intent(context, GlyphForegroundService::class.java)) }
    }
}
