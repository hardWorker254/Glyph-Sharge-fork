package com.bleelblep.glyphsharge.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only dump of the settings store.
 *
 * This lives outside the settings slices on purpose. It *reads* across all of
 * them, so putting it in any one slice would give that slice a permanent
 * dependency on five siblings it otherwise has no reason to know about. As its
 * own class, the slices keep their independence and the only place that knows
 * the whole shape of the settings is the one place that wants to print it.
 *
 * The output format is unchanged, because it is what existing bug reports
 * quote.
 */
@Singleton
class SettingsDiagnostics @Inject constructor(
    private val theme: ThemeSettings,
    private val fonts: FontSettings,
    private val glyphService: GlyphServiceSettings,
    private val features: FeatureSettings,
    private val quietHours: QuietHoursSettings,
) {

    fun dumpAllSettings() {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val dump = """
            === All Settings ===
            Font Variant: ${fonts.getFontVariant()}
            Dark Theme: ${theme.getTheme()}
            Theme Style: ${theme.getThemeStyle()}
            Glyph Service: ${glyphService.getGlyphServiceEnabled()}
            PowerPeek: ${features.isPowerPeekEnabled()}
            Shake Threshold: ${features.getPowerPeekThreshold()}
            Display Duration: ${features.getPowerPeekDuration()}
            Vibration Intensity: ${glyphService.getVibrationIntensity()}
            Glow Gate enabled: ${features.isPulseLockEnabled()}
            Low-Battery Alert enabled: ${features.isLowBatteryEnabled()}
            Screen Off Anim enabled: ${features.isScreenOffFeatureEnabled()}
            NFC Feature enabled: ${features.isNfcFeatureEnabled()}
            NFC Animation ID: ${features.getNfcAnimationId()}
            NFC Animation Duration: ${features.getNfcAnimationDuration()}ms
            Quiet Hours enabled: ${quietHours.isQuietHoursEnabled()}
            Quiet Hours start: ${quietHours.getQuietHoursStartHour()}:${quietHours.getQuietHoursStartMinute()}
            Quiet Hours end: ${quietHours.getQuietHoursEndHour()}:${quietHours.getQuietHoursEndMinute()}
            ===================
        """.trimIndent()
        Log.d(TAG, dump)
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
