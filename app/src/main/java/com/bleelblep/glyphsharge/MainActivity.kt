package com.bleelblep.glyphsharge

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bleelblep.glyphsharge.glyph.*
import com.bleelblep.glyphsharge.services.*
import com.bleelblep.glyphsharge.ui.components.*
import com.bleelblep.glyphsharge.ui.screens.*
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.utils.WatermarkHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ── DI ──────────────────────────────────────────────────────────────────
    @Inject lateinit var fontState: FontState
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphManager: GlyphManager
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager

    // ── State ────────────────────────────────────────────────────────────────
    private var animJob: Job? = null
    private var isGlyphDemoRunning = false
    private var wasServiceEnabled = false
    private var restoreSessionJob: Job? = null
    private var pendingLogText: String? = null

    private val _glyphServiceState = mutableStateOf(false)
    val glyphServiceState: State<Boolean> = _glyphServiceState

    private lateinit var createLogFileLauncher: androidx.activity.result.ActivityResultLauncher<String>

    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== App startup - checking settings persistence ===")
        settingsRepository.dumpAllSettings()

        requestNotificationPermission()

        createLogFileLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri -> uri?.let { writeLogToUri(it) } }

        configureWindow()
        initializeGlyphService()
        initializeServices()
        initializeNfcDispatch()
        WatermarkHelper.disable()
        setupUI()
        startPersistentGlyphService()
    }

    override fun onStop() {
        super.onStop()
        wasServiceEnabled = glyphServiceState.value
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
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
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
    private fun requestNotificationPermission() {
        val perm = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_NOTIFICATION)
        }
    }

    /** Start / stop every background service according to saved preferences. */
    private fun initializeServices() {
        initServiceByPref(PowerPeekService::class.java,       settingsRepository.isPowerPeekEnabled())
        initServiceByPref(LowBatteryAlertService::class.java, settingsRepository.isLowBatteryEnabled())
        initServiceByPref(QuietHoursService::class.java,      settingsRepository.isQuietHoursEnabled())
        initializeGlyphService()
        initializePulseLock()
        initializeScreenOffFeature()
        initializeNfcFeature()
    }

    /** Generic helper: start or stop a foreground service class based on a boolean flag. */
    private fun <T : android.app.Service> initServiceByPref(
        serviceClass: Class<T>,
        enabled: Boolean
    ) {
        val intent = Intent(this, serviceClass)
        if (enabled) startForegroundServiceCompat(intent) else stopService(intent)
    }

    private fun initializeGlyphService() {
        glyphManager.initialize()
        glyphManager.onSessionStateChanged = { isActive ->
            _glyphServiceState.value = isActive
            if (!isActive) maybeRestoreSession()
        }

        if (settingsRepository.getGlyphServiceEnabled() && glyphManager.isNothingPhone()) {
            lifecycleScope.launch {
                delay(STARTUP_DELAY_MS)
                if (!glyphManager.isSessionActive) toggleGlyphService(true)
                else _glyphServiceState.value = true
            }
        } else {
            _glyphServiceState.value = glyphManager.isSessionActive
        }
    }

    private fun initializePulseLock() {
        val enabled = settingsRepository.isPulseLockEnabled()
        val intent = Intent(this, PulseLockService::class.java).apply {
            action = if (enabled) PulseLockService.ACTION_START else PulseLockService.ACTION_STOP
        }
        if (enabled) startForegroundServiceCompat(intent) else stopService(intent)
    }

    private fun initializeScreenOffFeature() {
        val enabled = settingsRepository.isScreenOffFeatureEnabled()
        val intent = Intent(this, ScreenOffGlyphService::class.java).apply {
            action = if (enabled) ScreenOffGlyphService.ACTION_START else ScreenOffGlyphService.ACTION_STOP
        }
        if (enabled) startForegroundServiceCompat(intent) else stopService(intent)
    }

    // ── NFC initialisation ───────────────────────────────────────────────────

    /**
     * Prepare the NFC adapter + PendingIntent needed for foreground dispatch.
     * Called once from [onCreate].
     */
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

    /**
     * Start or stop [NfcGlyphService] based on saved preference.
     * Called from [initializeServices] and when the user toggles the feature.
     */
    private fun initializeNfcFeature() {
        val enabled = settingsRepository.isNfcFeatureEnabled()
        val intent = Intent(this, NfcGlyphService::class.java).apply {
            action = if (enabled) NfcGlyphService.ACTION_START else NfcGlyphService.ACTION_STOP
        }
        if (enabled) startForegroundServiceCompat(intent) else stopService(intent)
    }

    /**
     * Enable NFC foreground dispatch so that tag intents are delivered to
     * [onNewIntent] and forwarded to [NfcGlyphService].
     * Called from [onResume].
     */
    private fun enableNfcForegroundDispatch() {
        if (!settingsRepository.isNfcFeatureEnabled()) return
        try {
            nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
            Log.d(TAG, "NFC foreground dispatch enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable NFC foreground dispatch", e)
        }
    }

    /**
     * Disable NFC foreground dispatch. Called from [onPause].
     */
    private fun disableNfcForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable NFC foreground dispatch", e)
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    private fun setupUI() {
        setContent {
            GlyphZenTheme(themeState = themeState, fontState = fontState) {
                val bgColor = MaterialTheme.colorScheme.background
                WatermarkBox(enabled = false, text = "TESTING", alpha = 0.5f, fontSize = 20.sp) {
                    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
                        MainScreen(
                            isNothingPhone             = glyphManager.isNothingPhone(),
                            glyphServiceEnabled        = glyphServiceState.value,
                            onGlyphServiceToggle       = ::toggleGlyphService,
                            onLaunchGlyphDemo          = ::runGlyphDemo,
                            onTestAllZones             = ::testAllZones,
                            onTestCustomPattern        = ::testCustomPattern,
                            onRunWaveAnimation         = ::runWaveAnimation,
                            onRunPulseEffect           = ::runPulseEffect,
                            onTestGlyphGuard           = ::testGlyphGuard,
                            onStartGlyphGuard          = ::startGlyphGuard,
                            onStopGlyphGuard           = ::stopGlyphGuard,
                            onRunBoxBreathing          = ::runBoxBreathing,
                            onTestPowerPeek            = ::testPowerPeek,
                            onEnablePowerPeek          = ::enablePowerPeek,
                            onDisablePowerPeek         = ::disablePowerPeek,
                            onRunNotificationEffect    = ::runNotificationEffect,
                            onTestGlyphChannel         = ::testGlyphChannel,
                            onTestC1Segment            = ::testC1Segment,
                            onTestFinalState           = ::testFinalStateBeforeTurnoff,
                            onTestC14C15Isolated       = ::testC14AndC15Isolated,

                            // Pulse Lock
                            onTestPulseLock            = ::testPulseLock,
                            onEnablePulseLock          = ::enablePulseLock,
                            onDisablePulseLock         = ::disablePulseLock,

                            // Screen Off
                            onTestScreenOff            = ::testScreenOffAnimation,
                            onEnableScreenOff          = ::enableScreenOffFeature,
                            onDisableScreenOff         = ::disableScreenOffFeature,

                            // NFC
                            onTestNfc                  = ::testNfcAnimation,
                            onEnableNfc                = ::enableNfcFeature,
                            onDisableNfc               = ::disableNfcFeature,

                            onTestLowBattery           = ::testLowBatteryAlert,
                            onRunDiagnostics           = ::runDiagnostics,
                            backgroundColorMain        = bgColor,
                            settingsRepository         = settingsRepository
                        )
                    }
                }
            }
        }
    }

    // ── Glyph service toggle ──────────────────────────────────────────────────
    fun toggleGlyphService(enabled: Boolean) {
        try {
            if (glyphManager.isSessionActive == enabled) {
                _glyphServiceState.value = enabled
                settingsRepository.saveGlyphServiceEnabled(enabled)
                showToast("Glyph service is already ${if (enabled) "enabled" else "disabled"}")
                return
            }
            if (!enabled) {
                cancelRunningAnimations()
                settingsRepository.saveGlyphServiceEnabled(false)
                restoreSessionJob?.cancel()
            }

            glyphManager.toggleGlyphService()
            val newState = glyphManager.isSessionActive
            val success  = newState == enabled

            if (success) {
                _glyphServiceState.value = newState
                settingsRepository.saveGlyphServiceEnabled(newState)
                showToast(if (newState) "Glyph service enabled" else "Glyph service disabled")
                syncServicesAfterToggle(newState)
            } else {
                _glyphServiceState.value = glyphManager.isSessionActive
                showToast("Failed to ${if (enabled) "enable" else "disable"} glyph service")
                if (!enabled) settingsRepository.saveGlyphServiceEnabled(true) // roll back
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling glyph service", e)
            _glyphServiceState.value = glyphManager.isSessionActive
            showToast("Error: ${e.message}")
        }
    }

    private fun syncServicesAfterToggle(glyphOn: Boolean) {
        val fgIntent = Intent(this, GlyphForegroundService::class.java)
        if (glyphOn) {
            if (settingsRepository.isPulseLockEnabled())        initializePulseLock()
            if (settingsRepository.isScreenOffFeatureEnabled())  initializeScreenOffFeature()
            if (settingsRepository.isNfcFeatureEnabled())        initializeNfcFeature()   // NFC
            startForegroundServiceCompat(fgIntent)
        } else {
            runCatching { startService(Intent(this, PulseLockService::class.java).apply        { action = PulseLockService.ACTION_STOP }) }
            runCatching { startService(Intent(this, ScreenOffGlyphService::class.java).apply   { action = ScreenOffGlyphService.ACTION_STOP }) }
            runCatching { startService(Intent(this, NfcGlyphService::class.java).apply         { action = NfcGlyphService.ACTION_STOP }) }   // NFC
            stopService(fgIntent)
        }
    }

    // ── Animation helpers ─────────────────────────────────────────────────────

    private fun launchGlyphAnim(
        label: String,
        bypass: Boolean = false,
        block: suspend () -> Unit
    ) {
        if (!canPerformGlyphOperation(bypass)) return
        animJob?.cancel()
        isGlyphDemoRunning = false
        animJob = lifecycleScope.launch {
            try {
                block()
                delay(settingsRepository.getDisplayDuration())
            } catch (e: Exception) {
                Log.e(TAG, "Error in $label: ${e.message}")
                showToast("Error: ${e.message}")
            } finally {
                glyphAnimationManager.stopAnimations()
                isGlyphDemoRunning = false
            }
        }
    }

    // ── Public animation API ──────────────────────────────────────────────────
    fun runGlyphDemo() { /* ... */ }
    fun testAllZones(bypass: Boolean = false) = launchGlyphAnim("testAllZones", bypass) { glyphAnimationManager.testAllZones(bypass) }
    fun testCustomPattern(bypass: Boolean = false) = launchGlyphAnim("testCustomPattern", bypass) { glyphAnimationManager.testCustomPattern(bypass) }
    fun runWaveAnimation() = launchGlyphAnim("runWaveAnimation") { glyphAnimationManager.runWaveAnimation() }
    fun runPulseEffect() = launchGlyphAnim("runPulseEffect") { glyphAnimationManager.runPulseEffect(3) }
    fun runBoxBreathing() = launchGlyphAnim("runBoxBreathing") { glyphAnimationManager.runC1SequentialWithBreathingTiming(true, 2) }
    fun runNotificationEffect() = launchGlyphAnim("runNotificationEffect") { glyphAnimationManager.runNotificationEffect() }
    fun testGlyphChannel(channelIndex: Int) = launchGlyphAnim("testGlyphChannel[$channelIndex]", bypass = true) { glyphAnimationManager.testGlyphChannel(channelIndex, true) }
    fun testC1Segment(c1Index: Int) = launchGlyphAnim("testC1Segment[$c1Index]", bypass = true) { glyphAnimationManager.testC1Segment(c1Index, true) }
    fun testFinalStateBeforeTurnoff() = launchGlyphAnim("testFinalState", bypass = true) { glyphAnimationManager.testFinalStateBeforeTurnoff(true) }
    fun testC14AndC15Isolated() = launchGlyphAnim("testC14C15Isolated", bypass = true) { glyphAnimationManager.testOnlyC14AndC15Isolated(true) }

    fun testPowerPeek(bypass: Boolean = false) = launchGlyphAnim("testPowerPeek", bypass) {
        showToast("Testing PowerPeek – showing battery percentage…")
        val duration = settingsRepository.getDisplayDuration()
        glyphAnimationManager.runBatteryPercentageVisualization(this@MainActivity, duration) {}
    }

    fun testGlyphGuard(bypass: Boolean = false) { /* ... */ }

    // ── Glyph Guard service ───────────────────────────────────────────────────
    fun startGlyphGuard() { /* ... */ }
    fun stopGlyphGuard() { /* ... */ }

    // ── PowerPeek ─────────────────────────────────────────────────────────────
    fun enablePowerPeek() {
        settingsRepository.savePowerPeekEnabled(true)
        startForegroundServiceCompat(Intent(this, PowerPeekService::class.java))
        showToast("PowerPeek enabled! Shake when screen is off to see battery %.")
    }

    fun disablePowerPeek() {
        settingsRepository.savePowerPeekEnabled(false)
        stopService(Intent(this, PowerPeekService::class.java))
        showToast("PowerPeek disabled")
    }

    // ── Glow Gate (PulseLock) ─────────────────────────────────────────────────
    fun testPulseLock() = lifecycleScope.launch {
        runCatching { glyphAnimationManager.playPulseLockAnimation(settingsRepository.getPulseLockAnimationId()) }
            .onFailure { Log.e(TAG, "Error testing Glow Gate", it) }
    }

    fun enablePulseLock() {
        settingsRepository.savePulseLockEnabled(true)
        initializePulseLock()
        showToast("Glow Gate enabled")
    }

    fun disablePulseLock() {
        settingsRepository.savePulseLockEnabled(false)
        initializePulseLock()
        showToast("Glow Gate disabled")
    }

    // ── Screen Off Animation ──────────────────────────────────────────
    fun testScreenOffAnimation() = lifecycleScope.launch {
        runCatching {
            glyphAnimationManager.playScreenOffAnimation(settingsRepository.getScreenOffAnimationId())
        }.onFailure { Log.e(TAG, "Error testing Screen Off Anim", it) }
    }

    fun enableScreenOffFeature() {
        settingsRepository.saveScreenOffFeatureEnabled(true)
        initializeScreenOffFeature()
        showToast("Screen Off Animation enabled")
    }

    fun disableScreenOffFeature() {
        settingsRepository.saveScreenOffFeatureEnabled(false)
        initializeScreenOffFeature()
        showToast("Screen Off Animation disabled")
    }

    // ── NFC Glyph Animation ──────────────────────────────────────────────────
    fun testNfcAnimation() = lifecycleScope.launch {
        runCatching {
            glyphAnimationManager.playNfcAnimation(settingsRepository.getNfcAnimationId())
        }.onFailure { Log.e(TAG, "Error testing NFC animation", it) }
    }


    fun enableNfcFeature() {
        if (nfcAdapter == null) {
            showToast("NFC is not available on this device")
            return
        }
        if (nfcAdapter?.isEnabled == false) {
            showToast("Please enable NFC in system settings first")
            return
        }
        settingsRepository.saveNfcFeatureEnabled(true)
        initializeNfcFeature()
        enableNfcForegroundDispatch()
        showToast("NFC Glyph Animation enabled")
    }


    fun disableNfcFeature() {
        settingsRepository.saveNfcFeatureEnabled(false)
        initializeNfcFeature()
        disableNfcForegroundDispatch()
        showToast("NFC Glyph Animation disabled")
    }

    // ── Low Battery Alert ─────────────────────────────────────────────────────
    fun testLowBatteryAlert() {
        runCatching {
            startForegroundServiceCompat(
                Intent(this, LowBatteryAlertService::class.java).apply {
                    action = LowBatteryAlertService.ACTION_TEST_ALERT
                }
            )
            showToast("Testing Low Battery Alert")
        }.onFailure {
            Log.e(TAG, "Failed to test low battery alert", it)
            showToast("Failed to test alert: ${it.message}")
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────
    fun runDiagnostics() { /* ... */ }
    private fun captureLogcatText(): String = Runtime.getRuntime().exec("logcat -d").inputStream.bufferedReader().use { it.readText() }
    private fun writeLogToUri(uri: android.net.Uri) { /* ... */ }
    private fun promptShareLog(uri: android.net.Uri) { /* ... */ }

    // ── Session management ────────────────────────────────────────────────────
    private fun maybeRestoreSession() { /* ... */ }
    private fun startPersistentGlyphService() { /* ... */ }
    private fun canPerformGlyphOperation(bypass: Boolean = false): Boolean { return true /* ... */ }
    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() { /* ... */ }
    private fun cancelRunningAnimations() { animJob?.cancel(); isGlyphDemoRunning = false }
    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun showServiceDisabledToast() = showToast("Glyph service is not enabled.")
    private fun startForegroundServiceCompat(intent: Intent) { startForegroundService(intent) }

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG                   = "MainActivity"
        private const val REQ_NOTIFICATION      = 1001
        private const val STARTUP_DELAY_MS      = 100L
        private const val GUARD_TEST_DURATION_MS = 5_000L
        private const val GUARD_BLINK_MS        = 200L
    }
}


// ── MainScreen ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isNothingPhone: Boolean,
    glyphServiceEnabled: Boolean,
    onGlyphServiceToggle: (Boolean) -> Unit,
    onLaunchGlyphDemo: () -> Unit,
    onTestAllZones: (Boolean) -> Unit,
    onTestCustomPattern: (Boolean) -> Unit,
    onRunWaveAnimation: () -> Unit,
    onRunPulseEffect: () -> Unit,
    onTestGlyphGuard: () -> Unit,
    onStartGlyphGuard: () -> Unit,
    onStopGlyphGuard: () -> Unit,
    onRunBoxBreathing: () -> Unit,
    onTestPowerPeek: () -> Unit,
    onEnablePowerPeek: () -> Unit,
    onDisablePowerPeek: () -> Unit,
    onRunNotificationEffect: () -> Unit,
    onTestGlyphChannel: (Int) -> Unit,
    onTestC1Segment: (Int) -> Unit,
    onTestFinalState: () -> Unit,
    onTestC14C15Isolated: () -> Unit,

    // Pulse Lock
    onTestPulseLock: () -> Unit,
    onEnablePulseLock: () -> Unit,
    onDisablePulseLock: () -> Unit,

    // Screen Off
    onTestScreenOff: () -> Unit,
    onEnableScreenOff: () -> Unit,
    onDisableScreenOff: () -> Unit,

    // NFC
    onTestNfc: () -> Unit,
    onEnableNfc: () -> Unit,
    onDisableNfc: () -> Unit,

    onTestLowBattery: () -> Unit,
    onRunDiagnostics: () -> Unit,
    backgroundColorMain: ComposeColor,
    settingsRepository: SettingsRepository
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    /** Show a toast and invoke [action] only when the glyph service is enabled. */
    fun requireGlyphService(action: () -> Unit) {
        if (glyphServiceEnabled) action()
        else Toast.makeText(context, "Please enable the Glyph service first", Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onEnablePowerPeek()
        else Toast.makeText(context, "Permission denied. PowerPeek cannot run without it.", Toast.LENGTH_LONG).show()
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition    = { MaterialSharedAxisZ.enterTransition() },
        exitTransition     = { MaterialSharedAxisZ.exitTransition() },
        popEnterTransition = { MaterialSharedAxisZ.popEnterTransition() },
        popExitTransition  = { MaterialSharedAxisZ.popExitTransition() }
    ) {
        composable("home") {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text("Glyph Sharge", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp)) },
                        actions = {
                            IconButton(onClick = { navController.navigate("settings") }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        windowInsets = WindowInsets.statusBars,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                containerColor = backgroundColorMain,
                contentWindowInsets = WindowInsets(0.dp)
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColorMain)
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        GlyphControlCard(
                            enabled = glyphServiceEnabled,
                            onEnabledChange = onGlyphServiceToggle
                        )
                    }
                    item { HomeSectionHeader(title = "Features") }
                    item {
                        PowerPeekCard(
                            title = "Power Peek",
                            description = "Peek at your battery life with a quick shake.",
                            icon = painterResource(id = R.drawable._44),
                            onTestPowerPeek = { requireGlyphService(onTestPowerPeek) },
                            onEnablePowerPeek = {
                                requireGlyphService {
                                    when (ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)) {
                                        PackageManager.PERMISSION_GRANTED -> onEnablePowerPeek()
                                        else -> permissionLauncher.launch(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
                                    }
                                }
                            },
                            onDisablePowerPeek = { requireGlyphService(onDisablePowerPeek) },
                            modifier = Modifier.fillMaxWidth(),
                            iconSize = 32,
                            isServiceActive = glyphServiceEnabled,
                            settingsRepository = settingsRepository
                        )
                    }
                    item {
                        PulseLockCard(
                            title = "Glow Gate",
                            description = "Light up your unlock with stunning glyph animations.",
                            icon = rememberVectorPainter(image = Icons.Default.Lock),
                            modifier = Modifier.fillMaxWidth(),
                            iconSize = 32,
                            isServiceActive = glyphServiceEnabled,
                            onTestPulseLock = { requireGlyphService(onTestPulseLock) },
                            onEnablePulseLock = { requireGlyphService(onEnablePulseLock) },
                            onDisablePulseLock = onDisablePulseLock,
                            settingsRepository = settingsRepository
                        )
                    }
                    item {
                        BatteryStoryCard(
                            title = "Battery Story",
                            description = "Track your device's charging patterns and battery health.",
                            icon = rememberVectorPainter(image = Icons.Default.BatteryChargingFull),
                            onOpen = { navController.navigate("battery_story") },
                            modifier = Modifier.fillMaxWidth(),
                            iconSize = 32,
                            isServiceActive = glyphServiceEnabled,
                            settingsRepository = settingsRepository
                        )
                    }

                    item {
                        ScreenOffCard(
                            title = "Screen Off",
                            description = "Play a Glyph animation when the screen turns off.",
                            icon = rememberVectorPainter(image = Icons.Default.PowerSettingsNew),
                            modifier = Modifier.fillMaxWidth(),
                            iconSize = 32,
                            isServiceActive = glyphServiceEnabled,
                            onTestScreenOff    = { requireGlyphService(onTestScreenOff) },
                            settingsRepository = settingsRepository,
                            onEnableScreenOff = { requireGlyphService(onEnableScreenOff) },
                            onDisableScreenOff = { requireGlyphService(onDisableScreenOff) },
                        )
                    }

                    item {
                        NfcGlyphCard(
                            title = "NFC Glyph",
                            description = "Play a Glyph animation on NFC tap or contactless payment.",
                            icon = rememberVectorPainter(image = Icons.Default.Nfc),
                            modifier = Modifier.fillMaxWidth(),
                            iconSize = 32,
                            isServiceActive = glyphServiceEnabled,
                            onTestNfc    = { requireGlyphService(onTestNfc) },
                            onEnableNfc  = { requireGlyphService(onEnableNfc) },
                            onDisableNfc = { requireGlyphService(onDisableNfc) },
                            settingsRepository = settingsRepository
                        )
                    }

                    item {
                        LowBatteryAlertCard(
                            onTestAlert = onTestLowBattery,
                            modifier = Modifier.fillMaxWidth(),
                            settingsRepository = settingsRepository,
                            isServiceActive = glyphServiceEnabled
                        )
                    }
                }
            }
        }

        // ── Settings branches ─────────────────────────────────────────────────
        composable("settings") {
            SettingsScreen(
                onBackClick               = { navController.popBackStack() },
                onHiddenSettingsAccess    = { navController.navigate("hidden_settings") },
                onThemeSettingsClick      = { navController.navigate("theme_settings") },
                onFontSettingsClick       = { navController.navigate("font_settings") },
                onVibrationSettingsClick  = { navController.navigate("vibration_settings") },
                onQuietHoursSettingsClick = { navController.navigate("quiet_hours_settings") },
                settingsRepository = settingsRepository
            )
        }
        composable("theme_settings")   { ThemeSettingsScreen(onBackClick = { navController.popBackStack() }) }
        composable("font_settings")    { FontSettingsScreen(fontState = LocalFontState.current, onNavigateBack = { navController.popBackStack() }) }
        composable("hardware_diagnostics") { HardwareDiagnosticsScreen(onBackClick = { navController.popBackStack() }) }
        composable("vibration_settings") { VibrationSettingsScreen(onBackClick = { navController.popBackStack() }, settingsRepository = settingsRepository) }
        composable("quiet_hours_settings") { QuietHoursSettingsScreen(onBackClick = { navController.popBackStack() }, settingsRepository = settingsRepository) }
        composable("battery_story") { BatteryStoryScreen(onBackClick = { navController.popBackStack() }) }
    }
}

