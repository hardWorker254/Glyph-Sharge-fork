package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.ServiceScriptRepository
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.data.ScriptPermissionStore
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.battery.BatteryStateReader
import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.script.GlyphScriptHost
import com.bleelblep.glyphsharge.glyph.script.ScriptAbortedError
import com.bleelblep.glyphsharge.glyph.script.ServiceScriptEngine
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.rules.GlyphRuleRepository
import com.bleelblep.glyphsharge.triggers.TriggerEvent
import com.bleelblep.glyphsharge.triggers.TriggerSource
import com.bleelblep.glyphsharge.triggers.TriggerSources
import com.bleelblep.glyphsharge.triggers.TriggerType
import com.bleelblep.glyphsharge.utils.LoggingManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The one foreground service that runs every user-defined [GlyphRule].
 *
 * This is the destination the whole refactor is heading toward: instead of
 * eight bespoke services that each open-code a receiver and a trigger chain,
 * there is one host that reads a list of rules and, for each enabled rule,
 * attaches its trigger through the shared [TriggerSources] factory and plays
 * its animation through the shared [FeatureTriggerPlayer].
 *
 * ### Why one participant
 *
 * From [com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator]'s point of view
 * this is a single strip participant — [GlyphFeature.RULE_HOST] — no matter how
 * many rules exist. Putting an unbounded, user-generated set into the coordinator's
 * enum would not scale; instead the host serialises its own rules internally and
 * lets the coordinator arbitrate between itself and the other features as it
 * always has.
 *
 * ### Reload, not live mutation
 *
 * When the rule list changes, the controller sends [ACTION_RELOAD]; the host
 * re-reads the list, detaches every trigger, and re-attaches the enabled ones.
 * [reloadRules] is idempotent (detach first), so a start that follows a
 * still-attached service cannot double-register a receiver. Rule changes are
 * infrequent — they come from the constructor UI — so a full reload is cheaper
 * and easier to reason about than a diff.
 *
 * ### Advanced (`.servicelua`) rules
 *
 * A rule that carries a [GlyphRule.scriptId] runs its script instead of a
 * plain trigger chain: on reload the host loads a [ServiceScriptEngine] per
 * rule, attaches one [TriggerSource] per trigger the script filed a handler
 * for, and every firing funnels back through [fire] — so cooldown, the
 * preempt/cap/wake-lock knobs and the enable re-check are common to both
 * kinds of rule. The script's `glyph.playAnimation()` carries no id: the
 * animation is resolved from the rule, which is the whole design contract
 * of `.servicelua`.
 */
@AndroidEntryPoint
class RuleHostService : FeatureService() {

