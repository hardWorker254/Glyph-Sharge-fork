package com.bleelblep.glyphsharge.glyph.audio

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that answers "what is playing right now".
 *
 * There are two captures and they are not interchangeable: [PlaybackAudioSource]
 * (`MediaProjection` plus `AudioRecord`) is the real path and the only one that
 * works on Android 14+, while [AudioAnalyzer] (`Visualizer` on the global
 * output mix) is a fallback for devices that still open it. Each keeps its own
 * frame stream, so a painter that reads one directly is bound to *that*
 * mechanism. Painters, scripts and the service ask for [latest] instead and get
 * whichever capture is actually running.
 *
 * [PlaybackAudioSource] wins whenever it is capturing, the fallback otherwise.
 * Not "the one with the louder frame": the two are never open at the same time
 * (the service starts the fallback only after the primary reports
 * [CaptureStatus.FAILED]), so preferring the primary keeps the decision in one
 * place instead of flickering between two streams.
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
