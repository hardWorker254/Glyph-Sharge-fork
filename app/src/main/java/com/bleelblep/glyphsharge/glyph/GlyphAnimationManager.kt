package com.bleelblep.glyphsharge.glyph

import android.content.Context
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.GlyphFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import com.bleelblep.glyphsharge.data.SettingsRepository

/**
 * Optimized glyph animation manager.
 * Public API preserved. Internal duplication reduced via DeviceProfile.
 */
@Singleton
class GlyphAnimationManager @Inject constructor(
    private val glyphManager: GlyphManager,
    private val settingsRepository: SettingsRepository
) {
    private val TAG = "GlyphAnimationManager"

    @Volatile
    private var isAnimationRunning = false

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

    private var maxBrightness = DEFAULT_MAX_BRIGHTNESS

    private enum class DeviceType {
        PHONE1,
        PHONE2,
        PHONE2A,
        PHONE3A
    }

    private data class AnimGroup(
        val segments: List<Int>,
        val step: Long,
        val off: Long = 0L
    )

    private data class DeviceProfile(
        val type: DeviceType,
        val all: List<Int>,
        val c: List<Int>,
        val a: List<Int>,
        val b: List<Int>,
        val d: List<Int>,
        val e: List<Int>,
        val cOther: List<Int>,
        val waveGroups: List<AnimGroup>,
        val beedahGroups: List<AnimGroup>,
        val spiralOrder: List<Int>,
        val spiralStep: Long,
        val pulseSegments: List<Int>,
        val lockMain: List<Int>,
        val lockAll: List<Int>,
        val zones: List<Pair<List<Int>, String>>,
        val channelMap: Map<Int, List<Int>>,
        val c1SeqMain: List<Int>,
        val c1SeqSupport: List<Int>,
        val c1SeqStep: Long,
        val c1SeqHold: Long,
        val customPatterns: List<List<Int>>,
        val batteryBar: List<Int>
    )

    private val profile: DeviceProfile? by lazy { buildProfile() }

    // region Public API

    fun stopAnimations() {
        isAnimationRunning = false
        runCatching { glyphManager.turnOffAll() }
    }

    suspend fun runWaveAnimation() = anim { p ->
        for (group in p.waveGroups) {
            for (segment in group.segments) {
                if (!isAnimationRunning) return@anim
                toggleSingle(segment, group.step, group.off)
            }
        }
    }

    suspend fun runBeedahAnimation() = anim { p ->
        runBeedahGroups(p.beedahGroups)
    }

    suspend fun runPhone3aSpiralAnimation() = anim { p ->
        if (p.type == DeviceType.PHONE3A) {
            runPhone3aSpiralInternal(p)
        }
    }

    suspend fun runPulseEffect(cycles: Int = 3) = anim { p ->
        if (p.pulseSegments.isEmpty()) return@anim
        val builder = createFrameBuilder(p.pulseSegments) ?: return@anim

        repeat(cycles) {
            if (!isAnimationRunning) return@repeat
            try {
                glyphManager.mGM?.toggle(builder.build())
                delay(250L)
                glyphManager.turnOffAll()
                delay(250L)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Pulse error: ${e.message}")
                delay(250L)
            }
        }
    }

    suspend fun runNotificationEffect() = anim { p ->
        repeat(2) {
            if (!isAnimationRunning) return@repeat
            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return@repeat
                p.all.forEach { builder.buildChannel(it, maxBrightness) }
                glyphManager.mGM?.toggle(builder.build())
                delay(1000L)
                glyphManager.turnOffAll()
                delay(500L)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Notification effect error: ${e.message}")
                delay(500L)
            }
        }
    }

    suspend fun testGlyphChannel(channelIndex: Int, bypassServiceCheck: Boolean = false) {
        if (!isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        val p = profile ?: return
        val channels = p.channelMap[channelIndex] ?: return

        anim(requireService = false) {
            flashChannels(channels, repeats = 3, onMs = 300L, offMs = 200L)
        }
    }

    suspend fun testC1Segment(c1Index: Int, bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        val p = profile ?: return
        val channel = mapC1Index(p, c1Index)
        if (channel == -1) return

        anim(requireService = false) {
            flashChannels(listOf(channel), repeats = 3, onMs = 300L, offMs = 200L)
        }
    }

    suspend fun runC1SequentialAnimation() = anim(requireService = false) { p ->
        if (p.c1SeqMain.isEmpty()) return@anim
        runC1Phase(p, forward = true)
        delay(p.c1SeqHold)
        runC1Phase(p, forward = false)
        glyphManager.turnOffAll()
    }

    suspend fun testAllZones(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        anim(requireService = false) { p ->
            for ((channels, _) in p.zones) {
                if (!isAnimationRunning) break
                toggleChannels(channels, delayMs = 1000L)
                glyphManager.turnOffAll()
                delay(500L)
            }
        }
    }

    suspend fun testCustomPattern(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        anim(requireService = false) { p ->
            repeat(3) {
                if (!isAnimationRunning) return@repeat
                for (pattern in p.customPatterns) {
                    if (!isAnimationRunning) break
                    toggleChannels(pattern, delayMs = 500L)
                    glyphManager.turnOffAll()
                    delay(200L)
                }
            }
        }
    }

    suspend fun runC1SequentialWithBreathingTiming(is478Pattern: Boolean, cycles: Int) = anim { p ->
        if (p.type != DeviceType.PHONE2) return@anim

        val stepDuration = if (is478Pattern) 400L else 200L

        repeat(cycles) {
            if (!isAnimationRunning) return@repeat

            for (segment in p.c) {
                if (!isAnimationRunning) break
                toggleChannels(listOf(segment), delayMs = stepDuration)
            }

            if (is478Pattern) delay(700L)

            for (segment in p.c.reversed()) {
                if (!isAnimationRunning) break
                toggleChannels(listOf(segment), delayMs = stepDuration)
            }

            if (is478Pattern) delay(800L)
        }
    }

    suspend fun testFinalStateBeforeTurnoff(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        anim(requireService = false) { p ->
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return@anim

            when (p.type) {
                DeviceType.PHONE1 -> {
                    builder.buildChannel(2, maxBrightness)
                    p.all.filter { it != 2 }.forEach { builder.buildChannel(it, maxBrightness / 4) }
                }
                DeviceType.PHONE2 -> {
                    builder.buildChannel(3, maxBrightness)
                    p.all.filter { it != 3 }.forEach { builder.buildChannel(it, maxBrightness / 16) }
                }
                else -> {
                    val main = p.c.firstOrNull() ?: return@anim
                    builder.buildChannel(main, maxBrightness)
                    p.all.filter { it != main }.forEach { builder.buildChannel(it, maxBrightness / 8) }
                }
            }

            toggleFrame(builder, 3000L)
        }
    }

    suspend fun testOnlyC14AndC15Isolated(bypassServiceCheck: Boolean = false) {
        if (!glyphManager.isNothingPhone() || !Common.is22111()) return
        if (!bypassServiceCheck && !glyphManager.canPerformOperation()) return

        anim(requireService = false) {
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return@anim

            for (i in 0..32) {
                if (i != 16 && i != 17) {
                    builder.buildChannel(i, 0)
                }
            }

            builder.buildChannel(16, maxBrightness)
            builder.buildChannel(17, maxBrightness)

            toggleFrame(builder, 5000L)
        }
    }

    suspend fun playBatteryStatusAnimation(
        context: Context,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit = {}
    ) {
        if (!isGlyphServiceEnabled() || !glyphManager.isNothingPhone()) return

        val p = profile ?: return
        isAnimationRunning = true

        try {
            resetGlyphs()
            delay(CLEANUP_DELAY)

            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, intentFilter)

            if (batteryIntent != null) {
                val batteryLevel = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val batteryScale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val batteryStatus = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val pluggedType = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)

                val isPluggedIn = pluggedType == android.os.BatteryManager.BATTERY_PLUGGED_AC ||
                        pluggedType == android.os.BatteryManager.BATTERY_PLUGGED_USB

                val isCharging = isPluggedIn ||
                        batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL

                val batteryPercentage = if (batteryLevel != -1 && batteryScale != -1) {
                    (batteryLevel * 100 / batteryScale.toFloat()).toInt().coerceIn(0, 100)
                } else {
                    50
                }

                animateBattery(p, batteryPercentage, isCharging, durationMillis, onProgressUpdate)
            } else {
                animateBattery(p, 50, false, durationMillis, onProgressUpdate)
            }
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    suspend fun playPulseLockAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return
        playAnimation(id, settingsRepository.getPulseLockDuration()) { cycles ->
            runPulseEffect(cycles)
        }
    }

    suspend fun playLowBatteryAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return
        playAnimation(id, settingsRepository.getLowBatteryDuration()) { cycles ->
            runPulseEffect(cycles)
        }
    }

    suspend fun playScreenOffAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return
        playAnimation(id, settingsRepository.getScreenOffDuration()) { cycles ->
            runPulseEffect(cycles)
        }
    }

    suspend fun playNfcAnimation(id: String) {
        if (!isGlyphServiceEnabled()) return
        playAnimation(id, settingsRepository.getScreenOffDuration()) { cycles ->
            runPulseEffect(cycles)
        }
    }

    suspend fun runLockPulseAnimation() = anim { p ->
        if (p.lockMain.isEmpty()) return@anim

        val mainSet = p.lockMain.toSet()
        val nonC = p.lockAll.filterNot { it in mainSet }

        for (idx in p.lockMain.indices) {
            if (!isAnimationRunning) break

            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
            val nonCBrightness = (maxBrightness * 0.5f).toInt()

            nonC.forEach { builder.buildChannel(it, nonCBrightness) }

            for (j in 0..idx) {
                val brightness = if (idx == 0 || j == idx) {
                    maxBrightness
                } else {
                    (maxBrightness * (0.3f + 0.7f * (j.toFloat() / idx))).toInt()
                }
                builder.buildChannel(p.lockMain[j], brightness)
            }

            toggleFrame(builder, 100L)
        }

        toggleChannels(p.lockAll, delayMs = 700L)
    }

    suspend fun runSpiralAnimation() = anim { p ->
        if (p.type == DeviceType.PHONE3A) {
            runPhone3aSpiralInternal(p)
        } else {
            runSpiralOrder(p)
        }
    }

    suspend fun runHeartbeatAnimation() = anim { p ->
        runHeartbeat(p)
    }

    suspend fun runMatrixRainAnimation() = anim { p ->
        when (p.type) {
            DeviceType.PHONE1 -> runMatrixForSegments(p.all, 20, 3, 8, 100L, 50L, 200)
            DeviceType.PHONE2 -> runMatrixForSegments(p.all, 25, 4, 10, 80L, 40L, 150)
            DeviceType.PHONE2A -> runMatrixForSegments(p.all, 30, 5, 12, 70L, 35L, 120)
            DeviceType.PHONE3A -> runMatrixForSegments(p.all, 35, 6, 15, 60L, 30L, 100)
        }
    }

    suspend fun runFireworksAnimation() = anim { p ->
        when (p.type) {
            DeviceType.PHONE1 -> runFireworksForSegments(p.all, 5, 5, 10, 300L, 500L, 200L)
            DeviceType.PHONE2 -> runFireworksForSegments(p.all, 6, 8, 15, 250L, 400L, 150L)
            DeviceType.PHONE2A -> runFireworksForSegments(p.all, 7, 10, 20, 200L, 350L, 100L)
            DeviceType.PHONE3A -> runFireworksForSegments(p.all, 8, 12, 25, 180L, 300L, 80L)
        }
    }

    suspend fun runDNAHelixAnimation() = anim { p ->
        when (p.type) {
            DeviceType.PHONE1 -> runDNAHelixForSegments(p.all, 3, 150L, 50L)
            DeviceType.PHONE2 -> runDNAHelixForSegments(p.all, 3, 120L, 40L)
            DeviceType.PHONE2A -> runDNAHelixForSegments(p.all, 3, 100L, 30L)
            DeviceType.PHONE3A -> runDNAHelixForSegments(p.all, 3, 80L, 25L)
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
                val e = listOf(6)
                val d = (7..14).toList()
                val all = a + b + c + e + d

                DeviceProfile(
                    type = DeviceType.PHONE1,
                    all = all,
                    c = c,
                    a = a,
                    b = b,
                    d = d,
                    e = e,
                    cOther = emptyList(),
                    waveGroups = listOf(AnimGroup(all, WAVE_PHONE1_STEP, 50L)),
                    beedahGroups = listOf(AnimGroup(all, WAVE_PHONE1_STEP)),
                    spiralOrder = e + a + b + c + d,
                    spiralStep = 100L,
                    pulseSegments = a + b + e,
                    lockMain = c,
                    lockAll = all,
                    zones = listOf(
                        a to "A Zone",
                        b to "B Zone",
                        c to "C Zone",
                        e to "E Zone",
                        d to "D Zone"
                    ),
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
                    c1SeqMain = c,
                    c1SeqSupport = a + b + e + d,
                    c1SeqStep = 250L,
                    c1SeqHold = 1000L,
                    customPatterns = listOf(
                        all.filterIndexed { index, _ -> index % 2 == 0 },
                        all.filterIndexed { index, _ -> index % 2 == 1 }
                    ),
                    batteryBar = c
                )
            }

            Common.is22111() -> {
                val a = listOf(0, 1)
                val b = listOf(2)
                val c1 = (3..18).toList()
                val cOther = (19..23).toList()
                val e = listOf(24)
                val d = (25..32).toList()
                val all = a + b + c1 + cOther + e + d

                DeviceProfile(
                    type = DeviceType.PHONE2,
                    all = all,
                    c = c1,
                    a = a,
                    b = b,
                    d = d,
                    e = e,
                    cOther = cOther,
                    waveGroups = listOf(AnimGroup(all, WAVE_PHONE2_STEP, 30L)),
                    beedahGroups = listOf(AnimGroup(all, WAVE_PHONE2_STEP)),
                    spiralOrder = e + a + b + c1 + cOther + d,
                    spiralStep = 80L,
                    pulseSegments = a + b + e,
                    lockMain = c1,
                    lockAll = all,
                    zones = listOf(
                        a to "A Zone",
                        b to "B Zone",
                        c1 to "C1 Zone",
                        cOther to "C Other Zone",
                        e to "E Zone",
                        d to "D Zone"
                    ),
                    channelMap = mapOf(
                        1 to a,
                        2 to b,
                        3 to c1,
                        4 to cOther,
                        5 to e,
                        6 to d,
                        7 to (a + b),
                        8 to all,
                        9 to all
                    ),
                    c1SeqMain = c1,
                    c1SeqSupport = a + b + cOther + d + e,
                    c1SeqStep = 250L,
                    c1SeqHold = 1000L,
                    customPatterns = listOf(
                        c1.filterIndexed { index, _ -> index % 2 == 0 },
                        c1.filterIndexed { index, _ -> index % 2 == 1 }
                    ),
                    batteryBar = c1
                )
            }

            Common.is23111() || Common.is23113() -> {
                val c = (0..23).toList()
                val a = listOf(25)
                val b = listOf(24)
                val all = (0..25).toList()

                DeviceProfile(
                    type = DeviceType.PHONE2A,
                    all = all,
                    c = c,
                    a = a,
                    b = b,
                    d = emptyList(),
                    e = emptyList(),
                    cOther = emptyList(),
                    waveGroups = listOf(
                        AnimGroup(c, WAVE_PHONE2A_STEP, 30L),
                        AnimGroup(listOf(25, 24), WAVE_PHONE2A_STEP * 2, 50L)
                    ),
                    beedahGroups = listOf(
                        AnimGroup(c, WAVE_PHONE2A_STEP),
                        AnimGroup(listOf(25, 24), WAVE_PHONE2A_STEP * 2)
                    ),
                    spiralOrder = a + b + c,
                    spiralStep = 70L,
                    pulseSegments = a + b,
                    lockMain = c,
                    lockAll = all,
                    zones = listOf(
                        c.take(12) to "C1 Zone",
                        c.drop(12) to "C2 Zone",
                        b to "B Zone",
                        a to "A Zone"
                    ),
                    channelMap = mapOf(
                        1 to a,
                        2 to b,
                        3 to (0..11).toList(),
                        4 to (12..23).toList(),
                        5 to c,
                        6 to listOf(24, 25),
                        7 to all,
                        8 to all
                    ),
                    c1SeqMain = c,
                    c1SeqSupport = a + b,
                    c1SeqStep = 180L,
                    c1SeqHold = 1500L,
                    customPatterns = listOf(
                        c.filterIndexed { index, _ -> index % 2 == 0 },
                        c.filterIndexed { index, _ -> index % 2 == 1 }
                    ),
                    batteryBar = c
                )
            }

            Common.is24111() -> {
                val c = (0..19).toList()
                val a = (20..30).toList()
                val b = (31..35).toList()
                val all = c + a + b

                DeviceProfile(
                    type = DeviceType.PHONE3A,
                    all = all,
                    c = c,
                    a = a,
                    b = b,
                    d = emptyList(),
                    e = emptyList(),
                    cOther = emptyList(),
                    waveGroups = listOf(
                        AnimGroup(c, WAVE_PHONE3A_STEP, 25L),
                        AnimGroup(a, WAVE_PHONE3A_STEP + 20L, 30L),
                        AnimGroup(b, WAVE_PHONE3A_STEP + 40L, 40L)
                    ),
                    beedahGroups = listOf(
                        AnimGroup(c, WAVE_PHONE3A_STEP),
                        AnimGroup(a, WAVE_PHONE3A_STEP + 20L),
                        AnimGroup(b, WAVE_PHONE3A_STEP + 40L)
                    ),
                    spiralOrder = all,
                    spiralStep = 60L,
                    pulseSegments = listOf(25, 33, 9),
                    lockMain = c,
                    lockAll = all,
                    zones = listOf(
                        c.take(10) to "C1 Zone",
                        c.drop(10) to "C2 Zone",
                        a to "A Zone",
                        b to "B Zone"
                    ),
                    channelMap = mapOf(
                        1 to a,
                        2 to b,
                        3 to (0..9).toList(),
                        4 to (10..19).toList(),
                        5 to c,
                        6 to (a + b),
                        7 to all,
                        8 to all
                    ),
                    c1SeqMain = c,
                    c1SeqSupport = a + b,
                    c1SeqStep = 200L,
                    c1SeqHold = 2000L,
                    customPatterns = listOf(
                        c.filterIndexed { index, _ -> index % 2 == 0 },
                        c.filterIndexed { index, _ -> index % 2 == 1 }
                    ),
                    batteryBar = c
                )
            }

            else -> null
        }
    }

    // endregion

    // region Core animation runners

    private suspend fun anim(
        requireService: Boolean = true,
        reset: Boolean = true,
        block: suspend (DeviceProfile) -> Unit
    ) {
        if (requireService && !isGlyphServiceEnabled()) return
        if (!glyphManager.isNothingPhone()) return

        val p = profile ?: return
        isAnimationRunning = true

        try {
            if (reset) {
                resetGlyphs()
                delay(CLEANUP_DELAY)
            }
            block(p)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Animation error: ${e.message}")
        } finally {
            isAnimationRunning = false
            glyphManager.turnOffAll()
        }
    }

    private fun resetGlyphs() {
        runCatching { glyphManager.turnOffAll() }
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
        }.getOrNull()
    }

    private suspend fun toggleFrame(builder: GlyphFrame.Builder?, delayMs: Long = 0L) {
        if (builder == null) return
        try {
            glyphManager.mGM?.toggle(builder.build())
            if (delayMs > 0) delay(delayMs)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "toggleFrame error: ${e.message}")
            if (delayMs > 0) delay(delayMs)
        }
    }

    private suspend fun toggleChannels(
        channels: Collection<Int>,
        brightness: Int = maxBrightness,
        delayMs: Long = 0L
    ) {
        if (channels.isEmpty()) return
        try {
            createFrameBuilder(channels, brightness)?.let {
                glyphManager.mGM?.toggle(it.build())
            }
            if (delayMs > 0) delay(delayMs)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "toggleChannels error: ${e.message}")
            if (delayMs > 0) delay(delayMs)
        }
    }

    private suspend fun toggleSingle(channel: Int, onMs: Long, offMs: Long = 0L) {
        try {
            createFrameBuilder(listOf(channel))?.let {
                glyphManager.mGM?.toggle(it.build())
            }
            if (onMs > 0) delay(onMs)
            glyphManager.turnOffAll()
            if (offMs > 0) delay(offMs)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "toggleSingle error: ${e.message}")
            if (onMs > 0) delay(onMs)
        }
    }

    private suspend fun flashChannels(
        channels: List<Int>,
        repeats: Int,
        onMs: Long,
        offMs: Long
    ) {
        repeat(repeats) {
            if (!isAnimationRunning) return
            toggleChannels(channels, delayMs = onMs)
            glyphManager.turnOffAll()
            delay(offMs)
        }
    }

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
        repeat(3) {
            if (!isAnimationRunning) return
            glyphManager.turnOffAll()
            delay(PULSE_OFF_DURATION)
            toggleChannels(channels, delayMs = PULSE_ON_DURATION)
        }
    }

    private suspend fun runHeartbeat(p: DeviceProfile) {
        repeat(3) {
            if (!isAnimationRunning) return

            toggleChannels(p.all, delayMs = 200L)
            glyphManager.turnOffAll()
            delay(100L)

            toggleChannels(p.all, delayMs = 200L)
            glyphManager.turnOffAll()
            delay(300L)
        }
    }

    private suspend fun runSpiralOrder(p: DeviceProfile) {
        val segments = p.spiralOrder.ifEmpty { p.all }
        if (segments.isEmpty()) return

        val size = segments.size

        for (i in segments.indices) {
            if (!isAnimationRunning) return
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return

            for (j in 0..i) {
                val brightness = (maxBrightness * (0.6f + (j.toFloat() / size) * 0.4f)).toInt()
                builder.buildChannel(segments[j], brightness)
            }

            toggleFrame(builder, p.spiralStep)
        }

        toggleChannels(segments, delayMs = 250L)

        for (i in segments.indices.reversed()) {
            if (!isAnimationRunning) return
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return

            val denom = (size - i).coerceAtLeast(1)
            for (j in i until size) {
                val brightness = (maxBrightness * (0.6f + ((size - j).toFloat() / denom) * 0.4f)).toInt()
                builder.buildChannel(segments[j], brightness)
            }

            toggleFrame(builder, p.spiralStep)
        }

        val center = segments.firstOrNull() ?: return
        toggleSingle(center, 200L, 100L)
        toggleSingle(center, 200L, 0L)
    }

    private suspend fun runPhone3aSpiralInternal(p: DeviceProfile) {
        val stepDuration = 60L

        for (i in p.c.indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            for (j in 0..i) {
                val brightness = if (j == i) {
                    maxBrightness
                } else {
                    (maxBrightness * (0.3f + (j.toFloat() / i.coerceAtLeast(1)) * 0.4f)).toInt()
                }
                builder.buildChannel(p.c[j], brightness)
            }

            toggleFrame(builder, stepDuration)
        }

        for (i in p.a.indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            p.c.forEach { builder.buildChannel(it, (maxBrightness * 0.3f).toInt()) }

            for (j in 0..i) {
                val brightness = if (j == i) {
                    maxBrightness
                } else {
                    (maxBrightness * (0.5f + (j.toFloat() / i.coerceAtLeast(1)) * 0.5f)).toInt()
                }
                builder.buildChannel(p.a[j], brightness)
            }

            toggleFrame(builder, stepDuration + 10L)
        }

        for (i in p.b.indices) {
            if (!isAnimationRunning) break
            val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break

            p.c.forEach { builder.buildChannel(it, (maxBrightness * 0.4f).toInt()) }
            p.a.forEach { builder.buildChannel(it, (maxBrightness * 0.7f).toInt()) }

            for (j in 0..i) {
                builder.buildChannel(p.b[j], maxBrightness)
            }

            toggleFrame(builder, stepDuration + 20L)
        }

        val first = createFrameBuilder(p.all) ?: return
        toggleFrame(first, 500L)
        glyphManager.turnOffAll()
        delay(200L)

        val second = createFrameBuilder(p.all)
        toggleFrame(second, 300L)
    }

    private suspend fun runC1Phase(p: DeviceProfile, forward: Boolean) {
        val main = p.c1SeqMain
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
            p.c1SeqSupport.forEach { builder.buildChannel(it, supportBrightness) }

            toggleFrame(builder, p.c1SeqStep)
        }
    }

    private suspend fun runMatrixForSegments(
        segments: List<Int>,
        drops: Int,
        minLength: Int,
        maxLength: Int,
        stepDelay: Long,
        offDelay: Long,
        brightnessDecrement: Int
    ) {
        if (segments.isEmpty()) return

        repeat(drops) {
            if (!isAnimationRunning) return@repeat

            val safeMax = maxLength
                .coerceAtMost(segments.size + 1)
                .coerceAtLeast(minLength + 1)

            val dropLength = Random.nextInt(minLength, safeMax).coerceAtMost(segments.size)
            val maxStart = (segments.size - dropLength).coerceAtLeast(0)
            val startIndex = if (maxStart == 0) 0 else Random.nextInt(maxStart)

            for (i in 0 until dropLength) {
                if (!isAnimationRunning) break

                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return
                val segmentIndex = startIndex + i

                if (segmentIndex < segments.size) {
                    val brightness = (maxBrightness - (i * brightnessDecrement)).coerceAtLeast(0)
                    builder.buildChannel(segments[segmentIndex], brightness)
                }

                toggleFrame(builder, stepDelay)
                glyphManager.turnOffAll()
                delay(offDelay)
            }
        }
    }

    private suspend fun runFireworksForSegments(
        segments: List<Int>,
        fireworks: Int,
        minExplosion: Int,
        maxExplosion: Int,
        launchDelay: Long,
        explosionDelay: Long,
        fadeDelay: Long
    ) {
        if (segments.isEmpty()) return

        repeat(fireworks) {
            if (!isAnimationRunning) return@repeat

            toggleChannels(listOf(segments.random()), delayMs = launchDelay)

            val safeMax = maxExplosion
                .coerceAtMost(segments.size + 1)
                .coerceAtLeast(minExplosion + 1)

            val explosionCount = Random.nextInt(minExplosion, safeMax).coerceAtMost(segments.size)
            val explosionSegments = segments.shuffled().take(explosionCount)

            toggleChannels(explosionSegments, delayMs = explosionDelay)
            glyphManager.turnOffAll()
            delay(fadeDelay)
        }
    }

    private suspend fun runDNAHelixForSegments(
        segments: List<Int>,
        rotations: Int,
        stepDelay: Long,
        offDelay: Long
    ) {
        if (segments.isEmpty()) return

        val size = segments.size

        repeat(rotations) {
            if (!isAnimationRunning) return@repeat

            for (i in segments.indices) {
                if (!isAnimationRunning) break

                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: return

                builder.buildChannel(segments[i], maxBrightness)
                builder.buildChannel(segments[(i + size / 2) % size], maxBrightness)

                toggleFrame(builder, stepDelay)
                glyphManager.turnOffAll()
                delay(offDelay)
            }
        }
    }

    // endregion

    // region Battery

    private suspend fun animateBattery(
        p: DeviceProfile,
        batteryPercentage: Int,
        isCharging: Boolean,
        durationMillis: Long,
        onProgressUpdate: (Float) -> Unit
    ) {
        if (durationMillis <= 0) return

        val bar = p.batteryBar
        if (bar.isEmpty()) return

        val total = bar.size
        val target = (batteryPercentage / 100f * total).toInt().coerceIn(0, total)

        var current = 0
        var step = 0
        val startTime = System.currentTimeMillis()

        while (isAnimationRunning) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= durationMillis) break

            onProgressUpdate((elapsed / durationMillis.toFloat()).coerceIn(0f, 1f))

            try {
                val builder = glyphManager.mGM?.getGlyphFrameBuilder() ?: break
                val base = calculateBaseBrightness(batteryPercentage, isCharging)

                val delayTime = if (current < target) {
                    current++
                    BATTERY_FILL_STEP_DELAY
                } else {
                    BATTERY_STEP_DELAY
                }

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
                                if (batteryPercentage >= 20) {
                                    addPlayfulGlow(builder, bar, batteryPercentage, base, step)
                                } else if (current > 0) {
                                    addAlert(builder, bar[current - 1], step)
                                }
                            }
                            DeviceType.PHONE2 -> {
                                if (batteryPercentage >= 20) {
                                    addPlayfulGlowPhone2(builder, p, step)
                                    addWaveAnimation(
                                        builder,
                                        bar,
                                        (current.toFloat() / total * 100f).toInt(),
                                        base,
                                        step
                                    )
                                } else {
                                    p.a.forEach { addAlert(builder, it, step) }
                                }
                            }
                            DeviceType.PHONE2A -> {
                                if (batteryPercentage < 20) {
                                    p.a.firstOrNull()?.let { addAlert(builder, it, step) }
                                }
                            }
                            DeviceType.PHONE3A -> {
                                if (batteryPercentage < 20 && current > 0) {
                                    addAlert(builder, bar[current - 1], step)
                                }
                            }
                        }
                    }
                }

                glyphManager.mGM?.toggle(builder.build())
                delay(delayTime)
                step++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Battery animation error: ${e.message}")
                delay(BATTERY_STEP_DELAY)
                step++
            }
        }
    }

    private fun calculateBaseBrightness(batteryPercentage: Int, isCharging: Boolean): Int {
        return when {
            batteryPercentage < 20 -> maxBrightness / 3
            isCharging -> maxBrightness
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

    private fun addPlayfulGlow(
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
                val twinkleChannel = bar[unused[Random.nextInt(unused.size)]]
                builder.buildChannel(twinkleChannel, (maxBrightness * 0.5f).toInt())
            }
        }
    }

    private fun addPlayfulGlowPhone2(
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
        batteryPercentage: Int,
        baseBrightness: Int,
        step: Int
    ) {
        val total = segments.size.toFloat()
        val filledLevel = batteryPercentage / 100f * total

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

    // region Selection helpers

    private suspend fun playAnimation(
        id: String,
        durationMs: Long,
        fallback: suspend (Int) -> Unit
    ) {
        val cycles = (durationMs / 500L).toInt().coerceAtLeast(1)
        val key = id.trim().uppercase(java.util.Locale.ROOT)

        when (key) {
            "C1" -> runC1SequentialAnimation()
            "WAVE" -> runWaveAnimation()
            "BEEDAH" -> runBeedahAnimation()
            "LOCK" -> runLockPulseAnimation()
            "PULSE" -> runPulseEffect(cycles)
            "SPIRAL" -> runSpiralAnimation()
            "HEARTBEAT" -> runHeartbeatAnimation()
            "MATRIX" -> runMatrixRainAnimation()
            "FIREWORKS" -> runFireworksAnimation()
            "DNA" -> runDNAHelixAnimation()
            else -> fallback(cycles)
        }
    }

    private fun mapC1Index(p: DeviceProfile, c1Index: Int): Int {
        return when (p.type) {
            DeviceType.PHONE1 -> if (c1Index in 1..4) c1Index + 1 else -1
            DeviceType.PHONE2 -> if (c1Index in 1..16) p.c1SeqMain[c1Index - 1] else -1
            DeviceType.PHONE2A -> if (c1Index in 1..24) c1Index - 1 else -1
            DeviceType.PHONE3A -> if (c1Index in 1..20) c1Index - 1 else -1
        }
    }

    private fun isGlyphServiceEnabled(): Boolean {
        val enabled = settingsRepository.getGlyphServiceEnabled()
        if (!enabled) {
            Log.d(TAG, "Glyph service disabled – animation call ignored")
        }
        return enabled
    }

    // endregion
}