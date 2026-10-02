package com.bleelblep.glyphsharge.ui.utils

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.provider.Settings

/**
 * Utility class for managing haptic feedback across the app
 * Provides consistent haptic patterns for different interactions
 * 
 * Android Vibration Amplitude Documentation:
 * - Amplitude range: 1-255 (0 = OFF)
 * - DEFAULT_AMPLITUDE = -1 (system default)
 * - Always check hasAmplitudeControl() for device support
 */
object HapticUtils {
    
    // Standard intensity levels based on Android documentation
    object Intensity {
        const val OFF = 0
        const val LIGHT = 85          // ~33% of max (255 * 0.33)
        const val MEDIUM = 170        // ~66% of max (255 * 0.66) 
        const val STRONG = 255        // 100% max amplitude
        const val DEFAULT = VibrationEffect.DEFAULT_AMPLITUDE // -1
    }
    
    // Default durations for different haptic types
    private const val SHORT_DURATION = 50L
    private const val MEDIUM_DURATION = 100L
    private const val LONG_DURATION = 200L

    /**
     * Performs light haptic feedback with specified intensity
     */
    fun performLightHaptic(
        context: Context, 
        hapticFeedback: HapticFeedback,
        intensity: Int = Intensity.LIGHT,
    ) {
        haptic(context, hapticFeedback, SHORT_DURATION, intensity, HapticFeedbackType.TextHandleMove)
    }
    
    /**
     * The one place a tap's haptic is produced.
     *
     * Every helper used to call `performHapticFeedback` and then, separately and
     * unconditionally, drive the vibrator directly. That produced two buzzes on
     * every tap in the app — the first at `LongPress` grade, which is the
     * strongest of the four types and is meant for a long press.
     *
     * Worse, the second one did not consult the system setting. The first call
     * returns `false` when the user has turned touch feedback off; the raw
     * `vibrator.vibrate` that followed ignored that and buzzed anyway, at an
     * amplitude the user cannot influence. So the comment "respects user
     * settings" described only half of what the function did.
     *
     * Now the return value decides. If the system is going to vibrate for this
     * tap, the custom amplitude is added on top of it; if the user has said no,
     * nothing happens at all — which is what they asked for.
     */
    private fun haptic(
        context: Context,
        hapticFeedback: HapticFeedback,
        duration: Long,
        intensity: Int,
        type: HapticFeedbackType,
    ) {
        // The gate is the system setting rather than the return value of
        // `performHapticFeedback`, which in current Compose returns the type it
        // performed rather than a yes/no, so it cannot answer this.
        //
        // Reading the setting is the honest check: it is the same flag Developer
        // Options and the accessibility touch-feedback toggle write, so a user
        // who has switched haptics off is not buzzed at anyway.
        if (!hapticsEnabled(context)) return
        hapticFeedback.performHapticFeedback(type)
        if (intensity == Intensity.DEFAULT) return
        performCustomVibration(context, duration, intensity)
    }

    /**
     * Performs medium haptic feedback with specified intensity
     */
    fun performMediumHaptic(
        context: Context,
        hapticFeedback: HapticFeedback,
        intensity: Int = Intensity.MEDIUM,
    ) {
        haptic(context, hapticFeedback, MEDIUM_DURATION, intensity, HapticFeedbackType.LongPress)
    }
    
    /**
     * Performs strong haptic feedback with specified intensity
     */
    fun performStrongHaptic(
        context: Context,
        hapticFeedback: HapticFeedback,
        intensity: Int = Intensity.STRONG,
    ) {
        haptic(context, hapticFeedback, LONG_DURATION, intensity, HapticFeedbackType.LongPress)
    }

    /**
     * Performs button click haptic feedback
     */
    fun performClickHaptic(
        context: Context,
        hapticFeedback: HapticFeedback,
        intensity: Int = Intensity.LIGHT,
    ) {
        // A click is not a long press. `TextHandleMove` is the lightest of the
        // four and is what a tap should feel like.
        haptic(context, hapticFeedback, SHORT_DURATION, intensity, HapticFeedbackType.TextHandleMove)
    }

    /**
     * Performs success haptic feedback
     */
    fun performSuccessHaptic(
        context: Context,
        hapticFeedback: HapticFeedback,
        intensity: Int = Intensity.MEDIUM,
    ) {
        // Gated on the same answer as [haptic]: a predefined `EFFECT_CLICK` is
        // still a buzz, and a user who has switched touch feedback off does not
        // want it.
        if (!hapticsEnabled(context)) return
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        // Use predefined VibrationEffect if available (API 29+)
        val vibrator = getVibrator(context)
        if (vibrator?.hasVibrator() == true) {
            try {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator.vibrate(effect)
                return
            } catch (_: Exception) {
                // Fall back to custom vibration
            }
        }
        
        // Fallback to custom pattern
        performCustomVibration(context, SHORT_DURATION, intensity)
    }
    
    /**
     * Performs error haptic feedback
     */
    fun performErrorHaptic(
        context: Context,
        hapticFeedback: HapticFeedback,
        intensity: Int = Intensity.STRONG,
    ) {
        // Same gate as every other haptic here.
        if (!hapticsEnabled(context)) return
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

        // Create a double-pulse pattern for error feedback
        val pattern = longArrayOf(0, 100, 50, 100) // off, on, off, on
        val amplitudes = intArrayOf(0, intensity, 0, intensity)
        
        performCustomVibrationPattern(context, pattern, amplitudes)
    }
    
