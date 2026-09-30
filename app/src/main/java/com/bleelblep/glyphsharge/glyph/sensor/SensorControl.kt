package com.bleelblep.glyphsharge.glyph.sensor

/**
 * Starts and stops whatever is feeding a script its accelerometer.
 *
 * Separate from the reader on purpose. A [com.bleelblep.glyphsharge.glyph.script
 * .ScriptSession] that could only be *given* a reading would have no way to
 * express "I do not want the sensor running", and a test would have to build
 * a real sensor to say so. Splitting the pair lets a test hand in a recording
 * fake and assert that a `require` really did — or really did not — start
 * anything, which is the only evidence available without a device.
 *
 * The only implementation in the app is [SensorSource]; [NONE] stands in for
 * "this session has nothing to start", which is the state of every session
 * that is not driving a real phone.
 */
interface SensorControl {

    /**
     * Registers a listener, taking a reference on it.
     *
     * Counting rather than setting a flag, because two script runs can overlap
     * — the charging animation and the music visualiser routinely are — and a
     * plain boolean would let one run's teardown pull the sensor out from
     * under the other. A second `start` while one is live must do nothing but
     * count.
     */
    fun start()

    /**
     * Gives up a reference, unregistering only when the last one goes.
     *
     * Must be safe to call when nothing was started and safe to call twice.
     */
    fun stop()

    companion object {
        /**
         * A control that does nothing at all.
         *
         * The default for every session that was not handed a real one, so
         * `glyph.sensor` can be required by a script and answered with
         * [SensorSnapshot.STILL] rather than failing to construct. This is what
         * every unit test gets unless it asks for something else.
         */
        val NONE: SensorControl = object : SensorControl {
            override fun start() = Unit
            override fun stop() = Unit

            override fun toString(): String = "SensorControl.NONE"
        }
    }
}
