package com.bleelblep.glyphsharge

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.bleelblep.glyphsharge.glyph.*
import com.bleelblep.glyphsharge.services.*
import com.bleelblep.glyphsharge.ui.components.WatermarkBox
import com.bleelblep.glyphsharge.ui.navigation.GlyphNavHost
import com.bleelblep.glyphsharge.ui.screens.applyLocale
import com.bleelblep.glyphsharge.ui.screens.home.HomeActions
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.utils.WatermarkHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import com.bleelblep.glyphsharge.data.SettingsRepository
import kotlin.time.Duration.Companion.milliseconds

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

    private val _glyphServiceState = mutableStateOf(false)
    val glyphServiceState: State<Boolean> = _glyphServiceState

    private lateinit var createLogFileLauncher: androidx.activity.result.ActivityResultLauncher<String>

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


        requestNotificationPermission()

        createLogFileLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri -> uri?.let { writeLogToUri(it) } }

        configureWindow()
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
        initializeGlyphService()
        initServiceByPref(PowerPeekService::class.java, settingsRepository.isPowerPeekEnabled())
        initServiceByPref(LowBatteryAlertService::class.java, settingsRepository.isLowBatteryEnabled())
        initServiceByPref(QuietHoursService::class.java, settingsRepository.isQuietHoursEnabled())
        initServiceByPref(ChargingAnimationService::class.java, settingsRepository.isChargingAnimationEnabled())
        initServiceByPref(PulseLockService::class.java, settingsRepository.isPulseLockEnabled())
        initServiceByPref(ScreenOffGlyphService::class.java, settingsRepository.isScreenOffFeatureEnabled())
        initServiceByPref(NfcGlyphService::class.java, settingsRepository.isNfcFeatureEnabled())
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
                delay(STARTUP_DELAY_MS.milliseconds)
                if (!glyphManager.isSessionActive) toggleGlyphService(true)
                else _glyphServiceState.value = true
            }
        } else {
            _glyphServiceState.value = glyphManager.isSessionActive
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
        if (!settingsRepository.isNfcFeatureEnabled()) return
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

    // ── UI ────────────────────────────────────────────────────────────────────
    private fun setupUI() {
        setContent {
            GlyphZenTheme(themeState = themeState, fontState = fontState) {
                val bgColor = MaterialTheme.colorScheme.background
                WatermarkBox(enabled = false, text = "TESTING", alpha = 0.5f, fontSize = 20.sp) {
                    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
                        GlyphNavHost(
                            glyphServiceEnabled = glyphServiceState.value,
                            onGlyphServiceToggle = ::toggleGlyphService,
                            actions = HomeActions(
                                onTestPowerPeek = ::testPowerPeek,
                                onEnablePowerPeek = ::enablePowerPeek,
                                onDisablePowerPeek = ::disablePowerPeek,
                                onTestPulseLock = ::testPulseLock,
                                onEnablePulseLock = ::enablePulseLock,
                                onDisablePulseLock = ::disablePulseLock,
                                onTestScreenOff = ::testScreenOffAnimation,
                                onEnableScreenOff = ::enableScreenOffFeature,
                                onDisableScreenOff = ::disableScreenOffFeature,
                                onTestNfc = ::testNfcAnimation,
                                onEnableNfc = ::enableNfcFeature,
                                onDisableNfc = ::disableNfcFeature,
                                onTestChargingAnimation = ::testChargingAnimation,
                                onEnableChargingAnimation = ::enableChargingAnimation,
                                onDisableChargingAnimation = ::disableChargingAnimation,
                                onTestLowBattery = ::testLowBattery,
                                onEnableLowBattery = ::onEnableLowBattery,
                                onDisableLowBattery = ::onDisableLowBattery
                            ),
                            settingsRepository = settingsRepository
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
                // I can't make this function Composable -> context.getString(...)
                showToast(if (newState) applicationContext.getString(R.string.glyph_service_start)
                    else applicationContext.getString(R.string.glyph_service_stop))
                syncServicesAfterToggle(newState)
            } else {
                _glyphServiceState.value = glyphManager.isSessionActive
                // The same situation
                showToast(if (enabled) applicationContext.getString(R.string.glyph_service_fstart)
                    else applicationContext.getString(R.string.glyph_service_fstop))
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
            initializeServices()
        } else {
            runCatching { startService(Intent(this, PowerPeekService::class.java).apply        { action = PowerPeekService.ACTION_STOP }) }
            runCatching { startService(Intent(this, LowBatteryAlertService::class.java).apply   { action = LowBatteryAlertService.ACTION_STOP }) }
            runCatching { startService(Intent(this, QuietHoursService::class.java).apply         { action = QuietHoursService.ACTION_STOP }) }
            runCatching { startService(Intent(this, PulseLockService::class.java).apply        { action = PulseLockService.ACTION_STOP }) }
            runCatching { startService(Intent(this, ScreenOffGlyphService::class.java).apply   { action = ScreenOffGlyphService.ACTION_STOP }) }
            runCatching { startService(Intent(this, NfcGlyphService::class.java).apply         { action = NfcGlyphService.ACTION_STOP }) }
            runCatching { startService(Intent(this, ChargingAnimationService::class.java).apply { action = ChargingAnimationService.ACTION_STOP }) }
            stopService(fgIntent)
        }
    }

    // ── PowerPeek ─────────────────────────────────────────────────────────────
    fun testPowerPeek() {
        showToast("Testing Power Peek")
        lifecycleScope.launch {
            glyphAnimationManager.playPowerPeekAnimation(this@MainActivity) {}
        }
    }

    fun enablePowerPeek() {
        settingsRepository.savePowerPeekEnabled(true)
        initServiceByPref(PowerPeekService::class.java, settingsRepository.isPowerPeekEnabled())
        showToast("PowerPeek enabled! Shake when screen is off to see battery %.")
    }

    fun disablePowerPeek() {
        settingsRepository.savePowerPeekEnabled(false)
        stopService(Intent(this, PowerPeekService::class.java))
        showToast("PowerPeek disabled")
    }

    // ── Charging Animation ────────────────────────────────────────────────────
    fun testChargingAnimation() {
        showToast("Testing Charging Animation")
        lifecycleScope.launch {
            glyphAnimationManager.playChargingAnimationAnimation(this@MainActivity) {}
        }
    }

    fun enableChargingAnimation() {
        settingsRepository.saveChargingAnimationEnabled(true)
        initServiceByPref(ChargingAnimationService::class.java, settingsRepository.isChargingAnimationEnabled())
        showToast("Charging Animation enabled")
    }

    fun disableChargingAnimation() {
        settingsRepository.saveChargingAnimationEnabled(false)
        stopService(Intent(this, ChargingAnimationService::class.java))
        showToast("Charging Animation disabled")
    }

    // ── Glow Gate (PulseLock) ─────────────────────────────────────────────────
    fun testPulseLock() = lifecycleScope.launch {
        showToast("Testing Pulse Lock")
        lifecycleScope.launch {
            glyphAnimationManager.playPulseLockAnimation()
        }
    }

    fun enablePulseLock() {
        settingsRepository.savePulseLockEnabled(true)
        initServiceByPref(PulseLockService::class.java, settingsRepository.isPulseLockEnabled())
        showToast("Glow Gate enabled")
    }

    fun disablePulseLock() {
        settingsRepository.savePulseLockEnabled(false)
        stopService(Intent(this, PulseLockService::class.java))
        showToast("Glow Gate disabled")
    }

    // ── Screen Off Animation ──────────────────────────────────────────
    fun testScreenOffAnimation() = lifecycleScope.launch {
        showToast("Testing Screen Off")
        lifecycleScope.launch {
            glyphAnimationManager.playScreenOffAnimation()
        }
    }

    fun enableScreenOffFeature() {
        settingsRepository.saveScreenOffFeatureEnabled(true)
        initServiceByPref(ScreenOffGlyphService::class.java, settingsRepository.isScreenOffFeatureEnabled())
        showToast("Screen Off Animation enabled")
    }

    fun disableScreenOffFeature() {
        settingsRepository.saveScreenOffFeatureEnabled(false)
        stopService(Intent(this, ScreenOffGlyphService::class.java))
        showToast("Screen Off Animation disabled")
    }

    // ── NFC Glyph Animation ──────────────────────────────────────────────────
    fun testNfcAnimation() = lifecycleScope.launch {
        showToast("Testing NFC")
        lifecycleScope.launch {
            glyphAnimationManager.playNfcAnimation()
        }
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
        initServiceByPref(NfcGlyphService::class.java, settingsRepository.isNfcFeatureEnabled())
        enableNfcForegroundDispatch()
        showToast("NFC Glyph Animation enabled")
    }

    fun disableNfcFeature() {
        settingsRepository.saveNfcFeatureEnabled(false)
        stopService(Intent(this, NfcGlyphService::class.java))
        disableNfcForegroundDispatch()
        showToast("NFC Glyph Animation disabled")
    }

    // ── Low Battery Alert ─────────────────────────────────────────────────────
    fun testLowBattery() {
        showToast("Testing Power Peek")
        lifecycleScope.launch {
            glyphAnimationManager.playLowBatteryAnimation()
        }
    }

    fun onEnableLowBattery() {
        settingsRepository.saveLowBatteryEnabled(true)
        initServiceByPref(LowBatteryAlertService::class.java, settingsRepository.isLowBatteryEnabled())
        showToast("Low Battery Alert enabled")
    }

    fun onDisableLowBattery() {
        settingsRepository.saveLowBatteryEnabled(false)
        stopService(Intent(this, LowBatteryAlertService::class.java))
        showToast("Low Battery Alert disabled")
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────
    private fun writeLogToUri(uri: android.net.Uri) { /* ... */ }

    // ── Session management ────────────────────────────────────────────────────
    private fun maybeRestoreSession() { /* ... */ }
    private fun startPersistentGlyphService() { /* ... */ }
    @SuppressLint("BatteryLife")
    private fun cancelRunningAnimations() { animJob?.cancel(); isGlyphDemoRunning = false }
    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun startForegroundServiceCompat(intent: Intent) { startForegroundService(intent) }

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG                   = "MainActivity"
        private const val REQ_NOTIFICATION      = 1001
        private const val STARTUP_DELAY_MS      = 100L
    }
}