    /**
     * Performs custom vibration with specified duration and intensity
     */
    private fun performCustomVibration(
        context: Context,
        duration: Long,
        intensity: Int,
    ) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val safeIntensity = validateIntensity(intensity, vibrator)

        try {
            val effect = if (safeIntensity == Intensity.DEFAULT) {
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createOneShot(duration, safeIntensity)
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {
            // Fallback for very old devices
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    /**
     * Performs custom vibration pattern with amplitudes
     */
    private fun performCustomVibrationPattern(
        context: Context,
        pattern: LongArray,
        amplitudes: IntArray,
    ) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        
        if (vibrator.hasAmplitudeControl()) {
            try {
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            } catch (_: Exception) {
                // Fallback to pattern without amplitudes
            @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } else {
            // Fallback for devices without amplitude control
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /**
     * Validates and adjusts intensity based on device capabilities
     */
    private fun validateIntensity(intensity: Int, vibrator: Vibrator): Int {
        return when {
            intensity == Intensity.DEFAULT -> intensity
            intensity <= 0 -> Intensity.OFF
            intensity > 255 -> Intensity.STRONG
            // Device doesn't support amplitude control: any non-zero value is
            // rounded up to full power anyway, so there is nothing to scale.
            // `intensity` is already known to be positive here — the two
            // branches above claim everything at or below zero.
            !vibrator.hasAmplitudeControl() -> Intensity.STRONG
            else -> intensity
        }
    }

    /**
     * Whether the system wants haptic feedback at all.
     *
     * `HAPTIC_FEEDBACK_ENABLED` is the flag Developer Options and the
     * accessibility touch-feedback toggle write. Read with a default of `1`,
     * which is the documented default: a device that does not have the setting
     * at all is a device where haptics are on.
     *
     * `Settings.System.getInt` throws when the key is absent, which is why the
     * second form is the one used — the two-argument overload answers `1`
     * instead.
     */
    // Deprecated in API 33, but it is still the documented way to answer this
    // question: the haptics guide points at exactly this key for "has the user
    // turned touch feedback off", and there is no replacement that reports the
    // accessibility toggle rather than just this app's own feedback.
    @Suppress("DEPRECATION")
    private fun hapticsEnabled(context: Context): Boolean =
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) != 0

    /**
     * The system vibrator, or `null` where there is not one.
     *
     * Nullable rather than assumed: `getSystemService` returns `null` on a
     * device with no vibrator hardware, and the unchecked cast turned that into
     * an `NPE` on every tap in the app. A missing vibrator is a device without
     * haptics, which is a perfectly ordinary thing to be, not a crash.
     */
    private fun getVibrator(context: Context): Vibrator? =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    
    /**
     * Converts user intensity (0.0-1.0) to Android amplitude (1-255)
     */
    fun convertUserIntensityToAndroidAmplitude(userIntensity: Float): Int {
        return when {
            userIntensity <= 0.0f -> Intensity.OFF
            userIntensity >= 1.0f -> Intensity.STRONG
            else -> {
                // Scale 0.0-1.0 to 1-255: multiply by 254 then add 1
                val scaled = (userIntensity * 254f).toInt() + 1
                // Ensure we never go below 1 (unless OFF) or above 255
                scaled.coerceIn(1, 255)
            }
        }
    }

    /**
     * Performs haptic with specified user intensity (0.0-1.0 range)
     */
    fun performHapticWithIntensity(
        context: Context,
        hapticFeedback: HapticFeedback,
        userIntensity: Float,
        type: HapticType = HapticType.LIGHT,
    ) {
        val androidIntensity = convertUserIntensityToAndroidAmplitude(userIntensity)
        
        when (type) {
            HapticType.LIGHT -> performLightHaptic(context, hapticFeedback, androidIntensity)
            HapticType.MEDIUM -> performMediumHaptic(context, hapticFeedback, androidIntensity)
            HapticType.STRONG -> performStrongHaptic(context, hapticFeedback, androidIntensity)
            HapticType.CLICK -> performClickHaptic(context, hapticFeedback, androidIntensity)
            HapticType.SUCCESS -> performSuccessHaptic(context, hapticFeedback, androidIntensity)
            HapticType.ERROR -> performErrorHaptic(context, hapticFeedback, androidIntensity)
        }
    }
    
    /**
     * The five helpers every tap in the app actually goes through.
     *
     * `intensity` is the user's own 0..1 setting and is injected by the caller
     * because these run inside click handlers, which cannot read a repository.
     * The value arrives from
     * [com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity], which the
     * Activity provides once from its injected settings store.
     */
    fun triggerLightFeedback(hapticFeedback: HapticFeedback, context: Context, intensity: Float) {
        performHapticWithIntensity(context, hapticFeedback, intensity, HapticType.LIGHT)
    }

    fun triggerMediumFeedback(hapticFeedback: HapticFeedback, context: Context, intensity: Float) {
        performHapticWithIntensity(context, hapticFeedback, intensity, HapticType.MEDIUM)
    }
}

/**
 * Enum for different haptic feedback types
 */
enum class HapticType {
    LIGHT,
    MEDIUM, 
    STRONG,
    CLICK,
    SUCCESS,
    ERROR
} 