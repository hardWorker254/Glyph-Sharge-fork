package com.bleelblep.glyphsharge.glyph.audio

import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.animations.AnimationRunner
import com.bleelblep.glyphsharge.glyph.animations.runMusicVisualization
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptPlayback
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the music visualiser: the real thing, and the settings preview.
 *
 * It lives beside [AudioFrameFeed] rather than with the other animations
 * because it is the one sequence that is driven by something outside the
 * phone — a frame of analysed audio per tick — and it is the one that has to
 * keep following whichever capture the service happens to have open.
 */
@Singleton
class MusicVisualisation @Inject constructor(
    private val runner: AnimationRunner,
    private val renderer: GlyphRenderer,
    private val glyphManager: GlyphManager,
    private val settingsRepository: SettingsRepository,
    private val audioFeed: AudioFrameFeed,
    private val scriptPlayback: ScriptPlayback,
) {
    private companion object {
        /** How long the settings dialog's preview runs: 150 frames at 33 ms, ~5 s. */
        const val PREVIEW_FRAMES = 150
    }

    /**
     * Runs the visualiser the user configured, for as long as the service holds
     * the strip.
     *
     * A `custom:` id goes down the same path as every other feature's script:
     * [ScriptPlayback.playCustomAnimation]. That is what lets a Lua script be an
     * audio visualiser at all — it reads the same [AudioFrameFeed] singleton
     * through `glyph.audio`, and the service is what keeps the capture open
     * underneath it.
     *
     * The mode is read once per run rather than per frame: the service hands
     * the strip over in 1.5 s slices and starts a fresh run for each one, so a
     * change in the settings lands on the next slice instead of stuttering
     * mid-frame.
     */
    suspend fun play() {
        if (!runner.isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return
        val device = runner.profile ?: return

        val id = settingsRepository.getMusicVizAnimationId()
        if (ScriptAnimation.isCustomId(id)) {
            scriptPlayback.playCustomAnimation(id)
            return
        }

        val mode = MusicVisualizationMode.of(id) ?: MusicVisualizationMode.DEFAULT
        audioFeed.setGain(settingsRepository.getMusicVizSensitivity())

        // Through `AudioFrameFeed`, never a capture directly: which mechanism
        // is open is the service's decision, and the painter has to follow it
        // rather than pin one. Reading the wrong one hands every mode a silent
        // frame, and the strip then plays the idle animation for any setting.
        runner.anim {
            runMusicVisualization(mode, device, nextFrame = { audioFeed.latest() })
        }
    }

    /**
     * Shows what a mode looks like, on made-up audio.
     *
     * Bounded, unlike the real thing: a preview that runs until the user closes
     * the dialog is a dialog that never closes. No service check either — this
     * is an explicit user action in the settings dialog, and the studio preview
     * already plays for real.
     */
    suspend fun preview(mode: MusicVisualizationMode) {
        if (!glyphManager.isNothingPhone()) return
        val device = runner.profile ?: return

        renderer.start()
        try {
            renderer.turnOff()
            delay(AnimationRunner.CLEANUP_DELAY_MS.milliseconds)
            val track = SyntheticTrack()
            renderer.runMusicVisualization(
                mode,
                device,
                nextFrame = { track.next() },
                maxFrames = PREVIEW_FRAMES
            )
        } catch (e: Exception) {
            renderer.onError(e, "Music preview error")
        } finally {
            renderer.stop()
            renderer.turnOff()
        }
    }
}
