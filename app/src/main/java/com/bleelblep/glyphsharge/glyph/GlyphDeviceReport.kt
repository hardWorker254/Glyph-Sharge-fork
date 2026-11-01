package com.bleelblep.glyphsharge.glyph

import android.os.Build
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app concluded about the phone it is running on.
 *
 * One fact with two readers — the log, which has always had it, and the home
 * screen, which has to be able to *say* it. The case that needs the second
 * reader: the app recognises the phone (`A059P`, a Phone (3a) Pro), the system
 * Glyph service refuses every id offered, and the result is a phone whose strip
 * never lights for any feature. With nothing but a log line, that is
 * indistinguishable from a broken feature — and on this handset there is no
 * logcat to pull.
 *
 * Published from [GlyphManager] where the verdict is reached, so the reason
 * shown is the one that happened rather than a guess assembled at the far end.
 */
@Singleton
class GlyphDeviceReport @Inject constructor() {

    data class Verdict(
        /** `Build.MODEL`, verbatim — the only thing certain about the hardware. */
        val model: String,
        /** The app's reading of [model], or `null` if it claims no phone at all. */
        val type: DeviceType?,
        /** The id the service accepted, or `null` when it accepted none. */
        val acceptedId: String?,
        /** Every id that was offered, in the order they were tried. */
        val offeredIds: List<String>,
    ) {
        /** Whether the strip can be driven at all. */
        val registered: Boolean get() = acceptedId != null
    }

    private val state = MutableStateFlow<Verdict?>(null)

    val verdict: StateFlow<Verdict?> = state.asStateFlow()

    /**
     * Publishes what registration concluded.
     *
     * A refusal publishes [Verdict] with no [acceptedId] rather than `null`,
     * so the screen can tell "never tried" from "tried and refused" — the first
     * is a phone the app does not know, the second is a support question.
     */
    fun publish(model: String, type: DeviceType?, acceptedId: String?, offeredIds: List<String>) {
        state.value = Verdict(model, type, acceptedId, offeredIds)
    }

    /** The model string as the OS reports it, for a verdict nothing else can fill in. */
    fun currentModel(): String = Build.MODEL ?: "unknown"
}