package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptTarget

/**
 * Finds `require("…")` names in a script that [ModuleRegistry] cannot resolve.
 *
 * The editor's Check button compiles the source, and compiling proves nothing
 * about `require`: `require("glyph.nett")` is a perfectly good expression, so
 * the typo survives a clean parse and then blows up on the real glyph, mid
 * animation, with a phone in someone's hand. Scanning the source is the only
 * place that mistake can still be caught cheaply.
 *
 * Deliberately a regex and not a parse of the compiled chunk. Comments are the
 * trap here — a commented-out `require` is text a naive scan reports as a
 * missing module, and a Check button that cries wolf about a line the author
 * already disabled gets switched off and never used again.
 */
internal object ModuleSourceScan {

    /**
     * A `require` of a string literal, either quote style, any spacing.
     *
     * The name is restricted to word characters and dots, which is exactly what
     * a module name may contain. A `require` of anything else — a variable, a
     * concatenation — is skipped rather than guessed at, because the checker
     * cannot know what it would resolve to and an invented answer is worse than
     * silence.
     */
    private val REQUIRE = Regex("""require\s*\(\s*["']([\w.]+)["']""")

    /**
     * Module names in [source] that are not registered, in the order written.
     *
     * Duplicates are dropped: a module required inside a loop is one mistake,
     * not one per iteration, and a list that repeats the same name tells the
     * author nothing the first copy did not.
     */
    fun missingModules(source: String): List<String> {
        // The two comment forms `ScriptTarget.detectIn` already strips, taken
        // from there rather than retyped: a script that the pickers classify
        // correctly and Check then rejects would be a far more confusing bug
        // than a duplicated pair of regexes.
        val code = source
            .replace(ScriptTarget.BLOCK_COMMENT, " ")
            .replace(ScriptTarget.LINE_COMMENT, " ")

        return REQUIRE.findAll(code)
            .map { it.groupValues[1] }
            .filterNot { ModuleRegistry.has(it) }
            .distinct()
            .toList()
    }
}
