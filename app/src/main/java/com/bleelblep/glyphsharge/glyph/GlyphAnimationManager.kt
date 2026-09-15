package com.bleelblep.glyphsharge.glyph

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.nothing.ketchum.Common
import com.nothing.ketchum.GlyphFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Plays glyph animations for supported Nothing Phone models.
 *
 * Device-specific channel layouts and timings are described by [DeviceProfile],
 * built once for the connected model. All public suspend functions are safe to
 * call concurrently: [isAnimationRunning] guards a running animation, and glyphs
 * are always turned off when an animation ends or is cancelled.
 */
@Singleton
class GlyphAnimationManager @Inject constructor(
    private val glyphManager: GlyphManager,
    private val settingsRepository: SettingsRepository
) {

    private companion object {
        const val TAG = "GlyphAnimationManager"

        const val DEFAULT_MAX_BRIGHTNESS = 4000
        const val CLEANUP_DELAY_MS = 100L

        // Pulse
        const val PULSE_ON_DURATION = 300L
        const val PULSE_OFF_DURATION = 300L
        const val PULSE_CYCLES = 3

        // Notification effect
        const val NOTIFICATION_CYCLES = 2
        const val NOTIFICATION_ON_MS = 1000L
        const val NOTIFICATION_OFF_MS = 500L

        // Battery
        const val BATTERY_STEP_DELAY = 50L
        const val LOW_BATTERY_THRESHOLD_PERCENT = 20
        const val FALLBACK_BATTERY_PERCENT = 50

        // Tests
        const val TEST_FLASH_REPEATS = 3
        const val TEST_FLASH_ON_MS = 300L
        const val TEST_FLASH_OFF_MS = 200L
        const val FINAL_STATE_HOLD_MS = 3000L
        const val ISOLATION_TEST_HOLD_MS = 5000L
        const val ZONE_SHOW_MS = 1000L
        const val ZONE_GAP_MS = 500L
        const val PATTERN_ROUNDS = 3
        const val PATTERN_SHOW_MS = 500L
        const val PATTERN_GAP_MS = 200L

        // Breathing (4-7-8: inhale 4s, hold 7s, exhale 8s — scaled)
        const val BREATHING_STEP_MS = 200L
        const val BREATHING_478_STEP_MS = 400L
        const val BREATHING_478_HOLD_MS = 700L
        const val BREATHING_478_EXHALE_MS = 800L

        // Lock pulse
        const val LOCK_STEP_MS = 100L
        const val LOCK_FINAL_ON_MS = 700L
        const val LOCK_DIM_RATIO = 0.5f

        // Group (wave/beedah) step per device
        const val GROUP_PHONE1_STEP = 150L
        const val GROUP_PHONE2_STEP = 100L
        const val GROUP_PHONE2A_STEP = 80L
        const val GROUP_PHONE3A_STEP = 80L

        // Spiral
        const val SPIRAL_STEP_PHONE1 = 100L
        const val SPIRAL_STEP_PHONE2 = 80L
        const val SPIRAL_STEP_PHONE2A = 70L
        const val SPIRAL_STEP_PHONE3A = 60L
        const val SPIRAL_FULL_HOLD_MS = 250L
        const val SPIRAL_END_BLINK_MS = 200L
        const val SPIRAL_END_GAP_MS = 100L
        const val SPIRAL_STAGE_EXTRA_MS = 10L
        const val SPIRAL_FINALE_MS = 500L
        const val SPIRAL_FINALE_GAP_MS = 200L
        const val SPIRAL_FINALE_TAIL_MS = 300L

        // Heartbeat
        const val HEARTBEAT_CYCLES = 3
        const val HEARTBEAT_BEAT_MS = 200L
        const val HEARTBEAT_GAP_MS = 100L
        const val HEARTBEAT_RECOVER_MS = 300L

        // Playback dispatcher
        const val CYCLE_MS = 500L
    }

    @Volatile
    private var isAnimationRunning = false

    private val maxBrightness = DEFAULT_MAX_BRIGHTNESS

    private val profile: DeviceProfile? by lazy { buildProfile() }

    private enum class DeviceType {
        PHONE1,
        PHONE2,
        PHONE2A,
        PHONE3A
    }

    private class AnimGroup(
        val segments: List<Int>,
        val step: Long,
        val off: Long = 0L
    )

    private class MatrixConfig(
        val drops: Int,
        val minLength: Int,
        val maxLength: Int,
        val stepDelayMs: Long,
        val offDelayMs: Long,
        val brightnessDecrement: Int
    )

    private class FireworksConfig(
        val count: Int,
        val minExplosion: Int,
        val maxExplosion: Int,
        val launchDelayMs: Long,
        val explosionDelayMs: Long,
        val fadeDelayMs: Long
    )

    private class DnaConfig(
        val rotations: Int,
        val stepDelayMs: Long,
        val offDelayMs: Long
    )

    private data class BatteryState(
        val percentage: Int,
        val isCharging: Boolean
    )

    private class DeviceProfile(
        val type: DeviceType,
        val all: List<Int>,
        val a: List<Int>,
        val b: List<Int>,
        val c: List<Int>,
        val cOther: List<Int>,
        val d: List<Int>,
        val e: List<Int>,
        val waveGroups: List<AnimGroup>,
        val spiralOrder: List<Int>,
        val spiralStep: Long,
        val pulseSegments: List<Int>,
        val zones: List<List<Int>>,
        val channelMap: Map<Int, List<Int>>,
        val c1SeqStep: Long,
        val c1SeqHold: Long,
        val customPatterns: List<List<Int>>,
        val matrixConfig: MatrixConfig,
        val fireworksConfig: FireworksConfig,
        val dnaConfig: DnaConfig
    ) {
        /** Same rhythm as [waveGroups], but without the pause between groups. */
        val beedahGroups: List<AnimGroup> = waveGroups.map { AnimGroup(it.segments, it.step) }

        /** Every channel outside the main C strip; used as supporting light. */
        val nonC: List<Int> = all.filterNot { it in c.toSet() }
    }

    // region Public API — animations

    fun stopAnimations() {
        isAnimationRunning = false
        turnOffAllSafely()
    }

    suspend fun runWaveAnimation() = anim { p ->
        for (group in p.waveGroups) {
            for (segment in group.segments) {
                if (!isAnimationRunning) return@anim
                pulse(listOf(segment), onMs = group.step, offMs = group.off)
            }
        }
    }

    suspend fun runBeedahAnimation() = anim { p ->
        runBeedahGroups(p.beedahGroups)
    }

    suspend fun runSpiralAnimation() = anim { p ->
        if (p.type == DeviceType.PHONE3A) {
            runPhone3aSpiralInternal(p)
        } else {
            runSpiralOrder(p)
        }
    }

    suspend fun runC1SequentialAnimation() = anim { p ->
        if (p.c.isEmpty()) return@anim
        runC1Phase(p, forward = true)
        delay(p.c1SeqHold.milliseconds)
        runC1Phase(p, forward = false)
    }

    suspend fun runLockPulseAnimation() = anim { p ->
        if (p.c.isEmpty()) return@anim

        val nonCBrightness = (maxBrightness * LOCK_DIM_RATIO).toInt()

        for (idx in p.c.indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            p.nonC.forEach { builder.buildChannel(it, nonCBrightness) }

            for (j in 0..idx) {
                val brightness = if (idx == 0 || j == idx) {
                    maxBrightness
                } else {
                    (maxBrightness * (0.3f + 0.7f * (j.toFloat() / idx))).toInt()
                }
                builder.buildChannel(p.c[j], brightness)
            }

            toggleFrame(builder, LOCK_STEP_MS)
        }

        pulse(p.all, onMs = LOCK_FINAL_ON_MS)
    }

    suspend fun runPulseEffect(cycles: Int = 3) = anim { p ->
        for (i in 0 until cycles) {
            if (!isAnimationRunning) break
            pulse(p.pulseSegments, onMs = PULSE_ON_DURATION, offMs = PULSE_OFF_DURATION)
        }
    }

    suspend fun runHeartbeatAnimation() = anim { p ->
        runHeartbeat(p)
    }

    suspend fun runMatrixRainAnimation() = anim { p ->
        runMatrixRain(p)
    }

    suspend fun runFireworksAnimation() = anim { p ->
        runFireworks(p)
    }

    suspend fun runDNAHelixAnimation() = anim { p ->
        runDNAHelix(p)
    }

    // endregion

    // region Public API — scenarios

    suspend fun playPulseLockAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(settingsRepository.getPulseLockAnimationId(), settingsRepository.getPulseLockDuration())
    }

    suspend fun playLowBatteryAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(settingsRepository.getLowBatteryAnimationId(), settingsRepository.getLowBatteryDuration())
    }

    suspend fun playScreenOffAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(settingsRepository.getScreenOffAnimationId(), settingsRepository.getScreenOffDuration())
    }

    suspend fun playNfcAnimation() {
        if (!isGlyphServiceEnabled()) return
        playAnimation(settingsRepository.getNfcAnimationId(), settingsRepository.getNfcAnimationDuration())
    }

    suspend fun playPowerPeekAnimation(
        context: Context,
        onProgressUpdate: (Float) -> Unit = {}
    ) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        val state = readBatteryState(context)

        anim { p ->
            animateBattery(p, state.percentage, state.isCharging, settingsRepository.getPowerPeekDuration(), onProgressUpdate)
        }
    }

    suspend fun playChargingAnimationAnimation(
        context: Context,
        onProgressUpdate: (Float) -> Unit = {}
    ) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        val state = readBatteryState(context)

        anim { p ->
            animateBattery(p, state.percentage, state.isCharging, settingsRepository.getChargingAnimationDuration(), onProgressUpdate)
        }
    }

    // endregion

    // region Device profile

    private fun buildProfile(): DeviceProfile? {
        return when {
            Common.is20111() -> {
                val a = listOf(0)
                val b = listOf(1)
                val c = (2..5).toList()
                val d = (7..14).toList()
                val e = listOf(6)
                // Physical channel order: A, B, C, E, D.
                val all = a + b + c + e + d
                val groups = listOf(AnimGroup(all, GROUP_PHONE1_STEP, 50L))

                DeviceProfile(
                    type = DeviceType.PHONE1,
                    all = all,
                    a = a,
                    b = b,
                    c = c,
                    cOther = emptyList(),
                    d = d,
                    e = e,
                    waveGroups = groups,
                    spiralOrder = e + a + b + c + d,
                    spiralStep = SPIRAL_STEP_PHONE1,
                    pulseSegments = a + b + e,
                    zones = listOf(a, b, c, e, d),
                    channelMap = mapOf(
                        1 to a,
                        2 to b,
                        3 to c,
                        4 to e,
                        5 to d,
                        6 to (a + b + e),
                        7 to (c + d),
                        8 to all
                    ),
                    c1SeqStep = 250L,
                    c1SeqHold = 1000L,
                    customPatterns = parityPatternsOf(all),
                    matrixConfig = MatrixConfig(
                        drops = 20,
                        minLength = 3,
                        maxLength = 8,
                        stepDelayMs = 100L,
                        offDelayMs = 50L,
                        brightnessDecrement = 200
                    ),
                    fireworksConfig = FireworksConfig(
                        count = 5,
                        minExplosion = 5,
                        maxExplosion = 10,
                        launchDelayMs = 300L,
                        explosionDelayMs = 500L,
                        fadeDelayMs = 200L
                    ),
                    dnaConfig = DnaConfig(rotations = 3, stepDelayMs = 150L, offDelayMs = 50L)
                )
            }

            Common.is22111() -> {
                val a = listOf(0, 1)
                val b = listOf(2)
                val c = (3..18).toList()
                val cOther = (19..23).toList()
                val d = (25..32).toList()
                val e = listOf(24)
                val all = a + b + c + cOther + e + d
                val groups = listOf(AnimGroup(all, GROUP_PHONE2_STEP, 30L))

                DeviceProfile(
                    type = DeviceType.PHONE2,
                    all = all,
                    a = a,
                    b = b,
                    c = c,
                    cOther = cOther,
                    d = d,
                    e = e,
                    waveGroups = groups,
                    spiralOrder = e + a + b + c + cOther + d,
                    spiralStep = SPIRAL_STEP_PHONE2,
                    pulseSegments = a + b + e,
                    zones = listOf(a, b, c, cOther, e, d),
                    channelMap = mapOf(
                        1 to a,
                        2 to b,
                        3 to c,
                        4 to cOther,
                        5 to e,
                        6 to d,
                        7 to (a + b),
                        8 to all
                    ),
                    c1SeqStep = 250L,
                    c1SeqHold = 1000L,
                    customPatterns = parityPatternsOf(c),
                    matrixConfig = MatrixConfig(
                        drops = 25,
                        minLength = 4,
                        maxLength = 10,
                        stepDelayMs = 80L,
                        offDelayMs = 40L,
                        brightnessDecrement = 150
                    ),
                    fireworksConfig = FireworksConfig(
                        count = 6,
                        minExplosion = 8,
                        maxExplosion = 15,
                        launchDelayMs = 250L,
                        explosionDelayMs = 400L,
                        fadeDelayMs = 150L
                    ),
                    dnaConfig = DnaConfig(rotations = 3, stepDelayMs = 120L, offDelayMs = 40L)
                )
            }

            Common.is23111() || Common.is23113() -> {
                val a = listOf(25)
                val b = listOf(24)
                val c = (0..23).toList()
                // Physical channel order: C, B, A == 0..25.
                val all = c + b + a
                val groups = listOf(
                    AnimGroup(c, GROUP_PHONE2A_STEP, 30L),
                    AnimGroup(a + b, GROUP_PHONE2A_STEP * 2, 50L)
                )

                DeviceProfile(
                    type = DeviceType.PHONE2A,
                    all = all,
                    a = a,
                    b = b,
                    c = c,
                    cOther = emptyList(),
                    d = emptyList(),
                    e = emptyList(),
                    waveGroups = groups,
                    spiralOrder = a + b + c,
                    spiralStep = SPIRAL_STEP_PHONE2A,
                    pulseSegments = a + b,
                    zones = listOf(c.take(12), c.drop(12), b, a),
                    channelMap = mapOf(
                        1 to a,
                        2 to b,
                        3 to c.take(12),
                        4 to c.drop(12),
                        5 to c,
                        6 to (b + a),
                        7 to all
                    ),
                    c1SeqStep = 180L,
                    c1SeqHold = 1500L,
                    customPatterns = parityPatternsOf(c),
                    matrixConfig = MatrixConfig(
                        drops = 30,
                        minLength = 5,
                        maxLength = 12,
                        stepDelayMs = 70L,
                        offDelayMs = 35L,
                        brightnessDecrement = 120
                    ),
                    fireworksConfig = FireworksConfig(
                        count = 7,
                        minExplosion = 10,
                        maxExplosion = 20,
                        launchDelayMs = 200L,
                        explosionDelayMs = 350L,
                        fadeDelayMs = 100L
                    ),
                    dnaConfig = DnaConfig(rotations = 3, stepDelayMs = 100L, offDelayMs = 30L)
                )
            }

            Common.is24111() -> {
                val c = (0..19).toList()
                val a = (20..30).toList()
                val b = (31..35).toList()
                val all = c + a + b
                val groups = listOf(
                    AnimGroup(c, GROUP_PHONE3A_STEP, 25L),
                    AnimGroup(a, GROUP_PHONE3A_STEP + 20L, 30L),
                    AnimGroup(b, GROUP_PHONE3A_STEP + 40L, 40L)
                )

                DeviceProfile(
                    type = DeviceType.PHONE3A,
                    all = all,
                    a = a,
                    b = b,
                    c = c,
                    cOther = emptyList(),
                    d = emptyList(),
                    e = emptyList(),
                    waveGroups = groups,
                    spiralOrder = all,
                    spiralStep = SPIRAL_STEP_PHONE3A,
                    pulseSegments = listOf(25, 33, 9),
                    zones = listOf(c.take(10), c.drop(10), a, b),
                    channelMap = mapOf(
                        1 to a,
                        2 to b,
                        3 to c.take(10),
                        4 to c.drop(10),
                        5 to c,
                        6 to (a + b),
                        7 to all
                    ),
                    c1SeqStep = 200L,
                    c1SeqHold = 2000L,
                    customPatterns = parityPatternsOf(c),
                    matrixConfig = MatrixConfig(
                        drops = 35,
                        minLength = 6,
                        maxLength = 15,
                        stepDelayMs = 60L,
                        offDelayMs = 30L,
                        brightnessDecrement = 100
                    ),
                    fireworksConfig = FireworksConfig(
                        count = 8,
                        minExplosion = 12,
                        maxExplosion = 25,
                        launchDelayMs = 180L,
                        explosionDelayMs = 300L,
                        fadeDelayMs = 80L
                    ),
                    dnaConfig = DnaConfig(rotations = 3, stepDelayMs = 80L, offDelayMs = 25L)
                )
            }

            else -> null
        }
    }

    private fun parityPatternsOf(channels: List<Int>): List<List<Int>> = listOf(
        channels.filterIndexed { index, _ -> index % 2 == 0 },
        channels.filterIndexed { index, _ -> index % 2 == 1 }
    )

    // endregion

    // region Core helpers

    private fun turnOffAllSafely() {
        runCatching { glyphManager.turnOffAll() }
            .onFailure { Log.e(TAG, "turnOffAll failed", it) }
    }

    private suspend fun handleAnimationError(
        e: Exception,
        message: String,
        retryDelayMs: Long = 0L
    ) {
        if (e is CancellationException) throw e
        Log.e(TAG, message, e)
        if (retryDelayMs > 0) delay(retryDelayMs)
    }

    private fun isGlyphServiceEnabled(): Boolean {
        val enabled = settingsRepository.getGlyphServiceEnabled()
        if (!enabled) {
            Log.d(TAG, "Glyph service disabled - animation call ignored")
        }
        return enabled
    }

    private suspend fun anim(
        requireService: Boolean = true,
        block: suspend (DeviceProfile) -> Unit
    ) {
        if (requireService && !isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return

        val p = profile ?: return
        isAnimationRunning = true

        try {
            turnOffAllSafely()
            delay(CLEANUP_DELAY_MS)
            block(p)
        } catch (e: Exception) {
            handleAnimationError(e, "Animation error")
        } finally {
            isAnimationRunning = false
            turnOffAllSafely()
        }
    }

    private fun createFrameBuilder(
        channels: Collection<Int>,
        brightness: Int = maxBrightness
    ): GlyphFrame.Builder? {
        if (channels.isEmpty()) return null
        return runCatching {
            glyphManager.mGM?.getGlyphFrameBuilder()?.apply {
                channels.forEach { buildChannel(it, brightness) }
            }
        }.onFailure { Log.e(TAG, "createFrameBuilder error", it) }.getOrNull()
    }

    private suspend fun toggleFrame(builder: GlyphFrame.Builder?, delayMs: Long = 0L) {
        if (builder == null) return
        try {
            glyphManager.mGM?.toggle(builder.build())
            if (delayMs > 0) delay(delayMs)
        } catch (e: Exception) {
            handleAnimationError(e, "toggleFrame error", delayMs)
        }
    }

    private suspend fun toggleChannels(
        channels: Collection<Int>,
        brightness: Int = maxBrightness,
        delayMs: Long = 0L
    ) {
        toggleFrame(createFrameBuilder(channels, brightness), delayMs)
    }

    /** Shows [channels] for [onMs], turns everything off, waits [offMs]. */
    private suspend fun pulse(
        channels: Collection<Int>,
        onMs: Long,
        offMs: Long = 0L,
        brightness: Int = maxBrightness
    ) {
        toggleChannels(channels, brightness, onMs)
        turnOffAllSafely()
        if (offMs > 0) delay(offMs)
    }

    private suspend fun flashChannels(
        channels: List<Int>,
        repeats: Int,
        onMs: Long,
        offMs: Long
    ) {
        for (i in 0 until repeats) {
            if (!isAnimationRunning) break
            pulse(channels, onMs = onMs, offMs = offMs)
        }
    }

    // endregion

    // region Animation runners

    private suspend fun runBeedahGroups(groups: List<AnimGroup>) {
        val lit = mutableListOf<Int>()

        for (group in groups) {
            for (segment in group.segments) {
                if (!isAnimationRunning) return
                lit.add(segment)
                toggleChannels(lit, delayMs = group.step)
            }
        }

        pulseSegments(lit)
    }

    private suspend fun pulseSegments(channels: Collection<Int>) {
        for (i in 0 until PULSE_CYCLES) {
            if (!isAnimationRunning) break
            pulse(channels, onMs = PULSE_ON_DURATION, offMs = PULSE_OFF_DURATION)
        }
    }

    private suspend fun runHeartbeat(p: DeviceProfile) {
        for (beat in 0 until HEARTBEAT_CYCLES) {
            if (!isAnimationRunning) break
            pulse(p.all, onMs = HEARTBEAT_BEAT_MS, offMs = HEARTBEAT_GAP_MS)
            if (!isAnimationRunning) break
            pulse(p.all, onMs = HEARTBEAT_BEAT_MS, offMs = HEARTBEAT_RECOVER_MS)
        }
    }

    private suspend fun runSpiralOrder(p: DeviceProfile) {
        val segments = p.spiralOrder.ifEmpty { p.all }
        if (segments.isEmpty()) return

        val size = segments.size

        for (i in segments.indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            for (j in 0..i) {
                val brightness = (maxBrightness * (0.6f + (j.toFloat() / size) * 0.4f)).toInt()
                builder.buildChannel(segments[j], brightness)
            }

            toggleFrame(builder, p.spiralStep)
        }

        toggleChannels(segments, delayMs = SPIRAL_FULL_HOLD_MS)

        for (i in segments.indices.reversed()) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            val denom = (size - i).coerceAtLeast(1)
            for (j in i until size) {
                val brightness = (maxBrightness * (0.6f + ((size - j).toFloat() / denom) * 0.4f)).toInt()
                builder.buildChannel(segments[j], brightness)
            }

            toggleFrame(builder, p.spiralStep)
        }

        pulse(listOf(segments.first()), onMs = SPIRAL_END_BLINK_MS, offMs = SPIRAL_END_GAP_MS)
        pulse(listOf(segments.first()), onMs = SPIRAL_END_BLINK_MS)
    }

    private suspend fun runPhone3aSpiralInternal(p: DeviceProfile) {
        val step = p.spiralStep

        progressiveSweep(p.c, step) { j, i ->
            if (j == i) {
                maxBrightness
            } else {
                (maxBrightness * (0.3f + (j.toFloat() / i.coerceAtLeast(1)) * 0.4f)).toInt()
            }
        }

        progressiveSweep(p.a, step + SPIRAL_STAGE_EXTRA_MS, dimmed(p.c, 0.3f)) { j, i ->
            if (j == i) {
                maxBrightness
            } else {
                (maxBrightness * (0.5f + (j.toFloat() / i.coerceAtLeast(1)) * 0.5f)).toInt()
            }
        }

        progressiveSweep(p.b, step + SPIRAL_STAGE_EXTRA_MS * 2, dimmed(p.c, 0.4f) + dimmed(p.a, 0.7f)) { _, _ ->
            maxBrightness
        }

        toggleFrame(createFrameBuilder(p.all), SPIRAL_FINALE_MS)
        turnOffAllSafely()
        delay(SPIRAL_FINALE_GAP_MS.milliseconds)
        toggleFrame(createFrameBuilder(p.all), SPIRAL_FINALE_TAIL_MS)
    }

    private suspend fun progressiveSweep(
        segments: List<Int>,
        stepMs: Long,
        base: List<Pair<Int, Int>> = emptyList(),
        brightness: (j: Int, i: Int) -> Int
    ) {
        for (i in segments.indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            base.forEach { (channel, baseBrightness) -> builder.buildChannel(channel, baseBrightness) }

            for (j in 0..i) {
                builder.buildChannel(segments[j], brightness(j, i))
            }

            toggleFrame(builder, stepMs)
        }
    }

    private fun dimmed(channels: List<Int>, ratio: Float): List<Pair<Int, Int>> =
        channels.map { it to (maxBrightness * ratio).toInt() }

    private suspend fun runC1Phase(p: DeviceProfile, forward: Boolean) {
        val main = p.c
        if (main.isEmpty()) return

        val indices = if (forward) main.indices else main.indices.reversed()

        for (i in indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            val range = if (forward) 0..i else i downTo 0
            for (j in range) {
                builder.buildChannel(main[j], maxBrightness)
            }

            val supportBrightness = (maxBrightness * ((i + 1) / main.size.toFloat())).toInt()
            p.nonC.forEach { builder.buildChannel(it, supportBrightness) }

            toggleFrame(builder, p.c1SeqStep)
        }
    }

    private suspend fun runMatrixRain(p: DeviceProfile) {
        val cfg = p.matrixConfig
        if (p.all.isEmpty()) return

        for (drop in 0 until cfg.drops) {
            if (!isAnimationRunning) break

            val safeMax = cfg.maxLength
                .coerceAtMost(p.all.size + 1)
                .coerceAtLeast(cfg.minLength + 1)

            val dropLength = Random.nextInt(cfg.minLength, safeMax).coerceAtMost(p.all.size)
            val maxStart = (p.all.size - dropLength).coerceAtLeast(0)
            val startIndex = if (maxStart == 0) 0 else Random.nextInt(maxStart)

            for (i in 0 until dropLength) {
                if (!isAnimationRunning) break

                val brightness = (maxBrightness - i * cfg.brightnessDecrement).coerceAtLeast(0)
                pulse(listOf(p.all[startIndex + i]), onMs = cfg.stepDelayMs, offMs = cfg.offDelayMs, brightness = brightness)
            }
        }
    }

    private suspend fun runFireworks(p: DeviceProfile) {
        val cfg = p.fireworksConfig
        if (p.all.isEmpty()) return

        for (i in 0 until cfg.count) {
            if (!isAnimationRunning) break

            pulse(listOf(p.all.random()), onMs = cfg.launchDelayMs)

            val safeMax = cfg.maxExplosion
                .coerceAtMost(p.all.size + 1)
                .coerceAtLeast(cfg.minExplosion + 1)

            val explosionCount = Random.nextInt(cfg.minExplosion, safeMax).coerceAtMost(p.all.size)
            val explosionSegments = p.all.shuffled().take(explosionCount)

            pulse(explosionSegments, onMs = cfg.explosionDelayMs, offMs = cfg.fadeDelayMs)
        }
    }

    private suspend fun runDNAHelix(p: DeviceProfile) {
        val cfg = p.dnaConfig
        if (p.all.isEmpty()) return

        val size = p.all.size

        for (rotation in 0 until cfg.rotations) {
            for (i in p.all.indices) {
                if (!isAnimationRunning) break
                pulse(
                    listOf(p.all[i], p.all[(i + size / 2) % size]),
                    onMs = cfg.stepDelayMs,
                    offMs = cfg.offDelayMs
                )
            }
        }
    }

    // endregion

    // region Battery

    private fun readBatteryState(context: Context): BatteryState {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Sticky system broadcast read: null receiver, no exporter flag required.
        val intent = context.registerReceiver(null, filter)
            ?: return BatteryState(FALLBACK_BATTERY_PERCENT, isCharging = false)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        val isPluggedIn = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
                plugged == BatteryManager.BATTERY_PLUGGED_DOCK

        val isCharging = isPluggedIn || status == BatteryManager.BATTERY_STATUS_CHARGING

        val percentage = if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
        } else {
            FALLBACK_BATTERY_PERCENT
        }

        return BatteryState(percentage, isCharging)
    }

    private suspend fun animateBattery(
        p: DeviceProfile,
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMs: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        if (durationMs <= 0) return

        val bar = p.c
        if (bar.isEmpty()) return

        val total = bar.size
        val target = (batteryPercentage / 100f * total).toInt().coerceIn(0, total)

        var current = 0
        var step = 0
        val startTime = System.currentTimeMillis()

        while (isAnimationRunning) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= durationMs) {
                onProgressUpdate(1f)
                break
            }

            onProgressUpdate((elapsed / durationMs.toFloat()).coerceIn(0f, 1f))

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val base = calculateBaseBrightness(batteryPercentage, isCharging)

                if (current < target) current++

                for (i in 0 until current) {
                    val brightness = if (isCharging) {
                        val offset = if (p.type == DeviceType.PHONE1) i * 0.5f else i * 0.3f
                        val wave = 0.6f + 0.4f * sin(step * 0.2f - offset)
                        (base * wave).toInt()
                    } else {
                        base
                    }
                    builder.buildChannel(bar[i], brightness.coerceIn(0, maxBrightness))
                }

                if (current == target) {
                    if (isCharging) {
                        if (batteryPercentage < 100) {
                            addBatteryEndBlink(builder, p, bar, target, total, base, step)
                        }

                        when (p.type) {
                            DeviceType.PHONE2 -> {
                                val chargeDot = (base * (0.5f + 0.5f * sin(step * 0.2f))).toInt()
                                p.b.forEach {
                                    builder.buildChannel(it, chargeDot.coerceIn(0, maxBrightness))
                                }
                            }
                            DeviceType.PHONE2A -> {
                                val chargeDot = (base * (0.6f + 0.4f * sin(step * 0.25f))).toInt()
                                p.b.firstOrNull()?.let {
                                    builder.buildChannel(it, chargeDot.coerceIn(0, maxBrightness))
                                }
                            }
                            else -> Unit
                        }
                    } else {
                        when (p.type) {
                            DeviceType.PHONE1 -> {
                                if (batteryPercentage >= LOW_BATTERY_THRESHOLD_PERCENT) {
                                    addPlayfulBarGlow(builder, bar, batteryPercentage, base, step)
                                } else if (current > 0) {
                                    addAlert(builder, bar[current - 1], step)
                                }
                            }
                            DeviceType.PHONE2 -> {
                                if (batteryPercentage >= LOW_BATTERY_THRESHOLD_PERCENT) {
                                    addPlayfulAccentGlow(builder, p, step)
                                    addWaveAnimation(
                                        builder,
                                        bar,
                                        filledCount = current,
                                        baseBrightness = base,
                                        step = step
                                    )
                                } else {
                                    p.a.forEach { addAlert(builder, it, step) }
                                }
                            }
                            DeviceType.PHONE2A -> {
                                if (batteryPercentage < LOW_BATTERY_THRESHOLD_PERCENT) {
                                    p.a.firstOrNull()?.let { addAlert(builder, it, step) }
                                }
                            }
                            DeviceType.PHONE3A -> {
                                if (batteryPercentage < LOW_BATTERY_THRESHOLD_PERCENT && current > 0) {
                                    addAlert(builder, bar[current - 1], step)
                                }
                            }
                        }
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(BATTERY_STEP_DELAY.milliseconds)
                step++
            } catch (e: Exception) {
                handleAnimationError(e, "Battery animation error", BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    private fun calculateBaseBrightness(batteryPercentage: Int, isCharging: Boolean): Int {
        return when {
            isCharging -> maxBrightness
            batteryPercentage < LOW_BATTERY_THRESHOLD_PERCENT -> maxBrightness / 3
            else -> (maxBrightness * 0.7f).toInt()
        }
    }

    private fun addBatteryEndBlink(
        builder: GlyphFrame.Builder,
        p: DeviceProfile,
        bar: List<Int>,
        target: Int,
        total: Int,
        base: Int,
        step: Int
    ) {
        val extra = if (p.type == DeviceType.PHONE1) 2 else 3
        val end = minOf(target + extra, total)

        for (j in target until end) {
            val offset = (j - target) * if (p.type == DeviceType.PHONE1) 0.8f else 0.5f
            val brightness = (base * (0.1f + 0.9f * abs(sin(step * 0.15f - offset)))).toInt()
            builder.buildChannel(bar[j], brightness.coerceIn(0, maxBrightness))
        }
    }

    private fun addAlert(builder: GlyphFrame.Builder, channel: Int, step: Int) {
        val brightness = (maxBrightness * (0.2f + 0.8f * abs(sin(step * 0.3f)))).toInt()
        builder.buildChannel(channel, brightness.coerceIn(0, maxBrightness))
    }

    private fun addPlayfulBarGlow(
        builder: GlyphFrame.Builder,
        bar: List<Int>,
        batteryPercentage: Int,
        baseBrightness: Int,
        step: Int
    ) {
        val filled = batteryPercentage / 100f * bar.size

        bar.forEachIndexed { idx, channel ->
            val base = when {
                idx + 1 <= filled -> baseBrightness
                idx < filled -> (baseBrightness * (filled - idx)).toInt()
                else -> 0
            }

            if (base == 0) return@forEachIndexed

            val wave = 0.75f + 0.25f * sin((step + idx) * 0.25f)
            val brightness = (base * wave).toInt().coerceIn(0, maxBrightness)
            builder.buildChannel(channel, brightness)
        }

        if (step % 20 == 0) {
            val unused = bar.indices.filter { it >= filled.toInt() }
            if (unused.isNotEmpty()) {
                val twinkleChannel = bar[unused.random()]
                builder.buildChannel(twinkleChannel, (maxBrightness * 0.5f).toInt())
            }
        }
    }

    private fun addPlayfulAccentGlow(
        builder: GlyphFrame.Builder,
        p: DeviceProfile,
        step: Int
    ) {
        val glow = (maxBrightness * (0.15f + 0.15f * sin(step * 0.18f))).toInt()
        val glow2 = (maxBrightness * (0.15f + 0.15f * sin(step * 0.18f + 1.5f))).toInt()

        p.b.forEach { builder.buildChannel(it, glow) }
        p.e.forEach { builder.buildChannel(it, glow2) }
    }

    private fun addWaveAnimation(
        builder: GlyphFrame.Builder,
        segments: List<Int>,
        filledCount: Int,
        baseBrightness: Int,
        step: Int
    ) {
        val filledLevel = filledCount.toFloat()

        for (i in segments.indices) {
            val base = when {
                i + 1 <= filledLevel -> baseBrightness
                i < filledLevel -> (baseBrightness * (filledLevel - i)).toInt()
                else -> 0
            }

            if (base == 0) continue

            val wave = 0.05f + 1.15f * (0.5f + 0.5f * sin((step * 0.5f) - i * 0.6f))
            val brightness = (base * wave).toInt().coerceIn(0, maxBrightness)
            builder.buildChannel(segments[i], brightness)
        }
    }

    // endregion

    // region Dispatch and mapping

    private suspend fun playAnimation(id: String, durationMs: Long) {
        val cycles = (durationMs / CYCLE_MS).toInt().coerceAtLeast(1)

        when (id.trim().uppercase(Locale.ROOT)) {
            "C1" -> runC1SequentialAnimation()
            "WAVE" -> runWaveAnimation()
            "BEEDAH" -> runBeedahAnimation()
            "LOCK" -> runLockPulseAnimation()
            "SPIRAL" -> runSpiralAnimation()
            "HEARTBEAT" -> runHeartbeatAnimation()
            "MATRIX" -> runMatrixRainAnimation()
            "FIREWORKS" -> runFireworksAnimation()
            "DNA" -> runDNAHelixAnimation()
            // "PULSE" and any unknown id fall back to the pulse effect.
            else -> runPulseEffect(cycles)
        }
    }
    // endregion
}