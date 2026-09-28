package com.bleelblep.glyphsharge.glyph.audio

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that answers "what is playing right now".
 *
 * ### Why this exists
 *
 * There are two captures, and they are not interchangeable:
 *
 *  * [PlaybackAudioSource] — `MediaProjection` plus `AudioRecord`. The real
 *    path, and the only one that works on Android 14+;
 *  * [AudioAnalyzer] — `Visualizer` on the global output mix. A fallback for
 *    devices that still open it, and the one the service starts only after
 *    playback capture has failed outright.
 *
 * Both keep their own frame stream, so anything that reads one of them
 * directly is bound to *that* mechanism. The painters used to read
 * [AudioAnalyzer] while the service captured through [PlaybackAudioSource] —
 * so on every phone where the primary path works, all six modes were handed
 * [AudioFrame.SILENT] forever and drew their idle animation instead. The strip
 * looked alive and reacted to nothing, and switching mode in the settings
 * changed nothing either, because the mode was never the thing being wrong.
 *
 * This class is the indirection that removes the choice: painters, scripts and
 * the service ask for [latest] and get whichever capture is actually running.
 *
 * ### Which one wins
 *
 * [PlaybackAudioSource] when it is capturing, the legacy one otherwise. Not
 * "the one with the louder frame": the two are never open at the same time
 * (the service starts the fallback only after the primary reports
 * [CaptureStatus.FAILED]), and preferring the primary keeps the decision in
 * one place instead of flickering between two streams.
 */
@Singleton
class AudioFrameFeed @Inject constructor(
    private val playback: PlaybackAudioSource,
    private val legacy: AudioAnalyzer,
) {

    /** `true` while either capture is open and publishing frames. */
    val isActive: Boolean
        get() = playback.isCapturing || legacy.isCapturing

    /**
     * The newest analysed frame, or [AudioFrame.SILENT] when nothing is open.
     *
     * Safe to call at 30 fps: it is a volatile read of a `StateFlow` value on
     * both sides, with no allocation and no collection.
     */
    fun latest(): AudioFrame =
        if (playback.isCapturing) playback.latest() else legacy.latest()

    /**
     * Applies the user's sensitivity to both captures.
     *
     * Both, not the active one: which of the two is open is decided at runtime
     * and can change under a running visualiser, and a sensitivity that only
     * reached the capture that happened to be open at the time it was set
     * would be quietly ignored on the next fallback.
     */
    fun setGain(value: Float) {
        playback.setGain(value)
        legacy.setGain(value)
    }
}
