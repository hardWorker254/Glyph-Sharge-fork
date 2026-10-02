package com.bleelblep.glyphsharge.glyph.script

import java.util.Locale

/**
 * Which service a user script is written for.
 *
 * A script that declares nothing is [ANY] and is offered by the four trigger
 * pickers (Pulse Lock, NFC, Low Battery, Screen Off) and the music visualiser
 * alike. A script that declares `glyph.target = "music"` is only offered by the
 * music visualiser, because it reads `glyph.audio` and is meaningless without a
 * running capture.
 *
 * The declaration lives in the Lua source, so it is only *known* after the
 * script has run. Two readers therefore exist and they are kept in step by
 * sharing this enum and nothing else:
 *
 *  * [detectIn] — a cheap scan of the source, used by the animation pickers
 *    that must classify a script before anyone runs it.
 *  * the `__newindex` handler installed by
 *    [GlyphLuaApi] — the authoritative
 *    value, reported back in [ScriptRunResult.target] once a run finishes.
 */
enum class ScriptTarget {
    /** Offered by every picker. The default for a script that says nothing. */
    ANY,

    /** Offered only by the music visualiser. */
    MUSIC;

    companion object {
        /** The name a script writes, and the only value besides the default. */
        const val MUSIC_ID = "music"

        /**
         * Matches `glyph.target = "music"`, tolerating whitespace and either
         * quote style. A match whose value is not a known target is ignored
         * rather than rejected, so a script written for a future target still
         * runs here instead of failing to load.
         */
        private val DECLARATION =
            Regex("""glyph\s*\.\s*target\s*=\s*["']([A-Za-z_][A-Za-z0-9_]*)["']""")

        /**
         * The field [DECLARATION] looks for, matched loosely.
         *
         * Every spelling the pattern accepts contains this substring — spaces
         * may be interleaved around the dot but not inside the word — so using
         * it as a pre-check cannot reject a declaration the pattern would have
         * found. That is the property that makes it safe rather than a
         * shortcut that quietly breaks a spaced-out script.
         */
        private const val FIELD_NAME = "target"

        /**
         * Lua comments, stripped before scanning.
         *
         * A commented-out declaration is the easiest way to end up with a
         * script the pickers file under the wrong service, and a `--` line is
         * by far the most common comment form here. The two forms are enough:
         * this is a classifier, not a parser, and the runtime handler is what
         * actually decides.
         *
         * `internal` rather than private because
         * [com.bleelblep.glyphsharge.glyph.script.module.ModuleSourceScan]
         * strips the same two forms before looking for `require`, and the two
         * scanners agreeing is what keeps a script from being classified one
         * way and then rejected by the other.
         */
        internal val BLOCK_COMMENT = Regex("""--\[(=*)\[.*?]\1]""", RegexOption.DOT_MATCHES_ALL)
        internal val LINE_COMMENT = Regex("""--[^\n]*""")

        /**
         * The target named in [source], or [ANY] when it names none.
         *
         * The pre-check is a plain substring scan rather than a pattern match,
         * and that is deliberate: [DECLARATION] accepts `glyph . target` with
         * spaces, so anything tighter than "contains the field name" would miss
         * a valid declaration. Anything looser would not be cheaper.
         *
         * It is there because [detectIn] runs on every keystroke — the editor
         * derives its target badge from the buffer — and the two regex replaces
         * below each allocate a copy of the entire source. A script with no
         * declaration in it, which is most of them, now costs one scan that
         * allocates nothing rather than two that each allocate the script, at
         * the rate the user types.
         */
        fun detectIn(source: String): ScriptTarget {
            if (!source.contains(FIELD_NAME)) return ANY

            val code = source.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, " ")
            val name = DECLARATION.find(code)?.groupValues?.get(1) ?: return ANY
            return fromName(name) ?: ANY
        }

        /**
         * The target a script named at runtime, or `null` when the name is not
         * one this build knows.
         *
         * `null` rather than [ANY] on purpose: a typo in `glyph.target` is a
         * mistake worth telling the author about, not a target to guess at.
         */
        fun fromName(name: String): ScriptTarget? =
            when (name.trim().lowercase(Locale.ROOT)) {
                MUSIC_ID -> MUSIC
                "any" -> ANY
                else -> null
            }
    }
}

/**
 * The picker a script is being listed in.
 *
 * The asymmetry is deliberate: [TRIGGER] hides music scripts, while [MUSIC]
 * shows everything. A script with no target means "anywhere", so it belongs in
 * both; the reverse is not true, which is why one enum value and a one-sided
 * filter is the whole rule.
 */
enum class ScriptScope {
    /** Pulse Lock, NFC, Low Battery, Screen Off. */
    TRIGGER,

    /** The music visualiser. */
    MUSIC
}

/** The target this script declares. */
val ScriptAnimation.target: ScriptTarget
    get() = ScriptTarget.detectIn(source)

/** Whether a picker in [scope] should offer this script. */
fun ScriptAnimation.isVisibleIn(scope: ScriptScope): Boolean =
    when (scope) {
        ScriptScope.TRIGGER -> target == ScriptTarget.ANY
        ScriptScope.MUSIC -> true
    }
