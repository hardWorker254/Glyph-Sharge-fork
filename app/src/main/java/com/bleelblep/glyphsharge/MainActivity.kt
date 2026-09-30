package com.bleelblep.glyphsharge

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.services.FeatureServiceController
import com.bleelblep.glyphsharge.services.NfcGlyphService
import com.bleelblep.glyphsharge.services.QuietHoursService
import com.bleelblep.glyphsharge.tiles.MusicVisualizerTileService
import com.bleelblep.glyphsharge.ui.navigation.GlyphNavHost
import com.bleelblep.glyphsharge.ui.screens.applyLocale
import com.bleelblep.glyphsharge.ui.theme.FontState
import com.bleelblep.glyphsharge.ui.theme.GlyphZenTheme
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.ThemeState
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel
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
 * Features state and service control live in [HomeViewModel]; this class keeps
 * only what genuinely needs an `Activity`.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Dependency injection
    @Inject lateinit var fontState: FontState
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphManager: GlyphManager
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureServiceController: FeatureServiceController

    private val homeViewModel: HomeViewModel by viewModels()

    // State
    private var animJob: Job? = null
    private var isGlyphDemoRunning = false
    private var wasServiceEnabled = false

    private lateinit var createLogFileLauncher: ActivityResultLauncher<String>
    private lateinit var recordAudioLauncher: ActivityResultLauncher<String>
    private lateinit var musicCaptureLauncher: ActivityResultLauncher<Intent>

    /**
     * `true` while the Activity is here only because a Quick Settings tile sent
     * it, which is the one case where leaving it on screen would be wrong: the
     * user asked for a glyph animation, got a permission dialog, and has
     * answered it. Nothing here is worth their screen after that.
     */
    private var launchedFromTile = false

    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("glyphzen_settings", MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"

        val contextWithLocale = newBase.applyLocale(lang)
        super.attachBaseContext(contextWithLocale)
    }

    // Lifecycle
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

        registerMusicCaptureLaunchers()
        watchMusicCaptureRequests()

        configureWindow()
        initializeGlyphService()
        startEnabledFeatureServices()
        startQuietHoursService()
        initializeNfcDispatch()
        setupUI()
        startPersistentGlyphService()
        // Last: it answers through a channel the launcher collection above
        // drains once this Activity is at least started.
        handleTileIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Every time, not only in `onCreate`.
        //
        // A feature service can be gone — killed by the system, or shut itself
        // down after the Glyph service was off for a moment — and nothing
        // noticed: the card still said "on" and the trigger the service listens
        // for was never registered, so the animation silently stopped coming.
        // Re-starting an already running service just calls `onStartCommand`
        // again, which is what keeps the card and the truth together.
        startEnabledFeatureServices()
    }

    override fun onStop() {
        super.onStop()
        wasServiceEnabled = glyphManager.isSessionActive
        cancelRunningAnimations()
        if (!settingsRepository.getGlyphServiceEnabled()) {
            glyphAnimationManager.stopAnimations()
            if (wasServiceEnabled) glyphManager.closeSession()
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasServiceEnabled) glyphManager.openSession()
        maybeRestoreSession()
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
        setIntent(intent)
        // The shade reuses a task, so a second tile tap arrives here rather
        // than through onCreate.
        handleTileIntent(intent)
        NfcGlyphService.forwardNfcIntent(this, intent)
    }

    override fun onDestroy() {
        cancelRunningAnimations()
        if (!settingsRepository.getGlyphServiceEnabled()) glyphManager.cleanup()
        super.onDestroy()
    }

    // Window configuration
    private fun configureWindow() {
        // enableEdgeToEdge() already lays the window out behind both bars and
        // makes them transparent, so statusBarColor/navigationBarColor and the
        // platform setDecorFitsSystemWindows() only repeated it — and all three
        // are deprecated from API 35 on.
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

    // Initialisation helpers
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

    // Music capture consent

    /**
     * The visualiser's two grants, in order.
     *
     * Both can only be asked for from an Activity, which is why they live
     * here rather than in the card that asks for them: the Quick Settings tile
     * needs the same chain and has no Activity of its own, and the visualiser
     * card is the last one in the home list, so it is not necessarily composed
     * when a tile drops the user into the app.
     */
    private fun registerMusicCaptureLaunchers() {
        recordAudioLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // Straight on to the second grant: agreeing to record and agreeing
            // to the capture are two questions, and the second deserves its
            // own prompt rather than a silent assumption.
            if (granted) requestMusicCaptureConsent()
            else rejectMusicCapture(R.string.music_viz_permission_denied)
        }

        // The token is *not* claimed here. `getMediaProjection` refuses to
        // return one unless a foreground service of the matching type is
        // already running, so the consent travels in the start intent and is
        // claimed by MusicVisualizerService after `startForeground`.
        musicCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            homeViewModel.onMusicCaptureResult(result.resultCode, result.data)
            // The question has been answered either way, so the tile that sent
            // the user here has nothing left to show them.
            if (launchedFromTile) closeIfOpenedByTile()
        }
    }

    /**
     * Leaves, rather than merely going away.
     *
     * `finish()` alone ends the Activity but leaves the task behind, which on
     * this shade looks exactly like what it is not: the app sliding into the
     * background and still waiting in the recents, one tap away from a screen
     * with nothing on it. The task was created for a permission dialog and has
     * no reason to outlive it.
     */
    private fun closeIfOpenedByTile() {
        finishAndRemoveTask()
    }

    /** Answers the request the card's switch and the tile both raise. */
    private fun watchMusicCaptureRequests() {
        lifecycleScope.launch {
            // STARTED, not CREATED: an Activity result cannot be asked for
            // before the window is there to answer it. Requests stay buffered
            // in the channel while this is not running.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.musicCaptureRequests.collect { requestMusicCapture() }
            }
        }
    }

    private fun requestMusicCapture() {
        // A live capture means the visualiser is already drawing, and asking
        // again would replace a working capture with a fresh one — confusing to
        // answer and destructive to answer. This is also how the settings
        // dialog's Enable button reaches the same place as the card's switch.
        if (homeViewModel.musicCaptureSource.isCapturing) return

        if (!homeViewModel.musicCaptureSource.canCapture()) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        requestMusicCaptureConsent()
    }

    private fun requestMusicCaptureConsent() {
        musicCaptureLauncher.launch(homeViewModel.musicCaptureSource.consentIntent())
    }

    /**
     * Puts the card back where it was.
     *
     * Declining either grant leaves the feature off rather than starting it
     * with nothing behind it: the card must never claim to be on while the
     * service cannot capture anything.
     */
    private fun rejectMusicCapture(@StringRes message: Int) {
        homeViewModel.setFeatureEnabled(GlyphFeature.MUSIC_VISUALIZER, false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // Declined is still an answer: an Activity the tile opened has done
        // its job and should not stay in front of what the user was doing.
        if (launchedFromTile) closeIfOpenedByTile()
    }

    /**
     * Acts on a Quick Settings tile that had to come through an Activity.
     *
     * Only the visualiser asks: the Glyph service tile does its whole job from
     * the shade.
     */
    private fun handleTileIntent(intent: Intent?) {
        if (intent?.action != MusicVisualizerTileService.ACTION_ENABLE_MUSIC) return
        launchedFromTile = true
        homeViewModel.requestMusicCapture()
    }

    // NFC initialisation

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

    // UI
    private fun setupUI() {
        setContent {
            GlyphZenTheme(themeState = themeState, fontState = fontState) {
                val bgColor = MaterialTheme.colorScheme.background
                // The one place the tree is handed the settings store and the
                // haptic strength: two values that never change for the life of
                // the Activity, so anything below reads them rather than
                // carrying them as parameters.
                CompositionLocalProvider(
                    LocalSettingsRepository provides settingsRepository,
                    LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
                        GlyphNavHost()
                    }
                }
            }
        }
    }

    // Diagnostics
    private fun writeLogToUri(uri: Uri) { /* ... */ }

    // Session management
    private fun maybeRestoreSession() { /* ... */ }
    private fun startPersistentGlyphService() { /* ... */ }

    @SuppressLint("BatteryLife")
    private fun cancelRunningAnimations() {
        animJob?.cancel()
        isGlyphDemoRunning = false
    }

    // Constants
    companion object {
        private const val TAG                   = "MainActivity"
        private const val STARTUP_DELAY_MS      = 100L
    }
}
