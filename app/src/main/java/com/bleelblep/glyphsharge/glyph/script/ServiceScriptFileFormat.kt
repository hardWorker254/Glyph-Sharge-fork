package com.bleelblep.glyphsharge.glyph.script

/**
 * The `.servicelua` container: the same commented-header idea as
 * [ScriptFileFormat], minus the `devices` claim.
 *
 * ```
 * -- Glyph Sharge service rule
 * -- format: 1
 * -- name: Warn at 15%
 * -- id: 9c1f0a77b3d2
 *
 * glyph.on("low_battery", function()
 *     glyph.playAnimation()
 * end)
 * ```
 *
 * A service rule makes no claim about models: its handlers name triggers, and
 * the animation it fires is resolved by the app against the device the app is
 * already running on — so there is nothing for a `devices:` line to say that
 * could be wrong.
 *
 * Headerless files are accepted on import, exactly as for animations: someone
 * who hand-wrote a script in another editor expects it to import as theirs —
 * including a file that merely *opens* with comments. The top comment run
 * counts as the header only when it declares a field; prose stays in the body.
 */
object ServiceScriptFileFormat {

    const val EXTENSION = "servicelua"
    const val MIME_TYPE = "text/plain"

    private const val FORMAT_VERSION = 1
    private const val HEADER_MARKER = "-- Glyph Sharge service rule"
    private const val COMMENT = "--"

    fun encode(script: ServiceScript): String = buildString {
        appendLine(HEADER_MARKER)
        appendLine("$COMMENT format: $FORMAT_VERSION")
        appendLine("$COMMENT name: ${sanitiseHeaderValue(script.name)}")
        appendLine("$COMMENT id: ${script.id}")
        appendLine()
        append(script.source)
    }

    /**
     * Parses a file produced by [encode], or any `.servicelua` text.
     *
     * @param fallbackName used when the file carries no `name` field
     * @throws ScriptFileFormat.InvalidScriptException when there is no Lua body
     */
    fun decode(text: String, fallbackName: String = "Imported rule"): ServiceScript {
        val lines = text.lines()
        var index = 0
        var name: String? = null
        var id: String? = null

        // A run of `--` lines at the very top, but a *header* only when it
        // declares something: a name, an id, a format version. A script that
        // merely opens with comments — a hand-written note, or the
        // constructor's starter, which is mostly comments — keeps them: a
        // block eaten on every save round-trip would leave the buffer and the
        // file disagreeing, which is worse than any header ambiguity.
        var header = false
        while ((index < lines.size) && lines[index].trimStart().startsWith(COMMENT)) {
            val line = lines[index].trimStart().removePrefix(COMMENT).trim()
            val separator = line.indexOf(':')
            if (separator > 0) {
                when (line.substring(0, separator).trim().lowercase()) {
                    "name" -> {
                        name = line.substring(separator + 1).trim()
                        header = true
                    }
                    "id" -> {
                        id = line.substring(separator + 1).trim()
                        header = true
                    }
                    "format" -> header = true
                }
            }
            index++
        }
        // Nothing was declared: the run is content, not a header, so the
        // body starts where the file does.
        if (!header) index = 0

        val body = lines.subList(index, lines.size).joinToString("\n").trim('\n')
        if (body.isBlank()) {
            throw ScriptFileFormat.InvalidScriptException("The file contains no Lua source.")
        }

        val now = System.currentTimeMillis()
        return ServiceScript(
            id = id?.takeIf { it.isNotBlank() && it.matches(ID_PATTERN) } ?: ServiceScript.newId(),
            name = name?.takeIf { it.isNotBlank() } ?: fallbackName,
            source = body,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** `Warn at 15%` -> `Warn_at_15_.servicelua`, with file-system rejects removed. */
    fun suggestedFileName(script: ServiceScript): String {
        val base = script.name.trim()
            .map { if ((it.isLetterOrDigit()) || (it == '_') || (it == '-') || (it == ' ')) it else '_' }
            .joinToString("")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifEmpty { "rule" }
        return "$base.$EXTENSION"
    }

    /** Header values are line-based, so a name with a newline would corrupt the file. */
    private fun sanitiseHeaderValue(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').trim()

    private val ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,64}")
}
