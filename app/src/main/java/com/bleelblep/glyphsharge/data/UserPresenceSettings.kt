package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.di.GlyphPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slice 4: what Glow Gate has learned about this phone's unlock behaviour.
 *
 * A single flag with a single writer ([PulseLockService]), kept on its own
 * because it is the only setting in the app whose value is *inferred* from
 * observed behaviour rather than chosen by the user.
 */
@Singleton
class UserPresenceSettings @Inject constructor(
    @GlyphPrefs private val prefs: SharedPreferences
) {

    /**
     * Whether to wait for `ACTION_USER_PRESENT` before giving up on it.
     *
     * The honest trigger for Glow Gate is "the user got past the keyguard". On
     * some phones that broadcast simply never arrives — the keyguard never
     * presents, or the system does not send it — and then every unlock waits
     * out a grace period for an event that is never coming, which is latency
     * on the one thing the user is looking at.
     *
     * So the answer is learned rather than assumed. The first unlock waits,
     * which is the only way to find out; every unlock after that goes straight
     * to the animation. If a real unlock ever does show up, this flips back and
     * the wait resumes.
     */
    fun isUserPresentExpected(): Boolean = prefs.getSetting(KEY_USER_PRESENT_EXPECTED, true)

    fun markUserPresentSeen() = prefs.putSetting(KEY_USER_PRESENT_EXPECTED, true)

    fun markUserPresentMissing() = prefs.putSetting(KEY_USER_PRESENT_EXPECTED, false)

    internal companion object {
        const val KEY_USER_PRESENT_EXPECTED = "user_present_expected"
    }
}
