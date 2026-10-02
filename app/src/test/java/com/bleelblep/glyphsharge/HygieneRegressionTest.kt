package com.bleelblep.glyphsharge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards for the second round of review fixes.
 *
 * Each of these was invisible at its own call site: a dialog saving a setting
 * it should have held, a message dropped because two identical ones arrived in
 * a row, a sensor that refused to start and reported itself working. They are
 * asserted across files here for the same reason — the defect was in the gap
 * between two pieces of code that each looked correct on their own.
 */
class HygieneRegressionTest {

    private val mainRoot = File("src/main/java/com/bleelblep/glyphsharge")

    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun ui(vararg path: String): String =
        codeOf(File(mainRoot, "ui/" + path.joinToString("/")))

    @Test
    fun `the network metered flag means what its own docs say`() {
        val code = codeOf(File(mainRoot, "glyph/net/NetworkSource.kt"))

        // `isActiveNetworkMetered` already answers "should the user be charged",
        // which is what `NetworkSnapshot.metered` documents itself as. Negating
        // it handed every Lua script the opposite of the truth, and the comment
        // that rationalised the inversion was worse than the bug because it
        // stopped the next reader from fixing it.
        assertFalse(
            "metered must not be inverted against its own documented contract",
            code.contains("metered = !manager.isActiveNetworkMetered"),
        )
        assertTrue(
            "metered should come straight from the platform answer",
            code.contains("metered = manager.isActiveNetworkMetered"),
        )
    }

    @Test
    fun `a refused sensor registration is not recorded as success`() {
        val code = codeOf(File(mainRoot, "glyph/sensor/SensorSource.kt"))

        // `registerListener` returns false rather than throwing when it
        // declines. Checking only for an exception kept the 50 Hz thread alive
        // for the whole run and handed the script STILL forever.
        assertFalse(
            "a `false` return from registerListener is a failure, not a success",
            code.contains("registered.isFailure"),
        )
        assertTrue(
            "the registration result should be compared against true",
            code.contains("registered != true"),
        )
    }

    @Test
    fun `the sensor reference and its registration cannot be separated`() {
        val code = codeOf(File(mainRoot, "glyph/sensor/SensorSource.kt"))
        val acquire = code.substringAfter("private fun acquireIfIdle()")
            .substringBefore("\n    private fun register()")

        // Taking the count and registering are two steps; a `stop` in between
        // found nothing to unregister and the registration then outlived the
        // run by the life of the process. The re-check is what closes it.
        val registerAt = acquire.indexOf("register()")
        // `lastIndexOf`, not `indexOf`: the read at the top of the loop is the
        // *first* thing in the function, and the assertion is about the one
        // that comes after registering.
        val recheckAt = acquire.lastIndexOf("holders.get()")
        assertTrue("acquireIfIdle should register", registerAt >= 0)
        assertTrue(
            "acquireIfIdle must re-read the count after registering, or a stop that " +
                "slipped through leaves a listener nobody will ever unregister",
            recheckAt > registerAt,
        )
    }

    @Test
    fun `a failed master switch-on does not leave the flag lying`() {
        val code = codeOf(File(mainRoot, "services/GlyphServiceSwitch.kt"))

        // The flag was written before the services were started and never
        // rolled back, so a `startForegroundService` refused from the background
        // left the tile reading ACTIVE with none of the eight services running.
        val catchBlock = code.substringAfter("} catch (e: Exception) {\n        Log.e(TAG, \"Turning the Glyph service on failed\"")
        assertTrue(
            "a failed switch-on must roll the flag back",
            catchBlock.contains("saveGlyphServiceEnabled(enabled = false)"),
        )
        assertTrue(
            "and must stop whatever did manage to start",
            catchBlock.contains("serviceController.stopAll()"),
        )
    }

