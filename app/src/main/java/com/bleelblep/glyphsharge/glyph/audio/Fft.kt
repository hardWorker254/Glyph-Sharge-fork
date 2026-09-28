package com.bleelblep.glyphsharge.glyph.audio

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A small iterative radix-2 FFT, enough of one to turn a block of PCM into
 * magnitudes.
 *
 * Why it is here rather than a dependency: the visualiser needs exactly one
 * transform, a few hundred times a second, on a 1024-sample block. A general
 * signal-processing library would bring a build, a proguard rule and a
 * transitive dependency tree to answer that, and the whole algorithm is small
 * enough to read in one sitting — which matters more here than breadth,
 * because a wrong constant shows up as a spectrum that looks almost right.
 *
 * Deliberately free of Android types so it can be tested against a known
 * signal: a 1 kHz sine must put its energy in the bin that corresponds to
 * 1 kHz, and silence must produce zeros rather than noise.
 */
internal object Fft {

    /** The smallest transform that resolves a musical note. */
    const val MIN_SIZE = 64

    /**
     * Per-size precomputed tables.
     *
     * The bit-reversal permutation and the twiddle factors depend only on the
     * size, and recomputing them would mean a `cos` and a `sin` per butterfly
     * — tens of thousands of transcendentals per frame, to be thrown away
     * immediately. One plan per size, reused for the life of the process.
     */
    private class Plan(size: Int) {
        val reverse = IntArray(size)
        val cosines = FloatArray(size / 2)
        val sines = FloatArray(size / 2)

        /**
         * How many bits an index of this transform has: `10` for 1024.
         *
         * Passed to [reverseBits] as the *count* of shifts, and it has to be.
         * Handed the size instead, the function shifts 1024 times on a ten-bit
         * index: the input is exhausted after ten, and the remaining shifts
         * walk every value off the end of an `Int`, so the whole permutation
         * comes back as zero. Every sample was then written to `real[0]`, the
         * last one landing on a window weight of `0`, and the transform
         * returned 512 zeros — an empty spectrum, every band on the floor, and
         * a visualiser that looks switched off with nothing in the log.
         */
        private val bitCount = Integer.numberOfTrailingZeros(size)

        init {
            for (i in 0 until size) {
                reverse[i] = reverseBits(i, bitCount)
            }
            for (k in 0 until size / 2) {
                val angle = 2.0 * Math.PI * k / size
                cosines[k] = cos(angle).toFloat()
                sines[k] = sin(angle).toFloat()
            }
        }

        private fun reverseBits(value: Int, bits: Int): Int {
            var input = value
            var output = 0
            repeat(bits) {
                output = (output shl 1) or (input and 1)
                input = input shr 1
            }
            return output
        }
    }

    private val plans = HashMap<Int, Plan>()

    /** The largest power of two that fits in [length]; the size to transform. */
    fun frameSizeFor(length: Int): Int {
        var size = MIN_SIZE
        while (size * 2 <= length) size *= 2
        return size
    }

    /**
     * Hann-windowed magnitude spectrum of [input].
     *
     * The scale is set so that a full-scale sine at a bin centre comes out at
     * 0.5 rather than 1.0, because the window is part of the answer: a Hann
     * window sums to `size / 2` and the transform is scaled by `2 / size`, so
     * the window's coherent gain cancels exactly one half. Undoing it would
     * mean dividing by a number that depends on the window, and the only
     * reader is [AudioAnalysis.pcmMagnitudeToUnit], which cares about the
     * ratio between bands rather than the absolute level. A full-scale note
     * reads as 0.5 here, and as 0.88 on the unit scale.
     *
     * @param input time-domain samples, already in `-1..1`
     * @param size the transform length, a power of two; `input` may be longer,
     *   in which case the leading [size] samples are used
     * @return magnitudes for bins `0`..`size / 2`
     */
    fun magnitudeSpectrum(input: FloatArray, size: Int = frameSizeFor(input.size)): FloatArray {
        require(size >= MIN_SIZE && size and (size - 1) == 0) {
            "FFT size must be a power of two of at least $MIN_SIZE, was $size"
        }

        val plan = plans.getOrPut(size) { Plan(size) }
        val real = FloatArray(size)
        val imaginary = FloatArray(size)

        // The window is applied before the permutation, so the maths below
        // stays a textbook transform with no extra bookkeeping.
        val usable = minOf(size, input.size)
        val denominator = (usable - 1).coerceAtLeast(1)
        for (i in 0 until usable) {
            val window = 0.5 * (1.0 - cos(2.0 * Math.PI * i / denominator))
            real[plan.reverse[i]] = (input[i] * window).toFloat()
        }

        var width = 2
        while (width <= size) {
            val half = width / 2
            val step = size / width
            var block = 0
            while (block < size) {
                var k = 0
                for (j in block until block + half) {
                    val partner = j + half
                    val c = plan.cosines[k]
                    val s = plan.sines[k]

                    // (a + bi) * e^(-2*pi*i*k/n) == (ac + bs) + i(bc - as)
                    val tRe = real[partner] * c + imaginary[partner] * s
                    val tIm = imaginary[partner] * c - real[partner] * s

                    real[partner] = real[j] - tRe
                    imaginary[partner] = imaginary[j] - tIm
                    real[j] += tRe
                    imaginary[j] += tIm

                    k += step
                }
                block += width
            }
            width *= 2
        }

        val bins = size / 2
        val magnitudes = FloatArray(bins)
        val scale = 2f / size
        for (i in 0 until bins) {
            magnitudes[i] = sqrt(real[i] * real[i] + imaginary[i] * imaginary[i]) * scale
        }
        return magnitudes
    }
}