    companion object {
        private const val TAG = "RuleHostService"
        private const val NOTIF_CHANNEL_ID = "RuleHostChannel"
        private const val NOTIF_ID = 1090

        const val ACTION_START = "com.bleelblep.glyphsharge.RULE_HOST_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.RULE_HOST_STOP"

        /** Re-reads the rule list; sent whenever a rule is added, changed or toggled. */
        const val ACTION_RELOAD = "com.bleelblep.glyphsharge.RULE_HOST_RELOAD"

        /**
         * Whether a start command has to tear the current triggers down and
         * rebuild them.
         *
         * [ACTION_RELOAD] always means the rules changed. [ACTION_START] is
         * only an ensure-alive ping, so a host that already has triggers
         * attached must keep them: the screen-on trigger's edge latch is live
         * state, and detaching it during the screen-on transition loses the
         * event. An empty trigger map means a cold start (or every attach
         * failed), which must read the rules regardless.
         */
        internal fun shouldReloadOnStart(action: String?, hasAttachedTriggers: Boolean): Boolean =
            action == ACTION_RELOAD || !hasAttachedTriggers
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ruleRepository: GlyphRuleRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var triggerPlayer: FeatureTriggerPlayer
    @Inject lateinit var serviceScriptRepository: ServiceScriptRepository
    @Inject lateinit var scriptPermissions: ScriptPermissionStore
    @Inject lateinit var ruleHostDiagnostics: RuleHostDiagnostics

    /** The live trigger for each attached rule, keyed by rule id, for detach. */
    private val activeTriggers = mutableMapOf<String, TriggerSource>()

    /** The live script engines, keyed by rule id, for detach. */
    private val activeEngines = mutableMapOf<String, ServiceScriptEngine>()

    /** When each rule last fired, keyed by rule id, for cooldown. */
    private val lastFiredAt = mutableMapOf<String, Long>()

    /**
     * The master gate only. Whether an individual rule is on is re-checked at
     * fire time inside [fire], so a rule toggled off between reloads still
     * cannot draw.
     */
    override val isRunnable: Boolean
        get() = settingsRepository.getGlyphServiceEnabled()

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = NOTIF_CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.rule_host_channel

    @get:StringRes
    override val channelDescriptionRes: Int? get() = R.string.rule_host_channel_description

    override val wakeLockTag: String get() = "GlyphSharge:RuleHostAnimation"
    override val tag: String get() = TAG

    // Lifecycle

    /**
     * Loads the rules on a start and on a reload, but does not tear down a
     * running host just because something asked it to be alive.
     *
     * The distinction is the screen-on trigger's edge latch. `MainActivity`
     * calls the ensure path on every `onStart`, and on a fingerprint unlock
     * that start can happen before the screen-on watcher has had its tick.
     * Detaching the trigger there creates a fresh watcher whose latch is
     * already `true`, so the transition it was supposed to report is gone —
     * the event is swallowed by a reload that changed nothing. A real rule
     * change sends [ACTION_RELOAD] and still gets the full detach/attach.
     *
     * [activeTriggers] empty means this is a cold start (or every attach
     * failed), and the rules have to be read either way.
     */
    override fun onStartCommandAfterGate(intent: Intent?): Int {
        if (shouldReloadOnStart(intent?.action, activeTriggers.isNotEmpty())) {
            reloadRules()
        } else {
            Log.d(TAG, "Ensure-start on a running host; keeping attached triggers")
            LoggingManager.log(
                TAG,
                "ensure-start: keeping ${activeTriggers.size} attached trigger(s)",
            )
        }
        return START_STICKY
    }

    override fun onFeatureDestroying() {
        detachAll()
        ruleHostDiagnostics.clear()
        Log.d(TAG, "Rule host destroyed")
    }

    // Rules

    private fun reloadRules() {
        detachAll()
        val rules = ruleRepository.getAll().filter { it.enabled }
        var attached = 0
        for (rule in rules) {
            // Two shapes, one contract: a scripted rule brings its own triggers
            // through the script's subscriptions; a simple rule fires from the
            // one trigger it names.
            //
            // One rule that throws must not take the rest down. The loop used
            // to let an exception out, and an exception out of `attach` is an
            // exception out of `onStartCommand` — which kills the service, and
            // with it every rule that was fine. A rule that cannot attach is
            // one dark row; a rule that takes the host with it is every row.
            val reason = try {
                if (rule.isScripted) attachScripted(rule) else attachTrigger(rule)
            } catch (e: Exception) {
                Log.w(TAG, "Rule '${rule.name}' could not be attached", e)
                getString(R.string.rules_fail_attach_failed)
            }
            if (reason == null) {
                attached++
                ruleHostDiagnostics.markAttached(rule.id)
            } else {
                ruleHostDiagnostics.markNotAttached(rule.id, reason)
            }
            LoggingManager.log(
                TAG,
                "rule '${rule.name}' trigger=${rule.triggerId} " +
                    if (reason == null) "attached" else "not attached: $reason",
            )
        }
        Log.d(TAG, "Reloaded rules: $attached attached of ${rules.size} enabled")
        LoggingManager.log(TAG, "reloaded: $attached attached of ${rules.size} enabled rules")
    }

    /**
     * Attaches one rule's trigger, or returns the reason it could not.
     *
     * A rule whose trigger is unknown to this build, or whose listener has not
     * been ported onto [TriggerSources], is skipped with a log line rather than
     * silently dropped — the palette is honest about what it can build today.
     * The reason travels to the list through [RuleHostDiagnostics], so the row
     * can say why it is dark instead of the user having to pull a logcat.
     *
     * @return `null` when the trigger is attached, the localized reason for the
     *   user when it is not.
     */
    private fun attachTrigger(rule: GlyphRule): String? {
        val type = TriggerType.fromId(rule.triggerId)
        if (type == null) {
            Log.w(TAG, "Rule '${rule.name}' names unknown trigger '${rule.triggerId}'; skipping")
            return getString(R.string.rules_fail_unknown_trigger)
        }
        val source = TriggerSources.create(
            context = this,
            type = type,
            lowBatteryThreshold = rule.lowBatteryThreshold,
            shakeThreshold = rule.shakeThreshold,
        )
        if (source == null) {
            Log.w(TAG, "Trigger '$type' not available yet; skipping rule '${rule.name}'")
            return getString(R.string.rules_fail_trigger_unavailable)
        }
        source.attach { fire(rule) }
        activeTriggers[rule.id] = source
        return null
    }

    /**
     * Attaches an advanced rule: loads its `.servicelua` and subscribes one
     * trigger per handler the script filed.
     *
     * The engine is per *rule*, not per script: two rules pointing at the same
     * script are two independent listeners with their own cooldown clocks and
     * their own enable checks, exactly as two simple rules sharing an animation
     * are. Grant decisions are read once here, at load — the moment the Lua
     * table is built — so a revocation takes effect on the next reload.
     *
     * Every failure is a log line and a reason for [RuleHostDiagnostics] —
     * none of them should take the other rules down, and the list should be
     * able to say which one went dark.
     *
     * @return `null` when the script is attached, the localized reason for the
     *   user when it is not.
     */
    private fun attachScripted(rule: GlyphRule): String? {
        val script = serviceScriptRepository.getById(rule.scriptId!!)
        if (script == null) {
            Log.w(TAG, "Rule '${rule.name}' names a script that is not installed; skipping")
            return getString(R.string.rules_fail_script_missing)
        }
        val profile = DeviceProfileFactory.forConnectedDevice()
        if (profile == null) {
            Log.w(TAG, "Rule '${rule.name}': phone profile unknown; cannot load script")
            return getString(R.string.rules_fail_profile_unknown)
        }

        val engine = ServiceScriptEngine(
            profile = profile,
            host = batteryOnlyHost,
            granted = { capability -> scriptPermissions.isGranted(script.id, capability) },
            // A duration override from the script is per firing; without one,
            // the rule's own duration stands — same as the simple path.
            onFireAnimation = { durationMs -> fire(rule, durationMs ?: rule.durationMs) },
        )

        val load = engine.load(script.source)
        if (!load.isSuccess) {
            val reason = getString(R.string.rules_fail_load_failed, load.message ?: "")
            Log.w(TAG, "Rule '${rule.name}': script failed to load (${load.status}): ${load.message}")
            engine.stop()
            return reason
        }

        val subscriptions = engine.subscriptions()
        if (subscriptions.isEmpty()) {
            Log.w(TAG, "Rule '${rule.name}': script files no handlers; nothing to attach")
            engine.stop()
            return getString(R.string.rules_fail_no_handlers)
        }

        var attachedAny = false
        for (type in subscriptions) {
            val source = TriggerSources.create(
                context = this,
                type = type,
                lowBatteryThreshold = rule.lowBatteryThreshold,
                shakeThreshold = rule.shakeThreshold,
            )
            if (source == null) {
                Log.w(TAG, "Rule '${rule.name}': trigger '$type' not available; that handler stays dark")
                continue
            }
            source.attach { event -> dispatchTo(rule, engine, event) }
            activeTriggers["${rule.id}:${type.id}"] = source
            attachedAny = true
        }

        if (!attachedAny) {
            Log.w(TAG, "Rule '${rule.name}': script attached no handlers; skipping")
            engine.stop()
            return getString(R.string.rules_fail_trigger_unavailable)
        }
        activeEngines[rule.id] = engine
        return null
    }

    /**
     * Hands one trigger firing to the script, keeping the failure modes apart.
     *
     * A [ScriptAbortedError] means the session itself was told to stop — an
     * instruction-budget overrun inside a handler — so that engine is torn
     * down here rather than left half-alive until the next reload. Everything
     * else is already logged and swallowed inside [ServiceScriptEngine.dispatch].
     */
    private fun dispatchTo(rule: GlyphRule, engine: ServiceScriptEngine, event: TriggerEvent) {
        try {
            engine.dispatch(event)
        } catch (e: ScriptAbortedError) {
            Log.w(TAG, "Rule '${rule.name}': script stopped itself (${e.message})")
            activeEngines.remove(rule.id)?.stop()
        }
    }

    /** The trigger chain for one rule: cooldown, then hand to the shared player. */
    private fun fire(rule: GlyphRule, durationMs: Long = rule.durationMs) {
        val now = SystemClock.elapsedRealtime()
        val last = lastFiredAt[rule.id] ?: 0L
        if (rule.cooldownMs > 0 && now - last < rule.cooldownMs) {
            Log.d(TAG, "Rule '${rule.name}' in cooldown; skipping")
            LoggingManager.log(TAG, "rule '${rule.name}' in cooldown; skipping")
            return
        }
        lastFiredAt[rule.id] = now
        LoggingManager.log(
            TAG,
            "rule '${rule.name}' fired trigger=${rule.triggerId} anim=${rule.animationId}",
        )

        triggerPlayer.playOnTrigger(
            scope = animationScope,
            // Re-check both the master gate *and* that this specific rule is
            // still on: a broadcast can arrive long after its switch was flipped
            // and before the next reload detaches this trigger.
            isRunnable = { isRunnable && ruleRepository.getById(rule.id)?.enabled == true },
            request = FeatureTriggerPlayer.Request(
                owner = GlyphFeature.RULE_HOST,
                eventLabel = "rule '${rule.name}'",
                animationId = rule.animationId,
                configuredDurationMs = durationMs,
                // The three behaviour knobs live on the rule itself. A rule the
                // user built in the constructor gets the defaults (interrupt, cap);
                // a rule that projects a migrated feature carries the exact values
                // its old service open-coded, so the migration moves *where* the
                // trigger is listened for and nothing else.
                applyScriptCapMs = rule.applyScriptCapMs,
                preempt = rule.preempt,
                acquireWakeLock = rule.acquireWakeLock,
            ),
            wakeLock = wakeLock,
        ) {
            glyphAnimationManager.playAnimationById(rule.animationId, durationMs, rule.batteryArc)
        }
    }

    /**
     * The hardware a `.servicelua` is allowed to see: the battery, and nothing
     * else that matters.
     *
     * The drawing half is unreachable — the engine never installs the drawing
     * API — so `draw`/`blank` are no-ops rather than a lie about hardware; the
     * audio defaults already read as "no capture". The battery reads go
     * through the same reader the built-in animations use, so a script and a
     * built-in always see the same number.
     */
    private val batteryOnlyHost: GlyphScriptHost = object : GlyphScriptHost {
        override fun draw(channels: List<Int>, brightness: Int) = Unit
        override fun blank() = Unit
        override fun batteryPercent(): Int = BatteryStateReader.read(this@RuleHostService).percentage
        override fun isCharging(): Boolean = BatteryStateReader.read(this@RuleHostService).isCharging
    }

    private fun detachAll() {
        activeTriggers.values.forEach { runCatching { it.detach() } }
        activeTriggers.clear()
        // Engines after their triggers: a handler that could still be mid-dispatch
        // when its trigger is detached should not outlive the detach anyway —
        // the strip is the shared resource, and detaching first stops new calls.
        activeEngines.values.forEach { runCatching { it.stop() } }
        activeEngines.clear()
    }

    // Notification

    override fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.rule_host_notif_title))
            .setContentText(getString(R.string.rule_host_notif_text))
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}
