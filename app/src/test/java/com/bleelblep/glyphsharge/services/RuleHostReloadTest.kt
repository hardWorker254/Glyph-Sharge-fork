package com.bleelblep.glyphsharge.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Ensure the host is alive" and "the rules changed" are not the same command.
 *
 * `MainActivity.onStart` asks for the first on every start. On a fingerprint
 * unlock that start can land before the screen-on watcher has had its tick.
 * If the host treated it as a reload, it would detach the watcher and build a
 * new one whose latch already reads "interactive" — so the transition it was
 * waiting for is gone, and the rule never fires. Only the second command may
 * tear the triggers down.
 */
class RuleHostReloadTest {

    @Test
    fun `a reload action always rebuilds the triggers`() {
        assertTrue(
            "a rule change must re-read the list even while triggers are attached",
            RuleHostService.shouldReloadOnStart(
                RuleHostService.ACTION_RELOAD,
                hasAttachedTriggers = true,
            ),
        )
    }

    @Test
    fun `an ensure-start keeps a running host's triggers`() {
        assertFalse(
            "an ensure-start must not detach the screen-on watcher mid-transition",
            RuleHostService.shouldReloadOnStart(
                RuleHostService.ACTION_START,
                hasAttachedTriggers = true,
            ),
        )
    }

    @Test
    fun `a cold start reads the rules`() {
        assertTrue(
            "with no triggers attached there is nothing to preserve",
            RuleHostService.shouldReloadOnStart(
                RuleHostService.ACTION_START,
                hasAttachedTriggers = false,
            ),
        )
    }

    @Test
    fun `a sticky restart reads the rules`() {
        assertTrue(
            "a restart has no intent action and no live triggers, so it must load",
            RuleHostService.shouldReloadOnStart(
                action = null,
                hasAttachedTriggers = false,
            ),
        )
    }
}
