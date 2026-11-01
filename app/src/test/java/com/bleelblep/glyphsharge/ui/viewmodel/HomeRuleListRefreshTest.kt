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
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphDeviceReport
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.audio.PlaybackAudioSource
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.rules.GlyphRuleRepository
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.FeatureSpecs
import com.bleelblep.glyphsharge.services.GlyphServiceSwitch
import com.bleelblep.glyphsharge.tiles.TileStateBus
import com.bleelblep.glyphsharge.triggers.TriggerType
import dagger.Lazy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home screen's rules, after another Activity wrote one.
 *
 * The constructor is its own Activity, so "save a rule" and "show the rule" are
 * two objects and two lifecycles: the first writes through the repository
 * singleton, the second re-reads it in `MainActivity.onResume`. Nothing forces
 * those two halves to agree, and when they disagree the symptom is a save that
 * silently did nothing — the worst kind of bug in this app, because the user
 * pressed a button and the button worked.
 *
 * So the half that is usually taken on trust is asserted here: a rule written
 * by someone else appears in this screen's own list the next time it refreshes.
 * The refresh called here is exactly the one `onResume` calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRuleListRefreshTest {

    private lateinit var rules: GlyphRuleRepository
    private lateinit var controller: FeatureServiceController
    private lateinit var home: HomeViewModel

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val prefs: SharedPreferences =
            context.getSharedPreferences("glyphzen_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val theme = ThemeSettings(prefs)
        val fonts = FontSettings(prefs)
        val glyphService = GlyphServiceSettings(prefs)
        val features = FeatureSettings(prefs)
        val settings = SettingsRepository(
            theme, fonts, glyphService, features, LanguageSettings(prefs),
            SettingsMigrations(prefs, glyphService),
            SettingsDiagnostics(theme, fonts, glyphService, features),
        )
        rules = GlyphRuleRepository(prefs)
        controller = FeatureServiceController(context, settings, rules)
        val report = GlyphDeviceReport()
        val glyphManager = GlyphManager(context, report)
        val tileStateBus = TileStateBus(context)

        home = HomeViewModel(
            context = context,
            settingsRepository = settings,
            glyphManager = glyphManager,
            glyphAnimationManager = Lazy<GlyphAnimationManager> { error("not needed to list rules") },
            serviceController = controller,
            ruleRepository = rules,
            glyphServiceSwitch = GlyphServiceSwitch(glyphManager, settings, controller, tileStateBus),
            tileStateBus = tileStateBus,
            playbackAudioSource = PlaybackAudioSource(context),
            serviceScriptRepository = ServiceScriptRepository(context),
            scriptPermissionStore = ScriptPermissionStore(context),
            deviceReport = report,
        )
    }

    private fun write(name: String): GlyphRule = GlyphRule(
        id = GlyphRule.newId(),
        name = name,
        triggerId = TriggerType.SCREEN_OFF.id,
        animationId = "WAVE",
        durationMs = 3_000L,
    ).also { rules.save(it) }

    @Test
    fun `a rule written by the other Activity is listed on the next refresh`() {
        // What `MainActivity.onResume` runs when the constructor closes.
        home.refreshFeatures()
        val before = home.uiState.value.rules.map { it.name }

        write("Wake me")
        home.refreshFeatures()

        val after = home.uiState.value.rules.map { it.name }
        assertEquals(
            "the rule the user just saved is not on the home screen; this is the " +
                "half of the round trip nothing else can prove. before=$before after=$after",
            before + "Wake me",
            after,
        )
    }

    @Test
    fun `the list holds the user's rules and nothing else`() {
        write("Mine")
        home.refreshFeatures()

        val names = home.uiState.value.rules.map { it.name }

        // Every rule in the app is one the user built: the app ships no
        // preinstalled rules, so the list is exactly what they made. The
        // visualiser has a card and no rule, and must not be in it.
        assertEquals(
            "listed=$names stored=${rules.getAll().map { it.id }}",
            listOf("Mine"),
            names,
        )
        assertEquals("no row may appear twice", names.size, names.toSet().size)
        assertTrue(
            "the visualiser has a card and no rule, so it must not be listed",
            names.none { it == context.getString(specTitle) },
        )
    }

    private val specTitle: Int
        get() = FeatureSpecs.of(GlyphFeature.MUSIC_VISUALIZER).titleRes
}
