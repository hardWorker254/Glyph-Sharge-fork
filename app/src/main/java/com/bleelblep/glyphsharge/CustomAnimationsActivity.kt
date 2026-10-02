package com.bleelblep.glyphsharge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.script.ScriptFileFormat
import com.bleelblep.glyphsharge.ui.screens.animations.AnimationEditorScreen
import com.bleelblep.glyphsharge.ui.screens.animations.AnimationListScreen
import com.bleelblep.glyphsharge.ui.screens.animations.ScriptStoreScreen
import com.bleelblep.glyphsharge.ui.screens.applyLocale
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.ui.theme.GlyphZenTheme
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.ThemeState
import com.bleelblep.glyphsharge.ui.viewmodel.AnimationStudioViewModel
import com.bleelblep.glyphsharge.ui.viewmodel.ScriptStoreViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The animation studio: write, preview, import and export glyph animations.
 *
 * A separate Activity on purpose. The studio has its own back stack, its own
 * system file pickers, and a code editor — none of which belong in the shared
 * settings `NavHost`. It also keeps the blast radius small: a script run here
 * goes through the same [com.bleelblep.glyphsharge.glyph.GlyphAnimationManager]
 * the feature services use, but nothing here can change a feature's
 * configuration.
 */
@AndroidEntryPoint
class CustomAnimationsActivity : ComponentActivity() {

    @Inject lateinit var fontState: FontState
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var glyphManager: GlyphManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val viewModel: AnimationStudioViewModel by viewModels()

    private val storeViewModel: ScriptStoreViewModel by viewModels()

    /** Whether the store is on screen, so back leaves it before the studio. */
    private var storeOpen by mutableStateOf(value = false)

    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var exportLauncher: ActivityResultLauncher<String>

    /** The id waiting to be written; the launcher only hands back a uri. */
    private var pendingExportId: String? = null

