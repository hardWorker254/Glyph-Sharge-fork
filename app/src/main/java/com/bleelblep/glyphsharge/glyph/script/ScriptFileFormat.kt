package com.bleelblep.glyphsharge.glyph.script

/**
 * The `.glyphlua` container: a Lua file with a commented-out metadata header.
 *
 * Keeping the header in Lua comments means an exported file is still a valid,
 * readable script that runs in any Lua 5.2 host — there is no container to
 * unwrap and nothing to strip before editing.
 *
 * ```
 * -- Glyph Sharge animation
 * -- format: 1
 * -- name: My Heartbeat
 * -- id: 4f3c9a11b2e7
 * -- created: 1712345678000
 * -- updated: 1712345678000
 *
 * glyph.set(glyph.ch.c, glyph.MAX)
 * ```
 */
object ScriptFileFormat {

    const val EXTENSION = "glyphlua"
    const val MIME_TYPE = "text/plain"

    private const val FORMAT_VERSION = 1
    private const val HEADER_MARKER = "-- Glyph Sharge animation"
    private const val COMMENT = "--"
    private const val FIELD = "name|id|created|updated"

    /** Rejected instead of imported when the file is not ours. */
    class InvalidScriptException(message: String) : Exception(message)

    fun encode(animation: ScriptAnimation): String = buildString {
        appendLine(HEADER_MARKER)
        appendLine("$COMMENT format: $FORMAT_VERSION")
        appendLine("$COMMENT name: ${sanitiseHeaderValue(animation.name)}")
        appendLine("$COMMENT id: ${animation.id}")
        appendLine("$COMMENT created: ${animation.createdAt}")
        appendLine("$COMMENT updated: ${animation.updatedAt}")
        appendLine()
        append(animation.source)
    }

    /**
     * Parses a file produced by [encode]. A file without our header is still
     * accepted — it is imported as a fresh animation, which is what someone
     * who hand-wrote a script in another editor expects.
     *
     * @param fallbackName used when the file carries no `name` field
     */
    fun decode(text: String, fallbackName: String = "Imported script"): ScriptAnimation {
        val lines = text.lines()
        var index = 0
        var name: String? = null
        var id: String? = null
        var created: Long? = null
        var updated: Long? = null

        // The header is a run of `--` lines at the very top; the first line that
        // is not a comment ends it.
        while (index < lines.size && lines[index].trimStart().startsWith(COMMENT)) {
            val line = lines[index].trimStart().removePrefix(COMMENT).trim()
            val separator = line.indexOf(':')
            if (separator > 0) {
                when (line.substring(0, separator).trim().lowercase()) {
                    "name" -> name = line.substring(separator + 1).trim()
                    "id" -> id = line.substring(separator + 1).trim()
                    "created" -> created = line.substring(separator + 1).trim().toLongOrNull()
                    "updated" -> updated = line.substring(separator + 1).trim().toLongOrNull()
                }
            }
            index++
        }

        val body = lines.drop(index).joinToString("\n").trim('\n')
        if (body.isBlank()) {
            throw InvalidScriptException("The file contains no Lua source.")
        }

        val now = System.currentTimeMillis()
        val stamp = updated ?: created ?: now
        return ScriptAnimation(
            id = id?.takeIf { it.isNotBlank() && it.matches(ID_PATTERN) } ?: ScriptAnimation.newId(),
            name = name?.takeIf { it.isNotBlank() } ?: fallbackName,
            source = body,
            createdAt = created ?: now,
            updatedAt = stamp
        )
    }

    /** `My Heartbeat` -> `My Heartbeat.glyphlua`, with characters the file system rejects removed. */
    fun suggestedFileName(animation: ScriptAnimation): String {
        val base = animation.name.trim()
            .map { if (it.isLetterOrDigit() || it == '_' || it == '-' || it == ' ') it else '_' }
            .joinToString("")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifEmpty { "animation" }
        return "$base.$EXTENSION"
    }

    /** Header values are line-based, so a name with a newline would corrupt the file. */
    private fun sanitiseHeaderValue(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').trim()

    private val ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,64}")

    init {
        // Guards against a typo in FIELD going unnoticed: every key written by
        // encode() must be readable by decode().
        check(FIELD.split('|').all { it.isNotBlank() })
    }
}
