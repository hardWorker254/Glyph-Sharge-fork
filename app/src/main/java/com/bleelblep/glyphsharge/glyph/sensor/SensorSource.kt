package com.bleelblep.glyphsharge.glyph.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * The phone's accelerometer, opened only while a script is actually reading
 * it, and closed again when that run ends.
 *
 * This is the first hardware source in the script layer that owns a lifecycle
 * rather than a value. `glyph.net` can be read whenever it likes because
 * answering costs one cached binder call and registers nothing. A sensor is
 * not like that: a registered listener keeps the sensor powered, wakes the
 * process on every sample, and — if nobody ever unregisters it — outlives the
 * animation that wanted it and keeps draining a battery on a phone whose
 * entire feature set is about not doing that. So the shape here is start/stop
 * with a reference count, not a getter.
 *
 * ### Why nothing is registered until something asks
 *
 * [snapshot] starts the sensor the first time it is called and [stop] is what
 * gives it back. Registration is therefore not part of running a script, it is
 * part of *reading the sensor from* one, and a script that never touches
 * `glyph.sensor` never causes a sample to be taken. The alternative — opening
 * the listener whenever any script starts — would charge every animation on
 * the phone for a feature it did not ask for.
 *
 * ### Why the count rather than a boolean
 *
 * Services are independent and two of them can be running scripts at once; the
 * charging animation and the music visualiser routinely are. With a plain
 * boolean, the first run to finish would unregister a listener the second run
 * is still reading, and that run would silently keep getting
 * [SensorSnapshot.STILL] — a script that reacts to shaking would simply never
 * react, with nothing in the studio console to say why. A count makes the
 * registration belong to the *set* of runs rather than to whichever one
 * happened to register first.
 *
 * ### Why a HandlerThread of its own
 *
 * Sensor callbacks arrive on the looper given to `registerListener`, and the
 * default for that overload is the thread that made the call — here, the VM
 * thread, which is already busy running a script and blocking on the renderer.
 * Handing the samples to a dedicated low-priority thread instead keeps the
 * listener's work off the interpreter and means a device that is slow to
 * deliver a sample delays nothing the script is drawing. It mirrors the
 * capture thread [com.bleelblep.glyphsharge.glyph.audio.PlaybackAudioSource]
 * keeps for the same reason, including the `quitSafely` teardown.
 */
@Singleton
class SensorSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SensorControl {
    private companion object {
        const val TAG = "SensorSource"

        /**
         * Roughly 50 Hz.
         *
         * `SENSOR_DELAY_GAME` is a request for the platform's game rate, which
         * is about 20 ms — five times finer than a glyph frame is long. A
         * shake is a hand moving at a few hertz, so 50 samples a second
         * describes one with room to spare, while `SENSOR_DELAY_UI` would
         * roughly halve that and `SENSOR_DELAY_NORMAL` would leave a script
         * waiting a third of a second to learn the phone moved.
         */
        const val SAMPLE_PERIOD_US = 20_000

        /**
         * What counts as a shake, in m/s².
         *
         * The accelerometer measures gravity too, so a phone lying untouched
         * already reads about 9.81 — any threshold below that would report
         * every still phone as shaken. 14 m/s² is roughly 1.4 g: above the
         * peaks a phone produces when it is set down on a desk or sits next to
         * a notification buzzing, and comfortably below the 2 g or more a
         * deliberate flick reaches on its first reversal. A lower bar would
         * leave `shaken` true whenever the phone was picked up at all, and
         * "the user touched the phone" is not a gesture worth animating.
         *
         * Compared against the magnitude rather than per axis, so turning the
         * phone over — a big change in x, y and z, no change in how hard it is
         * being accelerated — is not a shake.
         */
        const val SHAKE_THRESHOLD_MS2 = 14.0f
    }

    /**
     * The newest sample, or [SensorSnapshot.STILL] before the first one.
     *
     * An [AtomicReference] rather than a `@Volatile` field because the type
     * itself is the unit of publication: one reference write makes all five
     * values visible together, which is the whole point of the snapshot and
     * something five separate fields could not promise.
     */
    private val latest = AtomicReference(SensorSnapshot.STILL)

    /** How many runs currently want samples. See the class KDoc. */
    private val holders = AtomicInteger(0)

    /** The registered listener, or `null` while nothing is running. */
    @Volatile
    private var listener: SensorEventListener? = null

    /** The thread the listener's callbacks are delivered on. */
    @Volatile
    private var sensorThread: HandlerThread? = null

    /**
     * Latched once the device has been found to have no accelerometer.
     *
     * The manifest asks for one, but an app is installed on tablets, on
     * emulators, and on phones that shipped without the sensor, and a
     * `getDefaultSensor` returning `null` is not an error this class should
     * raise. Remembering it means [start] is a genuine no-op for the rest of
     * the process rather than a repeated lookup that can only fail again.
     */
    @Volatile
    private var noAccelerometer = false

    /**
     * Registers a listener, or counts alongside one that is already running.
     *
     * Reached only on the 0→1 edge; the platform is not asked to register
     * anything for the second and third caller, and an unbalanced pair of
     * `registerListener`/`unregisterListener` calls is how a listener ends up
     * alive after the last run that wanted it has gone.
     */
    override fun start() {
        if (holders.incrementAndGet() == 1) register()
    }

