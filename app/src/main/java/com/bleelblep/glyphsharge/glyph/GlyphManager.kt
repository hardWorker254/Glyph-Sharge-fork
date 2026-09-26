package com.bleelblep.glyphsharge.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import com.bleelblep.glyphsharge.glyph.engine.GLYPH_MAX_BRIGHTNESS
import com.bleelblep.glyphsharge.utils.LoggingManager
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Nothing Glyph SDK session.
 *
 * This class is deliberately narrow: it binds to the system service, registers
 * the device model, and owns the open/closed session. It knows **nothing** about
 * animations — that is [com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer] and
 * [GlyphAnimationManager] — and it does not hard-code channel numbers, which
 * live in [DeviceProfileFactory].
 *
 * Following the official Nothing Glyph Developer Kit documentation:
 * https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit
 */
@Singleton
class GlyphManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "GlyphManager"

        /** Settling time between tearing the SDK down and binding it again. */
        const val RECONNECT_DELAY_MS = 1000L

        /** How long [forceEnsureSession] waits for the service to bind. */
        const val SERVICE_WAIT_MS = 2000L
        const val SERVICE_POLL_MS = 100L

        /** Errors the SDK raises when a call is made too early or too late. */
        val RECOVERABLE_ERRORS = setOf("Session not active", "Service not connected")
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** The raw SDK manager. For [com.bleelblep.glyphsharge.glyph.engine.GlyphRenderer]; `null` before [initialize]. */
    var mGM: GlyphManager? = null
        private set

    @Volatile
    private var initialized = false

    @Volatile
    private var _isSessionActive = false

    @Volatile
    private var _isServiceConnected = false

    val isSessionActive: Boolean get() = _isSessionActive

    /** Whether the system Glyph service is bound. */
    val isServiceConnected: Boolean get() = _isServiceConnected

    /** Notified whenever the session opens or closes, so the UI can mirror the SDK. */
    var onSessionStateChanged: ((Boolean) -> Unit)? = null

    private val mCallback: GlyphManager.Callback by lazy {
        object : GlyphManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                Log.d(TAG, "Glyph Service Connected")
                LoggingManager.logSessionState(
                    "SERVICE_CONNECTED",
                    "Component: ${componentName.className}",
                )
                _isServiceConnected = true
                registerDevice()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                Log.d(TAG, "Glyph Service Disconnected")
                LoggingManager.logSessionState(
                    "SERVICE_DISCONNECTED",
                    "Component: ${componentName.className}"
                )
                _isServiceConnected = false
                cleanup()
            }
        }
    }

    /**
     * Binds to the system Glyph service.
     *
     * This triggers `onServiceConnected` → `register(deviceType)` → `openSession()`,
     * as the GDK requires. Without this call the service never connects and the
     * SDK logs "Non registed" for every frame.
     */
    fun initialize() {
        if (initialized) return

        try {
            mGM = GlyphManager.getInstance(context)
            mGM?.init(mCallback)
            initialized = true
            Log.d(TAG, "Glyph Manager initialized and service binding started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Glyph Manager: ${e.message}")
            handleError(e)
        }
    }

    /** Tells the SDK which phone this is, so it knows the channel layout. */
    private fun registerDevice() {
        try {
            val deviceType = DeviceType.detect() ?: throw GlyphException("Unsupported device type")
            val registrationId = deviceType.registrationId

            mGM?.register(registrationId)
            Log.d(TAG, "Registered device type: $registrationId")
            LoggingManager.logSDKOperation(
                "DEVICE_REGISTRATION",
                "Successfully registered $registrationId"
            )
        } catch (e: GlyphException) {
            Log.e(TAG, "Failed to register device: ${e.message}")
            handleError(e)
        }
    }

    /** Closes the session, unbinds, and allows [initialize] to run again. */
    fun cleanup() {
        runCatching {
            if (_isSessionActive) {
                mGM?.closeSession()
            }
        }

        _isSessionActive = false
        _isServiceConnected = false
        initialized = false

        Log.d(TAG, "GlyphManager cleaned up")
    }

    /** `true` when the session is open and frames can be drawn. */
    fun canPerformOperation(): Boolean {
        if (!isSessionActive) {
            Log.w(TAG, "Cannot perform operation - session not active")
            return false
        }
        return true
    }

    fun isNothingPhone(): Boolean = try {
        DeviceType.detect() != null
    } catch (e: Exception) {
        Log.e(TAG, "Error checking device type: ${e.message}")
        false
    }

    fun openSession() {
        if (_isSessionActive) return

        try {
            mGM?.openSession()
            _isSessionActive = true
            onSessionStateChanged?.invoke(true)
            Log.d(TAG, "Glyph session opened")
            LoggingManager.logSessionState("SESSION_OPENED", "Successfully opened session")
        } catch (e: GlyphException) {
            Log.e(TAG, "Failed to open session: ${e.message}")
            throw e
        }
    }

    fun closeSession() {
        if (!_isSessionActive) return

        try {
            mGM?.closeSession()
            Log.d(TAG, "Glyph session closed")
            LoggingManager.logSessionState("SESSION_CLOSED", "Session closed")
        } catch (e: GlyphException) {
            Log.e(TAG, "Failed to close session: ${e.message}")
            throw e
        } finally {
            _isSessionActive = false
            onSessionStateChanged?.invoke(false)
        }
    }

    /**
     * Opens the session if it is closed, and closes it if it is open.
     * @return the new state
     */
    fun toggleGlyphService(): Boolean {
        return if (isSessionActive) {
            runCatching { closeSession() }
            false
        } else {
            runCatching { openSession() }.isSuccess
        }
    }

    /**
     * Makes sure a session exists, waiting for the system service to bind.
     *
     * Used by [GlyphFeatureCoordinator] so a feature does not have to care
     * whether the session happens to be open when its service starts.
     *
     * @return `true` if a session is open when this returns
     */
    fun forceEnsureSession(): Boolean {
        if (!_isServiceConnected) {
            Log.d(TAG, "forceEnsureSession: Service not connected, attempting connection")
            try {
                initialize()
            } catch (_: Exception) {
                // initialize() already logged; fall through to the wait below.
            }

            var waited = 0L
            while (!_isServiceConnected && (waited < SERVICE_WAIT_MS)) {
                Thread.sleep(SERVICE_POLL_MS)
                waited += SERVICE_POLL_MS
            }

            if (!_isServiceConnected) {
                Log.w(TAG, "forceEnsureSession: Service still not connected after wait")
                return false
            }
        }

        if (isSessionActive) {
            Log.d(TAG, "Session already active")
            return true
        }

        return try {
            openSession()
            Log.d(TAG, "Temporary session opened for bypass operation")
            true
        } catch (e: GlyphException) {
            Log.e(TAG, "Error opening temporary session: ${e.message}")
            false
        }
    }

    /** Turns every LED off. */
    fun turnOffAll() {
        try {
            mGM?.turnOff()
            Log.d(TAG, "All glyphs turned off")
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off all glyphs: ${e.message}")
            handleError(e)
        }
    }

    /**
     * Lights every channel at full brightness. The channel list comes from
     * [DeviceProfileFactory], so this stays correct when a new phone is added.
     */
    fun turnOnAllGlyphs() {
        if (!canPerformOperation()) return

        val channels = DeviceProfileFactory.allChannelsForConnectedDevice()
        if (channels.isEmpty()) {
            Log.w(TAG, "turnOnAllGlyphs: no channels for this device")
            return
        }

        try {
            val builder = mGM?.getGlyphFrameBuilder() ?: return
            channels.forEach { builder.buildChannel(it, GLYPH_MAX_BRIGHTNESS) }
            mGM?.toggle(builder.build())
            Log.d(TAG, "All glyphs turned on")
        } catch (e: Exception) {
            Log.e(TAG, "Error turning on all glyphs: ${e.message}")
            handleError(e)
        }
    }

    /**
     * Reports a failure and tries to get back to a usable state.
     *
     * The two errors the SDK raises for calling too early or too late are
     * recoverable with a rebind; anything else means the SDK state cannot be
     * trusted, so it is torn down.
     */
    private fun handleError(error: Exception) {
        Log.e(TAG, "Glyph error: ${error.message}")

        if (error is GlyphException && error.message in RECOVERABLE_ERRORS) {
            Log.d(TAG, "Attempting to recover from ${error.message}")
            reconnect()
            return
        }

        Log.e(TAG, "Unrecoverable Glyph error: ${error.message}")
        cleanup()
    }

    /** Tears the SDK down and binds it again after a short settling delay. */
    private fun reconnect() {
        scope.launch {
            try {
                cleanup()
                delay(RECONNECT_DELAY_MS.milliseconds)
                initialize()
                Log.d(TAG, "Reconnection attempt completed")
            } catch (e: Exception) {
                Log.e(TAG, "Reconnection failed: ${e.message}")
            }
        }
    }
}
