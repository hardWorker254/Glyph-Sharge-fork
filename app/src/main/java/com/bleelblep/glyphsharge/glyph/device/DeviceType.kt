package com.bleelblep.glyphsharge.glyph.device

import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph

/**
 * Every Nothing Phone model the app supports, together with the single
 * `Common.is*` check that identifies it.
 *
 * This enum is the **only** place that asks the SDK "which phone is this?",
 * so adding a model means adding one enum entry and nothing else.
 */
enum class DeviceType(
    /** The id handed to `GlyphManager.register(...)` for this model. */
    val sdkId: String,
    private val matches: () -> Boolean,
) {
    PHONE1(Glyph.DEVICE_20111, { Common.is20111() }),
    PHONE2(Glyph.DEVICE_22111, { Common.is22111() }),
    PHONE2A(Glyph.DEVICE_23111, { Common.is23111() || Common.is23113() }),
    PHONE3A(Glyph.DEVICE_24111, { Common.is24111() });

    /**
     * Phone (2a) ships under two SDK ids, so the registration id has to be
     * resolved per variant instead of being fixed at construction.
     */
    val registrationId: String
        get() = if ((this == PHONE2A) && Common.is23113()) Glyph.DEVICE_23113 else sdkId

    companion object {
        /** The connected model, or `null` on hardware without a Glyph strip. */
        fun detect(): DeviceType? = entries.firstOrNull { it.matches() }
    }
}
