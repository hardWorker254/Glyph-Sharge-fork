package com.bleelblep.glyphsharge.glyph

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates exclusive access to the Glyph LEDs across independent features (services).
 * Only one [GlyphFeature] may hold the lock at any given time.
 */
@Singleton
class GlyphFeatureCoordinator @Inject constructor(
    private val glyphManager: GlyphManager,
    private val glyphAnimationManager: GlyphAnimationManager,
) {
    private companion object {
        /** How long a contended lock is retried before giving up. */
        const val TRY_LOCK_POLL_MS = 25L

        /**
         * How long a feature that is willing to be skipped waits for the strip.
         * Short by design: these are bursts, and a burst that arrives late is
         * just a burst the user did not see.
         */
        const val ACQUIRE_TIMEOUT_MS = 500L

        /**
         * How long a preempting feature waits for the interrupted one to let
         * go. The loser stops drawing immediately and releases in its `finally`,
         * so this is generous; it only has to outlive that unwind.
         */
        const val PREEMPT_TIMEOUT_MS = 1_500L

        private const val TAG = "GlyphCoordinator"
    }
    private val lock = Mutex()

    /**
     * Serialises opening a Glyph session across features.
     *
     * Separate from [lock] because it guards something that is not the strip:
     * the session is a single global resource, and two features arriving
     * together would otherwise both try to open it.
     */
    private val sessionGuard = Mutex()

    private val _currentOwner = MutableStateFlow<GlyphFeature?>(null)
    val currentOwner: StateFlow<GlyphFeature?> = _currentOwner.asStateFlow()

    /**
     * Takes the LEDs for an event the user just caused — an unlock, a tap, a
     * screen-off — even if something is still playing.
     *
     * Glow Gate and Screen Off fire seconds apart in the normal course of
     * locking a phone, and a looping custom animation holds the strip for the
     * whole duration, so without an interrupt the one the user actually did
     * would be skipped half the time.
     *
     * Interrupting is safe because the current owner is not dispossessed: it is
     * asked to stop drawing, returns, and releases in its own `finally` — the
     * only path that unlocks correctly. Taking the lock from under it here
     * would strand it, because [release] ignores a caller that is no longer
     * the owner.
     */
    suspend fun acquireNow(
        owner: GlyphFeature,
        timeoutMs: Long = PREEMPT_TIMEOUT_MS,
    ): Boolean {
        if ((_currentOwner.value != null) && (_currentOwner.value != owner)) {
            Log.d(TAG, "${owner.name} interrupts ${_currentOwner.value} for the strip")
            glyphAnimationManager.stopAnimations()
        }
        return acquire(owner, timeoutMs)
    }

    /**
     * Runs [block] with exclusive hold of the Glyph strip, and hands the strip
     * back afterwards.
     *
     * The release has to be in a `finally`. The strip is one shared resource
     * behind a [Mutex], so a service that returns, throws or is cancelled
     * without releasing leaves the *lock* held, not merely the LEDs lit — and
     * [release] ignores a caller that is no longer the owner, so nothing can
     * take it back and every other feature logs "strip busy" on every trigger
     * until the process dies.
     *
     * A failed acquisition returns `null` rather than running [block], so the
     * caller can tell "never got the strip" from "held it" without its own flag.
     *
     * @param preempt `true` for something the user just did, which interrupts
     *   the current owner through [acquireNow]; `false` gives up after
     *   [ACQUIRE_TIMEOUT_MS].
     * @param onRelease runs from the same `finally`, *after* [release], so the
     *   LEDs are already off — the order the services' own cleanup (WakeLock,
     *   `stopForeground`) expects. It does not run when acquisition failed,
     *   because then nothing was taken and nothing needs undoing.
     * @return whatever [block] returned, or `null` if the strip was not taken.
     */
    suspend fun <T> withStrip(
        owner: GlyphFeature,
        preempt: Boolean = false,
        timeoutMs: Long = if (preempt) PREEMPT_TIMEOUT_MS else ACQUIRE_TIMEOUT_MS,
        onRelease: () -> Unit = {},
        block: suspend () -> T,
    ): T? {
        val acquired = if (preempt) {
            acquireNow(owner, timeoutMs)
        } else {
            acquire(owner, timeoutMs)
        }
        if (!acquired) return null

        return try {
            block()
        } finally {
            release(owner)
            onRelease()
        }
    }

    /**
     * Takes the LEDs if they are free, and gives up if they are not.
     *
     * `tryLock` in a poll loop, deliberately not
     * `withTimeoutOrNull { lock.lock() }`: if that timeout fires after the lock
     * has been granted but before the block returns, the coroutine is cancelled
     * while *holding* it. The lock is then never released and every feature
     * reports "LEDs busy" on every event until the process dies — the same
     * symptom as a feature that works once and then never again. A
     * non-blocking `tryLock()` has no such window: it either grants the lock
     * or takes nothing.
     */
    suspend fun acquire(owner: GlyphFeature, timeoutMs: Long = ACQUIRE_TIMEOUT_MS): Boolean {
        // The Glyph session is ensured *before* contending for the strip, not
        // while holding it.
        //
        // Opening a session does not touch the LEDs, so there was no reason to
        // serialise it behind the strip mutex — but holding the mutex across it
        // starved everything else: `forceEnsureSession` waits up to two seconds
        // for the system service to bind, and every other feature gives up
        // after [ACQUIRE_TIMEOUT_MS], 500 ms. So a single trigger arriving
        // while the Glyph service was reconnecting silently dropped every other
        // trigger for the rest of that window — and the feature that did get
        // in could not draw either.
        if (!ensureSession()) return false

        if (!tryLockWithin(timeoutMs)) return false

        _currentOwner.value = owner
        return true
    }

    /**
     * Opens a Glyph session if there is not one, with at most one waiter.
     *
     * Without the guard two features arriving together would both see no
     * session and both call `forceEnsureSession`. `openSession` is guarded by a
     * plain `if` against a volatile flag, so one of them would go on to open a
     * session the other had just opened.
     *
     * Cheap because the guard is only contended on the cold path: the active
     * check is the common case and never waits.
     */
    private suspend fun ensureSession(): Boolean = sessionGuard.withLock {
        if (glyphManager.isSessionActive) return@withLock true
        withContext(Dispatchers.IO) { glyphManager.forceEnsureSession() }
    }

    /** Polls [lock] until it is free or [timeoutMs] elapses. */
    private suspend fun tryLockWithin(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000L)
        do {
            if (lock.tryLock()) return true
            delay(TRY_LOCK_POLL_MS.milliseconds)
        } while (System.nanoTime() < deadline)
        return false
    }

    /**
     * Hands the strip back, ignoring any caller that is not the current owner.
     *
     * [lock] is the mutual-exclusion primitive here and it alone decides who
     * holds the strip; [_currentOwner] is what the rest of the app reads, to
     * report and to decide who to preempt. Nulling the owner and *then*
     * unlocking is safe in that order — a second `tryLock()` cannot succeed
     * while the mutex is still locked, so there is no window in which the next
     * feature is already inside and the previous owner then frees its lock.
     *
     * What the owner check does guard is a caller that has already been
     * dispossessed: it has returned, and letting it unlock would free a lock
     * the real owner is still holding.
     *
     * The LEDs are turned off *before* the lock is released, so the next owner
     * never inherits a strip that is still lit.
     *
     * `unlock` is wrapped because this runs from `withStrip`'s `finally`, where
     * an exception replaces whatever the block was already unwinding with.
     */
    fun release(owner: GlyphFeature) {
        if (_currentOwner.value != owner) return

        runCatching { glyphManager.turnOffAll() }

        _currentOwner.value = null
        runCatching { lock.unlock() }
    }
}

/**
 * All high level app features that can drive the Glyph LEDs.
 *
 * The single enumeration the feature list, the service wiring and the UI all
 * agree on, so a feature cannot be switched on without something behind it.
 */
enum class GlyphFeature {
    PULSE_LOCK,
    POWER_PEEK,
    LOW_BATTERY,
    SCREEN_OFF,
    NFC,
    CHARGING_ANIMATION,
    MUSIC_VISUALIZER,
    VPN_CONNECTED,

    /**
     * The studio's preview, and not a feature.
     *
     * It is in this enum because it has to take the strip through exactly the
     * same mutex as everything else. It used not to, and the cost was that
     * opening the editor drew over a live service's animation — the service
     * kept drawing too, both interleaved on the same LEDs, and whichever
     * finished first left the other's half-played sequence on the strip with
     * nothing anywhere in the log.
     *
     * No service and no preference, so no `FeatureSpec`; see
     * `FeatureSpecs.NON_FEATURE_PARTICIPANTS`.
     */
    PREVIEW,
}