    /**
     * `true` when this Activity opened the Glyph session and is therefore the
     * one that has to close it again.
     *
     * The studio plays scripts on the real glyph, and the session belongs to
     * whichever screen happens to be in front — `MainActivity` closes it on
     * `onStop` when the service toggle is off, which is exactly the case the
     * user hits when they walk straight from the home screen into the studio.
     * Opening it here, and putting it back the way it was found, is what makes
     * the Glyph button work.
     */
    private var openedSessionHere = false

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("glyphzen_settings", MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"
        super.attachBaseContext(newBase.applyLocale(lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerLaunchers()
        configureWindow()
        glyphManager.initialize()

        setContent {
            GlyphZenTheme(themeState = themeState, fontState = fontState) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                // Collected as a flow, not as state, because these are events.
                // They used to be a `StateFlow<String?>` plus a `consumeMessage()`
                // called from inside the effect, which meant a message published
                // between `Toast.show()` and that call was overwritten with
                // `null` and never seen. A channel delivers every message, and
                // has nothing to acknowledge.
                //
                // The two are collected separately on purpose: an install, a
                // test and a refusal are three different sentences, and folding
                // them into one channel would lose which of the three happened.
                LaunchedEffect(Unit) {
                    viewModel.messages.collect {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }
                }
                LaunchedEffect(Unit) {
                    storeViewModel.messages.collect {
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    }
                }

                // The same two values `MainActivity` publishes, for the same
                // reason: the studio's screens should find the store and the
                // user's own haptic strength already in the composition.
                CompositionLocalProvider(
                    LocalSettingsRepository provides settingsRepository,
                    LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                    when {
                        state.isEditorOpen -> {
                            // One route out of the editor, for both the system
                            // back gesture and the toolbar arrow.
                            //
                            // Both used to call `closeEditor()` directly, which
                            // discards `name` and `source` and resets
                            // `isDirty` — so a script half-written and then
                            // lost to a back press was indistinguishable from
                            // one that had never been typed. `isDirty` was
                            // already computed and drawn as a dot on the row;
                            // this is the thing that dot was for.
                            //
                            // Saveable, so rotating with the question on screen
                            // does not lose the question.
                            var askToDiscard by rememberSaveable { mutableStateOf(value = false) }
                            val leaveEditor = {
                                if (state.isDirty) askToDiscard = true else viewModel.closeEditor()
                            }

                            BackHandler { leaveEditor() }

                            if (askToDiscard) {
                                AlertDialog(
                                    onDismissRequest = { askToDiscard = false },
                                    title = { Text(stringResource(R.string.studio_discard_title)) },
                                    text = { Text(stringResource(R.string.studio_discard_body)) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                askToDiscard = false
                                                viewModel.closeEditor()
                                            },
                                        ) { Text(stringResource(R.string.studio_discard_confirm)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { askToDiscard = false }) {
                                            Text(stringResource(R.string.studio_discard_cancel))
                                        }
                                    },
                                )
                            }

                            AnimationEditorScreen(
                                name = state.name,
                                source = state.source,
                                console = state.console,
                                isDirty = state.isDirty,
                                isRunning = state.isRunning,
                                onBackClick = { leaveEditor() },
                                onNameChange = viewModel::updateName,
                                onSourceChange = viewModel::updateSource,
                                onSave = viewModel::save,
                                onCheck = viewModel::check,
                                onRunGlyph = { viewModel.runOnGlyph() },
                                onStop = viewModel::stop,
                            )
                        }

                        // Its own branch rather than a route inside the list:
                        // the store is a screen in this Activity's back stack,
                        // not a state of the studio's.
                        storeOpen -> {
                            BackHandler { storeOpen = false }
                            val storeState by storeViewModel.uiState.collectAsStateWithLifecycle()
                            ScriptStoreScreen(
                                items = storeState.items,
                                loadState = storeState.loadState,
                                downloading = storeState.downloading,
                                testing = storeState.testing,
                                installed = storeState.installed,
                                outdated = storeState.outdated,
                                onBackClick = { storeOpen = false },
                                onRefresh = storeViewModel::refresh,
                                onInstall = storeViewModel::install,
                                onTest = storeViewModel::test,
                                isSupported = storeViewModel::isSupported,
                            )
                        }

                        else -> {
                            AnimationListScreen(
                                animations = state.animations,
                                onBackClick = { finish() },
                                onOpen = viewModel::open,
                                onCreate = {
                                    viewModel.newAnimation()
                                },
                                onDelete = viewModel::delete,
                                onDuplicate = viewModel::duplicate,
                                onPickImportFile = { importLauncher.launch(IMPORT_MIME_TYPES) },
                                onExportToDownloads = viewModel::exportToDownloads,
                                onExportToFile = { id -> promptExport(id) },
                                onOpenStore = { storeOpen = true },
                            )
                        }
                    }
                }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        openGlyphSession()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the studio must not leave the strip lit.
        viewModel.stop()
        closeGlyphSessionIfOurs()
    }

    override fun onDestroy() {
        viewModel.stop()
        closeGlyphSessionIfOurs()
        super.onDestroy()
    }

    /**
     * Makes sure a script can actually reach the hardware.
     *
     * [GlyphManager.openSession] is a no-op when the session is already open, so
     * this is safe to call on every `onStart`. The service binding itself is
     * asynchronous, so a phone that is still connecting gets a moment before
     * the first attempt.
     */
    private fun openGlyphSession() {
        if (!glyphManager.isNothingPhone()) return
        if (glyphManager.isSessionActive) return

        runCatching { glyphManager.openSession() }
            .onSuccess { openedSessionHere = true }
            .onFailure { Log.w(TAG, "Could not open the Glyph session", it) }
    }

    /** Leaves the session exactly as it was found. */
    private fun closeGlyphSessionIfOurs() {
        if (!openedSessionHere) return
        openedSessionHere = false
        runCatching { glyphManager.closeSession() }
            .onFailure { Log.w(TAG, "Could not close the Glyph session", it) }
    }

    // File pickers

    private fun registerLaunchers() {
        importLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { viewModel.importFrom(it) } }

        exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument(ScriptFileFormat.MIME_TYPE),
        ) { uri: Uri? ->
            val id = pendingExportId
            pendingExportId = null
            id?.let { animId ->
                uri?.let { viewModel.exportTo(it, animId) }
            }
        }
    }

    private fun promptExport(id: String) {
        val name = viewModel.uiState.value.animations.firstOrNull { it.id == id }?.name ?: "animation"
        pendingExportId = id
        exportLauncher.launch("$name.${ScriptFileFormat.EXTENSION}")
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
        private const val TAG = "CustomAnimations"

        /**
         * A `.glyphlua` file is plain text, and a user importing one may well
         * have renamed it or dropped it from a chat app that gave it no
         * extension — so the picker is opened permissively.
         */
        private val IMPORT_MIME_TYPES = arrayOf(
            "text/plain",
            "application/octet-stream",
            "*/*",
        )

        /** The entry point used by the settings card. */
        fun intent(context: Context): Intent = Intent(context, CustomAnimationsActivity::class.java)
    }
}
