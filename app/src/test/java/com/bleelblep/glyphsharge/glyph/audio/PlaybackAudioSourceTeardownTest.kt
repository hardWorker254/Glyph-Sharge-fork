package com.bleelblep.glyphsharge.glyph.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the capture teardown ordering.
 *
 * Every defect below had the same shape: two individually reasonable steps, in
 * the wrong order, against a thread that cannot be interrupted. None of them
 * reproduce in a unit test — there is no `AudioRecord` here — but all of them
 * are visible in the source, and the previous code shipped with them because
 * nothing checked.
 */
class PlaybackAudioSourceTeardownTest {

    private val code = File(
        "src/main/java/com/bleelblep/glyphsharge/glyph/audio/PlaybackAudioSource.kt",
    ).readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun body(signature: String): String = code.substringAfter(signature)
        .substringBefore("\n    private fun ").let { it.ifEmpty { code.substringAfter(signature) } }

    @Test
    fun `the recorder is stopped before the reader is awaited`() {
        // `AudioRecord.read` on a blocking-mode playback capture does not return
        // when nothing is playing — it parks. Coroutine cancellation cannot
        // interrupt a blocking native call. So `cancelAndJoin()` without a prior
        // `stop()` waits for a return that is not coming.
        //
        // Concretely: on a phone with nothing playing, `adopt` hung inside that
        // join while holding `isSwapping = true`, so the service's watch loop
        // spun forever without ever drawing again — and re-consenting could not
        // recover it.
        val teardown = body("private suspend fun teardown(")

        val stopAt = teardown.indexOf("it.stop()")
        val joinAt = teardown.indexOf("cancelAndJoin()")

        assertTrue("teardown should stop the recorder", stopAt >= 0)
        assertTrue("teardown should await the reader", joinAt >= 0)
        assertTrue(
            "stop() must come before cancelAndJoin(): it is the only thing that " +
                "unblocks a reader parked inside read()",
            stopAt in (0 until joinAt),
        )
    }

    @Test
    fun `the reader is not awaited forever`() {
        // Even with stop() first, the wait needs a ceiling. A wedged reader must
        // not become the reason a capture never restarts.
        val teardown = body("private suspend fun teardown(")

        assertTrue(
            "the join needs a timeout, or a wedged read pins the swap",
            teardown.contains("withTimeoutOrNull(READER_EXIT_TIMEOUT_MS.milliseconds)"),
        )
        assertTrue(
            "and reaching the ceiling must be reported rather than silently ignored",
            teardown.contains("report("),
        )
        assertTrue(
            "the recorder must still be released on that path — releasing it " +
                "underneath a live reader is a native crash",
            teardown.contains("releaseNow(session)"),
        )
    }

    @Test
    fun `a new recorder is never built before the old one is gone`() {
        // The platform refuses a playback `AudioRecord` while another is open.
        // The old code detached, released from a side coroutine and built
        // immediately — so the refusal was routine, the status went to FAILED,
        // and the capture stayed dead for the rest of the process's life even
        // though the projection token was still valid.
        val rebuild = body("private suspend fun rebuild(")

        assertTrue("rebuild should tear down first", rebuild.contains("teardown(stale)"))
        assertTrue("then build the new recorder", rebuild.contains("startRecorder(granted)"))

        val teardownAt = rebuild.indexOf("teardown(stale)")
        val buildAt = rebuild.indexOf("startRecorder(granted)")
        assertTrue(
            "the teardown must finish before the new recorder is built",
            teardownAt in (0 until buildAt),
        )
        assertFalse(
            "and there must be no fire-and-forget release left on this path",
            rebuild.contains("releaseSession("),
        )
    }

