package com.bleelblep.glyphsharge.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The composite "the phone, lying flat, was nudged" listener.
 *
 * Extracted from `PowerPeekService` the same way [UnlockTrigger] was extracted
 * from Pulse Lock: the accelerometer part cannot be a broadcast, so it is a
 * composite of a screen-state receiver *and* a sensor listener, armed only
 * while it can mean something.
 *
 * ### The three gates, in order
 *
 *  1. **The screen is off.** A nudge of a phone someone is looking at is not a
 *     peek — they can see the battery themselves. Sensors register on
 *     [Intent.ACTION_SCREEN_OFF] and unregister on screen-on, so the
 *     accelerometer is not draining the battery while the phone is in use.
 *  2. **The phone was resting flat for 500 ms.** Gravity along Z with almost
 *     no X or Y rules out a phone in a hand — being picked up produces the
 *     same horizontal acceleration this trigger fires on. Hysteresis on the
 *     way out (Z must leave a wider band) stops a wobble from re-arming the
 *     gate the moment it drops.
 *  3. **A horizontal jolt over the threshold.** Gravity is already in the
 *     vector, so [shakeThreshold] is shifted down by it, with a floor so a
 *     hand-held phone cannot fire on ordinary movement even past the gate.
 *
 * ### Per-rule threshold, in-memory state
 *
 * Like the low-battery threshold, the jolt threshold rides on the rule rather
 * than being read from a repository here — one rule can fire on a hard knock,
 * another on a barely-there tap, and the trigger stays free of settings.
 *
 * Everything else that was the service's own — the cooldown between fires —
 * lives on the rule too ([com.bleelblep.glyphsharge.rules.GlyphRule.cooldownMs]),
 * which is why this class holds no clock of its own beyond the 500 ms
 * rest-detection window, which is part of the *gesture*, not of the rate
 * limiting.
 */
class ShakeTrigger(
    context: Context,
    private val type: TriggerType,
    /** The raw horizontal jolt, in m/s², before the gravity shift below. */
    private val shakeThreshold: Float,
) : TriggerSource, SensorEventListener {

    companion object {
        /** Gravity along Z with almost no tilt — the phone is flat on a surface. */
        private val STILL_AND_FLAT_Z = 9.0f..10.6f

        /** Wider than the gate's own band, so a wobble does not re-arm instantly. */
        private val Z_UNCERTAIN_BAND = 8.0f..11.5f

        /** How long it must sit flat before a nudge counts as a peek. */
        private const val STABLE_MS = 500L

        private const val MAX_TILT = 1.5f

        /** After the gravity shift; a hand-held phone never reaches it. */
        private const val MIN_HORIZONTAL_THRESHOLD = 3.0f
    }

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var receiver: BroadcastReceiver? = null
    private var listening = false

    private var stableSince = 0L
    private var restingFlat = false

    override fun attach(onEvent: (TriggerEvent) -> Unit) {
        if (receiver != null) return

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    // A nudge can only mean "peek" while the screen is dark.
                    Intent.ACTION_SCREEN_OFF -> startListening()
                    Intent.ACTION_SCREEN_ON -> stopListening()
                }
            }
        }
        receiver = r

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            appContext,
            r,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        eventSink = onEvent

        // The screen is usually already off when a rule attaches at night, and
        // the screen-off broadcast has by then been and gone. Listening
        // straight away is what makes the trigger work on the reload that
        // attached it — the same reason `PowerPeekService` checked
        // `isInteractive` in `onStartCommand`.
        @Suppress("DEPRECATION")
        val interactive = (appContext.getSystemService(Context.POWER_SERVICE)
            as android.os.PowerManager).isInteractive
        if (!interactive) startListening()
    }

    override fun detach() {
        stopListening()
        val r = receiver ?: return
        receiver = null
        eventSink = null
        runCatching { appContext.unregisterReceiver(r) }
    }

    private var eventSink: ((TriggerEvent) -> Unit)? = null

    private fun startListening() {
        if (listening || accelerometer == null) return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        listening = true
        restingFlat = false
        stableSince = 0L
    }

    private fun stopListening() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val zAbs = abs(z)
        val now = System.currentTimeMillis()

        val isStillAndFlat = (zAbs in STILL_AND_FLAT_Z) && (abs(x) < MAX_TILT) && (abs(y) < MAX_TILT)

        if (isStillAndFlat) {
            if (stableSince == 0L) stableSince = now
            else if ((now - stableSince) > STABLE_MS) restingFlat = true
        } else {
            if (zAbs !in Z_UNCERTAIN_BAND) {
                restingFlat = false
                stableSince = 0L
            }
        }
        if (!restingFlat) return

        val horizontal = sqrt(((x * x) + (y * y)).toDouble()).toFloat()
        val threshold = max(MIN_HORIZONTAL_THRESHOLD, shakeThreshold - SensorManager.STANDARD_GRAVITY)
        if (horizontal > threshold) {
            restingFlat = false
            stableSince = 0L
            eventSink?.invoke(
                TriggerEvent(type, "shake while resting (horizontal ${"%.1f".format(horizontal)} > $threshold)"),
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