    @Test
    fun `one feature failing to start does not skip the rest`() {
        val code = codeOf(File(mainRoot, "services/FeatureServiceController.kt"))
        val startAll = code.substringAfter("fun startAllEnabled()")
            .substringBefore("\n    /**")

        // A `forEach` with an unguarded `start()` aborted on the first throw.
        // Matched on the guard rather than the exact argument, because the
        // loop walks the registry now and therefore passes `spec.feature` —
        // see `FeatureRegistryCoverageTest` for why that matters.
        assertTrue(
            "each feature start should be guarded so one failure cannot skip the others",
            Regex("""runCatching \{ start\(.*?\) \}""").containsMatchIn(startAll),
        )
    }

    @Test
    fun `a tile toggle cannot crash the process`() {
        val code = codeOf(File(mainRoot, "tiles/MusicVisualizerTileService.kt"))
        val onClick = code.substringAfter("override fun onClick()")
            .substringBefore("\n    /**")

        // An exception out of a `launch` on Main reaches the thread's default
        // handler, which kills the app — and left `switching` stuck true, so the
        // tile was dead for the rest of the process's life.
        assertTrue(
            "the toggle coroutine needs a try/catch",
            onClick.contains("catch (e: Exception)"),
        )
        assertTrue(
            "`switching` must be cleared in a finally, not on each early return",
            (onClick.contains("finally {")) && (onClick.contains("switching = false")),
        )
    }

    @Test
    fun `dialog state survives a configuration change`() {
        // The confirmation flow's `showSettings` and every feature card's
        // `showDialog` were plain `remember`, so rotating with a dialog open
        // dismissed it and threw away the configuration step.
        val flow = ui("components/dialogs/FeatureConfirmationFlow.kt")
        assertTrue(
            "FeatureConfirmationFlow.showSettings must be saveable",
            ("rememberSaveable" in flow) && ("var showSettings" in flow),
        )

        val cards = ui("components/FeatureCards.kt")
        assertFalse(
            "no feature card should hold its dialog state in a plain remember",
            "var showDialog by remember {" in cards,
        )
    }

    @Test
    fun `the low battery config cannot be silently dropped`() {
        val code = ui("components/LowBattery.kt")

        // A nullable `pendingConfig` plus `pendingConfig?.let(onEnableAlert)`
        // meant a lost config closed the dialog and enabled nothing, with no
        // message. Non-null makes that outcome unrepresentable.
        assertFalse(
            "pendingConfig must not be nullable, or Enable can close the dialog doing nothing",
            "pendingConfig?.let(onEnableAlert)" in code,
        )
        assertTrue(
            "LowBatteryAlertConfig must be saveable across a configuration change",
            ("java.io.Serializable" in code) && ("rememberSaveable" in code),
        )
    }

    @Test
    fun `every enable dialog can be scrolled`() {
        // PowerPeek and ChargingAnimation were the two of eight with no scroll
        // container. At a non-default font scale — which this app offers
        // sliders for — their second card and slider ended up outside the
        // dialog while the buttons stayed reachable: a dialog that cannot be
        // configured.
        listOf("PowerPeek.kt", "ChargingAnimation.kt").forEach { name ->
            val code = ui("components/$name")
            val dialog = code.substringAfter("AlertDialog(")
            assertTrue(
                "$name has no scrollable dialog body",
                dialog.contains("verticalScroll(rememberScrollState())"),
            )
        }
    }

    @Test
    fun `no dialog shows a spinner for a save that already finished`() {
        // `isSaving` was set on click and never cleared. The save is a few
        // synchronous SharedPreferences writes, so the spinner described
        // nothing — and on a dialog whose confirm did not close it, it stayed
        // for good.
        listOf(
            "LowBattery.kt", "MusicVisualizer.kt", "PowerPeek.kt", "PulseLock.kt",
            "ScreenOff.kt", "NfcGlyph.kt", "VpnConnected.kt", "ChargingAnimation.kt",
        ).forEach { name ->
            assertFalse(
                "$name still declares an isSaving flag",
                "var isSaving" in ui("components/$name"),
            )
        }
        assertFalse(
            "FeatureSaveButtons should not take a saving flag at all",
            "isSaving" in ui("components/CommonDialogComponents.kt"),
        )
    }