    @Test
    fun `a read error does not tear down the reader from inside itself`() {
        // `surviveReadError` runs *in* the read loop, so `readerJob` is its own
        // job. Cancelling and awaiting it there is cancelling and awaiting
        // yourself, which never completes.
        //
        // It has to hand the rebuild to a separate coroutine, and return false
        // so this one exits rather than racing the new recorder.
        val survive = body("private suspend fun CoroutineScope.surviveReadError(")

        assertFalse(
            "the read loop must not detach and re-launch a recorder on its own " +
                "account — the new one is built before the old is released",
            survive.substringBefore("return false").contains("startRecorder("),
        )
        assertTrue(
            "the rebuild should be handed to another coroutine",
            survive.contains("scope.launch"),
        )
        assertTrue(
            "and covered by isSwapping so a polling caller waits rather than gives up",
            survive.contains("swaps.incrementAndGet()"),
        )
        assertTrue(
            "the read loop must exit, leaving teardown to the other coroutine",
            survive.contains("return false"),
        )
    }

    @Test
    fun `stop unblocks the reader on the calling thread`() {
        // `stop` is called from `MediaProjection.Callback.onStop`, which runs
        // on the `glyph-capture` HandlerThread and cannot suspend. So the part
        // that has to be synchronous — `stop()` on the recorder — is done there
        // and there, rather than left for a coroutine that may not run before a
        // caller stops and immediately re-consents.
        val stop = body("fun stop()")

        val detachAt = stop.indexOf("detachSession()")
        val stopAt = stop.indexOf("it.stop()")
        val launchAt = stop.indexOf("launchTeardown(stale)")

        assertTrue("stop should detach the session", detachAt >= 0)
        assertTrue(
            "the recorder must be stopped before the teardown is merely scheduled",
            (detachAt in (0 until stopAt)) && (stopAt in (0 until launchAt)),
        )
    }

    @Test
    fun `the session fields are swapped as a unit`() {
        // Three writers: the `scope` coroutine that adopts a token, the
        // `glyph-capture` HandlerThread carrying `Callback.onStop`, and whoever
        // calls `stop`. `detachSession` reads five fields and nulls them, so an
        // interleaving produced a Session pairing the *new* recorder with the
        // *old* thread — leaking an AudioRecord, leaking a HandlerThread, and
        // skipping an unregisterCallback.
        assertTrue(
            "detachSession should hold the session lock for its whole read-clear-write",
            code.contains("private fun detachSession(): Session = synchronized(sessionLock) {"),
        )
        for (field in listOf("recorder", "projection", "callback", "thread", "job")) {
            assertTrue(
                "detachSession should carry $field out",
                code.contains("$field = ${'$'}field") || code.contains("$field ="),
            )
        }
    }

    @Test
    fun `a second overlapping swap is still counted`() {
        // `isSwapping` exists so a caller polling the status waits instead of
        // concluding the capture is dead. A boolean was cleared by whichever
        // swap finished first, under-reporting while another was still running —
        // which defeats the one thing the flag is for.
        assertTrue(
            "isSwapping should read a counter, not a boolean",
            code.contains("val isSwapping: Boolean get() = swaps.get() > 0"),
        )
        assertFalse(
            "the old boolean should be gone",
            code.contains("private var swapping ="),
        )
        assertTrue(
            "each swap path increments and decrements around itself",
            (code.contains("swaps.incrementAndGet()")) && (code.contains("swaps.decrementAndGet()")),
        )
    }

    @Test
    fun `the diagnostic file cannot grow without bound`() {
        // `report` runs several times a second while music plays, from the audio
        // thread. Appending straight to the file each time meant an
        // open/write/close per line, and a file in cacheDir that grows for as
        // long as the visualiser runs.
        val report = body("private fun report(")

        assertTrue(
            "report should go through the bounded writer",
            report.contains("appendDiagnostic(message)"),
        )
        assertFalse(
            "nothing should append to the diagnostic file directly",
            (code.contains("appendText(")) && (!code.contains("appendDiagnostic")),
        )
        assertTrue(
            "and the writer should keep the newest lines, since the end is what " +
                "describes what went wrong",
            (code.contains("DIAG_MAX_LINES")) && (code.contains("takeLast(")),
        )
    }
}
