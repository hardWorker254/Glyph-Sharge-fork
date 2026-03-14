package com.bleelblep.glyphsharge.glyph

import android.content.Context
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.GlyphFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import com.bleelblep.glyphsharge.data.SettingsRepository

/**
 * Manager class for creating and running glyph animations.
 * Includes comprehensive animations with proper phone model support.
 */
@Singleton
class GlyphAnimationManager @Inject constructor(
    private val glyphManager: GlyphManager,
    private val settingsRepository: SettingsRepository
) {
    private val TAG = "GlyphAnimationManager"
    private var isAnimationRunning = false
    private val animationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private companion object {
        const val DEFAULT_MAX_BRIGHTNESS = 4000
        const val CLEANUP_DELAY = 100L
        const val PULSE_ON_DURATION = 300L
        const val PULSE_OFF_DURATION = 300L
        const val BATTERY_STEP_DELAY = 50L
        const val BATTERY_FILL_STEP_DELAY = 50L
        const val WAVE_PHONE1_STEP = 150L
        const val WAVE_PHONE2_STEP = 100L
        const val WAVE_PHONE2A_STEP = 80L
        const val WAVE_PHONE3A_STEP = 80L
    }

    // Settings for animation
    private var maxBrightness = DEFAULT_MAX_BRIGHTNESS

    // Cached segment lists for Phone 2
    private val phone2Segments by lazy {
        aSegments + bSegments + c1Segments + cOtherSegments + eSegments + dSegments
    }

    // Cached segment lists for Phone 3a
    private val phone3aCSegments by lazy {
        (Phone3a.C_START until Phone3a.C_START + 20).toList()
    }
    private val phone3aASegments by lazy {
        (Phone3a.A_START until Phone3a.A_START + 11).toList()
    }
    private val phone3aBSegments by lazy {
        (Phone3a.B_START until Phone3a.B_START + 5).toList()
    }
    private val phone3aAllSegments by lazy {
        phone3aCSegments + phone3aASegments + phone3aBSegments
    }

    // Phone (1) segments
    private object Phone1 {
        const val A = 0
        const val B = 1
        const val C_START = 2
        const val E = 6
        const val D_START = 7
    }

    // Phone (2) segments - corrected mapping
    private val c1Segments = listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
    private val aSegments = listOf(0, 1)
    private val bSegments = listOf(2)
    private val cOtherSegments = listOf(19, 20, 21, 22, 23)
    private val dSegments = listOf(25, 26, 27, 28, 29, 30, 31, 32)
    private val eSegments = listOf(24)

    // Phone (2a/2a+) segments
    private object Phone2a {
        const val C_START = 0
        const val B = 24
        const val A = 25
    }

    // Phone (3a/3a Pro) segments - CORRECTED based on official documentation
    private object Phone3a {
        const val C_START = 0
        const val A_START = 20
        const val B_START = 31
    }

    /**
     * Reset all glyphs by turning them off
     */
    private fun resetGlyphs() {
        try {
            glyphManager.mGM?.getGlyphFrameBuilder()?.build()?.let {
                glyphManager.mGM?.toggle(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting glyphs: ${e.message}")
        }
    }

    /**
     * Ensure proper cleanup when animation is interrupted or completed
     */
    private suspend fun ensureCleanup() {
        try {
            isAnimationRunning = false
            glyphManager.mGM?.turnOff()
            delay(CLEANUP_DELAY)
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    /**
     * Stop any currently running animations
     */
    fun stopAnimations() {
        isAnimationRunning = false
        try {
            glyphManager.turnOffAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping animations: ${e.message}")
        }
    }

    /**
     * Helper method to build and toggle a frame
     */
    private suspend fun buildAndToggleFrame(
        builder: GlyphFrame.Builder,
        delayOn: Long = 0L,
        delayOff: Long = 0L,
        turnOffAfter: Boolean = true
    ) {
        try {
            glyphManager.mGM?.toggle(builder.build())
            if (delayOn > 0) delay(delayOn)
            if (turnOffAfter) {
                glyphManager.turnOffAll()
                if (delayOff > 0) delay(delayOff)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in buildAndToggleFrame: ${e.message}")
            if (delayOn > 0) delay(delayOn)
        }
    }

    /**
     * Helper method to create builder with channels
     */
    private fun createFrameBuilder(
        channels: List<Int>,
        brightness: Int = maxBrightness
    ): GlyphFrame.Builder? {
        return glyphManager.mGM?.getGlyphFrameBuilder()?.apply {
            channels.forEach { buildChannel(it, brightness) }
        }
    }

    /**
     * Run a wave animation across the phone's glyph segments
     */
    suspend fun runWaveAnimation() {
        if (!isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            when {
                Common.is20111() -> runPhone1WaveAnimation()
                Common.is22111() -> runPhone2WaveAnimation()
                Common.is23111() || Common.is23113() -> runPhone2aWaveAnimation()
                Common.is24111() -> runPhone3aWaveAnimation()
                else -> runDefaultWaveAnimation()
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private suspend fun runPhone1WaveAnimation() {
        val segments = listOf(
            Phone1.A, Phone1.B,
            Phone1.C_START, Phone1.C_START + 1, Phone1.C_START + 2, Phone1.C_START + 3,
            Phone1.E,
            Phone1.D_START, Phone1.D_START + 1, Phone1.D_START + 2, Phone1.D_START + 3,
            Phone1.D_START + 4, Phone1.D_START + 5, Phone1.D_START + 6, Phone1.D_START + 7
        )

        for (segment in segments) {
            if (!isAnimationRunning) break
            createFrameBuilder(listOf(segment))?.let {
                buildAndToggleFrame(it, WAVE_PHONE1_STEP, 50L)
            }
        }
    }

    private suspend fun runPhone2WaveAnimation() {
        for (segment in phone2Segments) {
            if (!isAnimationRunning) break
            createFrameBuilder(listOf(segment))?.let {
                buildAndToggleFrame(it, WAVE_PHONE2_STEP, 30L)
            }
        }
    }

    private suspend fun runPhone2aWaveAnimation() {
        // Wave through C segments
        for (i in 0 until 24) {
            if (!isAnimationRunning) break
            createFrameBuilder(listOf(Phone2a.C_START + i))?.let {
                buildAndToggleFrame(it, WAVE_PHONE2A_STEP, 30L)
            }
        }
        // A and B segments
        listOf(Phone2a.A, Phone2a.B).forEach { segment ->
            if (!isAnimationRunning) return@forEach
            createFrameBuilder(listOf(segment))?.let {
                buildAndToggleFrame(it, WAVE_PHONE2A_STEP * 2, 50L)
            }
        }
    }

    private suspend fun runPhone3aWaveAnimation() {
        // C segments wave
        for (i in 0 until 20) {
            if (!isAnimationRunning) break
            createFrameBuilder(listOf(Phone3a.C_START + i))?.let {
                buildAndToggleFrame(it, WAVE_PHONE3A_STEP, 25L)
            }
        }
        // A segments wave
        for (i in 0 until 11) {
            if (!isAnimationRunning) break
            createFrameBuilder(listOf(Phone3a.A_START + i))?.let {
                buildAndToggleFrame(it, WAVE_PHONE3A_STEP + 20L, 30L)
            }
        }
        // B segments wave
        for (i in 0 until 5) {
            if (!isAnimationRunning) break
            createFrameBuilder(listOf(Phone3a.B_START + i))?.let {
                buildAndToggleFrame(it, WAVE_PHONE3A_STEP + 40L, 40L)
            }
        }
    }

    private suspend fun runDefaultWaveAnimation() {
        delay(1000L)
    }

    /**
     * Beedah Animation - Wave animation where glyphs stay lit after the wave passes
     */
    suspend fun runBeedahAnimation() {
        if (!isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            when {
                Common.is20111() -> runPhone1BeedahAnimation()
                Common.is22111() -> runPhone2BeedahAnimation()
                Common.is23111() || Common.is23113() -> runPhone2aBeedahAnimation()
                Common.is24111() -> runPhone3aBeedahAnimation()
                else -> runDefaultBeedahAnimation()
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private suspend fun runPhone1BeedahAnimation() {
        val segments = listOf(
            Phone1.A, Phone1.B,
            Phone1.C_START, Phone1.C_START + 1, Phone1.C_START + 2, Phone1.C_START + 3,
            Phone1.E,
            Phone1.D_START, Phone1.D_START + 1, Phone1.D_START + 2, Phone1.D_START + 3,
            Phone1.D_START + 4, Phone1.D_START + 5, Phone1.D_START + 6, Phone1.D_START + 7
        )
        runBeedahAnimationForSegments(segments, WAVE_PHONE1_STEP)
    }

    private suspend fun runPhone2BeedahAnimation() {
        runBeedahAnimationForSegments(phone2Segments, WAVE_PHONE2_STEP)
    }

    private suspend fun runPhone2aBeedahAnimation() {
        val litSegments = mutableSetOf<Int>()
        val stepDuration = WAVE_PHONE2A_STEP

        // Wave through C segments
        for (i in 0 until 24) {
            if (!isAnimationRunning) break
            litSegments.add(Phone2a.C_START + i)
            createFrameBuilder(litSegments.toList())?.let {
                try {
                    glyphManager.mGM?.toggle(it.build())
                    delay(stepDuration)
                } catch (e: Exception) {
                    delay(stepDuration)
                }
            }
        }
        // A and B segments
        listOf(Phone2a.A, Phone2a.B).forEach { segment ->
            if (!isAnimationRunning) return@forEach
            litSegments.add(segment)
            createFrameBuilder(litSegments.toList())?.let {
                try {
                    glyphManager.mGM?.toggle(it.build())
                    delay(stepDuration * 2)
                } catch (e: Exception) {
                    delay(stepDuration * 2)
                }
            }
        }
        // Pulse phase
        runBeedahPulsePhase(litSegments)
    }

    private suspend fun runPhone3aBeedahAnimation() {
        val litSegments = mutableSetOf<Int>()
        val stepDuration = WAVE_PHONE3A_STEP

        // C segments wave
        for (i in 0 until 20) {
            if (!isAnimationRunning) break
            litSegments.add(Phone3a.C_START + i)
            createFrameBuilder(litSegments.toList())?.let {
                try {
                    glyphManager.mGM?.toggle(it.build())
                    delay(stepDuration)
                } catch (e: Exception) {
                    delay(stepDuration)
                }
            }
        }
        // A segments wave
        for (i in 0 until 11) {
            if (!isAnimationRunning) break
            litSegments.add(Phone3a.A_START + i)
            createFrameBuilder(litSegments.toList())?.let {
                try {
                    glyphManager.mGM?.toggle(it.build())
                    delay(stepDuration + 20L)
                } catch (e: Exception) {
                    delay(stepDuration + 20L)
                }
            }
        }
        // B segments wave
        for (i in 0 until 5) {
            if (!isAnimationRunning) break
            litSegments.add(Phone3a.B_START + i)
            createFrameBuilder(litSegments.toList())?.let {
                try {
                    glyphManager.mGM?.toggle(it.build())
                    delay(stepDuration + 40L)
                } catch (e: Exception) {
                    delay(stepDuration + 40L)
                }
            }
        }
        // Pulse phase
        runBeedahPulsePhase(litSegments)
    }

    /**
     * Helper method for Beedah animation wave phase
     */
    private suspend fun runBeedahAnimationForSegments(segments: List<Int>, stepDuration: Long) {
        val litSegments = mutableSetOf<Int>()

        // Wave phase
        for (segment in segments) {
            if (!isAnimationRunning) break
            litSegments.add(segment)
            createFrameBuilder(litSegments.toList())?.let {
                try {
                    glyphManager.mGM?.toggle(it.build())
                    delay(stepDuration)
                } catch (e: Exception) {
                    delay(stepDuration)
                }
            }
        }
        // Pulse phase
        runBeedahPulsePhase(litSegments)
    }

    /**
     * Helper method for Beedah pulse phase
     */
    private suspend fun runBeedahPulsePhase(litSegments: Set<Int>) {
        repeat(3) {
            if (!isAnimationRunning) return@repeat
            try {
                glyphManager.turnOffAll()
                delay(PULSE_OFF_DURATION)
                createFrameBuilder(litSegments.toList())?.let {
                    glyphManager.mGM?.toggle(it.build())
                }
                delay(PULSE_ON_DURATION)
            } catch (e: Exception) {
                delay(PULSE_ON_DURATION)
            }
        }
    }

    private suspend fun runDefaultBeedahAnimation() {
        delay(2000L)
    }

    /**
     * Phone 3a Spiral Animation
     */
    suspend fun runPhone3aSpiralAnimation() {
        if (!Common.is24111() || !glyphManager.isNothingPhone()) return
        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            val stepDuration = 60L

            // Phase 1: Spiral through C segments
            for (i in phone3aCSegments.indices) {
                if (!isAnimationRunning) break
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                for (j in 0..i) {
                    val brightness = if (j == i) maxBrightness
                    else (maxBrightness * (0.3f + (j.toFloat() / i) * 0.4f)).toInt()
                    builder.buildChannel(phone3aCSegments[j], brightness)
                }
                glyphManager.mGM?.toggle(builder.build())
                delay(stepDuration)
            }

            // Phase 2: Transition to A segments
            for (i in phone3aASegments.indices) {
                if (!isAnimationRunning) break
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                phone3aCSegments.forEach {
                    builder.buildChannel(it, (maxBrightness * 0.3f).toInt())
                }
                for (j in 0..i) {
                    val brightness = if (j == i) maxBrightness
                    else (maxBrightness * (0.5f + (j.toFloat() / i) * 0.5f)).toInt()
                    builder.buildChannel(phone3aASegments[j], brightness)
                }
                glyphManager.mGM?.toggle(builder.build())
                delay(stepDuration + 10L)
            }

            // Phase 3: Culmination with B segments
            for (i in phone3aBSegments.indices) {
                if (!isAnimationRunning) break
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                phone3aCSegments.forEach {
                    builder.buildChannel(it, (maxBrightness * 0.4f).toInt())
                }
                phone3aASegments.forEach {
                    builder.buildChannel(it, (maxBrightness * 0.7f).toInt())
                }
                for (j in 0..i) {
                    builder.buildChannel(phone3aBSegments[j], maxBrightness)
                }
                glyphManager.mGM?.toggle(builder.build())
                delay(stepDuration + 20L)
            }

            // Phase 4: Final flash
            createFrameBuilder(phone3aAllSegments)?.let {
                glyphManager.mGM?.toggle(it.build())
                delay(500L)
                glyphManager.turnOffAll()
                delay(200L)
                glyphManager.mGM?.toggle(it.build())
                delay(300L)
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    /**
     * Create a pulsing breathing effect
     */
    suspend fun runPulseEffect(cycles: Int = 3) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()

            val segmentsToLight = when {
                Common.is20111() -> listOf(Phone1.A, Phone1.B, Phone1.E)
                Common.is22111() -> aSegments + bSegments + eSegments
                Common.is23111() || Common.is23113() -> listOf(Phone2a.A, Phone2a.B)
                Common.is24111() -> listOf(Phone3a.A_START + 5, Phone3a.B_START + 2, Phone3a.C_START + 9)
                else -> emptyList()
            }

            if (segmentsToLight.isEmpty()) return

            createFrameBuilder(segmentsToLight)?.let { frame ->
                repeat(cycles) {
                    if (!isAnimationRunning) return@repeat
                    try {
                        glyphManager.mGM?.toggle(frame.build())
                        delay(250L)
                        glyphManager.turnOffAll()
                        delay(250L)
                    } catch (e: Exception) {
                        delay(250L)
                    }
                }
            }
        } finally {
            ensureCleanup()
        }
    }

    /**
     * Run a notification effect (quick flash pattern)
     */
    suspend fun runNotificationEffect() {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()

            repeat(2) {
                if (!isAnimationRunning) return@repeat
                try {
                    val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return
                    buildAllChannels(builder)
                    glyphManager.mGM?.toggle(builder.build())
                    delay(1000L)
                    glyphManager.turnOffAll()
                    delay(500L)
                } catch (e: Exception) {
                    delay(200L)
                }
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    /**
     * Helper method to build all channels for current device
     */
    private fun buildAllChannels(builder: GlyphFrame.Builder) {
        when {
            Common.is22111() -> {
                (aSegments + bSegments + c1Segments + cOtherSegments + dSegments + eSegments)
                    .forEach { builder.buildChannel(it, maxBrightness) }
            }
            Common.is20111() -> {
                listOf(Phone1.A, Phone1.B, Phone1.E).forEach { builder.buildChannel(it, maxBrightness) }
                (0..3).forEach { builder.buildChannel(Phone1.C_START + it, maxBrightness) }
                (0..7).forEach { builder.buildChannel(Phone1.D_START + it, maxBrightness) }
            }
            Common.is23111() || Common.is23113() -> {
                (0 until 24).forEach { builder.buildChannel(Phone2a.C_START + it, maxBrightness) }
                builder.buildChannel(Phone2a.A, maxBrightness)
                builder.buildChannel(Phone2a.B, maxBrightness)
            }
            Common.is24111() -> {
                (0 until 20).forEach { builder.buildChannel(Phone3a.C_START + it, maxBrightness) }
                (0 until 11).forEach { builder.buildChannel(Phone3a.A_START + it, maxBrightness) }
                (0 until 5).forEach { builder.buildChannel(Phone3a.B_START + it, maxBrightness) }
            }
        }
    }

    /**
     * Test individual Glyph channel
     */
    suspend fun testGlyphChannel(channelIndex: Int, bypassServiceCheck: Boolean = false) {
        if (!isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        val actualChannels = mapChannelIndexToHardwareChannels(channelIndex)
        if (actualChannels.isEmpty()) return

        isAnimationRunning = true
        try {
            glyphManager.mGM?.turnOff()
            delay(200L)

            repeat(3) {
                if (!isAnimationRunning) return@repeat
                try {
                    createFrameBuilder(actualChannels)?.let {
                        glyphManager.mGM?.toggle(it.build())
                        delay(300L)
                        glyphManager.mGM?.turnOff()
                        delay(200L)
                    }
                } catch (e: Exception) {
                    glyphManager.mGM?.turnOff()
                    delay(500L)
                }
            }
        } finally {
            isAnimationRunning = false
            glyphManager.mGM?.turnOff()
            delay(100L)
        }
    }

    /**
     * Map UI channel index to actual hardware channel numbers
     */
    private fun mapChannelIndexToHardwareChannels(channelIndex: Int): List<Int> {
        return when {
            Common.is22111() -> mapPhone2Channels(channelIndex)
            Common.is20111() -> mapPhone1Channels(channelIndex)
            Common.is23111() || Common.is23113() -> mapPhone2aChannels(channelIndex)
            Common.is24111() -> mapPhone3aChannels(channelIndex)
            else -> mapDefaultChannels(channelIndex)
        }
    }

    /**
     * Phone 2 channel mapping
     */
    private fun mapPhone2Channels(channelIndex: Int): List<Int> = when (channelIndex) {
        1 -> listOf(0, 1)
        2 -> listOf(2)
        3 -> c1Segments
        4 -> cOtherSegments
        5 -> eSegments
        6 -> dSegments
        7 -> listOf(0, 1, 2)
        8, 9 -> phone2Segments
        else -> emptyList()
    }

    /**
     * Phone 1 channel mapping
     */
    private fun mapPhone1Channels(channelIndex: Int): List<Int> = when (channelIndex) {
        1 -> listOf(0)
        2 -> listOf(1)
        3 -> listOf(2, 3, 4, 5)
        4 -> listOf(6)
        5 -> listOf(7, 8, 9, 10, 11, 12, 13, 14)
        6 -> listOf(0, 1, 6)
        7 -> listOf(2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14)
        8 -> (0..14).toList()
        else -> emptyList()
    }

    /**
     * Phone 2a channel mapping
     */
    private fun mapPhone2aChannels(channelIndex: Int): List<Int> = when (channelIndex) {
        1 -> listOf(25)
        2 -> listOf(24)
        3 -> (0..11).toList()
        4 -> (12..23).toList()
        5 -> (0..23).toList()
        6 -> listOf(24, 25)
        7, 8 -> (0..25).toList()
        else -> emptyList()
    }

    /**
     * Phone 3a channel mapping
     */
    private fun mapPhone3aChannels(channelIndex: Int): List<Int> = when (channelIndex) {
        1 -> phone3aASegments
        2 -> phone3aBSegments
        3 -> (0..9).toList()
        4 -> (10..19).toList()
        5 -> phone3aCSegments
        6 -> phone3aASegments + phone3aBSegments
        7, 8 -> phone3aAllSegments
        else -> emptyList()
    }

    /**
     * Default channel mapping
     */
    private fun mapDefaultChannels(channelIndex: Int): List<Int> = when {
        channelIndex in 1..7 -> listOf(channelIndex - 1)
        channelIndex == 8 -> (0..15).toList()
        else -> emptyList()
    }

    /**
     * Test individual C1 LED segment
     */
    suspend fun testC1Segment(c1Index: Int, bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        val hardwareChannel = mapC1IndexToHardwareChannel(c1Index)
        if (hardwareChannel == -1) return

        isAnimationRunning = true
        try {
            glyphManager.mGM?.turnOff()
            delay(200L)

            repeat(3) {
                if (!isAnimationRunning) return@repeat
                try {
                    createFrameBuilder(listOf(hardwareChannel))?.let {
                        glyphManager.mGM?.toggle(it.build())
                        delay(300L)
                        glyphManager.mGM?.turnOff()
                        delay(200L)
                    }
                } catch (e: Exception) {
                    glyphManager.mGM?.turnOff()
                    delay(500L)
                }
            }
        } finally {
            isAnimationRunning = false
            glyphManager.mGM?.turnOff()
            delay(100L)
        }
    }

    /**
     * Map UI C1 index to actual hardware channel number
     */
    private fun mapC1IndexToHardwareChannel(c1Index: Int): Int {
        return when {
            Common.is20111() -> if (c1Index in 1..4) c1Index + 1 else -1
            Common.is22111() -> if (c1Index in 1..16) c1Segments[c1Index - 1] else -1
            Common.is24111() -> if (c1Index in 1..20) c1Index - 1 else -1
            else -> -1
        }
    }

    /**
     * Run a C1 sequential animation
     */
    suspend fun runC1SequentialAnimation() {
        if (!glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            when {
                Common.is20111() -> runPhone1C1SequentialAnimation()
                Common.is22111() -> runPhone2C1SequentialAnimation()
                Common.is24111() -> runPhone3aC1SequentialAnimation()
            }
        } finally {
            ensureCleanup()
        }
    }

    /**
     * C1 Sequential animation for Nothing Phone 1
     */
    private suspend fun runPhone1C1SequentialAnimation() {
        val phone1C1Segments = listOf(2, 3, 4, 5)
        val phone1SupportingSegments = listOf(0, 1, 6, 7, 8, 9, 10, 11, 12, 13, 14)
        val stepDuration = 250L

        runC1SequentialPhase(phone1C1Segments, phone1SupportingSegments, stepDuration, forward = true)
        delay(1000L)
        runC1SequentialPhase(phone1C1Segments, phone1SupportingSegments, stepDuration, forward = false)

        glyphManager.turnOffAll()
    }

    /**
     * C1 Sequential animation for Nothing Phone 2
     */
    private suspend fun runPhone2C1SequentialAnimation() {
        val stepDuration = 250L
        val allSupportingSegments = aSegments + bSegments + cOtherSegments + dSegments + eSegments

        runC1SequentialPhase(c1Segments, allSupportingSegments, stepDuration, forward = true)
        delay(1000L)
        runC1SequentialPhase(c1Segments, allSupportingSegments, stepDuration, forward = false)

        glyphManager.turnOffAll()
    }

    /**
     * C1 Sequential animation for Nothing Phone 3a
     */
    private suspend fun runPhone3aC1SequentialAnimation() {
        val stepDuration = 200L

        runC1SequentialPhase(phone3aCSegments, phone3aASegments + phone3aBSegments, stepDuration, forward = true)
        delay(2000L)
        runC1SequentialPhase(phone3aCSegments, phone3aASegments + phone3aBSegments, stepDuration, forward = false)
    }

    /**
     * Optimized: Helper method for C1 sequential animation phases
     */
    private suspend fun runC1SequentialPhase(
        mainSegments: List<Int>,
        supportingSegments: List<Int>,
        stepDuration: Long,
        forward: Boolean
    ) {
        val indices = if (forward) mainSegments.indices else mainSegments.indices.reversed()

        for (i in indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            val range = if (forward) 0..i else i downTo 0
            for (j in range) {
                builder.buildChannel(mainSegments[j], maxBrightness)
            }

            val brightness = (maxBrightness * ((i + 1) / mainSegments.size.toFloat())).toInt()
            supportingSegments.forEach { builder.buildChannel(it, brightness) }

            try {
                glyphManager.mGM?.toggle(builder.build())
                delay(stepDuration)
            } catch (e: Exception) {
                delay(stepDuration)
            }
        }
    }

    /**
     * Test all glyph zones sequentially
     */
    suspend fun testAllZones(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            when {
                Common.is20111() -> testPhone1Zones()
                Common.is22111() -> testPhone2Zones()
            }
        } finally {
            ensureCleanup()
        }
    }

    /**
     * Test Phone 1 zones
     */
    private suspend fun testPhone1Zones() {
        val zones = listOf(
            listOf(0) to "A Zone",
            listOf(1) to "B Zone",
            listOf(2, 3, 4, 5) to "C Zone",
            listOf(6) to "E Zone",
            listOf(7, 8, 9, 10, 11, 12, 13, 14) to "D Zone"
        )
        testZones(zones)
    }

    /**
     * Test Phone 2 zones
     */
    private suspend fun testPhone2Zones() {
        val zones = listOf(
            aSegments to "A Zone",
            bSegments to "B Zone",
            c1Segments to "C1 Zone",
            cOtherSegments to "C Other Zone",
            eSegments to "E Zone",
            dSegments to "D Zone"
        )
        testZones(zones)
    }

    /**
     * Helper method to test zones
     */
    private suspend fun testZones(zones: List<Pair<List<Int>, String>>) {
        for ((channels, zoneName) in zones) {
            if (!isAnimationRunning) break
            createFrameBuilder(channels)?.let {
                try {
                    glyphManager.mGM?.toggle(it.build())
                    delay(1000L)
                    glyphManager.turnOffAll()
                    delay(500L)
                } catch (e: Exception) {
                    delay(500L)
                }
            }
        }
    }

    /**
     * Test a custom pattern of channels
     */
    suspend fun testCustomPattern(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            when {
                Common.is20111() -> testPhone1CustomPattern()
                Common.is22111() -> testPhone2CustomPattern()
            }
        } finally {
            ensureCleanup()
        }
    }

    /**
     * Test Phone 1 custom pattern
     */
    private suspend fun testPhone1CustomPattern() {
        val pattern1 = listOf(0, 2, 4, 6, 8, 10, 12, 14)
        val pattern2 = listOf(1, 3, 5, 7, 9, 11, 13)
        testCustomPatternRepeat(pattern1, pattern2)
    }

    /**
     * Test Phone 2 custom pattern
     */
    private suspend fun testPhone2CustomPattern() {
        val pattern1 = c1Segments.filterIndexed { index, _ -> index % 2 == 0 }
        val pattern2 = c1Segments.filterIndexed { index, _ -> index % 2 == 1 }
        testCustomPatternRepeat(pattern1, pattern2)
    }

    /**
     * Helper method for custom pattern testing
     */
    private suspend fun testCustomPatternRepeat(pattern1: List<Int>, pattern2: List<Int>) {
        repeat(3) {
            if (!isAnimationRunning) return@repeat
            testCustomPatternSingle(pattern1)
            testCustomPatternSingle(pattern2)
        }
    }

    /**
     * Helper method for single custom pattern test
     */
    private suspend fun testCustomPatternSingle(pattern: List<Int>) {
        createFrameBuilder(pattern)?.let {
            try {
                glyphManager.mGM?.toggle(it.build())
                delay(500L)
                glyphManager.turnOffAll()
                delay(200L)
            } catch (e: Exception) {
                delay(200L)
            }
        }
    }

    /**
     * Run C1 sequential animation with breathing timing
     */
    suspend fun runC1SequentialWithBreathingTiming(is478Pattern: Boolean, cycles: Int) {
        if (!isGlyphServiceEnabled() || !Common.is22111()) return

        isAnimationRunning = true
        try {
            val stepDuration = if (is478Pattern) 400L else 200L
            resetGlyphs()

            repeat(cycles) {
                if (!isAnimationRunning) return@repeat
                // Inhale
                for (segment in c1Segments) {
                    if (!isAnimationRunning) break
                    glyphManager.mGM?.toggle(GlyphFrame.Builder().buildChannel(segment).build())
                    delay(stepDuration)
                }
                // Hold
                if (is478Pattern) delay(700L)
                // Exhale
                for (segment in c1Segments.reversed()) {
                    if (!isAnimationRunning) break
                    glyphManager.mGM?.toggle(GlyphFrame.Builder().buildChannel(segment).build())
                    delay(stepDuration)
                }
                if (is478Pattern) delay(800L)
            }
        } finally {
            ensureCleanup()
        }
    }

    /**
     * Test final state before turnoff
     */
    suspend fun testFinalStateBeforeTurnoff(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            when {
                Common.is20111() -> testPhone1FinalState()
                Common.is22111() -> testPhone2FinalState()
            }
        } finally {
            ensureCleanup()
        }
    }

    /**
     * Test Phone 1 final state
     */
    private suspend fun testPhone1FinalState() {
        val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return
        builder.buildChannel(2, maxBrightness)
        val minBrightness = maxBrightness / 4
        listOf(0, 1, 6, 7, 8, 9, 10, 11, 12, 13, 14).forEach {
            builder.buildChannel(it, minBrightness)
        }
        glyphManager.mGM?.toggle(builder.build())
        delay(3000L)
    }

    /**
     * Test Phone 2 final state
     */
    private suspend fun testPhone2FinalState() {
        val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return
        builder.buildChannel(3, maxBrightness)
        val minBrightness = maxBrightness / 16
        (aSegments + bSegments + cOtherSegments + dSegments + eSegments).forEach {
            builder.buildChannel(it, minBrightness)
        }
        glyphManager.mGM?.toggle(builder.build())
        delay(3000L)
    }

    /**
     * Test only C14 and C15 isolated
     */
    suspend fun testOnlyC14AndC15Isolated(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone() || !Common.is22111()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return

            // Turn off all channels except C14 and C15
            for (i in 0..32) {
                if (i != 16 && i != 17) {
                    builder.buildChannel(i, 0)
                }
            }
            builder.buildChannel(16, maxBrightness)
            builder.buildChannel(17, maxBrightness)

            glyphManager.mGM?.toggle(builder.build())
            delay(5000L)
        } finally {
            ensureCleanup()
        }
    }

    /**
     * Run battery percentage visualization
     */
    suspend fun playBatteryPercentageVisualization(
        context: Context,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit = {}
    ) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()

            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, intentFilter)

            if (batteryIntent != null) {
                val batteryLevel = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val batteryScale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val batteryStatus = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val isCharging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL
                val batteryPercentage = if (batteryLevel != -1 && batteryScale != -1) {
                    (batteryLevel * 100 / batteryScale.toFloat()).toInt()
                } else {
                    50
                }

                when {
                    Common.is20111() -> runPhone1BatteryVisualization(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    Common.is22111() -> runPhone2BatteryVisualization(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    Common.is23111() || Common.is23113() -> runPhone2aBatteryVisualization(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    Common.is24111() -> runPhone3aBatteryVisualization(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    else -> runDefaultBatteryVisualization(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                }
            }
        } finally {
            ensureCleanup()
        }
    }

    // Battery visualization methods remain similar with optimized brightness calculations
    private suspend fun runPhone1BatteryVisualization(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val c1Segments = listOf(Phone1.C_START, Phone1.C_START + 1, Phone1.C_START + 2, Phone1.C_START + 3)
        var step = 0

        val targetSegments = (batteryPercentage / 25f).toInt().coerceIn(0, c1Segments.size)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break

            val progress = (elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f)
            onProgressUpdate(progress)

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                // Если мы еще не достигли нужного процента, увеличиваем кол-во глифов и ждем 0.1 сек
                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }

                for (i in 0 until currentSegments) {
                    val segmentBrightness = calculateSegmentBrightness(baseBrightness, isCharging, batteryPercentage, step, i)
                    builder.buildChannel(c1Segments[i], segmentBrightness.coerceIn(0, maxBrightness))
                }

                // Второстепенные эффекты включаем только когда шкала полностью заполнится
                if (currentSegments == targetSegments) {
                    val currentVirtualPercentage = if (c1Segments.isNotEmpty()) (currentSegments * 25) else 0
                    if (!isCharging && batteryPercentage >= 20) {
                        addPlayfulGlow(builder, currentVirtualPercentage, c1Segments.size, baseBrightness, step)
                    }

                    if (batteryPercentage < 20) {
                        val blinkBrightness = (maxBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.3f))).toInt()
                        builder.buildChannel(c1Segments.last(), blinkBrightness)
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    private suspend fun runPhone2BatteryVisualization(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val targetSegments = (batteryPercentage / 100f * c1Segments.size).toInt().coerceIn(0, c1Segments.size)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break

            val progress = (elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f)
            onProgressUpdate(progress)

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }

                if (isCharging || batteryPercentage < 20) {
                    for (i in 0 until currentSegments) {
                        val segmentBrightness = if (isCharging) {
                            (baseBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.3f))).toInt()
                        } else {
                            baseBrightness
                        }
                        builder.buildChannel(c1Segments[i], segmentBrightness.coerceIn(0, maxBrightness))
                    }
                } else {
                    val currentVirtualPercentage = (currentSegments.toFloat() / c1Segments.size * 100).toInt()
                    addWaveAnimation(builder, c1Segments, currentVirtualPercentage, baseBrightness, step)
                }

                // Дополнительные лампочки и свечения включаем только после заполнения
                if (currentSegments == targetSegments) {
                    if (batteryPercentage < 20) {
                        val blinkBrightness = (maxBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.3f))).toInt()
                        aSegments.forEach { builder.buildChannel(it, blinkBrightness) }
                    }

                    if (isCharging) {
                        val chargeBrightness = (baseBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.3f))).toInt()
                        bSegments.forEach { builder.buildChannel(it, chargeBrightness.coerceIn(0, maxBrightness)) }
                    }

                    if (!isCharging && batteryPercentage >= 20) {
                        addPlayfulGlowPhone2(builder, baseBrightness, step)
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    private suspend fun runPhone2aBatteryVisualization(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val targetSegments = (batteryPercentage / 100f * 24).toInt().coerceIn(0, 24)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break

            val progress = (elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f)
            onProgressUpdate(progress)

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }

                if (isCharging || batteryPercentage < 20) {
                    for (i in 0 until currentSegments) {
                        val segmentBrightness = if (isCharging) {
                            (baseBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.3f))).toInt()
                        } else {
                            baseBrightness
                        }
                        builder.buildChannel(Phone2a.C_START + i, segmentBrightness.coerceIn(0, maxBrightness))
                    }
                }

                if (currentSegments == targetSegments) {
                    if (batteryPercentage < 20) {
                        val blinkBrightness = (maxBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.3f))).toInt()
                        builder.buildChannel(Phone2a.A, blinkBrightness)
                    }

                    if (isCharging) {
                        val chargeBrightness = (baseBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.35f))).toInt()
                        builder.buildChannel(Phone2a.B, chargeBrightness.coerceIn(0, maxBrightness))
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    private suspend fun runPhone3aBatteryVisualization(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val targetSegments = (batteryPercentage / 100f * phone3aCSegments.size).toInt().coerceIn(0, phone3aCSegments.size)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break

            val progress = (elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f)
            onProgressUpdate(progress)

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }

                for (i in 0 until currentSegments) {
                    val segmentBrightness = when {
                        isCharging -> {
                            val waveFactor = 0.8f + 0.2f * kotlin.math.sin((step + i) * 0.15f)
                            (baseBrightness * waveFactor).toInt()
                        }
                        batteryPercentage < 20 -> {
                            (baseBrightness * (0.6f + 0.4f * kotlin.math.sin(step * 0.2f))).toInt()
                        }
                        else -> baseBrightness
                    }.coerceIn(0, maxBrightness)
                    builder.buildChannel(phone3aCSegments[i], segmentBrightness)
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    private suspend fun runDefaultBatteryVisualization(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val targetSegments = (batteryPercentage / 10f).toInt().coerceIn(0, 10)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break

            val progress = (elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f)
            onProgressUpdate(progress)

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }

                for (i in 0..currentSegments) {
                    val segmentBrightness = when {
                        isCharging -> (baseBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.1f + i * 0.5f))).toInt()
                        batteryPercentage < 20 -> baseBrightness
                        else -> (baseBrightness * (0.7f + 0.3f * kotlin.math.sin(step * 0.1f))).toInt()
                    }
                    builder.buildChannel(i, segmentBrightness.coerceIn(0, maxBrightness))
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }


    /**
     * Run battery percentage visualization
     */
    suspend fun playBatteryStatusAnimation(
        context: Context,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit = {}
    ) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()

            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, intentFilter)

            if (batteryIntent != null) {
                val batteryLevel = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val batteryScale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val batteryStatus = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)

                val isCharging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL

                val batteryPercentage = if (batteryLevel != -1 && batteryScale != -1) {
                    (batteryLevel * 100 / batteryScale.toFloat()).toInt().coerceIn(0, 100)
                } else {
                    50
                }

                when {
                    Common.is20111() -> animatePhone1BatteryStatus(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    Common.is22111() -> animatePhone2BatteryStatus(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    Common.is23111() || Common.is23113() -> animatePhone2aBatteryStatus(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    Common.is24111() -> animatePhone3aBatteryStatus(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                    else -> animateGenericBatteryStatus(batteryPercentage, isCharging, durationMillis, onProgressUpdate)
                }
            }
        } finally {
            ensureCleanup()
        }
    }

    // ------------------------------------------------------------------------
    // Phone 1
    // ------------------------------------------------------------------------
    private suspend fun animatePhone1BatteryStatus(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val cSegments = listOf(Phone1.C_START, Phone1.C_START + 1, Phone1.C_START + 2, Phone1.C_START + 3)
        var step = 0

        val targetSegments = (batteryPercentage / 25f).toInt().coerceIn(0, cSegments.size)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break
            onProgressUpdate((elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f))

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }
                for (i in 0 until currentSegments) {
                    val brightness = if (isCharging) {
                        val waveOffset = i * 0.5f
                        val waveMultiplier = 0.6f + 0.4f * kotlin.math.sin(step * 0.2f - waveOffset)
                        (baseBrightness * waveMultiplier).toInt()
                    } else {
                        baseBrightness
                    }
                    builder.buildChannel(cSegments[i], brightness.coerceIn(0, maxBrightness))
                }

                if (currentSegments == targetSegments) {
                    if (isCharging) {
                        if (batteryPercentage < 100) {
                            val endBlink = minOf(targetSegments + 2, cSegments.size)
                            for (j in targetSegments until endBlink) {
                                val waveOffset = (j - targetSegments) * 0.8f
                                val breatheBrightness = (baseBrightness * (0.1f + 0.9f * kotlin.math.abs(kotlin.math.sin(step * 0.15f - waveOffset)))).toInt()
                                builder.buildChannel(cSegments[j], breatheBrightness.coerceIn(0, maxBrightness))
                            }
                        }
                    } else {
                        if (batteryPercentage >= 20) {
                            addPlayfulGlow(builder, if (cSegments.isNotEmpty()) (currentSegments * 25) else 0, cSegments.size, baseBrightness, step)
                        } else if (currentSegments > 0) {
                            val alertBrightness = (maxBrightness * (0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin(step * 0.3f)))).toInt()
                            builder.buildChannel(cSegments[currentSegments - 1], alertBrightness.coerceIn(0, maxBrightness))
                        }
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    // ------------------------------------------------------------------------
    // Phone 2
    // ------------------------------------------------------------------------
    private suspend fun animatePhone2BatteryStatus(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val totalSegments = c1Segments.size
        val targetSegments = (batteryPercentage / 100f * totalSegments).toInt().coerceIn(0, totalSegments)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break
            onProgressUpdate((elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f))

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }
                for (i in 0 until currentSegments) {
                    val brightness = if (isCharging) {
                        val waveOffset = i * 0.3f
                        val waveMultiplier = 0.6f + 0.4f * kotlin.math.sin(step * 0.2f - waveOffset)
                        (baseBrightness * waveMultiplier).toInt()
                    } else {
                        baseBrightness
                    }
                    builder.buildChannel(c1Segments[i], brightness.coerceIn(0, maxBrightness))
                }

                if (currentSegments == targetSegments) {
                    if (isCharging) {
                        if (batteryPercentage < 100) {
                            val endBlink = minOf(targetSegments + 3, totalSegments)
                            for (j in targetSegments until endBlink) {
                                val waveOffset = (j - targetSegments) * 0.5f
                                val breatheBrightness = (baseBrightness * (0.1f + 0.9f * kotlin.math.abs(kotlin.math.sin(step * 0.15f - waveOffset)))).toInt()
                                builder.buildChannel(c1Segments[j], breatheBrightness.coerceIn(0, maxBrightness))
                            }
                        }
                        val chargeDotBrightness = (baseBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.2f))).toInt()
                        bSegments.forEach { builder.buildChannel(it, chargeDotBrightness.coerceIn(0, maxBrightness)) }
                    } else {
                        if (batteryPercentage >= 20) {
                            addPlayfulGlowPhone2(builder, baseBrightness, step)
                            addWaveAnimation(builder, c1Segments, (currentSegments.toFloat() / totalSegments * 100).toInt(), baseBrightness, step)
                        } else {
                            val alertBrightness = (maxBrightness * (0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin(step * 0.3f)))).toInt()
                            aSegments.forEach { builder.buildChannel(it, alertBrightness.coerceIn(0, maxBrightness)) }
                        }
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    // ------------------------------------------------------------------------
    // Phone 2a
    // ------------------------------------------------------------------------
    private suspend fun animatePhone2aBatteryStatus(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val totalSegments = 24
        val targetSegments = (batteryPercentage / 100f * totalSegments).toInt().coerceIn(0, totalSegments)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break
            onProgressUpdate((elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f))

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }
                for (i in 0 until currentSegments) {
                    val brightness = if (isCharging) {
                        val waveOffset = i * 0.25f
                        val waveMultiplier = 0.6f + 0.4f * kotlin.math.sin(step * 0.2f - waveOffset)
                        (baseBrightness * waveMultiplier).toInt()
                    } else {
                        baseBrightness
                    }
                    builder.buildChannel(Phone2a.C_START + i, brightness.coerceIn(0, maxBrightness))
                }

                if (currentSegments == targetSegments) {
                    if (isCharging) {
                        if (batteryPercentage < 100) {
                            val endBlink = minOf(targetSegments + 3, totalSegments)
                            for (j in targetSegments until endBlink) {
                                val waveOffset = (j - targetSegments) * 0.5f
                                val breatheBrightness = (baseBrightness * (0.1f + 0.9f * kotlin.math.abs(kotlin.math.sin(step * 0.15f - waveOffset)))).toInt()
                                builder.buildChannel(Phone2a.C_START + j, breatheBrightness.coerceIn(0, maxBrightness))
                            }
                        }
                        val chargeBrightness = (baseBrightness * (0.6f + 0.4f * kotlin.math.sin(step * 0.25f))).toInt()
                        builder.buildChannel(Phone2a.B, chargeBrightness.coerceIn(0, maxBrightness))
                    } else {
                        if (batteryPercentage < 20) {
                            val alertBrightness = (maxBrightness * (0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin(step * 0.3f)))).toInt()
                            builder.buildChannel(Phone2a.A, alertBrightness.coerceIn(0, maxBrightness))
                        }
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    // ------------------------------------------------------------------------
    // Phone 3a
    // ------------------------------------------------------------------------
    private suspend fun animatePhone3aBatteryStatus(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val totalSegments = phone3aCSegments.size
        val targetSegments = (batteryPercentage / 100f * totalSegments).toInt().coerceIn(0, totalSegments)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break
            onProgressUpdate((elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f))

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }
                for (i in 0 until currentSegments) {
                    val brightness = if (isCharging) {
                        val waveOffset = i * 0.3f
                        val waveMultiplier = 0.6f + 0.4f * kotlin.math.sin(step * 0.2f - waveOffset)
                        (baseBrightness * waveMultiplier).toInt()
                    } else {
                        baseBrightness
                    }
                    builder.buildChannel(phone3aCSegments[i], brightness.coerceIn(0, maxBrightness))
                }

                if (currentSegments == targetSegments) {
                    if (isCharging) {
                        if (batteryPercentage < 100) {
                            val endBlink = minOf(targetSegments + 3, totalSegments)
                            for (j in targetSegments until endBlink) {
                                val waveOffset = (j - targetSegments) * 0.5f
                                val breatheBrightness = (baseBrightness * (0.1f + 0.9f * kotlin.math.abs(kotlin.math.sin(step * 0.15f - waveOffset)))).toInt()
                                builder.buildChannel(phone3aCSegments[j], breatheBrightness.coerceIn(0, maxBrightness))
                            }
                        }
                    } else {
                        if (batteryPercentage < 20 && currentSegments > 0) {
                            val alertBrightness = (maxBrightness * (0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin(step * 0.3f)))).toInt()
                            builder.buildChannel(phone3aCSegments[currentSegments - 1], alertBrightness.coerceIn(0, maxBrightness))
                        }
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    // ------------------------------------------------------------------------
    // Standard Realisation (Default)
    // ------------------------------------------------------------------------
    private suspend fun animateGenericBatteryStatus(
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var step = 0

        val totalSegments = 10
        val targetSegments = (batteryPercentage / 10f).toInt().coerceIn(0, totalSegments)
        var currentSegments = 0

        while (isAnimationRunning) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= durationMillis) break
            onProgressUpdate((elapsedTime / durationMillis.toFloat()).coerceIn(0f, 1f))

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val baseBrightness = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (currentSegments < targetSegments) {
                    currentSegments++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }
                for (i in 0 until currentSegments) {
                    val brightness = if (isCharging) {
                        val waveOffset = i * 0.4f
                        val waveMultiplier = 0.6f + 0.4f * kotlin.math.sin(step * 0.2f - waveOffset)
                        (baseBrightness * waveMultiplier).toInt()
                    } else {
                        baseBrightness
                    }
                    builder.buildChannel(i, brightness.coerceIn(0, maxBrightness))
                }

                if (currentSegments == targetSegments) {
                    if (isCharging) {
                        if (batteryPercentage < 100) {
                            val endBlink = minOf(targetSegments + 3, totalSegments)
                            for (j in targetSegments until endBlink) {
                                val waveOffset = (j - targetSegments) * 0.5f
                                val breatheBrightness = (baseBrightness * (0.1f + 0.9f * kotlin.math.abs(kotlin.math.sin(step * 0.15f - waveOffset)))).toInt()
                                builder.buildChannel(j, breatheBrightness.coerceIn(0, maxBrightness))
                            }
                        }
                    } else {
                        if (batteryPercentage < 20 && currentSegments > 0) {
                            val alertBrightness = (maxBrightness * (0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin(step * 0.3f)))).toInt()
                            builder.buildChannel(currentSegments - 1, alertBrightness.coerceIn(0, maxBrightness))
                        }
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    /**
     * Helper method to calculate base brightness
     */
    private fun calculateBaseBrightness(batteryPercentage: Int, isCharging: Boolean): Int {
        return when {
            batteryPercentage < 20 -> maxBrightness / 3
            isCharging -> maxBrightness
            else -> (maxBrightness * 0.7f).toInt()
        }
    }

    /**
     * Helper method to calculate segment brightness
     */
    private fun calculateSegmentBrightness(
        baseBrightness: Int,
        isCharging: Boolean,
        batteryPercentage: Int,
        step: Int,
        index: Int
    ): Int {
        return when {
            isCharging -> (baseBrightness * (0.5f + 0.5f * kotlin.math.sin(step * 0.1f))).toInt()
            batteryPercentage < 20 -> baseBrightness
            else -> (baseBrightness * (0.7f + 0.3f * kotlin.math.sin(step * 0.1f))).toInt()
        }
    }

    /**
     * Helper method to add playful glow for Phone 1
     */
    private fun addPlayfulGlow(
        builder: GlyphFrame.Builder,
        batteryPercentage: Int,
        segmentCount: Int,
        baseBrightness: Int,
        step: Int
    ) {
        val filledLevel = batteryPercentage / 25f
        c1Segments.forEachIndexed { idx, seg ->
            val distance = filledLevel - idx
            val base = when {
                distance >= 1f -> baseBrightness
                distance > 0f -> (baseBrightness * distance).toInt()
                else -> 0
            }
            val waveFactor = 0.75f + 0.25f * kotlin.math.sin((step + idx) * 0.25f)
            val bright = (base * waveFactor).toInt().coerceIn(0, maxBrightness)
            if (bright > 0) builder.buildChannel(seg, bright)
        }

        if (step % 20 == 0) {
            val unused = c1Segments.indices.filter { it >= filledLevel.toInt() }
            if (unused.isNotEmpty()) {
                val twinkleSeg = c1Segments[unused[Random.nextInt(unused.size)]]
                builder.buildChannel(twinkleSeg, (maxBrightness * 0.5f).toInt())
            }
        }
    }

    /**
     * Helper method to add wave animation for Phone 2
     */
    private fun addWaveAnimation(
        builder: GlyphFrame.Builder,
        segments: List<Int>,
        batteryPercentage: Int,
        baseBrightness: Int,
        step: Int
    ) {
        val total = segments.size.toFloat()
        val filledLevel = batteryPercentage / 100f * total

        for (i in 0 until total.toInt()) {
            val base = when {
                i + 1 <= filledLevel -> baseBrightness
                i < filledLevel -> (baseBrightness * (filledLevel - i)).toInt()
                else -> 0
            }
            if (base == 0) continue

            val wave = 0.05f + 1.15f * (0.5f + 0.5f * kotlin.math.sin((step * 0.5f) - i * 0.6f))
            val brightness = (base * wave).toInt().coerceIn(0, maxBrightness)
            builder.buildChannel(segments[i], brightness)
        }
    }

    /**
     * Helper method to add playful glow for Phone 2
     */
    private fun addPlayfulGlowPhone2(
        builder: GlyphFrame.Builder,
        baseBrightness: Int,
        step: Int
    ) {
        val glow = (maxBrightness * (0.15f + 0.15f * kotlin.math.sin(step * 0.18f))).toInt()
        val glow2 = (maxBrightness * (0.15f + 0.15f * kotlin.math.sin(step * 0.18f + 1.5f))).toInt()
        bSegments.forEach { builder.buildChannel(it, glow) }
        eSegments.forEach { builder.buildChannel(it, glow2) }
    }

    /**
     * Play a Pulse Lock animation by identifier
     */
    suspend fun playPulseLockAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return
        when (id) {
            "C1" -> runC1SequentialAnimation()
            "WAVE" -> runWaveAnimation()
            "BEEDAH" -> runBeedahAnimation()
            "PULSE" -> runPulseEffect(3)
            "LOCK" -> runLockPulseAnimation()
            "SPIRAL" -> runSpiralAnimation()
            "HEARTBEAT" -> runHeartbeatAnimation()
            "MATRIX" -> runMatrixRainAnimation()
            "FIREWORKS" -> runFireworksAnimation()
            "DNA" -> runDNAHelixAnimation()
            else -> runC1SequentialAnimation()
        }
    }

    /**
     * Play a Low-Battery Alert animation by identifier
     */
    suspend fun playLowBatteryAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return

        val durationMs = settingsRepository.getLowBatteryDuration()
        val cyclesFromDuration = (durationMs / 500L).toInt().coerceAtLeast(1)

        when (id) {
            "C1" -> runC1SequentialAnimation()
            "WAVE" -> runWaveAnimation()
            "BEEDAH" -> runBeedahAnimation()
            "LOCK" -> runLockPulseAnimation()
            "PULSE" -> runPulseEffect(cyclesFromDuration)
            "SPIRAL" -> runSpiralAnimation()
            "HEARTBEAT" -> runHeartbeatAnimation()
            "MATRIX" -> runMatrixRainAnimation()
            "FIREWORKS" -> runFireworksAnimation()
            "DNA" -> runDNAHelixAnimation()
            else -> runPulseEffect(cyclesFromDuration)
        }
    }


    /**
     * Play a screen off glyph animation
     */
    suspend fun playScreenOffAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return

        val durationMs = settingsRepository.getScreenOffDuration()
        val cyclesFromDuration = (durationMs / 500L).toInt().coerceAtLeast(1)

        when (id) {
            "C1" -> runC1SequentialAnimation()
            "WAVE" -> runWaveAnimation()
            "BEEDAH" -> runBeedahAnimation()
            "LOCK" -> runLockPulseAnimation()
            "PULSE" -> runPulseEffect(cyclesFromDuration)
            "SPIRAL" -> runSpiralAnimation()
            "HEARTBEAT" -> runHeartbeatAnimation()
            "MATRIX" -> runMatrixRainAnimation()
            "FIREWORKS" -> runFireworksAnimation()
            "DNA" -> runDNAHelixAnimation()
            else -> runPulseEffect(cyclesFromDuration)
        }
    }



    /**
     * Play a nfc animation
     */
    suspend fun playNfcAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return

        val durationMs = settingsRepository.getScreenOffDuration()
        val cyclesFromDuration = (durationMs / 500L).toInt().coerceAtLeast(1)

        when (id) {
            "C1" -> runC1SequentialAnimation()
            "WAVE" -> runWaveAnimation()
            "BEEDAH" -> runBeedahAnimation()
            "LOCK" -> runLockPulseAnimation()
            "PULSE" -> runPulseEffect(cyclesFromDuration)
            "SPIRAL" -> runSpiralAnimation()
            "HEARTBEAT" -> runHeartbeatAnimation()
            "MATRIX" -> runMatrixRainAnimation()
            "FIREWORKS" -> runFireworksAnimation()
            "DNA" -> runDNAHelixAnimation()
            else -> runPulseEffect(cyclesFromDuration)
        }
    }

    /**
     * Quick guard: returns true if glyph service is enabled
     */
    private fun isGlyphServiceEnabled(): Boolean {
        val enabled = settingsRepository.getGlyphServiceEnabled()
        if (!enabled) {
            Log.d(TAG, "Glyph service disabled – animation call ignored")
        }
        return enabled
    }

    /**
     * Padlock Sweep animation
     */
    suspend fun runLockPulseAnimation() {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            val stepDuration = 100L
            val (cSegments, allSegments) = getLockAnimationSegments()
            val nonCSegments = allSegments.filterNot { it in cSegments }

            for (idx in cSegments.indices) {
                if (!isAnimationRunning) break
                try {
                    val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                    val nonCBrightness = (maxBrightness * 0.5f).toInt()
                    nonCSegments.forEach { builder.buildChannel(it, nonCBrightness) }

                    for (j in 0..idx) {
                        val brightness = if (idx == 0) maxBrightness
                        else if (j == idx) maxBrightness
                        else {
                            val fadeFactor = 0.3f + 0.7f * (j.toFloat() / idx)
                            (maxBrightness * fadeFactor).toInt()
                        }
                        builder.buildChannel(cSegments[j], brightness)
                    }

                    glyphManager.mGM?.toggle(builder.build())
                    delay(stepDuration)
                } catch (e: Exception) {
                    delay(stepDuration)
                }
            }

            // Final full brightness
            createFrameBuilder(allSegments)?.let {
                glyphManager.mGM?.toggle(it.build())
                delay(700L)
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    /**
     * Helper method to get lock animation segments
     */
    private fun getLockAnimationSegments(): Pair<List<Int>, List<Int>> {
        return when {
            Common.is20111() -> {
                val cSegs = listOf(Phone1.C_START, Phone1.C_START + 1, Phone1.C_START + 2, Phone1.C_START + 3)
                cSegs to (0..14).toList()
            }
            Common.is22111() -> {
                c1Segments to phone2Segments
            }
            Common.is24111() -> {
                phone3aCSegments to phone3aAllSegments
            }
            else -> {
                c1Segments to phone2Segments
            }
        }
    }

    /**
     * Spiral Animation
     */
    suspend fun runSpiralAnimation() {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            when {
                Common.is20111() -> runPhone1SpiralAnimation()
                Common.is22111() -> runPhone2SpiralAnimation()
                Common.is23111() || Common.is23113() -> runPhone2aSpiralAnimation()
                Common.is24111() -> runPhone3aSpiralAnimation()
                else -> runDefaultSpiralAnimation()
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private suspend fun runPhone1SpiralAnimation() {
        val stepDuration = 100L
        val segments = listOf(
            Phone1.E, Phone1.A, Phone1.B,
            Phone1.C_START, Phone1.C_START + 1, Phone1.C_START + 2, Phone1.C_START + 3,
            Phone1.D_START, Phone1.D_START + 1, Phone1.D_START + 2, Phone1.D_START + 3,
            Phone1.D_START + 4, Phone1.D_START + 5, Phone1.D_START + 6, Phone1.D_START + 7
        )
        runSpiralAnimationForSegments(segments, stepDuration, Phone1.E)
    }

    private suspend fun runPhone2SpiralAnimation() {
        val stepDuration = 80L
        val segments = eSegments + aSegments + bSegments + c1Segments + cOtherSegments + dSegments
        runSpiralAnimationForSegments(segments, stepDuration, eSegments.firstOrNull() ?: 24)
    }

    private suspend fun runPhone2aSpiralAnimation() {
        val stepDuration = 70L
        val segments = listOf(Phone2a.A, Phone2a.B) + (0 until 24).map { Phone2a.C_START + it }
        runSpiralAnimationForSegments(segments, stepDuration, Phone2a.A)
    }

    /**
     * Helper method for spiral animation
     */
    private suspend fun runSpiralAnimationForSegments(segments: List<Int>, stepDuration: Long, centerSegment: Int) {
        // Phase 1: Spiral outward
        for (i in segments.indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
            for (j in 0..i) {
                val fadeFactor = 0.6f + (j.toFloat() / segments.size * 0.4f)
                builder.buildChannel(segments[j], (maxBrightness * fadeFactor).toInt())
            }
            glyphManager.mGM?.toggle(builder.build())
            delay(stepDuration)
        }

        // Phase 2: Full brightness flash
        createFrameBuilder(segments)?.let {
            glyphManager.mGM?.toggle(it.build())
            delay(250L)
        }

        // Phase 3: Spiral inward
        for (i in segments.indices.reversed()) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
            for (j in i until segments.size) {
                val fadeFactor = 0.6f + ((segments.size - j).toFloat() / (segments.size - i) * 0.4f)
                builder.buildChannel(segments[j], (maxBrightness * fadeFactor).toInt())
            }
            glyphManager.mGM?.toggle(builder.build())
            delay(stepDuration)
        }

        // Phase 4: Final center pulse
        createFrameBuilder(listOf(centerSegment))?.let {
            glyphManager.mGM?.toggle(it.build())
            delay(200L)
            glyphManager.turnOffAll()
            delay(100L)
            glyphManager.mGM?.toggle(it.build())
            delay(200L)
        }
    }

    private suspend fun runDefaultSpiralAnimation() {
        delay(2000L)
    }

    /**
     * Heartbeat Animation
     */
    suspend fun runHeartbeatAnimation() {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            when {
                Common.is20111() -> runPhone1HeartbeatAnimation()
                Common.is22111() -> runPhone2HeartbeatAnimation()
                Common.is23111() || Common.is23113() -> runPhone2aHeartbeatAnimation()
                Common.is24111() -> runPhone3aHeartbeatAnimation()
                else -> runDefaultHeartbeatAnimation()
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private suspend fun runPhone1HeartbeatAnimation() {
        val segments = listOf(
            Phone1.A, Phone1.B, Phone1.E,
            Phone1.C_START, Phone1.C_START + 1, Phone1.C_START + 2, Phone1.C_START + 3,
            Phone1.D_START, Phone1.D_START + 1, Phone1.D_START + 2, Phone1.D_START + 3,
            Phone1.D_START + 4, Phone1.D_START + 5, Phone1.D_START + 6, Phone1.D_START + 7
        )
        runHeartbeatForSegments(segments)
    }

    private suspend fun runPhone2HeartbeatAnimation() {
        runHeartbeatForSegments(phone2Segments)
    }

    private suspend fun runPhone2aHeartbeatAnimation() {
        val segments = listOf(Phone2a.A, Phone2a.B) + (0 until 24).map { Phone2a.C_START + it }
        runHeartbeatForSegments(segments)
    }

    private suspend fun runPhone3aHeartbeatAnimation() {
        runHeartbeatForSegments(phone3aAllSegments)
    }

    /**
     * Helper method for heartbeat animation
     */
    private suspend fun runHeartbeatForSegments(segments: List<Int>) {
        repeat(3) {
            if (!isAnimationRunning) return@repeat
            // First beat
            createFrameBuilder(segments)?.let {
                glyphManager.mGM?.toggle(it.build())
                delay(200L)
                glyphManager.turnOffAll()
                delay(100L)
            }
            // Second beat
            createFrameBuilder(segments)?.let {
                glyphManager.mGM?.toggle(it.build())
                delay(200L)
                glyphManager.turnOffAll()
                delay(300L)
            }
        }
    }

    private suspend fun runDefaultHeartbeatAnimation() {
        delay(2000L)
    }

    /**
     * Matrix Rain Animation
     */
    suspend fun runMatrixRainAnimation() {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            when {
                Common.is20111() -> runPhone1MatrixRainAnimation()
                Common.is22111() -> runPhone2MatrixRainAnimation()
                Common.is23111() || Common.is23113() -> runPhone2aMatrixRainAnimation()
                Common.is24111() -> runPhone3aMatrixRainAnimation()
                else -> runDefaultMatrixRainAnimation()
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private suspend fun runPhone1MatrixRainAnimation() {
        val allSegments = listOf(Phone1.A, Phone1.B, Phone1.C_START, Phone1.C_START + 1,
            Phone1.C_START + 2, Phone1.C_START + 3, Phone1.E,
            Phone1.D_START, Phone1.D_START + 1, Phone1.D_START + 2,
            Phone1.D_START + 3, Phone1.D_START + 4, Phone1.D_START + 5,
            Phone1.D_START + 6, Phone1.D_START + 7)
        runMatrixRainForSegments(allSegments, 20, 3, 8, 100L, 50L, 200)
    }

    private suspend fun runPhone2MatrixRainAnimation() {
        runMatrixRainForSegments(phone2Segments, 25, 4, 10, 80L, 40L, 150)
    }

    private suspend fun runPhone2aMatrixRainAnimation() {
        val allSegments = (0 until 24).map { Phone2a.C_START + it } + listOf(Phone2a.A, Phone2a.B)
        runMatrixRainForSegments(allSegments, 30, 5, 12, 70L, 35L, 120)
    }

    private suspend fun runPhone3aMatrixRainAnimation() {
        runMatrixRainForSegments(phone3aAllSegments, 35, 6, 15, 60L, 30L, 100)
    }

    /**
     * Helper method for matrix rain animation
     */
    private suspend fun runMatrixRainForSegments(
        segments: List<Int>,
        drops: Int,
        minLength: Int,
        maxLength: Int,
        stepDelay: Long,
        offDelay: Long,
        brightnessDecrement: Int
    ) {
        repeat(drops) {
            if (!isAnimationRunning) return@repeat
            val dropLength = Random.nextInt(minLength, maxLength)
            val startIndex = Random.nextInt(segments.size - dropLength)

            for (i in 0 until dropLength) {
                if (!isAnimationRunning) break
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return
                val segmentIndex = startIndex + i
                if (segmentIndex < segments.size) {
                    val brightness = (maxBrightness - (i * brightnessDecrement)).coerceAtLeast(0)
                    builder.buildChannel(segments[segmentIndex], brightness)
                }
                glyphManager.mGM?.toggle(builder.build())
                delay(stepDelay)
                glyphManager.turnOffAll()
                delay(offDelay)
            }
        }
    }

    private suspend fun runDefaultMatrixRainAnimation() {
        delay(3000L)
    }

    /**
     * Fireworks Animation
     */
    suspend fun runFireworksAnimation() {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            when {
                Common.is20111() -> runPhone1FireworksAnimation()
                Common.is22111() -> runPhone2FireworksAnimation()
                Common.is23111() || Common.is23113() -> runPhone2aFireworksAnimation()
                Common.is24111() -> runPhone3aFireworksAnimation()
                else -> runDefaultFireworksAnimation()
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private suspend fun runPhone1FireworksAnimation() {
        val segments = listOf(Phone1.A, Phone1.B, Phone1.C_START, Phone1.C_START + 1,
            Phone1.C_START + 2, Phone1.C_START + 3, Phone1.E,
            Phone1.D_START, Phone1.D_START + 1, Phone1.D_START + 2,
            Phone1.D_START + 3, Phone1.D_START + 4, Phone1.D_START + 5,
            Phone1.D_START + 6, Phone1.D_START + 7)
        runFireworksForSegments(segments, 5, 5, 10, 300L, 500L, 200L)
    }

    private suspend fun runPhone2FireworksAnimation() {
        runFireworksForSegments(phone2Segments, 6, 8, 15, 250L, 400L, 150L)
    }

    private suspend fun runPhone2aFireworksAnimation() {
        val allSegments = (0 until 24).map { Phone2a.C_START + it } + listOf(Phone2a.A, Phone2a.B)
        runFireworksForSegments(allSegments, 7, 10, 20, 200L, 350L, 100L)
    }

    private suspend fun runPhone3aFireworksAnimation() {
        runFireworksForSegments(phone3aAllSegments, 8, 12, 25, 180L, 300L, 80L)
    }

    /**
     * Helper method for fireworks animation
     */
    private suspend fun runFireworksForSegments(
        segments: List<Int>,
        fireworks: Int,
        minExplosion: Int,
        maxExplosion: Int,
        launchDelay: Long,
        explosionDelay: Long,
        fadeDelay: Long
    ) {
        repeat(fireworks) {
            if (!isAnimationRunning) return@repeat
            // Launch
            createFrameBuilder(listOf(segments[Random.nextInt(segments.size)]))?.let {
                glyphManager.mGM?.toggle(it.build())
                delay(launchDelay)
            }
            // Explosion
            val explosionSegments = segments.shuffled().take(Random.nextInt(minExplosion, maxExplosion))
            createFrameBuilder(explosionSegments)?.let {
                glyphManager.mGM?.toggle(it.build())
                delay(explosionDelay)
                glyphManager.turnOffAll()
                delay(fadeDelay)
            }
        }
    }

    private suspend fun runDefaultFireworksAnimation() {
        delay(3000L)
    }

    /**
     * DNA Helix Animation
     */
    suspend fun runDNAHelixAnimation() {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        isAnimationRunning = true
        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            when {
                Common.is20111() -> runPhone1DNAHelixAnimation()
                Common.is22111() -> runPhone2DNAHelixAnimation()
                Common.is23111() || Common.is23113() -> runPhone2aDNAHelixAnimation()
                Common.is24111() -> runPhone3aDNAHelixAnimation()
                else -> runDefaultDNAHelixAnimation()
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private suspend fun runPhone1DNAHelixAnimation() {
        val segments = listOf(Phone1.A, Phone1.B, Phone1.C_START, Phone1.C_START + 1,
            Phone1.C_START + 2, Phone1.C_START + 3, Phone1.E,
            Phone1.D_START, Phone1.D_START + 1, Phone1.D_START + 2,
            Phone1.D_START + 3, Phone1.D_START + 4, Phone1.D_START + 5,
            Phone1.D_START + 6, Phone1.D_START + 7)
        runDNAHelixForSegments(segments, 3, 150L, 50L)
    }

    private suspend fun runPhone2DNAHelixAnimation() {
        runDNAHelixForSegments(phone2Segments, 3, 120L, 40L)
    }

    private suspend fun runPhone2aDNAHelixAnimation() {
        val allSegments = (0 until 24).map { Phone2a.C_START + it } + listOf(Phone2a.A, Phone2a.B)
        runDNAHelixForSegments(allSegments, 3, 100L, 30L)
    }

    private suspend fun runPhone3aDNAHelixAnimation() {
        runDNAHelixForSegments(phone3aAllSegments, 3, 80L, 25L)
    }

    /**
     * Helper method for DNA helix animation
     */
    private suspend fun runDNAHelixForSegments(
        segments: List<Int>,
        rotations: Int,
        stepDelay: Long,
        offDelay: Long
    ) {
        repeat(rotations) {
            if (!isAnimationRunning) return@repeat
            for (i in segments.indices) {
                if (!isAnimationRunning) break
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return

                val strand1Index = i
                if (strand1Index < segments.size) {
                    builder.buildChannel(segments[strand1Index], maxBrightness)
                }

                val strand2Index = (i + segments.size / 2) % segments.size
                builder.buildChannel(segments[strand2Index], maxBrightness)

                glyphManager.mGM?.toggle(builder.build())
                delay(stepDelay)
                glyphManager.turnOffAll()
                delay(offDelay)
            }
        }
    }

    private suspend fun runDefaultDNAHelixAnimation() {
        delay(3000L)
    }
}