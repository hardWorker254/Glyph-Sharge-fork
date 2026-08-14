package com.bleelblep.glyphsharge.glyph

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val glyphManager: GlyphManager
) {
    private val lock = Mutex()
    private val _currentOwner = MutableStateFlow<GlyphFeature?>(null)
    val currentOwner: StateFlow<GlyphFeature?> = _currentOwner.asStateFlow()

    suspend fun acquire(owner: GlyphFeature, timeoutMs: Long = 500L): Boolean {
        val acquired = withTimeoutOrNull(timeoutMs) {
            lock.lock()
            true
        } ?: false

        if (!acquired) return false

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
            if (lock.isLocked) lock.unlock()
            return false
        }

        return true
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