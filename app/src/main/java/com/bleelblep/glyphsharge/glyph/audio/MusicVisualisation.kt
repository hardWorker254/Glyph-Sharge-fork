package com.bleelblep.glyphsharge.glyph.audio

import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.animations.AnimationRunner
import com.bleelblep.glyphsharge.glyph.animations.runMusicVisualization
import com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptPlayback
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Provider
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
    /**
     * Lazy for the same reason as in [ScriptPlayback]: the coordinator holds the
     * animation manager, which holds this class, so a direct dependency closes a
     * construction cycle. Resolved only when a preview runs.
     */
    private val coordinator: Provider<GlyphFeatureCoordinator>,
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
     *
     * [sensitivity] is applied to the synthetic frames the same way the
     * capture applies it to real ones, so the preview shows what the
     * slider does instead of one fixed picture for every setting.
     */
    suspend fun preview(mode: MusicVisualizationMode, sensitivity: Float = 1f) {
        if (!glyphManager.isNothingPhone()) return
        val device = runner.profile ?: return

        // Through the coordinator, for the same reason
        // [ScriptPlayback.previewScript] does: a preview that skips the mutex
        // interleaves with a live service's animation and takes it down with it
        // on the way out. `preempt = false` — a preview waits its turn rather
        // than interrupting whatever the strip is already playing.
        coordinator.get().withStrip(
            owner = GlyphFeature.PREVIEW,
            preempt = false,
        ) {
            val lease = renderer.start("music-preview")
            try {
                renderer.turnOff()
                delay(AnimationRunner.CLEANUP_DELAY_MS.milliseconds)
                val track = SyntheticTrack()
                val gain = sensitivity.coerceIn(AudioAnalyzer.MIN_GAIN, AudioAnalyzer.MAX_GAIN)
                renderer.runMusicVisualization(
                    mode,
                    device,
                    nextFrame = { track.next().withGain(gain) },
                    maxFrames = PREVIEW_FRAMES,
                )
            } catch (e: Exception) {
                renderer.onError(e, "Music preview error")
            } finally {
                renderer.stop(lease)
                renderer.turnOff()
            }
        }
    }
}

/**
 * Applies the sensitivity to a made-up frame the way the capture does
 * to a real one: bands scaled, the thirds recomputed from them, the
 * overall level untouched. `rms` is measured off the raw PCM before
 * any gain in the real pipeline, and a preview that scaled it would
 * make the loudness-driven modes look livelier than they really get.
 */
private fun AudioFrame.withGain(gain: Float): AudioFrame {
    if (gain == 1f) return this
    val scaled = FloatArray(bands.size) { i -> (bands[i] * gain).coerceIn(0f, 1f) }
    return AudioFrame(
        seq = seq,
        bands = scaled,
        bass = AudioAnalysis.bassOf(scaled),
        mid = AudioAnalysis.midOf(scaled),
        treble = AudioAnalysis.trebleOf(scaled),
        rms = rms,
        beat = beat,
    )
}
