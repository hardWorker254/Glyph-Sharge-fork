package com.bleelblep.glyphsharge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.ui.screens.animations.ScriptStoreScreen
import com.bleelblep.glyphsharge.ui.screens.applyLocale
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.ui.theme.GlyphZenTheme
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.ThemeState
import com.bleelblep.glyphsharge.ui.viewmodel.ScriptStoreViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The catalogue: scripts other people wrote, including `.servicelua` services.
 *
 * A separate Activity rather than a route inside `MainActivity`, for a reason
 * that is not about navigation at all. **"Test on glyph" needs a session**, and
 * [MainActivity.maybeRestoreSession] opens one only while the master switch is
 * on — so a store living in the settings NavHost would inherit that gate and
 * run its preview into a closed session: the run reports zero frames and the
 * store still says it finished. The studio used to give the store its session
 * for free, which is why the button has always worked with the switch off; this
 * Activity takes the same responsibility for itself, opening the session in
 * `onStart` and closing it again in `onStop` only if it is the one that opened
 * it — so a store visit never closes a session that was already running.
 *
 * Everything else — the locale, the window, the theme, the two composition
 * locals the shared scaffold reads — is the same shape as the studio's, because
 * the two screens are the same kind of destination and a divergence there would
 * be found by a user, not by a test.
 */
@AndroidEntryPoint
class ScriptStoreActivity : ComponentActivity() {

    @Inject lateinit var fontState: FontState
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphManager: GlyphManager

    private val viewModel: ScriptStoreViewModel by viewModels()

    /** Whether *this* Activity opened the session, so it only closes its own. */
    private var openedSessionHere = false

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("glyphzen_settings", MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"
        super.attachBaseContext(newBase.applyLocale(lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindow()
        glyphManager.initialize()

        setContent {
            GlyphZenTheme(themeState = themeState, fontState = fontState) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                // The install, the refusal and the prompt's verdict are all
                // events, so they are collected as a flow the way the studio's
                // are: two failures that produce the same sentence have to
                // arrive as two messages, and a `StateFlow` would keep one.
                LaunchedEffect(Unit) {
                    viewModel.messages.collect {
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    }
                }

                // The same two values `MainActivity` publishes: the cards and
                // the shared scaffold's haptics both read them.
                CompositionLocalProvider(
                    LocalSettingsRepository provides settingsRepository,
                    LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ScriptStoreScreen(
                            items = state.items,
                            loadState = state.loadState,
                            downloading = state.downloading,
                            testing = state.testing,
                            installed = state.installed,
                            installedServices = state.installedServices,
                            outdated = state.outdated,
                            filter = state.filter,
                            pendingCapabilities = state.pendingInstall?.requested,
                            onBackClick = { finish() },
                            onRefresh = viewModel::refresh,
                            onInstall = viewModel::install,
                            onTest = viewModel::test,
                            isSupported = viewModel::isSupported,
                            onFilterChange = viewModel::setFilter,
                            onAcceptInstall = viewModel::acceptPendingInstall,
                            onDeclineInstall = viewModel::declinePendingInstall,
                        )
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
        closeGlyphSessionIfOurs()
    }

    override fun onDestroy() {
        closeGlyphSessionIfOurs()
        super.onDestroy()
    }

    /**
     * Makes sure a preview can actually reach the hardware.
     *
     * [GlyphManager.openSession] is a no-op when the session is already open, so
     * this is safe on every `onStart` — and a session the app already holds for
     * the master switch stays exactly as it was, because then
     * [openedSessionHere] never becomes true and nothing here will close it.
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
        private const val TAG = "ScriptStoreActivity"

        /** The entry point used by the settings card. */
        fun intent(context: Context): Intent = Intent(context, ScriptStoreActivity::class.java)
    }
}
