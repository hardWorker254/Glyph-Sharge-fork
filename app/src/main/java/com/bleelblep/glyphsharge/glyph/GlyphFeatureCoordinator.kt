package com.bleelblep.glyphsharge.glyph

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        /** How often a contended lock is retried before giving up. */
        const val TRY_LOCK_POLL_MS = 25L

        /**
         * How long a preempting feature waits for the interrupted one to let
         * go. The loser stops drawing immediately and releases in its `finally`,
         * so this is generous; it only has to outlive that unwind.
         */
        const val PREEMPT_TIMEOUT_MS = 1_500L

        private const val TAG = "GlyphCoordinator"
    }
    private val lock = Mutex()
    private val _currentOwner = MutableStateFlow<GlyphFeature?>(null)
    val currentOwner: StateFlow<GlyphFeature?> = _currentOwner.asStateFlow()

    /**
     * Takes the LEDs for an event the user just caused — an unlock, a tap, a
     * screen-off — even if something is still playing.
     *
     * Glow Gate and Screen Off fire seconds apart in the normal course of
     * locking a phone, and a looping custom animation holds the strip for the
     * whole duration. Whichever ran first would win, and the one the user
     * actually did would be skipped half the time depending on how quickly
     * they unlocked.
     *
     * Interrupting is safe without breaking the lock: the current owner is not
     * dispossessed, it is asked to stop drawing, returns, and releases in its
     * own `finally` — which is the only path that unlocks correctly. Taking
     * the lock out from under it here would strand it, because `release`
     * deliberately ignores a caller that is no longer the owner.
     */
    suspend fun acquireNow(
        owner: GlyphFeature,
        timeoutMs: Long = PREEMPT_TIMEOUT_MS
    ): Boolean {
        if (_currentOwner.value != null && _currentOwner.value != owner) {
            Log.d(TAG, "${owner.name} interrupts ${_currentOwner.value} for the strip")
            glyphAnimationManager.stopAnimations()
        }
        return acquire(owner, timeoutMs)
    }

    /**
     * Takes the LEDs if they are free, and gives up if they are not.
     *
     * `tryLock`, and deliberately not `withTimeoutOrNull { lock.lock() }`.
     *
     * That version has a fatal race: if the timeout fires after the lock has
     * been granted but before the block returns, the coroutine is cancelled
     * while *holding* it. The lock is then never released, and from that
     * moment every feature reports "LEDs busy" on every event until the process
     * dies — which is exactly what a feature that works once and then never
     * again looks like.
     *
     * A non-blocking `tryLock()` in a poll loop has no such window: it either
     * returns true and the caller owns the lock, or it returns false and
     * nothing was taken.
     */
    suspend fun acquire(owner: GlyphFeature, timeoutMs: Long = 500L): Boolean {
        // Deliberately not `withTimeoutOrNull { lock.lock(); true }`.
        //
        // That version has a fatal race: if the timeout fires after the lock
        // has been granted but before the block returns, the coroutine is
        // cancelled while *holding* it. The lock is then never released, and
        // from that moment every feature reports "LEDs busy" on every event
        // until the process dies — which is exactly what a feature that works
        // once and then never again looks like.
        //
        // A non-blocking `tryLock()` in a poll loop has no such window: it
        // either returns true and the caller owns the lock, or it returns false
        // and nothing was taken.
        if (!tryLockWithin(timeoutMs)) return false

        _currentOwner.value = owner

        val ready = if (!glyphManager.isSessionActive) {
            withContext(Dispatchers.IO) {
                glyphManager.forceEnsureSession()
            }
        } else {
            true
        }

        if (!ready) {
            _currentOwner.value = null
            runCatching { lock.unlock() }
            return false
        }

        return true
    }

    /** Polls [lock] until it is free or [timeoutMs] elapses. */
    private suspend fun tryLockWithin(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        do {
            if (lock.tryLock()) return true
            delay(TRY_LOCK_POLL_MS)
        } while (System.nanoTime() < deadline)
        return false
    }

    fun release(owner: GlyphFeature) {
        if (_currentOwner.value != owner) return

        // Сначала гасим LED, и только потом отдаём lock следующему владельцу.
        runCatching { glyphManager.turnOffAll() }

        _currentOwner.value = null
        if (lock.isLocked) {
            lock.unlock()
        }
    }
}

/** All high level app features that can drive Glyph LEDs. */
enum class GlyphFeature {
    PULSE_LOCK,
    POWER_PEEK,
    GLYPH_GUARD,
    BATTERY_STORY,
    MANUAL_DEMO,
    LOW_BATTERY,
    SCREEN_OFF,
    NFC,
    CHARGING_ANIMATION,
}