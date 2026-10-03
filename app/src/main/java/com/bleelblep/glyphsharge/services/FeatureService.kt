package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.StringRes
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The shape every feature service shares, and the only place it is written down.
 *
 * Eight services each hand-rolled the same forty lines — the notification
 * channel, `startForeground` before anything else, the `ACTION_STOP` branch,
 * the run gate, the restart on task removal, the teardown — and they had
 * already drifted in five observable ways:
 *
 * - `LowBatteryAlertService` had no run gate at all and ignored the
 *   `ACTION_STOP` the controller sends it;
 * - three services passed `onRelease = { stopForegroundCompat() }`, which
 *   demoted them to ordinary background services and let Android reclaim them
 *   after the first event, so each worked exactly once per boot;
 * - three created a root `animationJob` that nothing ever cancelled, so an
 *   animation outlived its service, still holding the strip and a WakeLock;
 * - two of the three that did tear down correctly called `unregisterReceiver`
 *   bare, which throws out of `onDestroy` when `onCreate` failed part-way;
 * - five named their notification channel in hard-coded English, which is
 *   unfixable after install because a channel's name is immutable once created.
 *
 * Each of those was a separate fix. The point of this class is that the next
 * one cannot happen, because there is no next one: the channel name is a string
 * resource, the gate is one abstract property, and the teardown is written once.
 *
 * ### Why no `@Inject` fields here
 *
 * Hilt injects the annotated subclass and its own fields; a plain
 * `@Inject` field on an *unannotated* superclass is silently never injected.
 * Rather than paper over that, everything the base needs arrives as an abstract
 * property the subclass answers from the objects it already holds. The result
 * is that the base class has no dependency at all, and so cannot become a
 * second source of truth for one.
 *
 * ### What is deliberately not here
 *
 * `GlyphForegroundService` is a service too, but it is not a *feature*
 * service: it has no [GlyphFeature] and reads no feature's preference — it is
 * the master session the features depend on. Folding it in here would mean a
 * nullable feature and a conditional gate, which is the shape that let Low
 * Battery drift in the first place.
 */
abstract class FeatureService : Service() {

    /**
     * The run gate: this feature's own switch, and the master Glyph switch.
     *
     * One property, read in all three places that need it — `onStartCommand`,
     * `onTaskRemoved`, and whatever the feature's own trigger handler checks.
     * A service that could answer it for one and not another was the whole of
     * the Low Battery defect.
     */
    protected abstract val isRunnable: Boolean

    /** The action this service restarts itself with. */
    protected abstract val startAction: String

    /** The action that tells this service to stop. */
    protected abstract val stopAction: String

    /** Stable per-service channel id. Never change it: the name is immutable. */
    protected abstract val channelId: String

    /** Stable per-service notification id, so an update replaces rather than adds. */
    protected abstract val notificationId: Int

    /**
     * Whether swiping the app away should bring this service back.
     *
     * Seven features say yes. `MusicVisualizerService` says no, and cannot be
     * argued out of it: a `MediaProjection` token can only be obtained from an
     * Activity result, so a restart from the background could only ever fail —
     * leaving a card that says "on" and a dark strip. Expressing that as a
     * property here keeps `onTaskRemoved` itself sealed, so a service cannot
     * drop the gate by accident on its way past it.
     */
    protected open val restartsOnTaskRemoval: Boolean get() = true

    /**
     * Answers `true` for a start intent this service should act on *without*
     * consulting the run gate.
     *
     * Needed by `LowBatteryAlertService`'s "Test" action. The gate is a pair of
     * preferences — this feature's switch and the master Glyph switch — and the
     * Test button means "show me what this sounds like" rather than "turn it
     * on", so refusing it would be answering a different question than the one
     * that was asked.
     *
     * Deliberately narrow: overriding it would bypass the gate for the wrong
     * action and leave a service drawing with its switch off.
     */
    protected open fun bypassesRunGate(intent: Intent?): Boolean = false

    /**
     * The last notification text posted, so an unchanged one is not rebuilt.
     *
     * A visualiser that polls whether music is playing every 500 ms would
     * otherwise rebuild and re-post an identical notification on every poll,
     * and `startForeground` is a binder call each time.
     */
    private var lastNotificationText: String? = null

    /**
     * The channel's name, as a resource.
     *
     * A resource rather than a string because a notification channel's name is
     * frozen the first time it is created — changing the literal afterwards
     * leaves every existing install showing the old one until the channel is
     * deleted. Making it a `String` here means the translation is the only
     * thing that can change it.
     */
    @get:StringRes
    protected abstract val channelNameRes: Int

    /**
     * Where this feature lives on the home screen, for the channel description.
     */
    @get:StringRes
    protected open val channelDescriptionRes: Int? = null

    /**
     * Scopes for the service's own work.
     *
     * Two, not one. [serviceScope] dies with the service's commands;
     * [animationScope] backs a run that is allowed to outlive the command that
     * started it. They are separate roots on purpose — a supervisor on each
     * means one failed animation does not cancel its siblings — which is
     * exactly why both have to be cancelled explicitly in [onDestroy]. The
     * second scope used to be a root job that nothing cancelled, so an
     * animation kept drawing after its service was gone.
     */
    protected val serviceScope = newScope()
    protected val animationScope = newScope()

