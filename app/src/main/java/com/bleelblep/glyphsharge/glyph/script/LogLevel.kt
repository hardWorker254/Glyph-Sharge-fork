package com.bleelblep.glyphsharge.glyph.script

/**
 * How serious a line the script wrote is.
 *
 * `print` and `glyph.log` have always been the same undifferentiated line.
 * That is fine for a script that is working, and useless for one that is
 * failing: a warning and a fatal note look identical in the studio console,
 * so a script debugging itself cannot tell which of its own messages mattered.
 *
 * The level travels with the text rather than being baked into the string, so
 * the console can group, filter or colour by it later without having to parse
 * a prefix back out of a sentence.
 *
 * Public because it leaves the engine: [ScriptLogLine] rides on
 * [ScriptRunResult] into the studio, which has to render the three levels
 * differently and cannot reach into the script package's internals.
 */
enum class LogLevel {
    /** Ordinary progress. The default for `print` and `glyph.log`. */
    INFO,

    /** Something the author should look at, but the run continues. */
    WARN,

    /** The script has given up; [com.bleelblep.glyphsharge.glyph.script.module.GlyphLogModule.error] logs and then raises. */
    ERROR
}

/**
 * One line the script wrote, kept with the severity it was written at.
 *
 * A data class rather than a `Pair`, because the console is the only reader
 * and it has to say `line.level` rather than `line.second`.
 */
data class ScriptLogLine(
    val text: String,
    val level: LogLevel
)
