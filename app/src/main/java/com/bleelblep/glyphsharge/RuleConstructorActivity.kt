package com.bleelblep.glyphsharge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.script.ServiceScriptFileFormat
import com.bleelblep.glyphsharge.ui.components.rememberServiceScriptImport
import com.bleelblep.glyphsharge.ui.screens.RuleConstructorScreen
import com.bleelblep.glyphsharge.ui.screens.RuleListScreen
import com.bleelblep.glyphsharge.ui.screens.applyLocale
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.ui.theme.GlyphZenTheme
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.ThemeState
import com.bleelblep.glyphsharge.ui.viewmodel.RuleConstructorViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The rule constructor, arranged the way the studio is: a list first, the
 * form behind it.
 *
 * A separate Activity for the reason the studio is one — it is a destination
 * with three front doors (the Settings card opens the list, the home list's
 * "＋" goes straight to the form, a rule card's tap edits one) and its own
 * screens, and a form that lives over the list it edits can only ever be as
 * big as that list is willing to give it. It also keeps the ownership rule
 * that `HomeViewModelOwnershipTest` guards intact: this screen has its own
 * [RuleConstructorViewModel] rather than a second copy of `HomeViewModel`,
 * and everything either of them writes goes to the same `@Singleton`
 * repositories, so both lists show the same rules because they read the same
 * store — not because they share a state holder.
 *
 * **List ⇄ form stays inside this Activity**, the way the studio keeps its
 * list and editor: when the list is the root, Save and back return to it and
 * only the list's own back closes the Activity. Entered through the home
 * doors, where there is no list behind the form, both close it — the pair of
 * exits the original dialog had.
 */
@AndroidEntryPoint
class RuleConstructorActivity : ComponentActivity() {

    @Inject lateinit var fontState: FontState
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var settingsRepository: SettingsRepository

    private val viewModel: RuleConstructorViewModel by viewModels()

    /** The rule whose script is waiting to be written; the launcher only hands back a uri. */
    private var pendingExportRuleId: String? = null

    private lateinit var exportLauncher: ActivityResultLauncher<String>

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("glyphzen_settings", MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"
        super.attachBaseContext(newBase.applyLocale(lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindow()
        registerLaunchers()

        val initialRuleId = intent.getStringExtra(EXTRA_RULE_ID)
        // The list is the root when this is the Settings door: no id and no
        // explicit request to build. The home "＋" passes `create` so it lands
        // in the form directly — which is what it has always done, and what
        // the user of that button expects from it.
        val startsWithList = (initialRuleId == null) && !intent.getBooleanExtra(EXTRA_CREATE, false)

        setContent {
            GlyphZenTheme(themeState = themeState, fontState = fontState) {
                val context = LocalContext.current

                // The import prompt and the export results are the only
                // actions here that can speak after the fact, and the
                // complaint has nowhere else to go: this Activity owns the
                // only instance of the channel.
                LaunchedEffect(Unit) {
                    viewModel.messages.collect { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }

                // The same two values `MainActivity` publishes: the shake
                // label reads the intensity scale, and the shared scaffold
                // haptics with it.
                CompositionLocalProvider(
                    LocalSettingsRepository provides settingsRepository,
                    LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val serviceScripts by viewModel.serviceScripts
                            .collectAsStateWithLifecycle()

                        // Saved across rotation: the screen the user was on
                        // is part of where they were, not something to
                        // re-derive from the intent.
                        var showList by rememberSaveable { mutableStateOf(startsWithList) }
                        var editingId by rememberSaveable { mutableStateOf(initialRuleId.orEmpty()) }

                        // Resolved when the form opens, not on every
                        // recomposition: a rule is a preferences read, and
                        // the name field recomposes this screen once per
                        // keystroke. Keyed on `showList` so re-opening the
                        // same rule reads it again rather than showing the
                        // copy cached behind the last save.
                        val existing = remember(showList, editingId) {
                            editingId.takeIf { it.isNotBlank() }?.let { viewModel.ruleFor(it) }
                        }

                        // System back pops the form the way its back arrow
                        // does. With no list behind it, the default — close —
                        // is exactly right, so this stays disabled.
                        BackHandler(enabled = (!showList) && startsWithList) {
                            showList = true
                        }

                        if (showList) {
                            val rules by viewModel.rules.collectAsStateWithLifecycle()
                            val attachFailures by viewModel.attachFailures.collectAsStateWithLifecycle()

                            // The same pick → scan → prompt → install
                            // sequence the form uses; nothing here selects
                            // the result, because the list is not where a
                            // script gets named.
                            val launchListImport = rememberServiceScriptImport(
                                onReadScript = viewModel::readServiceScriptText,
                                onImportScript = viewModel::importServiceScript,
                            ) { }

                            RuleListScreen(
                                rules = rules,
                                attachFailures = attachFailures,
                                onBackClick = { finish() },
                                onOpen = { ruleId ->
                                    editingId = ruleId
                                    showList = false
                                },
                                onCreate = {
                                    editingId = ""
                                    showList = false
                                },
                                onDelete = viewModel::deleteRule,
                                onToggle = viewModel::setRuleEnabled,
                                onPickImportFile = launchListImport,
                                onExportToDownloads = viewModel::exportScriptToDownloads,
                                onExportToFile = { ruleId -> promptExport(ruleId) },
                                onTest = viewModel::testRule,
                            )
                        } else {
                            RuleConstructorScreen(
                                existing = existing,
                                serviceScripts = serviceScripts,
                                grantedFor = viewModel::grantedFor,
                                onSave = { existingId, name, triggerId, animationId, durationMs, lowBatteryThreshold, shakeThreshold, batteryArc, scriptId, code, accepted ->
                                    viewModel.saveRule(
                                        existingId,
                                        name,
                                        triggerId,
                                        animationId,
                                        durationMs,
                                        lowBatteryThreshold,
                                        shakeThreshold,
                                        batteryArc,
                                        scriptId,
                                        code,
                                        accepted,
                                    )
                                    // Back to the list when the list is what
                                    // opened this; the home doors have none
                                    // behind them and close as they always did.
                                    if (startsWithList) showList = true else finish()
                                },
                                onBackClick = {
                                    if (startsWithList) showList = true else finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // File pickers

    private fun registerLaunchers() {
        exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument(ServiceScriptFileFormat.MIME_TYPE),
        ) { uri: Uri? ->
            val ruleId = pendingExportRuleId
            pendingExportRuleId = null
            if ((uri != null) && (ruleId != null)) viewModel.exportScriptTo(uri, ruleId)
        }
    }

    private fun promptExport(ruleId: String) {
        val name = viewModel.suggestedExportName(ruleId)
            ?: "rule.${ServiceScriptFileFormat.EXTENSION}"
        pendingExportRuleId = ruleId
        exportLauncher.launch(name)
    }

    private fun configureWindow() {
        // enableEdgeToEdge() already lays the window out behind both bars and
        // makes them transparent, so statusBarColor/navigationBarColor only
        // repeated it — and both are deprecated from API 35 on.
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.apply {
            isNavigationBarContrastEnforced = false
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    companion object {
        private const val EXTRA_RULE_ID = "rule_id"
        private const val EXTRA_CREATE = "create"

        /**
         * Three doors, two destinations:
         *
         *  * the Settings card passes neither extra and lands on the list —
         *    the studio's arrangement, and the reason this Activity now has a
         *    screen before the form;
         *  * the home list's "＋" passes `create` and lands in the form,
         *    straight into building, as it always has; and
         *  * a rule card's tap passes `ruleId` and lands in the same form
         *    with that rule loaded.
         */
        fun intent(
            context: Context,
            ruleId: String? = null,
            create: Boolean = false,
        ): Intent =
            Intent(context, RuleConstructorActivity::class.java).apply {
                if (ruleId != null) putExtra(EXTRA_RULE_ID, ruleId)
                if (create) putExtra(EXTRA_CREATE, true)
            }
    }
}
