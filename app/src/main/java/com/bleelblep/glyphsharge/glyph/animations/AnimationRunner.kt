package com.bleelblep.glyphsharge.glyph.animations

import android.util.Log
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.device.DeviceProfile
import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The gate and the renderer lifecycle that every sequence runs under.
 *
 * Before an animation may touch the strip there are three things to check — the
 * feature must be enabled, the hardware must be supported, and a layout for
 * this phone must exist — and four things to do around it: blank the strip
 * first, run, blank it again, and log a failure rather than letting it escape.
 *
 * They live in one place because every one of them is silent when it goes
 * wrong: a run that skips the guard reports a perfect animation over a dark
 * strip, and a caller that counts frames cannot tell the two apart.
 *
 * Not a facade and not an animation — this is the shared plumbing that the
 * built-in sequences, the music visualiser and the battery bar all borrow.
 */
@Singleton
class AnimationRunner @Inject constructor(
    private val glyphManager: GlyphManager,
    private val renderer: GlyphRenderer,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val TAG = "AnimationRunner"

        /** Pause after forcing the strip off, so the previous frame is really gone. */
        const val CLEANUP_DELAY_MS = 100L
    }

    /**
     * Resolved once: the LED layout cannot change while the process lives, and
     * `null` means the hardware is not supported.
     */
    val profile: DeviceProfile? by lazy { DeviceProfileFactory.forConnectedDevice() }

    /** Whether the user has the Glyph feature switched on at all. */
    fun isGlyphServiceEnabled(): Boolean {
        val enabled = settingsRepository.getGlyphServiceEnabled()
        if (!enabled) {
            Log.d(TAG, "Glyph service disabled - animation call ignored")
        }
        return enabled
    }

    /**
     * Runs one sequence under the shared guards: the service must be enabled,
     * the hardware supported, and a profile available. The strip is blanked
     * before and after the sequence, and a failing step is logged rather than
     * propagated — except coroutine cancellation, which must keep unwinding.
     */
    suspend fun anim(block: suspend GlyphRenderer.(DeviceProfile) -> Unit) {
        if (!isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return

        val device = profile ?: return

        renderer.start()
        try {
            renderer.turnOff()
            delay(CLEANUP_DELAY_MS.milliseconds)
            block(renderer, device)
        } catch (e: Exception) {
            renderer.onError(e, "Animation error")
        } finally {
            renderer.stop()
            renderer.turnOff()
        }
    }
}