// ── Shared-axis Z transition ──────────────────────────────────────────────────
object MaterialSharedAxisZ {
    private const val ENTER_DURATION  = 200
    private const val EXIT_DURATION   = 120
    private const val SCALE_ENTER     = 0.98f
    private const val SCALE_EXIT      = 1.02f

    fun enterTransition(): EnterTransition = fadeIn(tween(ENTER_DURATION, easing = FastOutSlowInEasing)) + scaleIn(tween(ENTER_DURATION, easing = FastOutSlowInEasing), initialScale = SCALE_ENTER)
    fun exitTransition(): ExitTransition = fadeOut(tween(EXIT_DURATION, easing = EaseOut)) + scaleOut(tween(EXIT_DURATION, easing = EaseOut), targetScale = SCALE_EXIT)
    fun popEnterTransition(): EnterTransition = fadeIn(tween(ENTER_DURATION, easing = FastOutSlowInEasing)) + scaleIn(tween(ENTER_DURATION, easing = FastOutSlowInEasing), initialScale = SCALE_EXIT)
    fun popExitTransition(): ExitTransition = fadeOut(tween(EXIT_DURATION, easing = EaseOut)) + scaleOut(tween(EXIT_DURATION, easing = EaseOut), targetScale = SCALE_ENTER)
}