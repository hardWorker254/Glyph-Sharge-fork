package com.bleelblep.glyphsharge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bleelblep.glyphsharge.services.*
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Ensures services are restored after a device reboot.
 *
 * Strategy:
 *  • Use goAsync() so we don't block the main thread and get more execution time.
 *  • Tier 1 (immediate): GlyphForegroundService — the master session gate.
 *  • Tier 2 (100 ms delay): User-visible features that depend on an open session.
 *  • Tier 3 (3 s coroutine delay): Battery Story / ChargeTracker — heavy IO,
 *    not time-critical.
 *
 * Also registers for ACTION_LOCKED_BOOT_COMPLETED (Android N+) so the master
 * glyph service can open even before the user unlocks after reboot.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        Log.d(TAG, "Boot broadcast received: ${intent.action}")

        // goAsync() gives us ~10 s instead of ~5 s, and frees the main thread immediately.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                startServicesInOrder(context)
            } finally {
                pendingResult.finish() // must always be called
            }
        }
    }

    private suspend fun startServicesInOrder(context: Context) {
        val glyphOn = settingsRepository.getGlyphServiceEnabled()

        // ── Tier 1: start the master session service immediately ──────────────
        // Everything else depends on an open Glyph session, so this goes first.
        if (glyphOn) {
            Log.d(TAG, "Tier 1 – starting GlyphForegroundService")
            context.startForegroundServiceCompat(GlyphForegroundService::class.java)
        }

        // ── Tier 2: user-visible features (small delay lets Tier 1 bind first) ─
        delay(TIER2_DELAY_MS)

        if (glyphOn) {
            if (settingsRepository.isPowerPeekEnabled()) {
                Log.d(TAG, "Tier 2 – PowerPeek")
                context.startForegroundServiceCompat(PowerPeekService::class.java)
            }
            if (settingsRepository.isLowBatteryEnabled()) {
                Log.d(TAG, "Tier 2 – LowBatteryAlert")
                context.startForegroundServiceCompat(LowBatteryAlertService::class.java)
            }
            if (settingsRepository.isPulseLockEnabled()) {
                Log.d(TAG, "Tier 2 – PulseLock")
                context.startForegroundServiceCompat(PulseLockService::class.java)
            }
            if (settingsRepository.isScreenOffFeatureEnabled()) {
                Log.d(TAG, "Tier 2 – ScreenOff")
                context.startForegroundServiceCompat(ScreenOffGlyphService::class.java)
            }
            if (settingsRepository.isNfcFeatureEnabled()) {
                Log.d(TAG, "Tier 2 – Hfc")
                context.startForegroundServiceCompat(NfcGlyphService::class.java)
            }
            if (settingsRepository.isChargingAnimationEnabled()) {
                Log.d(TAG, "Tier 2 – Charging Animation")
                context.startForegroundServiceCompat(ChargingAnimationService::class.java)
            }
        }

        // Quiet Hours has no Glyph dependency
        if (settingsRepository.isQuietHoursEnabled()) {
            Log.d(TAG, "Tier 2 – QuietHours")
            context.startForegroundServiceCompat(QuietHoursService::class.java) {
                action = QuietHoursService.ACTION_START_QUIET_HOURS
            }
        }
    }

    companion object {
        private const val TAG            = "BootReceiver"
        private const val TIER2_DELAY_MS = 100L
    }
}

// ── Extension: removes the Build.VERSION boilerplate everywhere ──────────────
private fun Context.startForegroundServiceCompat(
    clazz: Class<*>,
    configure: Intent.() -> Unit = {}
) {
    val intent = Intent(this, clazz).apply(configure)
    startForegroundService(intent)
}