    /**
     * A partial wake lock, created but never acquired unless a feature asks.
     *
     * `newWakeLock` does not hold the CPU awake, so an unused one costs
     * nothing; acquiring is always explicit. Released unconditionally in
     * [onDestroy] because the alternative — releasing only from a strip's
     * release callback — means a service torn down mid-animation holds it for
     * its full timeout.
     */
    protected val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag)
    }

    /** Identifies this service's wake lock in `dumpsys power`. */
    protected abstract val wakeLockTag: String

    // region Lifecycle

    /**
     * Creates the channel, then hands over to [onFeatureCreated].
     *
     * Not `final`, and that is not negotiable: Hilt generates
     * `Hilt_<Service> extends FeatureService` and has to override `onCreate()`
     * there to run `inject()`. A `final` `onCreate` makes every `@AndroidEntryPoint`
     * service that extends this class fail `javac` with
     * "`onCreate()` ... cannot override `onCreate()` in `FeatureService`" — and,
     * because injection runs in the generated override, this ordering is what
     * guarantees `@Inject` fields are populated before [onFeatureCreated] reads
     * one. A subclass must not override this: put its work in
     * [onFeatureCreated] instead.
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        onFeatureCreated()
    }

    /**
     * Handles the part of `onStartCommand` that is the same everywhere.
     *
     * `startForeground` is first because Android gives a service five seconds
     * to call it, and a service that draws nothing in that window is killed —
     * the feature simply stops and the card still claims it is on.
     */
    final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())

        if (intent?.action == stopAction) {
            shutDown()
            return START_NOT_STICKY
        }

        // Answered before the gate, and only for the action that asks for it:
        // see [bypassesRunGate].
        if (bypassesRunGate(intent)) return onStartCommandAfterGate(intent)

        // A service that starts while its switch is off registers no trigger
        // and sits there with a notification the user cannot explain.
        if (!isRunnable) {
            Log.i(tag, "Not runnable; shutting down")
            shutDown()
            return START_NOT_STICKY
        }

        return onStartCommandAfterGate(intent)
    }

    /**
     * What a feature does once it is allowed to run.
     *
     * @return [START_STICKY] for the seven services whose trigger is a
     *   broadcast they registered themselves. `MusicVisualizerService`
     *   overrides this to say otherwise, because a `MediaProjection` token
     *   cannot be re-obtained from the background and a restart could only
     *   ever fail.
     */
    protected open fun onStartCommandAfterGate(intent: Intent?): Int = START_STICKY

    /**
     * Registers receivers, opens sensors and reads settings.
     *
     * After the channel exists, before anything may draw.
     */
    protected open fun onFeatureCreated() = Unit

    final override fun onDestroy() {
        super.onDestroy()

        // Subclass teardown first, so it can still see a live service.
        onFeatureDestroying()

        // Cancelled before the WakeLock is released, so nothing that is
        // running can reach for it after this returns.
        animationScope.cancel()
        serviceScope.cancel()
        runCatching { if (wakeLock.isHeld) wakeLock.release() }

        stopForegroundCompat()
    }

    /**
     * Undoes whatever [onFeatureCreated] set up.
     *
     * Every call out to the platform has to be guarded here: this runs on the
     * way down from a service whose `onCreate` may have failed part-way, and
     * an unguarded `unregisterReceiver` throws out of `onDestroy`, which is an
     * uncaught crash rather than a cleanup failure.
     */
    protected open fun onFeatureDestroying() = Unit

    final override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Restarts the service when the user swipes the app away.
     *
     * Gated, because restarting a service whose feature is off resurrects a
     * notification and a WakeLock for nothing.
     */
    final override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!restartsOnTaskRemoval) return
        if (!isRunnable) return

        startForegroundService(
            Intent(this, javaClass).apply { action = startAction },
        )
    }

    /** Ends the service. Overridable only to do more before this. */
    protected open fun shutDown() {
        stopForegroundCompat()
        stopSelf()
    }

    // endregion

    // region Notification

    /**
     * The ongoing notification this feature shows while it waits for its trigger.
     *
     * Open because the text is the feature's business; the shape is not.
     */
    protected abstract fun buildNotification(): Notification

    /**
     * Re-posts the notification with new text, leaving the service foregrounded.
     *
     * Skipped when the text has not changed: the polling loops above call this
     * several times a second, and `startForeground` is a binder call each time.
     */
    protected fun showNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        startForegroundCompat(buildNotification(text))
    }

    /** The notification with the same shape and different text. */
    protected abstract fun buildNotification(text: String): Notification

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            getString(channelNameRes),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            channelDescriptionRes?.let { description = getString(it) }
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * `startForeground` with the types the manifest declares, never throwing.
     *
     * A service that throws here dies silently — the feature stops and the
     * card still claims it is on — so the failure is logged and swallowed on
     * purpose.
     */
    protected open fun startForegroundCompat(notification: Notification) {
        runCatching {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.onFailure { Log.e(tag, "startForeground refused", it) }
    }

    @Suppress("DEPRECATION")
    protected fun stopForegroundCompat() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    // endregion

    private fun newScope() = CoroutineScope(
        Dispatchers.Main + SupervisorJob(),
    )

    /** Short name for log lines. Overridden only where the class name is unclear. */
    protected open val tag: String get() = javaClass.simpleName
}