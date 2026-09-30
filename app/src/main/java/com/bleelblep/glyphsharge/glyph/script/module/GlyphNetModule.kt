package com.bleelblep.glyphsharge.glyph.script.module

import com.bleelblep.glyphsharge.glyph.script.ScriptSession
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * `require("glyph.net")` — is the phone on a network, and what kind.
 *
 * ```lua
 * local net = require("glyph.net")
 * if not net.connected then
 *   glyph.log("offline, going quiet")
 *   glyph.exit()
 * end
 * glyph.setAll(net.vpn and 200 or net.metered and 100 or 255)
 * ```
 *
 * Every field is *live*, and that is the only interesting thing about this
 * module. A snapshot would be worse than useless here, because the whole reason
 * a script asks is a reaction: a polling animation that should calm down when
 * the phone leaves Wi-Fi, a VPN trigger that should light up the moment the
 * tunnel comes up. A value frozen at module-build time answers neither, and
 * would look like it worked right up until the network changed.
 *
 * The four fields are read together through [ScriptSession.networkSnapshot]
 * rather than one at a time, so a script branching on `net.vpn` and then
 * `net.metered` cannot be handed two halves of two different instants. See
 * `NetworkSnapshot` for why that window is real.
 *
 * Nothing here is a permission-gated probe and nothing is a request: reading the
 * network *state* needs no runtime permission and this module never opens a
 * socket, so it cannot become a way for a script to talk to the network.
 */
internal object GlyphNetModule {

    /** The name a script writes in `require`. */
    const val NAME = "glyph.net"

    fun build(session: ScriptSession): LuaTable =
        ModuleBuilder(NAME)
            .apply {
                live("connected") { LuaValue.valueOf(session.networkSnapshot().connected) }
                live("wifi") { LuaValue.valueOf(session.networkSnapshot().wifi) }
                live("metered") { LuaValue.valueOf(session.networkSnapshot().metered) }
                live("vpn") { LuaValue.valueOf(session.networkSnapshot().vpn) }
            }
            .build()
}
