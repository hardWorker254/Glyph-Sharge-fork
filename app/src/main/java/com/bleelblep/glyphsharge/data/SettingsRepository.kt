package com.bleelblep.glyphsharge.data

import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.FontSizeSettings
import com.bleelblep.glyphsharge.ui.theme.FontVariant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the settings slices.
 *
 * The real settings now live in [ThemeSettings], [FontSettings],
 * [GlyphServiceSettings], [FeatureSettings],
 * [LanguageSettings] and [UserPresenceSettings], each owning its own keys. This
 * class owns none of them: every method below is a one-line forward, and there
 * is not a single preference key, default or `SharedPreferences` call left
 * here.
 *
 * ## Why it still exists
 *
 * It is a compatibility surface, not a design. Rewriting all ~25 call sites —
 * 13 services, the boot receiver, the animation manager, the feature card
 * composables and their parameter chains — in the same change that splits the
 * storage would have put every settings read in the app behind review at once,
 * for a refactor whose whole claim is that it changes no behaviour. The split
 * is the valuable half; the call-site migration is a separate, mechanical change
 * that can be made afterwards and reviewed on its own, one slice at a time.
 *
 * Every signature below is unchanged from the monolith, which is what makes
 * that later migration a pure import-and-constructor swap. Deleting a method
 * here is a compile error somewhere real, so the facade cannot quietly fall
 * behind the slices it forwards to.
 *
 * [migrations] is injected but never called on: it is a constructor argument
 * so that building this class still forces [SettingsMigrations] to be built,
 * which preserves the original guarantee that the first-run defaults are on
 * disk before any getter here can run.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val theme: ThemeSettings,
    private val fonts: FontSettings,
    private val glyphService: GlyphServiceSettings,
    private val features: FeatureSettings,
    private val language: LanguageSettings,
    private val userPresence: UserPresenceSettings,
    @Suppress("unused") private val migrations: SettingsMigrations,
    private val diagnostics: SettingsDiagnostics,
) {

    // Font settings

    fun saveFontVariant(variant: FontVariant) = fonts.saveFontVariant(variant)

    fun getFontVariant(): FontVariant = fonts.getFontVariant()

    fun saveUseCustomFonts(useCustom: Boolean) = fonts.saveUseCustomFonts(useCustom)

    fun getUseCustomFonts(): Boolean = fonts.getUseCustomFonts()

    fun saveFontSizeSettings(settings: FontSizeSettings) = fonts.saveFontSizeSettings(settings)

    fun clearFontSizeCustomization() = fonts.clearFontSizeCustomization()

    fun getFontSizeSettingsForFont(fontVariant: FontVariant): FontSizeSettings =
        fonts.getFontSizeSettingsForFont(fontVariant)

    // Theme settings

    fun saveTheme(isDarkTheme: Boolean) = theme.saveTheme(isDarkTheme)

    fun getTheme(): Boolean = theme.getTheme()

    fun saveThemeStyle(themeStyle: AppThemeStyle) = theme.saveThemeStyle(themeStyle)

    fun getThemeStyle(): AppThemeStyle = theme.getThemeStyle()

    // Services

    fun saveGlyphServiceEnabled(enabled: Boolean) = glyphService.saveGlyphServiceEnabled(enabled)

    fun getGlyphServiceEnabled(): Boolean = glyphService.getGlyphServiceEnabled()

    // Glow Gate: what "unlocked" looks like on this phone

    fun isUserPresentExpected(): Boolean = userPresence.isUserPresentExpected()

    fun markUserPresentSeen() = userPresence.markUserPresentSeen()

    fun markUserPresentMissing() = userPresence.markUserPresentMissing()

    // Power Peek

    fun savePowerPeekEnabled(enabled: Boolean) = features.savePowerPeekEnabled(enabled)

    fun isPowerPeekEnabled(): Boolean = features.isPowerPeekEnabled()

    fun savePowerPeekThreshold(threshold: Float) = features.savePowerPeekThreshold(threshold)

    fun getPowerPeekThreshold(): Float = features.getPowerPeekThreshold()

    fun getShakeIntensityLevel(threshold: Float): Int =
        glyphService.getShakeIntensityLevel(threshold)

    fun savePowerPeekDuration(duration: Long) = features.savePowerPeekDuration(duration)

    fun getPowerPeekDuration(): Long = features.getPowerPeekDuration()

    fun getVibrationIntensity(): Float = glyphService.getVibrationIntensity()

    // Glow Gate (Pulse Lock)

    fun savePulseLockEnabled(enabled: Boolean) = features.savePulseLockEnabled(enabled)

    fun isPulseLockEnabled(): Boolean = features.isPulseLockEnabled()

    fun savePulseLockAnimationId(id: String) = features.savePulseLockAnimationId(id)

    fun getPulseLockAnimationId(): String = features.getPulseLockAnimationId()

    fun savePulseLockDuration(durationMs: Long) = features.savePulseLockDuration(durationMs)

    fun getPulseLockDuration(): Long = features.getPulseLockDuration()

    // Low-Battery

    fun saveLowBatteryEnabled(enabled: Boolean) = features.saveLowBatteryEnabled(enabled)

    fun isLowBatteryEnabled(): Boolean = features.isLowBatteryEnabled()

    fun saveLowBatteryThreshold(pct: Int) = features.saveLowBatteryThreshold(pct)

    fun getLowBatteryThreshold(): Int = features.getLowBatteryThreshold()

    fun saveLowBatteryAnimationId(id: String) = features.saveLowBatteryAnimationId(id)

    fun getLowBatteryAnimationId(): String = features.getLowBatteryAnimationId()

    fun saveLowBatteryDuration(durationMs: Long) = features.saveLowBatteryDuration(durationMs)

    fun getLowBatteryDuration(): Long = features.getLowBatteryDuration()

    // Screen Off

    fun saveScreenOffFeatureEnabled(enabled: Boolean) =
        features.saveScreenOffFeatureEnabled(enabled)

    fun isScreenOffFeatureEnabled(): Boolean = features.isScreenOffFeatureEnabled()

    fun saveScreenOffAnimationId(id: String) = features.saveScreenOffAnimationId(id)

    fun getScreenOffAnimationId(): String = features.getScreenOffAnimationId()

    fun saveScreenOffDuration(durationMs: Long) = features.saveScreenOffDuration(durationMs)

    fun getScreenOffDuration(): Long = features.getScreenOffDuration()

    // NFC

    fun saveNfcFeatureEnabled(enabled: Boolean) = features.saveNfcFeatureEnabled(enabled)

    fun isNfcFeatureEnabled(): Boolean = features.isNfcFeatureEnabled()

    fun saveNfcAnimationId(id: String) = features.saveNfcAnimationId(id)

    fun getNfcAnimationId(): String = features.getNfcAnimationId()

    fun saveNfcAnimationDuration(durationMs: Long) = features.saveNfcAnimationDuration(durationMs)

    fun getNfcAnimationDuration(): Long = features.getNfcAnimationDuration()

    // Charging animation

    fun saveChargingAnimationEnabled(enabled: Boolean) =
        features.saveChargingAnimationEnabled(enabled)

    fun isChargingAnimationEnabled(): Boolean = features.isChargingAnimationEnabled()

    fun saveChargingAnimationDuration(durationMs: Long) =
        features.saveChargingAnimationDuration(durationMs)

    fun getChargingAnimationDuration(): Long = features.getChargingAnimationDuration()

    // VPN connected

    fun saveVpnConnectedEnabled(enabled: Boolean) = features.saveVpnConnectedEnabled(enabled)

    fun isVpnConnectedEnabled(): Boolean = features.isVpnConnectedEnabled()

    fun saveVpnConnectedAnimationId(id: String) = features.saveVpnConnectedAnimationId(id)

    fun getVpnConnectedAnimationId(): String = features.getVpnConnectedAnimationId()

    fun saveVpnConnectedDuration(durationMs: Long) = features.saveVpnConnectedDuration(durationMs)

    fun getVpnConnectedDuration(): Long = features.getVpnConnectedDuration()

    // Music visualiser

    fun saveMusicVizEnabled(enabled: Boolean) = features.saveMusicVizEnabled(enabled)

    fun isMusicVizEnabled(): Boolean = features.isMusicVizEnabled()

    fun saveMusicVizAnimationId(id: String) = features.saveMusicVizAnimationId(id)

    fun getMusicVizAnimationId(): String = features.getMusicVizAnimationId()

    fun saveMusicVizSensitivity(sensitivity: Float) =
        features.saveMusicVizSensitivity(sensitivity)

    fun getMusicVizSensitivity(): Float = features.getMusicVizSensitivity()

    fun saveMusicVizScreenOffOnly(onlyWhenScreenOff: Boolean) =
        features.saveMusicVizScreenOffOnly(onlyWhenScreenOff)

    fun getMusicVizScreenOffOnly(): Boolean = features.getMusicVizScreenOffOnly()

    // Language Settings

    fun getAppLanguageCode(): String = language.getAppLanguageCode()

    fun saveAppLanguageCode(code: String) = language.saveAppLanguageCode(code)

    // Debug

    fun dumpAllSettings() = diagnostics.dumpAllSettings()

    companion object {
        /**
         * Re-exported from [GlyphServiceSettings] so the Power Peek slider, which
         * snaps to these steps, keeps compiling unchanged.
         */
        const val SHAKE_SOFT = GlyphServiceSettings.SHAKE_SOFT
        const val SHAKE_EASY = GlyphServiceSettings.SHAKE_EASY
        const val SHAKE_MEDIUM = GlyphServiceSettings.SHAKE_MEDIUM
        const val SHAKE_HARD = GlyphServiceSettings.SHAKE_HARD
        const val SHAKE_HARDEST = GlyphServiceSettings.SHAKE_HARDEST
    }
}