    /**
     * Gives up a reference, and tears the listener down on the last one.
     *
     * Safe in every state a caller can reach: never started, already stopped,
     * or stopped twice in a row. The count is pinned back to zero on an
     * over-release rather than left negative, so a later [start] still sees the
     * edge it needs and the class does not have to be restarted to recover.
     */
    override fun stop() {
        val remaining = holders.decrementAndGet()
        when {
            remaining == 0 -> unregister()
            remaining < 0 -> holders.compareAndSet(remaining, 0)
        }
    }

    /**
     * The newest sample, starting the sensor if nothing has yet.
     *
     * The lazy start is the reason a script that merely mentions `glyph.sensor`
     * costs nothing: the first read is the first moment anyone wanted
     * acceleration. It acquires a reference *only* when none is held, rather
     * than taking one per call, because a script in a draw loop reads this
     * sixty times a second and a reference taken per read is a reference
     * nothing can ever bring back to zero.
     */
    fun snapshot(): SensorSnapshot {
        acquireIfIdle()
        return latest.get()
    }

    /**
     * Takes the reference only if there is none outstanding, atomically.
     *
     * The `get` alone would not do: two runs starting at once would both see
     * zero and both start, and the second `stop` would leave the registration
     * alive for the rest of the process. Failing the compare and re-reading is
     * the cheap half of a spin that is taken once per run, not once per frame.
     */
    private fun acquireIfIdle() {
        while (true) {
            val held = holders.get()
            if (held > 0) return
            if (holders.compareAndSet(0, 1)) {
                register()
                return
            }
        }
    }

    /**
     * Opens the sensor. Never throws, and never leaves half of itself behind.
     *
     * Every call out to the platform is wrapped, because the alternative is a
     * foreground service being killed by something the script could not have
     * handled: a device with no [SensorManager], a manufacturer that refuses
     * the registration. A script cannot see any of it, which is the point —
     * [SensorSnapshot.STILL] is an answer it already knows how to branch on.
     *
     * Registration is visible to other threads only once it has succeeded, so
     * a failure here cannot leave [unregister] trying to detach a listener
     * that was never attached.
     */
    private fun register() {
        if (noAccelerometer) return

        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || accelerometer == null) {
            noAccelerometer = true
            Log.i(TAG, "No accelerometer on this device; glyph.sensor reports still")
            return
        }

        val thread = HandlerThread("glyph-sensor", Process.THREAD_PRIORITY_MORE_FAVORABLE)
            .also { it.start() }

        val created = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                publish(event)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            manager.registerListener(created, accelerometer, SAMPLE_PERIOD_US, Handler(thread.looper))
        }
        if (registered.isFailure) {
            // The thread has to go back even though the listener never
            // arrived; a HandlerThread started and abandoned is a thread that
            // runs until the process does.
            runCatching { thread.quitSafely() }
            Log.w(TAG, "Cannot register the accelerometer; glyph.sensor reports still", registered.exceptionOrNull())
            return
        }

        listener = created
        sensorThread = thread
    }

    /**
     * Unregisters the listener and stops its thread, and clears the sample.
     *
     * Clearing rather than leaving the last reading in place is what makes the
     * *next* run honest: a script that opens its first frame before the first
     * event arrives would otherwise be told the phone was being shaken by
     * whatever the previous animation left behind.
     *
     * The two fields are read into locals before anything is released so a
     * second `stop` racing the first cannot detach an already-detached
     * listener, and every platform call is wrapped for the same reason
     * [register] wraps its own: teardown runs on a script's exit path, which
     * is the worst possible place to throw.
     */
    private fun unregister() {
        val created = listener
        val thread = sensorThread
        listener = null
        sensorThread = null

        if (created != null) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            runCatching { manager?.unregisterListener(created) }
        }
        // `quitSafely` rather than `quit`: the listener thread may be in the
        // middle of publishing a sample, and dropping it is harmless where
        // interrupting it mid-write would not be.
        if (thread != null) runCatching { thread.quitSafely() }

        latest.set(SensorSnapshot.STILL)
    }

    /**
     * Turns one [SensorEvent] into a [SensorSnapshot] and publishes it whole.
     *
     * The event is the unit of truth: `x`, `y` and `z` are copied out of one
     * array and reduced to a magnitude before anything is stored, so a script
     * can never be handed axes from one instant and a magnitude from another.
     * Publishing the five together through a single reference write is what
     * makes that hold for the reader as well.
     */
    private fun publish(event: SensorEvent?) {
        runCatching {
            val values = event?.values ?: return
            val x = values.getOrElse(0) { 0f }
            val y = values.getOrElse(1) { 0f }
            val z = values.getOrElse(2) { 0f }
            val magnitude = sqrt(x * x + y * y + z * z)
            latest.set(
                SensorSnapshot(
                    x = x,
                    y = y,
                    z = z,
                    magnitude = magnitude,
                    shaken = magnitude > SHAKE_THRESHOLD_MS2
                )
            )
        }.onFailure {
            // A malformed event must not take the listener thread with it: a
            // thread that dies here leaves the registration alive with nothing
            // feeding it, and the script waiting on it never finds out.
            Log.w(TAG, "Dropping a malformed accelerometer sample", it)
        }
    }
}
