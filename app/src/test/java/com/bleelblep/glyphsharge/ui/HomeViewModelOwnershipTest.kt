package com.bleelblep.glyphsharge.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.bleelblep.glyphsharge.services.QuietHoursService
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.inject.Inject

/**
 * Guards the one-owner rule for [HomeViewModel].
 *
 * There were two instances of it, and nothing in the code said so.
 * `MainActivity` built one through `by viewModels()` and drove it;
 * `HomeScreen` asked `hiltViewModel()` for its own, which `NavHost` resolved
 * against the `NavBackStackEntry` it substitutes for
 * `LocalViewModelStoreOwner`. Both are `@HiltViewModel`, both construct
 * cleanly, and neither raises — so the split only showed up as a home screen
 * stuck at "service off" with every card disabled, an NFC hook installed on an
 * object nobody listened to, and a visualiser switch whose request went into a
 * channel nobody drained.
 *
 * A test that renders the whole graph would catch it too, but it would need a
 * `NavHost`, an Activity and a device. What actually went wrong was one call in
 * two files, so that is what is asserted here.
 */
class HomeViewModelOwnershipTest {

    private val sourceRoot = File("src/main/java/com/bleelblep/glyphsharge")

    /**
     * The file with its comments removed.
     *
     * Without this the scan matches the KDoc that explains why the call is
     * gone — which names it, in backticks, on purpose. A regression guard that
     * fails on the explanation of the fix is worse than no guard.
     */
    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun codeOf(path: String): String = codeOf(File(sourceRoot, path))

    @Test
    fun `HomeViewModel is still injectable`() {
        // The rest of this file is only worth anything if Hilt can build it,
        // which is what makes a second instance silent rather than a crash.
        // `@HiltViewModel` is checked by the compiler on every build, and it
        // has CLASS retention, so it is not visible here by design.
        assertTrue(
            "HomeViewModel needs an @Inject constructor for the activity to build it",
            HomeViewModel::class.java.constructors.any { it.isAnnotationPresent(Inject::class.java) },
        )
    }

    @Test
    fun `no file in the tree resolves HomeViewModel for itself`() {
        // `hiltViewModel<HomeViewModel>()` anywhere under the package is the
        // bug. `MainActivity` is the one allowed owner and it goes through
        // `viewModels()`, so this covers the whole tree with no exemption list
        // to keep in sync.
        val offenders = sourceRoot.walkTopDown()
            .filter { (it.isFile) && (it.extension == "kt") }
            .filter { file -> codeOf(file).contains("hiltViewModel<HomeViewModel>") }
            .map { it.path }
            .toList()

        assertTrue(
            "These files resolve their own HomeViewModel, so the tree has more than one:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `HomeScreen takes its ViewModel as a parameter`() {
        val code = codeOf("ui/screens/home/HomeScreen.kt")

        assertTrue(
            "HomeScreen must be handed the Activity's instance, so viewModel has to be a " +
                "plain parameter rather than one with a default that resolves its own",
            ("viewModel: HomeViewModel," in code) && ("hiltViewModel" !in code),
        )
    }

    @Test
    fun `the Test-button helper reads the shared instance`() {
        assertTrue(
            "rememberGlyphAnimationManager must read LocalHomeViewModel, or 'Test' drives a " +
                "manager nothing else is using",
            "LocalHomeViewModel.current" in codeOf("ui/components/GlyphDependencies.kt"),
        )
    }
}

/**
 * The exact-alarm guard that came with quiet hours.
 *
 * `setExactAndAllowWhileIdle` throws `SecurityException` without the
 * "Alarms & reminders" grant, which since Android 14 is off by default. It was
 * called unguarded on the main thread out of `onStartCommand`, so the process
 * died when the user switched quiet hours on, and again on every reboot with it
 * enabled.
 *
 * What is asserted here is the question the settings toggle now asks before
 * sending the user to the system screen: it has to be answerable, and the
 * answer has to be "no" when nothing has been granted — which is the ordinary
 * state on a device that has never been to that settings page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuietHoursExactAlarmAccessTest {

    @Test
    fun `reports no exact-alarm access instead of throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(QuietHoursService.canScheduleExactAlarms(context))
    }

    @Test
    fun `is answerable on a context with no alarm service`() {
        // `getSystemService` returns null rather than throwing where there is
        // no AlarmManager, so the question must not assume it is there.
        val noServices = object : ContextWrapper(null as Context?) {}

        assertFalse(QuietHoursService.canScheduleExactAlarms(noServices))
    }
}
