package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction

/**
 * The closed set of things `require` can resolve, and the `require` itself.
 *
 * Lua's `require` is the one door the sandbox shut and this opens it again —
 * so only just, and only onto this list. There is no search path behind it, no
 * `package.path`, no file system: a name either names one of the factories
 * below or the script gets an error saying which ones it could have used.
 * `io`, `os`, `luajava` and the rest stay `nil`, which is the point, and is
 * what `ModuleRegistryTest` exists to prove.
 *
 * ### Factories, not tables
 *
 * The map holds `(ScriptSession) -> LuaTable` and never a built table. Several
 * foreground services can be running scripts at the same time — the charging
 * animation and the music visualiser routinely are — and a module built once
 * and shared would let those two runs read and write each other's state. A
 * fresh table per run is the whole of the isolation; there is nothing to freeze
 * and no shared key to reset.
 */
internal object ModuleRegistry {

    /**
     * The registered modules, by the name a script writes in `require`.
     *
     * A `Map` literal so the order in the source is the order a reader meets
     * them; [names] sorts them anyway, because the error message should list
     * them the same way every time rather than in whatever order the entries
     * happen to hash to.
     */
    private val factories: Map<String, (ScriptSession) -> LuaTable> = mapOf(
        GlyphBatteryModule.NAME to GlyphBatteryModule::build,
        GlyphLogModule.NAME to GlyphLogModule::build,
        GlyphNetModule.NAME to GlyphNetModule::build,
        GlyphSensorModule.NAME to GlyphSensorModule::build,
        GlyphTimeModule.NAME to GlyphTimeModule::build,
        GlyphUtilModule.NAME to GlyphUtilModule::build,
    )

    /** Every module name a script may require, sorted. */
    fun names(): List<String> = factories.keys.sorted()

    /** Whether [name] is one this build ships. */
    fun has(name: String): Boolean = factories.containsKey(name)

    /**
     * A new table for [name], bound to [session].
     *
     * `null` rather than an error, so [requireFor] and the studio's Check
     * button can each phrase the failure in their own words.
     */
    fun build(name: String, session: ScriptSession): LuaTable? = factories[name]?.invoke(session)

    /**
     * What a script sees when it asks for something that is not registered.
     *
     * Lists what *is* available, because the overwhelmingly likely cause is a
     * typo — `glyph.nett` — and the fix is to read the right name off the
     * error rather than to work out which half of `require` is broken.
     */
    fun unknownModuleMessage(name: String): String =
        "require: no module '$name' (available: ${names().joinToString(", ")})"

    /**
     * The `require` for one script run.
     *
     * A [VarArgFunction] written out longhand rather than going through
     * [com.bleelblep.glyphsharge.glyph.script.GlyphLuaApi.luaFunction]: that
     * wrapper catches `Exception` to turn a Kotlin failure into a Lua error,
     * and `LuaError` *is* a `RuntimeException`, so the error raised here would
     * be caught by the very wrapper that raised it and the author would read
     * the message twice.
     *
     * The cache is a local rather than a field: it must live exactly as long as
     * the run does. Caching gives a script the identity it expects —
     * `require("glyph.time") == require("glyph.time")` — and a field would
     * have handed that same identity to the *next* script to run, which is the
     * shared-state problem [build] exists to avoid.
     */
    fun requireFor(session: ScriptSession): VarArgFunction {
        val built = HashMap<String, LuaTable>()
        return object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val name = args.arg(1).tojstring()
                built[name]?.let { return it }
                val module = build(name, session)
                    ?: return error(unknownModuleMessage(name))
                built[name] = module
                return module
            }

            override fun toString(): String = "require"
        }
    }
}
