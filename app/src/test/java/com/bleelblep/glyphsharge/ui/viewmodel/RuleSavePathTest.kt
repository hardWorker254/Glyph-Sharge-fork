package com.bleelblep.glyphsharge.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.bleelblep.glyphsharge.data.FeatureSettings
import com.bleelblep.glyphsharge.data.FontSettings
import com.bleelblep.glyphsharge.data.GlyphServiceSettings
import com.bleelblep.glyphsharge.data.LanguageSettings
import com.bleelblep.glyphsharge.data.ScriptPermissionStore
import com.bleelblep.glyphsharge.data.ServiceScriptRepository
import com.bleelblep.glyphsharge.data.SettingsDiagnostics
import com.bleelblep.glyphsharge.data.SettingsMigrations
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.data.ThemeSettings
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.rules.GlyphRuleRepository
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.FeatureSpecs
import com.bleelblep.glyphsharge.services.RuleHostDiagnostics
import com.bleelblep.glyphsharge.tiles.TileStateBus
import com.bleelblep.glyphsharge.triggers.TriggerType
import android.os.Looper
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The constructor's Save button, driven the way the screen drives it.
 *
 * This is the path the user reaches in two taps — open the form, press Save —
 * and until now nothing in the suite touched it: the projections had a matrix
 * test ([com.bleelblep.glyphsharge.services.ProjectedRuleEditingTest]) and the
 * repository had round-trip tests, but the button between them had none. A gap
 * like that does not stay empty long.
 *
 * The dispatcher matters as much as the assertions, which is why this test
 * deliberately sets none. `viewModelScope` is `Dispatchers.Main.immediate`, so
 * on the phone the body of a save runs *inline* on the click, and the Activity
 * that called it may already be finishing by the next line. Robolectric runs
 * the test on the main looper, where `isDispatchNeeded` is false for the same
 * reason — so calling [save] here executes the same program the button
 * executes rather than a second one written for the test. Anything the body
 * posts is flushed by [drainMain] instead of being left pending for a scheduler
 * nobody is going to run.
 *
 * Robolectric for a real `SharedPreferences`, because what this path promises
 * is that the rule *survives*: the repository is the thing under test, and a
 * fake of it would be asserting the fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuleSavePathTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var settings: SettingsRepository
    private lateinit var rules: GlyphRuleRepository
    private lateinit var viewModel: RuleConstructorViewModel

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * What a save threw, if it threw.
     *
     * An exception inside `viewModelScope` goes to the thread's default handler,
     * and under Robolectric that handler logs and carries on — so a save that
     * dies halfway looks exactly like a save that quietly did less than it was
     * asked to, which is the whole difficulty here. Collected instead, so the
     * failure names the throw instead of the absence of a line.
     */
    private val crashes = mutableListOf<Throwable>()

    /** What the ViewModel said to the user, in order. */
    private val said = mutableListOf<String>()

    /**
     * Collects that channel for the length of a test.
     *
     * On the main dispatcher from the main thread, so a collection resumed by a
     * send resumes *during* the send — the same inline behaviour the button
     * relies on, which is what lets a message be observed without a scheduler.
     */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val noAnimations = Lazy<com.bleelblep.glyphsharge.glyph.GlyphAnimationManager> {
        error("the animation stack must not be needed to save a rule")
    }

    /** Runs whatever a save posted to the main looper, as the device would. */
    private fun drainMain() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Thread.setDefaultUncaughtExceptionHandler { _, error -> crashes += error }
        said.clear()

        prefs = context.getSharedPreferences("glyphzen_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val theme = ThemeSettings(prefs)
        val fonts = FontSettings(prefs)
        val glyphService = GlyphServiceSettings(prefs)
        val features = FeatureSettings(prefs)
        settings = SettingsRepository(
            theme,
            fonts,
            glyphService,
            features,
            LanguageSettings(prefs),
            SettingsMigrations(prefs, glyphService),
            SettingsDiagnostics(theme, fonts, glyphService, features),
        )
        rules = GlyphRuleRepository(prefs)

        viewModel = RuleConstructorViewModel(
            context = context,
            ruleRepository = rules,
            serviceController = FeatureServiceController(context, settings, rules),
            glyphAnimationManager = noAnimations,
            tileStateBus = TileStateBus(context),
            serviceScriptRepository = ServiceScriptRepository(context),
            scriptPermissionStore = ScriptPermissionStore(context),
            ruleHostDiagnostics = RuleHostDiagnostics(),
        )

        scope.launch { viewModel.messages.collect { said += it } }
    }

    @After
    fun tearDown() {
        scope.cancel()
        drainMain()
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    private fun save(
        existingId: String? = null,
        name: String = "My rule",
        triggerId: String = TriggerType.SCREEN_OFF.id,
        animationId: String = "WAVE",
        durationMs: Long = 3_000L,
    ) = viewModel.saveRule(
        existingId = existingId,
        name = name,
        triggerId = triggerId,
        animationId = animationId,
        durationMs = durationMs,
        lowBatteryThreshold = 20,
        shakeThreshold = GlyphServiceSettings.SHAKE_MEDIUM,
    ).also {
        drainMain()
        assertTrue(
            "a save threw instead of finishing: ${crashes.joinToString("; ")}",
            crashes.isEmpty(),
        )
    }

    @Test
    fun `a save says so, naming the rule`() {
        // The rule is written last, after the seven projected ones, so it lands
        // a screen and a half below where the user is looking. A save that
        // works and a save that quietly did not are indistinguishable from the
        // form otherwise — so the one thing the save knows for certain is said
        // out loud.
        save(name = "Wake me")

        assertEquals(
            listOf(context.getString(com.bleelblep.glyphsharge.R.string.rules_msg_saved, "Wake me")),
            said,
        )
    }

    @Test
    fun `a new simple rule reaches the repository`() {
        save()

        val stored = rules.getAll()
        assertEquals("nothing was written", 1, stored.size)
        val rule = stored.single()
        assertEquals("My rule", rule.name)
        assertEquals(TriggerType.SCREEN_OFF.id, rule.triggerId)
        assertEquals("WAVE", rule.animationId)
        assertEquals(3_000L, rule.durationMs)
        assertTrue("a new rule has to be on — nobody asked for an off rule", rule.enabled)
        assertNull("a simple rule carries no script", rule.scriptId)
    }

    @Test
    fun `a new rule lands in the list the form's own screen reads`() {
        save(name = "First")
        save(name = "Second")

        // The state flow rather than the repository: what the user sees is this,
        // and a save that writes but does not publish looks exactly like a save
        // that does nothing.
        val names = viewModel.rules.value.map { it.name }
        assertEquals(
            "the constructor's own list did not learn about them; on the home " +
                "door the Activity is finished instead, so this is the only thing " +
                "that would show them",
            listOf("First", "Second"),
            names,
        )
    }

    @Test
    fun `an edit keeps the rule's id and replaces the fields the form owns`() {
        save(name = "Before", animationId = "WAVE")
        val id = rules.getAll().single().id

        save(existingId = id, name = "After", animationId = "HEARTBEAT", durationMs = 5_000L)

        val stored = rules.getAll()
        assertEquals("an edit created a second rule", 1, stored.size)
        val rule = stored.single()
        assertEquals("the id changed, so the edit is a new rule", id, rule.id)
        assertEquals("After", rule.name)
        assertEquals("HEARTBEAT", rule.animationId)
        assertEquals(5_000L, rule.durationMs)
    }

    @Test
    fun `a deleted rule is gone from the repository and from the list`() {
        save()
        val id = rules.getAll().single().id

        viewModel.deleteRule(id)

        assertTrue("the rule survived its delete", rules.getAll().isEmpty())
        assertTrue("the list still shows it", viewModel.rules.value.isEmpty())
    }
}