    @Test
    fun `the visualiser saves its mode on Save, not on chip tap`() {
        val code = ui("components/MusicVisualizer.kt")
        val onSave = code.substringAfter("onSave = {")

        assertTrue(
            "the animation id should be written in onSave, alongside the other two",
            onSave.contains("saveMusicVizAnimationId("),
        )
        assertTrue(
            "and all three should be written there together",
            (onSave.contains("saveMusicVizSensitivity(")) &&
                (onSave.contains("saveMusicVizScreenOffOnly(")),
        )
        // Everything before onSave is the chip row; a write there survives Cancel.
        val beforeSave = code.substringBefore("onSave = {")
        assertFalse(
            "writing the mode from the chip handler left it changed on disk after Cancel",
            beforeSave.contains("saveMusicVizAnimationId("),
        )
    }

    @Test
    fun `one-shot messages are not conflated`() {
        // StateFlow conflates equal values, and several of these strings take
        // no argument — deleting two scripts in a row produced one toast.
        listOf(
            "viewmodel/AnimationStudioViewModel.kt",
            "viewmodel/ScriptStoreViewModel.kt",
        ).forEach { name ->
            val code = ui(name)
            assertTrue(
                "$name should use a Channel for its messages",
                "Channel<String>" in code,
            )
            assertFalse(
                "$name should not assign a message StateFlow's value",
                "_messages.value =" in code,
            )
        }
    }

    @Test
    fun `lazy lists identify their rows`() {
        // Rows hold their own UI state (an open overflow menu). An unkeyed
        // LazyColumn preserves that by index, so deleting row 3 left its menu
        // on row 4 — and "Duplicate" then duplicated the wrong script.
        listOf(
            "screens/animations/AnimationListScreen.kt",
            "screens/animations/ScriptStoreScreen.kt",
        ).forEach { name ->
            val code = ui(name)
            assertTrue(
                "$name should key its rows",
                (code.contains("items(")) && (code.contains("key = { it.id })")),
            )
        }
    }

    @Test
    fun `haptics respect the system setting and do not double up`() {
        val code = ui("utils/HapticUtils.kt")

        // Every tap used to fire `performHapticFeedback(LongPress)` *and* a raw
        // 50 ms vibration. The second ignored the touch-feedback setting
        // entirely, so a user who had switched haptics off was buzzed anyway.
        val gate = code.substringAfter("private fun haptic(").substringBefore("private fun performMediumHaptic")
        assertTrue(
            "the gate should read the system haptic setting",
            gate.contains("if (!hapticsEnabled(context)) return"),
        )
        assertTrue(
            "and the raw vibration must come after it, not regardless of it",
            gate.indexOf("hapticsEnabled(context)) return") < gate.indexOf("performCustomVibration"),
        )
        assertTrue(
            "the setting itself should be the documented one",
            code.contains("Settings.System.HAPTIC_FEEDBACK_ENABLED"),
        )
        assertFalse(
            "a missing vibrator is an ordinary device, not an NPE",
            "as VibratorManager" in code,
        )
    }

    @Test
    fun `MainActivity has no empty bodies behind documented promises`() {
        val code = codeOf(File(mainRoot, "MainActivity.kt"))

        // Three of these were `{ /* ... */ }`, which reads as finished work
        // rather than as a hole. A log export that wrote a zero-byte file and a
        // session restore that restored nothing.
        assertFalse(
            "MainActivity still has a stub body",
            "{ /* ... */ }" in code,
        )
        assertTrue(
            "the log export should use what LoggingManager already assembles",
            code.contains("LoggingManager.exportLogs()"),
        )
    }

    @Test
    fun `slider values are clamped on the way in as well as out`() {
        // `Slider` requires `value` to be inside `valueRange` and throws
        // otherwise, so a stored threshold outside 5..50 took the dialog down.
        val code = ui("components/LowBattery.kt")
        assertTrue(
            "the threshold should be clamped where it is read",
            "getLowBatteryThreshold().toFloat().coerceIn(5f, 50f)" in code,
        )
    }
}
