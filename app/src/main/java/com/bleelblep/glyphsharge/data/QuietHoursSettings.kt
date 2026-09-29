package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import com.bleelblep.glyphsharge.di.GlyphPrefs
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slice 7: the do-not-disturb window.
 *
 * Not folded into [FeatureSettings] because it is the one "feature" that is not
 * a Glyph feature at all: it suppresses the others rather than animating, and
 * [isCurrentlyInQuietHours] is a question about the wall clock that every
 * feature service asks.
 */
@Singleton
class QuietHoursSettings @Inject constructor(
    @GlyphPrefs private val prefs: SharedPreferences
) {

    fun saveQuietHoursEnabled(enabled: Boolean) = prefs.putSetting(KEY_QUIET_HOURS_ENABLED, enabled)

    fun isQuietHoursEnabled(): Boolean = prefs.getSetting(KEY_QUIET_HOURS_ENABLED, false)

    fun saveQuietHoursStartHour(hour: Int) = prefs.putSetting(KEY_QUIET_HOURS_START_HOUR, hour)

    fun getQuietHoursStartHour(): Int =
        prefs.getSetting(KEY_QUIET_HOURS_START_HOUR, DEFAULT_QUIET_HOURS_START_HOUR)

    fun saveQuietHoursStartMinute(minute: Int) = prefs.putSetting(KEY_QUIET_HOURS_START_MINUTE, minute)

    fun getQuietHoursStartMinute(): Int =
        prefs.getSetting(KEY_QUIET_HOURS_START_MINUTE, DEFAULT_QUIET_HOURS_START_MINUTE)

    fun saveQuietHoursEndHour(hour: Int) = prefs.putSetting(KEY_QUIET_HOURS_END_HOUR, hour)

    fun getQuietHoursEndHour(): Int =
        prefs.getSetting(KEY_QUIET_HOURS_END_HOUR, DEFAULT_QUIET_HOURS_END_HOUR)

    fun saveQuietHoursEndMinute(minute: Int) = prefs.putSetting(KEY_QUIET_HOURS_END_MINUTE, minute)

    fun getQuietHoursEndMinute(): Int =
        prefs.getSetting(KEY_QUIET_HOURS_END_MINUTE, DEFAULT_QUIET_HOURS_END_MINUTE)

    /**
     * Whether the current wall-clock time falls inside the window.
     *
     * A window that wraps past midnight (22:00 -> 07:00) is stored as a start
     * that is numerically *after* its end, so the two cases are genuinely
     * different and are branched on separately. In the overnight case the
     * inactive range starts at `end + 1`, because the end hour is itself still
     * part of the quiet window -- 23:30 to 07:00 is quiet, 07:00 is not.
     */
    fun isCurrentlyInQuietHours(): Boolean {
        if (!isQuietHoursEnabled()) return false

        val calendar = Calendar.getInstance()
        val currentTimeInMinutes =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startTimeInMinutes =
            getQuietHoursStartHour() * 60 + getQuietHoursStartMinute()
        val endTimeInMinutes =
            getQuietHoursEndHour() * 60 + getQuietHoursEndMinute()

        return if (startTimeInMinutes <= endTimeInMinutes) {
            currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
        } else {
            // Overnight
            currentTimeInMinutes !in (endTimeInMinutes + 1)..<startTimeInMinutes
        }
    }

    internal companion object {
        const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_HOURS_START_HOUR = "quiet_hours_start_hour"
        const val KEY_QUIET_HOURS_START_MINUTE = "quiet_hours_start_minute"
        const val KEY_QUIET_HOURS_END_HOUR = "quiet_hours_end_hour"
        const val KEY_QUIET_HOURS_END_MINUTE = "quiet_hours_end_minute"

        const val DEFAULT_QUIET_HOURS_START_HOUR = 22
        const val DEFAULT_QUIET_HOURS_START_MINUTE = 0
        const val DEFAULT_QUIET_HOURS_END_HOUR = 7
        const val DEFAULT_QUIET_HOURS_END_MINUTE = 0
    }
}
