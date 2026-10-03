package com.bleelblep.glyphsharge.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the service lifecycle, which now lives in one place.
 *
 * Every assertion here used to be a per-service check, and every one of them
 * was written *after* the corresponding defect was found and fixed by hand.
 * That is the shape that produced the defects: eight services each hand-rolling
 * the same forty lines, diverging in five observable ways, and the fifth only
 * surfacing when somebody went looking for it.
 *
 * So the assertions moved up a level. What is checked now is that a service
 * cannot reintroduce the bug — that the base owns the lifecycle, and that a
 * subclass has no way to opt out of it.
 */
class FeatureServiceLifecycleTest {

    private val servicesDir = File("src/main/java/com/bleelblep/glyphsharge/services")

    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun services(): List<File> =
        servicesDir.listFiles { f: File -> f.extension == "kt" }.orEmpty().sortedBy { it.name }

    private val base = codeOf(File(servicesDir, "FeatureService.kt"))

    @Test
    fun `every feature service extends the base`() {
        val offenders = FeatureSpecs.all.asSequence().map { it.serviceClass.simpleName }.filter { name ->
            val file = File(servicesDir, "$name.kt")
            !file.exists() || !codeOf(file).contains("FeatureService()")
        }.toList()

        assertTrue(
            "These feature services are not on FeatureService, so they still hand-roll " +
                "the lifecycle:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no feature service re-declares a lifecycle method the base owns`() {
        // `final` in the base is what actually stops this, but a subclass that
        // somehow declares one would be silently shadowing the gate rather than
        // failing — and `override` on a final method is a compile error, so this
        // is belt and braces for the case where someone removes the `final`.
        val owned = listOf("fun onStartCommand(", "fun onDestroy(", "fun onBind(", "fun onTaskRemoved(")

        val offenders = FeatureSpecs.all.asSequence().map { it.serviceClass.simpleName }.filter { name ->
            val file = File(servicesDir, "$name.kt")
            file.exists() && owned.any { codeOf(file).contains("override $it") }
        }.toList()

        assertTrue(
            "These services override a method FeatureService owns:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `onCreate cannot be final, because Hilt has to override it`() {
        // Hilt generates `Hilt_<Service> extends FeatureService` and overrides
        // `onCreate()` there to run `inject()`. A `final` `onCreate` therefore
        // fails `javac` for *every* annotated service — and does so silently
        // from `compileDebugKotlin`, which only runs Kotlin and never sees the
        // generated Java. `assembleDebug` is the task that catches it.
        assertFalse(
            "FeatureService.onCreate must stay open: Hilt's generated base overrides it",
            base.contains("final override fun onCreate()"),
        )
        assertTrue(
            "and it must still create the channel before handing over",
            (base.contains("createNotificationChannel()")) &&
                (base.contains("onFeatureCreated()")),
        )
    }

    @Test
    fun `the base tears both scopes down and releases the wake lock`() {
        // `animationJob` was a root SupervisorJob with no parent, so cancelling
        // `serviceJob` never reached it: an animation kept drawing after its
        // service was destroyed, still holding the strip, the WakeLock and a
        // strong reference to the dead Service.
        val onDestroy = base.substringAfter("override fun onDestroy()")
            .substringBefore("\n    }")

        assertTrue(
            "the animation scope must be cancelled, not just the service scope",
            onDestroy.contains("animationScope.cancel()"),
        )
        assertTrue(
            "the service scope must be cancelled too",
            onDestroy.contains("serviceScope.cancel()"),
        )
        assertTrue(
            "and the wake lock released unconditionally",
            (onDestroy.contains("wakeLock.isHeld")) && (onDestroy.contains("release()")),
        )
    }

    @Test
    fun `the base tears the animation scope down before releasing the wake lock`() {
        // The order is the point: released first, the animation reaches for a
        // lock that is no longer there.
        val onDestroy = base.substringAfter("override fun onDestroy()")
            .substringBefore("\n    }")

        assertTrue(
            "cancel must come before release, or a running animation outlives its own lock",
            (onDestroy.indexOf("animationScope.cancel()")) < (onDestroy.indexOf("release()")),
        )
    }

    @Test
    fun `every unregisterReceiver is guarded`() {
        // `IllegalArgumentException: Receiver not registered` out of onDestroy
        // is an uncaught crash whenever onCreate failed before the registration
        // completed — no accelerometer, a restricted profile, a failed Hilt
        // graph. Two services did this; the rest already had the pattern.
        val offenders = services().asSequence().filter { file ->
            val code = codeOf(file)
            code.contains("unregisterReceiver(") &&
                !code.contains("runCatching { unregisterReceiver(") &&
                !code.contains("receiverRegistered") &&
                !code.contains("callbackRegistered")
        }.map { it.name }.toList()

        assertTrue(
            "These services call unregisterReceiver unguarded:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the base owns the run gate and the stop action`() {
        val onStart = base.substringAfter("override fun onStartCommand(")
            .substringBefore("\n    /**")

        assertTrue(
            "the stop action must be answered before the gate",
            (onStart.indexOf("stopAction")) < (onStart.indexOf("!isRunnable")),
        )
        assertTrue(
            "a service that is not runnable must shut down rather than sit there",
            onStart.contains("shutDown()"),
        )
        assertTrue(
            "and the bypass hook must be consulted before the gate, so 'Test' is not " +
                "refused by a feature switch it deliberately ignores",
            (onStart.indexOf("bypassesRunGate")) < (onStart.indexOf("!isRunnable")),
        )
    }

    @Test
    fun `the gate is asked only once per place, and the places are sealed`() {
        assertTrue(
            "onTaskRemoved must consult the gate too, or a swiped-away service " +
                "resurrects with its switch off",
            base.contains("if (!isRunnable) return"),
        )
        assertTrue(
            "and it must also honour the no-restart property, which is how the " +
                "visualiser stays down (a MediaProjection token cannot be re-obtained)",
            base.contains("if (!restartsOnTaskRemoval) return"),
        )
    }

    @Test
    fun `the Glyph session is ensured before the strip is contended`() {
        // `acquire` used to take the strip mutex and *then* wait up to two
        // seconds for the Glyph service to bind, while every other feature
        // gives up after 500 ms. One trigger arriving during a reconnect
        // therefore dropped every other trigger in that window — and the
        // feature that got in could not draw either.
        val code = codeOf(
            File("src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt"),
        )

        val acquire = code.substringAfter("suspend fun acquire(").substringBefore("\n    }")
        val ensureAt = acquire.indexOf("ensureSession()")
        val lockAt = acquire.indexOf("tryLockWithin(")

        assertTrue("acquire() should ensure the session", ensureAt >= 0)
        assertTrue("acquire() should still take the strip lock", lockAt >= 0)
        assertTrue(
            "The session must be ensured before contending for the strip, otherwise a " +
                "two-second bind wait starves every other feature's 500 ms timeout",
            ensureAt in (0 until lockAt),
        )
    }

    @Test
    fun `the manifest declares a subtype for every specialUse service`() {
        // Play rejects a `specialUse` service with no
        // PROPERTY_SPECIAL_USE_FGS_SUBTYPE. GlyphForegroundService was missing
        // one while both of its copy-pasted neighbours had been given the
        // right text.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared = Regex("""<service\s[^>]*android:name="\.services\.([^"]+)"""")
            .findAll(manifest).map { it.groupValues[1] }.toList()

        assertTrue("the manifest should declare services", declared.isNotEmpty())

        val withoutSubtype = declared.filter { name ->
            val block = Regex("""<service\s[^>]*android:name="\.services\.$name""""")
                .find(manifest)?.value ?: return@filter false
            block.contains("specialUse") && !block.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
        }

        assertTrue(
            "These specialUse services have no subtype property:\n" +
                withoutSubtype.joinToString("\n"),
            withoutSubtype.isEmpty(),
        )
    }

    @Test
    fun `the manifest declares no permission the app never uses`() {
        // SYSTEM_ALERT_WINDOW and WRITE_SETTINGS are two of the most alarming
        // entries on a permission screen for zero functionality, and
        // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is Play-policy-restricted and a
        // common rejection trigger. All three, plus four more, were declared
        // and never referenced.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val kotlinSources = File("src/main/java").walkTopDown()
            .filter { (it.isFile) && (it.extension == "kt") }
            .joinToString("\n") { it.readText() }

        val declared = Regex("""<uses-permission android:name="android\.permission\.([A-Z_]+)"""")
            .findAll(manifest).map { it.groupValues[1] }.toList()

        // Permissions whose only consumer is the platform rather than a name in
        // code: an FGS or notification one, a receiver matched by an <action>,
        // or a capability the SDK uses directly.
        val platformOnly = setOf(
            "FOREGROUND_SERVICE",
            "FOREGROUND_SERVICE_SPECIAL_USE",
            "FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "RECEIVE_BOOT_COMPLETED",
            "VIBRATE",
            "WAKE_LOCK",
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "NFC",
        )

        val unused = declared
            .asSequence()
            .filterNot { it in platformOnly }
            .filterNot { kotlinSources.contains(it) }
            .toList()

        assertTrue(
            "These permissions are declared but never referenced in the app:\n" +
                unused.joinToString("\n"),
            unused.isEmpty(),
        )
    }

}
