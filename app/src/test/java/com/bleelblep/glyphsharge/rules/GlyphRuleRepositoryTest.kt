package com.bleelblep.glyphsharge.rules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rule list's persistence, and the one field that must not be defaulted.
 *
 * A rule with no animation is dropped rather than given one: a rule that would
 * light up an arbitrary animation is worse than a rule that does not exist.
 * Everything else has a defensible default, which is what lets a partially
 * written or hand-edited list still load.
 *
 * Robolectric because the storage is a real `SharedPreferences` written by
 * `JSONObject` and read back by `JSONArray`; a fake of either would prove
 * nothing about the pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlyphRuleRepositoryTest {

    private lateinit var repository: GlyphRuleRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun rule(
        id: String = "rule1",
        trigger: String = "screen_off",
        animation: String = "SPIRAL",
    ) = GlyphRule(
        id = id,
        name = "Rule $id",
        triggerId = trigger,
        animationId = animation,
        durationMs = 4_000L,
    )

    @Before
    fun setUp() {
        context.getSharedPreferences("glyphzen_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repository = GlyphRuleRepository(
            context.getSharedPreferences("glyphzen_settings", Context.MODE_PRIVATE),
        )
    }

    @Test
    fun `a rule survives a json round trip`() {
        val decoded = GlyphRule.fromJson(rule().toJson())!!

        assertEquals("rule1", decoded.id)
        assertEquals("Rule rule1", decoded.name)
        assertEquals("screen_off", decoded.triggerId)
        assertEquals("SPIRAL", decoded.animationId)
        assertEquals(4_000L, decoded.durationMs)
        assertTrue(decoded.enabled)
        assertNull(decoded.scriptId)
    }

    @Test
    fun `a scripted rule keeps its script id`() {
        val scripted = rule().copy(scriptId = "abc123", triggerId = "")
        val decoded = GlyphRule.fromJson(scripted.toJson())!!

        assertEquals("abc123", decoded.scriptId)
        assertTrue(decoded.isScripted)
    }

    /**
     * The execution knobs a migrated feature depends on.
     *
     * These are the values that make a host-run Charging or Low Battery fire
     * exactly as its old service did — skip on a busy strip, run the raw
     * duration, hold the CPU — so a default silently flipping in the JSON
     * would be a behaviour change on device with nothing in the suite to say
     * where it came from.
     */
    @Test
    fun `the execution knobs survive a json round trip`() {
        val tuned = rule().copy(
            preempt = false,
            applyScriptCapMs = false,
            acquireWakeLock = true,
        )
        val decoded = GlyphRule.fromJson(tuned.toJson())!!

        assertFalse(decoded.preempt)
        assertFalse(decoded.applyScriptCapMs)
        assertTrue(decoded.acquireWakeLock)
    }

    @Test
    fun `the shake threshold survives a json round trip`() {
        val tuned = rule().copy(triggerId = "shake", shakeThreshold = 28.0f)
        val decoded = GlyphRule.fromJson(tuned.toJson())!!

        assertEquals(28.0f, decoded.shakeThreshold, 0.0f)
    }

    /**
     * The arc the charge bar is drawn on, for the Phone (3a)'s three strips.
     *
     * Per-rule rather than a global, so two battery rules on the same phone
     * can light different arcs — which is exactly the choice this field exists
     * to carry, and the reason a default silently flipping (or a save dropping
     * the field) would be a change nobody would see until the bar drew on the
     * wrong strip.
     */
    @Test
    fun `the battery arc survives a json round trip`() {
        val tuned = rule().copy(batteryArc = "B")
        val decoded = GlyphRule.fromJson(tuned.toJson())!!

        assertEquals("B", decoded.batteryArc)
    }

    /** A rule written before the arc existed still loads, on the long strip. */
    @Test
    fun `a rule with no arc defaults to C`() {
        val json = rule().toJson()
        json.remove("batteryArc")

        assertEquals(GlyphRule.DEFAULT_BATTERY_ARC, GlyphRule.fromJson(json)!!.batteryArc)
    }

    /**
     * An arc this phone does not have falls back rather than being kept.
     *
     * The field arrives from JSON, and a hand-edited or foreign file can name
     * anything. Keeping it would hand the animation a channel group that may
     * not exist; C is the strip the bar has always filled, so it is the honest
     * fallback and the rule stays loadable.
     */
    @Test
    fun `an unknown battery arc falls back to C`() {
        val json = rule().toJson()
        json.put("batteryArc", "Z")

        assertEquals(GlyphRule.DEFAULT_BATTERY_ARC, GlyphRule.fromJson(json)!!.batteryArc)
    }

    @Test
    fun `a rule missing the shake threshold keeps the shipped default`() {
        val json = rule().toJson()
        json.remove("shakeThreshold")

        val decoded = GlyphRule.fromJson(json)!!

        // The same number Power Peek sensitivity has always shipped with, so a
        // hand-edited rule shakes exactly as hard as the feature it replaced.
        assertEquals(GlyphRule.DEFAULT_SHAKE_THRESHOLD, decoded.shakeThreshold, 0.0f)
    }

    @Test
    fun `a rule missing the execution knobs keeps the user-facing defaults`() {
        // Written before the knobs existed — a real rule file from an older
        // build must still load, with the constructor's defaults.
        val json = rule().toJson()
        json.remove("preempt")
        json.remove("applyScriptCapMs")
        json.remove("acquireWakeLock")

        val decoded = GlyphRule.fromJson(json)!!

        assertTrue(decoded.preempt)
        assertTrue(decoded.applyScriptCapMs)
        assertFalse(decoded.acquireWakeLock)
    }

    @Test
    fun `a rule with no animation is dropped rather than defaulted`() {
        val json = JSONObject().apply {
            put("id", "x")
            put("trigger", "screen_off")
            // no "animation"
        }

        assertNull(GlyphRule.fromJson(json))
    }

    @Test
    fun `save then read returns the rule`() {
        repository.save(rule())

        val loaded = repository.getById("rule1")

        assertNotNull(loaded)
        assertEquals("SPIRAL", loaded!!.animationId)
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun `saving the same id replaces rather than duplicates`() {
        repository.save(rule())
        repository.save(rule().copy(animationId = "WAVE"))

        assertEquals(1, repository.getAll().size)
        assertEquals("WAVE", repository.getById("rule1")!!.animationId)
    }

    @Test
    fun `setEnabled flips one rule and leaves the rest`() {
        repository.save(rule("a"))
        repository.save(rule("b"))

        repository.setEnabled("a", enabled = false)

        assertFalse(repository.getById("a")!!.enabled)
        assertTrue(repository.getById("b")!!.enabled)
    }

    @Test
    fun `delete removes only the named rule`() {
        repository.save(rule("a"))
        repository.save(rule("b"))

        repository.delete("a")

        assertNull(repository.getById("a"))
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun `the preinstalled feature rules are dropped from the list and from the storage`() {
        // What the previous version left on disk: the user's own rule, and the
        // seven the app preinstalled as projections of its features. They are
        // written raw on purpose — the point is what the app finds when it
        // reads a list it did not write.
        val prefs = context.getSharedPreferences("glyphzen_settings", Context.MODE_PRIVATE)
        val array = JSONArray()
        array.put(rule("mine").toJson())
        array.put(rule("feature:pulse_lock").toJson())
        prefs.edit().putString("glyph_rules_json", array.toString()).commit()

        val listed = repository.getAll()

        assertEquals(
            "a rule the app used to preinstall is not the user's rule, and it must not " +
                "be listed as one",
            listOf("mine"),
            listed.map { it.id },
        )
        val stored = JSONArray(prefs.getString("glyph_rules_json", "[]"))
        assertEquals(
            "and it must not be left on disk either: a rule that is still stored comes " +
                "back on the next start, looking exactly like the user's own",
            listOf("mine"),
            (0 until stored.length()).map { stored.getJSONObject(it).getString("id") },
        )
    }

    @Test
    fun `a rule counts as wanting an event only while it is switched on`() {
        val listening = rule("a", trigger = "nfc")
        val switchedOff = rule("b", trigger = "nfc").copy(enabled = false)
        val otherTrigger = rule("c", trigger = "screen_off")

        assertTrue(
            "the rule that listens for it is on, so the app must act on that",
            listOf(listening).hasEnabledTrigger("nfc"),
        )
        assertFalse(
            "a rule the user switched off is not asking for anything",
            listOf(switchedOff).hasEnabledTrigger("nfc"),
        )
        assertFalse(
            "a rule listening for a different event is not asking for this one",
            listOf(otherTrigger).hasEnabledTrigger("nfc"),
        )
        assertTrue(
            "one rule among several is enough",
            listOf(otherTrigger, switchedOff, listening).hasEnabledTrigger("nfc"),
        )
        assertFalse(
            "no rule at all is not asking for anything",
            emptyList<GlyphRule>().hasEnabledTrigger("nfc"),
        )
    }

    @Test
    fun `a corrupt list degrades to empty instead of throwing`() {
        context.getSharedPreferences("glyphzen_settings", Context.MODE_PRIVATE)
            .edit().putString("glyph_rules_json", "{not an array}").commit()

        assertEquals(emptyList<GlyphRule>(), repository.getAll())
    }
}
