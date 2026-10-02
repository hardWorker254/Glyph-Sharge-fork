package com.bleelblep.glyphsharge.glyph.sensor

/**
 * One accelerometer reading, the four numbers a script can use and whether it
 * counts as a shake.
 *
 * ### Why one snapshot and not four separate reads
 *
 * The obvious alternative is to hand a script a function per axis and let it
 * ask `x`, then `y`, then `z`. That is wrong in a way that only shows up while
 * the phone is in the user's hand, because an accelerometer does not report a
 * stationary value — it reports a *stream*, and four separate reads are four
 * different instants of a signal that is by definition moving.
 *
 * A script that reads x across one call and y across another is not reading a
 * vector, it is reading a smear: a `sensors.x > 2 and sensors.y > 2` test
 * could be satisfied by two separate flicks a tenth of a second apart and
 * answered true for a shake that never happened, or, worse, be answered false
 * for a single flick that did, and neither failure is reproducible enough for
 * an author to chase. Every value in one object is derived from the same
 * [android.hardware.SensorEvent], so the script gets a direction and a
 * magnitude that genuinely belong together — and the gesture it is trying to
 * recognise is a gesture in one instant, not a sequence.
 *
 * The cost is that a script reading two *different fields* still gets two
 * samples, one per field access, exactly as it does for `glyph.net`. That is
 * the price of being live and it is the right trade: within one value the
 * numbers agree, and a script that needs a decision made from several fields
 * should take it on the one that matters rather than on a pile of near
 * simultaneous ones.
 *
 * ### The zeros
 *
 * [STILL] is not what a phone lying on a table measures — see [SensorSource],
 * where a resting phone reads about one g because gravity is in the reading
 * too. Zeros mean *there is no reading*, and they are what a script gets on a
 * device with no accelerometer, on one whose listener could not be registered,
 * and before the first event has arrived. A zero magnitude is not a
 * physically meaningful acceleration; it is a state a script can branch on
 * without having to distinguish "the phone is perfectly still" from "I could
 * not tell", and only the second of those can be acted on.
 *
 * The script never sees this type: `glyph.sensor` exposes the five values as
 * plain Lua numbers and a boolean, in the same way [com.bleelblep.glyphsharge
 * .glyph.net.NetworkSnapshot] is never named on the other side either.
 */
data class SensorSnapshot(
    /** Acceleration along the phone's short axis, in m/s², gravity included. */
    val x: Float,
    /** Acceleration along the phone's long axis, in m/s², gravity included. */
    val y: Float,
    /** Acceleration perpendicular to the screen, in m/s², gravity included. */
    val z: Float,
    /**
     * How hard the phone is being accelerated, in m/s², whatever the direction.
     *
     * The number a tilt-detection script actually wants: a phone turning over
     * changes x, y and z without changing this, so `magnitude > still` is a
     * movement where `x` has changed is not.
     */
    val magnitude: Float,
    /** `true` when [magnitude] has risen past the shake threshold. */
    val shaken: Boolean,
) {
    companion object {
        /**
         * What a script sees when there is nothing to report.
         *
         * Zeros rather than an error or a `nil` field, so a script that loops
         * on `sensor.shaken` behaves on a phone that has no accelerometer
         * instead of erroring out — the same reasoning as
         * [com.bleelblep.glyphsharge.glyph.net.NetworkSnapshot.DISCONNECTED]:
         * "nothing is happening" is the answer a script can already act on.
         */
        val STILL = SensorSnapshot(
            x = 0f,
            y = 0f,
            z = 0f,
            magnitude = 0f,
            shaken = false,
        )
    }
}
