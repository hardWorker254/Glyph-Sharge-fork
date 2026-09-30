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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.script.ScriptFileFormat
import com.bleelblep.glyphsharge.ui.screens.animations.AnimationEditorScreen
import com.bleelblep.glyphsharge.ui.screens.animations.AnimationListScreen
import com.bleelblep.glyphsharge.ui.screens.applyLocale
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.ui.theme.GlyphZenTheme
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.ThemeState
import com.bleelblep.glyphsharge.ui.viewmodel.AnimationStudioViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
                val message by viewModel.messages.collectAsStateWithLifecycle()
                val context = LocalContext.current

                LaunchedEffect(message) {
                    message?.let {
                        Toast
                            .makeText(context, it, Toast.LENGTH_SHORT)
                            .show()
                        viewModel.consumeMessage()
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
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (state.isEditorOpen) {
                            BackHandler { viewModel.closeEditor() }
                            AnimationEditorScreen(
                                name = state.name,
                                source = state.source,
                                console = state.console,
                                isDirty = state.isDirty,
                                isRunning = state.isRunning,
                                onBackClick = { viewModel.closeEditor() },
                                onNameChange = viewModel::updateName,
                                onSourceChange = viewModel::updateSource,
                                onSave = viewModel::save,
                                onCheck = viewModel::check,
                                onRunGlyph = { viewModel.runOnGlyph() },
                                onStop = viewModel::stop
                            )
                        } else {
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
                                onExportToFile = { id -> promptExport(id) }
                            )
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
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let { viewModel.importFrom(it) } }

        exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument(ScriptFileFormat.MIME_TYPE)
        ) { uri: Uri? ->
            val id = pendingExportId
            pendingExportId = null
            if (uri != null && id != null) viewModel.exportTo(uri, id)
        }
    }

    private fun promptExport(id: String) {
        val name = viewModel.uiState.value.animations.firstOrNull { it.id == id }?.name ?: "animation"
        pendingExportId = id
        exportLauncher.launch("${name}.${ScriptFileFormat.EXTENSION}")
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
            "*/*"
        )

        /** The entry point used by the settings card. */
        fun intent(context: Context): Intent = Intent(context, CustomAnimationsActivity::class.java)
    }
}
