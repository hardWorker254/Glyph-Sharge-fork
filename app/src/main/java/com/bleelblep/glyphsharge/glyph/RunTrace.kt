package com.bleelblep.glyphsharge.glyph

import android.content.Context
import androidx.core.content.edit
import com.bleelblep.glyphsharge.utils.LoggingManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A durable trace of what the glyph features actually did.
 *
 * A feature can fail in a dozen places between "the broadcast arrived" and
 * "the strip lit up", and the only thing that separates them is one line in a
 * process that may never run again. The logcat buffer is no help either: a busy
 * system fills it in seconds, and by the time anyone thinks to look, the
 * evidence is gone.
 *
 * So every step is written to a small preference file — it survives process
 * death and can be read long afterwards — and mirrored into the app's own log
 * file, which the settings screen can export.
 */
@Singleton
class RunTrace @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(feature: String, step: String, detail: String = "") {
        val line = buildString {
            append(System.currentTimeMillis())
            append(' ')
            append(feature)
            append(' ')
            append(step)
            if (detail.isNotEmpty()) {
                append(' ')
                append(detail)
            }
        }

        val lines = (prefs.getString(KEY, "").orEmpty().lineSequence().toList() + line)
            .takeLast(MAX_LINES)
        prefs.edit { putString(KEY, lines.joinToString("\n")) }

        LoggingManager.log(TAG, "$feature $step $detail")
    }

    fun dump(): String = prefs.getString(KEY, "").orEmpty()

    private companion object {
        const val TAG = "GlyphRun"
        const val PREFS = "glyphsharge_run_trace"
        const val KEY = "lines"
        const val MAX_LINES = 40
    }
}
