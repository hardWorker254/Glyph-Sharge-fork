package com.bleelblep.glyphsharge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.FeatureSpecs
import com.bleelblep.glyphsharge.services.GlyphForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Restores the services the user had running when the device reboots.
 *
 *  • `super.onReceive` is deliberately *not* called: for a receiver Hilt does
 *    the injection differently than for an Activity or a Service. The Gradle
 *    plugin swaps the base class for the generated `Hilt_*` one and its ASM
 *    transform writes the `super.onReceive()` call — which is what runs
 *    `inject()` — into the bytecode, first instruction. In source, `super`
 *    would resolve against `BroadcastReceiver.onReceive`, which is abstract,
 *    and would not even compile. Adding it by hand is both impossible and
 *    unnecessary.
 *  • `goAsync()` buys ~10 s instead of the ~5 s a receiver gets, and the
 *    coroutine runs on [SupervisorJob] with a `catch`: an exception out of a
 *    bare `launch` reaches the thread's default handler, which kills the app —
 *    and `ForegroundServiceStartNotAllowedException` from a background
 *    `startForegroundService` is exactly what a boot restore can produce.
 *  • Tier 1 (immediate): [GlyphForegroundService], the master session gate,
 *    because every feature below it depends on an open session.
 *  • Tier 2 (100 ms delay): the enabled feature services, walked through the
 *    [FeatureSpecs] registry rather than a hand-written `if` per feature. A
 *    feature added to the registry is restored automatically; the old list had
 *    to be edited by hand and silently missed anything that was not added.
 *
 * Restored after an unlock only. Direct Boot would need `directBootAware` on
 * this receiver *and* a direct-boot-safe settings store — SharedPreferences
 * lives in credential-encrypted storage and is unreadable before unlock — so
 * the pre-unlock branch is deliberately absent rather than half-wired.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var serviceController: FeatureServiceController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot broadcast received")

        // goAsync() gives us ~10 s instead of ~5 s, and frees the main thread immediately.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                startServicesInOrder(context)
            } catch (e: Exception) {
                Log.e(TAG, "Boot restore failed", e)
            } finally {
                pendingResult.finish() // must always be called
            }
        }
    }

    private suspend fun startServicesInOrder(context: Context) {
        // Tier 1: master session service first, because everything else
        // depends on an open Glyph session.
        if (!settingsRepository.getGlyphServiceEnabled()) return

        Log.d(TAG, "Tier 1 – starting GlyphForegroundService")
        startServiceOrNull(context, GlyphForegroundService::class.java)

        // Tier 2: user-visible features. The small delay lets Tier 1 bind first.
        delay(TIER2_DELAY_MS.milliseconds)

        FeatureSpecs.all
            .filter { it.isEnabled(settingsRepository) }
            .forEach { spec ->
                // The visualiser holds no trigger of its own: its token can only
                // be obtained from an Activity, so a restored service would sit
                // dark until the app is opened. Left for the app to start, which
                // is where the consent chain lives.
                if (spec.feature == GlyphFeature.MUSIC_VISUALIZER) {
                    Log.d(TAG, "${spec.feature} is on – needs the app opened for consent")
                    return@forEach
                }
                Log.d(TAG, "Tier 2 – ${spec.feature}")
                startServiceOrNull(context, spec.serviceClass)
            }

        // The other half of the restore: the generic host carries the user's
        // rules, so it is synced once here rather than started per feature —
        // the same split `FeatureServiceController.startAllEnabled` makes.
        serviceController.syncRuleHost()
    }

    /**
     * `startForegroundService`, refusing rather than throwing.
     *
     * A boot restore runs from a coroutine whose only safety net is this
     * receiver's own `catch`, and Android 12+ raises
     * `ForegroundServiceStartNotAllowedException` from the background. One
     * feature being refused must not abort the ones after it.
     */
    private fun startServiceOrNull(context: Context, clazz: Class<*>) {
        runCatching { context.startForegroundService(Intent(context, clazz)) }
            .onFailure { Log.w(TAG, "${clazz.simpleName} did not start", it) }
    }

    companion object {
        private const val TAG            = "BootReceiver"
        private const val TIER2_DELAY_MS = 100L
    }
}
