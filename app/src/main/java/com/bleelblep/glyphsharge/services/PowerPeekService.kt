package com.bleelblep.glyphsharge.services

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphFeature
import com.bleelblep.glyphsharge.glyph.GlyphFeatureCoordinator
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@AndroidEntryPoint
class PowerPeekService : FeatureService(), SensorEventListener {

    companion object {
        private const val TAG = "PowerPeekService"
        private const val NOTIF_CHANNEL_ID = "PowerPeekServiceChannel"
        private const val NOTIF_ID = 1013

        const val ACTION_START = "com.bleelblep.glyphsharge.POWER_PEEK_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.POWER_PEEK_STOP"

        private const val TRIGGER_COOLDOWN_MS = 5000L
    }

    /**
     * This service's own registry entry, which owns the run gate — my switch
     * and the master Glyph switch.
     */
    private val spec = FeatureSpecs.of(GlyphFeature.POWER_PEEK)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var isSensorRegistered = false
    private var lastTriggerTime = 0L

    private var isRestingOnTable = false
    private var stableStartTime = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    startListeningToSensors()
                }
                Intent.ACTION_SCREEN_ON -> {
                    stopListeningToSensors()
                }
            }
        }
    }

    // Identity

    override val isRunnable: Boolean
        get() = spec.isRunnable(settingsRepository)

    override val startAction: String get() = ACTION_START
    override val stopAction: String get() = ACTION_STOP
    override val channelId: String get() = NOTIF_CHANNEL_ID
    override val notificationId: Int get() = NOTIF_ID

    @get:StringRes
    override val channelNameRes: Int get() = R.string.power_peek_channel

    override val wakeLockTag: String get() = "GlyphSharge:PowerPeekAnimation"

    override val tag: String get() = TAG

    // Lifecycle

    override fun onFeatureCreated() {
        super.onFeatureCreated()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        registerScreenReceiver()
    }

    override fun onStartCommandAfterGate(intent: Intent?): Int {
        // The screen is usually already off when this feature is turned on at
        // night, and the trigger is the screen-off broadcast — which has by then
        // been and gone. Listening straight away is what makes the feature work
        // on the switch that started it.
        @Suppress("DEPRECATION")
        val interactive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        if (!interactive) {
            startListeningToSensors()
        }

        return START_STICKY
    }

    override fun onFeatureDestroying() {
        // Guarded by the base class's own `runCatching` convention, and by the
        // initialisation check: `onCreate` can fail before the sensor manager
        // is resolved, and an unregister against a field that was never
        // assigned throws out of `onDestroy` rather than failing the cleanup.
        //
        // The animation scope and the WakeLock are the base's to cancel, in the
        // right order. `animationJob` used to be a root job that
        // `serviceJob.cancel()` never reached, so a shake animation could
        // outlive the service that started it — still holding the strip, the
        // WakeLock and a destroyed Service.
        runCatching { unregisterReceiver(screenStateReceiver) }
        if (::sensorManager.isInitialized) stopListeningToSensors()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun startListeningToSensors() {
        if ((!isSensorRegistered) && (accelerometer != null)) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isSensorRegistered = true
            isRestingOnTable = false
            stableStartTime = 0L
        }
    }

    private fun stopListeningToSensors() {
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    /**
     * Ignores the shake unless the phone is lying still and flat on a surface.
     *
     * Power Peek is meant to catch a nudge to a phone sitting on a table, but
     * a hand-held phone produces the same horizontal acceleration every time it
     * is picked up, read, or shifted. Requiring the Z axis to sit at gravity
     * with almost no X or Y for 500 ms rules out a phone in a hand, and the
     * threshold is only consulted after that gate has been passed.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val zAbs = abs(z)
        val now = System.currentTimeMillis()

        // Gravity along Z means flat, and near-zero X/Y means not tilted. The
        // hysteresis on the way out stops a wobble from re-arming the gate
        // immediately after it drops.
        val isStillAndFlat = (zAbs in (9.0f..10.6f)) && (abs(x) < 1.5f) && (abs(y) < 1.5f)

        if (isStillAndFlat) {
            if (stableStartTime == 0L) {
                stableStartTime = now
            } else if ((now - stableStartTime) > 500L) {
                isRestingOnTable = true
            }
        } else {
            if (zAbs !in (8.0f..11.5f)) {
                isRestingOnTable = false
                stableStartTime = 0L
            }
        }
        if (!isRestingOnTable) return
        val horizontalAcceleration = sqrt(((x * x) + (y * y)).toDouble()).toFloat()
        val baseThreshold = settingsRepository.getPowerPeekThreshold()
        // Gravity is already in the vector, so the slider's threshold is
        // shifted down by it; the floor keeps a hand-held phone from firing
        // on ordinary movement even after the flatness gate passed.
        val horizontalThreshold = max(3.0f, baseThreshold - SensorManager.STANDARD_GRAVITY)

        if (horizontalAcceleration > horizontalThreshold) {
            isRestingOnTable = false
            stableStartTime = 0L
            triggerPowerPeekAnimation()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Animation

    private fun triggerPowerPeekAnimation() {
        val now = System.currentTimeMillis()
        if ((now - lastTriggerTime) < TRIGGER_COOLDOWN_MS) return

        animationScope.launch {
            if (!spec.isRunnable(settingsRepository)) return@launch

            if (settingsRepository.isCurrentlyInQuietHours()) return@launch

            try {
                // `preempt` stays false: this feature skips on a busy strip rather
                // than interrupting whoever holds it, and the default timeout is
                // the 500 ms the `acquire` call used on its own.
                //
                // The WakeLock teardown hangs off `onRelease` rather than a local
                // `finally` so it keeps its old position — after `release`, and
                // only when the strip was actually taken. A failed acquisition
                // never runs the block, so the WakeLock was never taken and
                // there is nothing to undo.
                featureCoordinator.withStrip(
                    owner = GlyphFeature.POWER_PEEK,
                    onRelease = {
                        try {
                            if (wakeLock.isHeld) wakeLock.release()
                        } catch (_: Exception) {}
                    },
                ) {
                    lastTriggerTime = System.currentTimeMillis()
                    val duration = settingsRepository.getPowerPeekDuration()

                    Log.d(TAG, "Power Peek: Horizontal shake on table detected! Duration=${duration}ms")

                    // +2s: the bar is still drawing its last frames after the cap.
                    try {
                        wakeLock.acquire(duration + 2000L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
                    }

                    glyphAnimationManager.runCapped(duration) {
                        glyphAnimationManager.playPowerPeekAnimation(
                            this@PowerPeekService,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Power Peek glyph sequence", e)
            }
        }
    }

    // Notification

    override fun buildNotification(): Notification =
        buildNotification(getString(R.string.power_peek_notif_text))

    override fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.power_peek_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable._44)
            .setOngoing(true)
            .build()
}
