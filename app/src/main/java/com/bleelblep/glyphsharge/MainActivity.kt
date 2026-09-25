package com.bleelblep.glyphsharge

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.NfcGlyphService
import com.bleelblep.glyphsharge.services.QuietHoursService
import com.bleelblep.glyphsharge.ui.components.WatermarkBox
import com.bleelblep.glyphsharge.ui.navigation.GlyphNavHost
import com.bleelblep.glyphsharge.ui.screens.applyLocale
import com.bleelblep.glyphsharge.ui.state.GlyphFeature
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.ui.theme.GlyphZenTheme
import com.bleelblep.glyphsharge.ui.theme.ThemeState
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel
import com.bleelblep.glyphsharge.utils.WatermarkHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Hosts the Compose tree and owns everything that genuinely needs an
 * `Activity`: window configuration, runtime permissions, the log export
 * launcher, NFC foreground dispatch and the glyph session lifecycle.
 *
 * Feature state and service control live in [HomeViewModel] — this class used
 * to carry eighteen feature methods and hand them to the UI as lambdas.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ── DI ──────────────────────────────────────────────────────────────────
    @Inject lateinit var fontState: FontState
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphManager: GlyphManager
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureServiceController: FeatureServiceController

    private val homeViewModel: HomeViewModel by viewModels()

    // ── State ────────────────────────────────────────────────────────────────
    private var animJob: Job? = null
    private var isGlyphDemoRunning = false
    private var wasServiceEnabled = false

    private lateinit var createLogFileLauncher: ActivityResultLauncher<String>

    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("glyphzen_settings", MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"

        val contextWithLocale = newBase.applyLocale(lang)
        super.attachBaseContext(contextWithLocale)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== App startup - checking settings persistence ===")
        settingsRepository.dumpAllSettings()

        // NFC foreground dispatch needs the Activity, so the ViewModel calls
        // back into us when the NFC feature is toggled.
        homeViewModel.setNfcDispatchHook(::onNfcFeatureToggled)

        createLogFileLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri -> uri?.let { writeLogToUri(it) } }

        configureWindow()
        initializeGlyphService()
        startEnabledFeatureServices()
        startQuietHoursService()
        initializeNfcDispatch()
        WatermarkHelper.disable()
        setupUI()
        startPersistentGlyphService()
    }

    override fun onStop() {
        super.onStop()
        wasServiceEnabled = glyphManager.isSessionActive
        cancelRunningAnimations()
        if (!settingsRepository.getGlyphServiceEnabled()) {
            glyphManager.cancelAllAnimations()
            glyphAnimationManager.stopAnimations()
            if (wasServiceEnabled) glyphManager.closeSession()
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasServiceEnabled) glyphManager.openSession()
        maybeRestoreSession()
        WatermarkHelper.addToActivity(this)
        enableNfcForegroundDispatch()
        // A feature's own dialog may have changed its preference while the
        // home screen was off-screen.
        homeViewModel.refreshFeatures()
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        NfcGlyphService.forwardNfcIntent(this, intent)
    }

    override fun onDestroy() {
        cancelRunningAnimations()
        if (!settingsRepository.getGlyphServiceEnabled()) glyphManager.cleanup()
        WatermarkHelper.removeFromActivity(this)
        super.onDestroy()
    }

    // ── Window configuration ──────────────────────────────────────────────────
    private fun configureWindow() {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.apply {
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            isNavigationBarContrastEnforced = false
            setDecorFitsSystemWindows(false)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    // ── Initialisation helpers ────────────────────────────────────────────────
    private fun initializeGlyphService() {
        glyphManager.initialize()
        // The SDK is the source of truth for the session; mirror it into the
        // ViewModel so the cards can read it without touching GlyphManager.
        glyphManager.onSessionStateChanged = { isActive ->
            homeViewModel.onSessionStateChanged(isActive)
            if (!isActive) maybeRestoreSession()
        }

        if (settingsRepository.getGlyphServiceEnabled() && glyphManager.isNothingPhone()) {
            lifecycleScope.launch {
                delay(STARTUP_DELAY_MS.milliseconds)
                if (!glyphManager.isSessionActive) homeViewModel.toggleGlyphService(true)
                else homeViewModel.onSessionStateChanged(true)
            }
        } else {
            homeViewModel.onSessionStateChanged(glyphManager.isSessionActive)
        }
    }

    /** Starts every feature service the user has switched on. */
    private fun startEnabledFeatureServices() {
        featureServiceController.startAllEnabled()
    }

    private fun startQuietHoursService() {
        val intent = Intent(this, QuietHoursService::class.java)
        if (settingsRepository.isQuietHoursEnabled()) {
            startForegroundService(intent)
        } else {
            stopService(intent)
        }
    }

    // ── NFC initialisation ───────────────────────────────────────────────────

    private fun initializeNfcDispatch() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Log.w(TAG, "NFC adapter not available on this device")
            return
        }
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun enableNfcForegroundDispatch() {
        if (!featureServiceController.isEnabled(GlyphFeature.NFC)) return
        try {
            nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
            Log.d(TAG, "NFC foreground dispatch enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable NFC foreground dispatch", e)
        }
    }

    private fun disableNfcForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable NFC foreground dispatch", e)
        }
    }

    /** Keeps foreground dispatch in step with the NFC feature toggle. */
    fun onNfcFeatureToggled(enabled: Boolean) {
        if (enabled) enableNfcForegroundDispatch() else disableNfcForegroundDispatch()
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    private fun setupUI() {
        setContent {
            GlyphZenTheme(themeState = themeState, fontState = fontState) {
                val bgColor = MaterialTheme.colorScheme.background
                WatermarkBox(
                    enabled = false,
                    text = "TESTING",
                    alpha = 0.5f,
                    fontSize = 20.sp
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
                        GlyphNavHost(
                            settingsRepository = settingsRepository,
                            homeViewModel = homeViewModel,
                        )
                    }
                }
            }
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────
    private fun writeLogToUri(uri: Uri) { /* ... */ }

    // ── Session management ────────────────────────────────────────────────────
    private fun maybeRestoreSession() { /* ... */ }
    private fun startPersistentGlyphService() { /* ... */ }

    @SuppressLint("BatteryLife")
    private fun cancelRunningAnimations() {
        animJob?.cancel()
        isGlyphDemoRunning = false
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG                   = "MainActivity"
        private const val STARTUP_DELAY_MS      = 100L
    }
}
