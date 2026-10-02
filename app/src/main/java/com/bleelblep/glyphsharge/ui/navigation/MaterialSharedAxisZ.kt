package com.bleelblep.glyphsharge.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

/**
 * Shared-axis Z transition used for every navigation in the app.
 *
 * Pushing a destination fades and scales in from 0.98; popping fades and
 * scales out towards 1.02, so the reverse trip mirrors the forward one.
 */
object MaterialSharedAxisZ {
    private const val ENTER_DURATION = 200
    private const val EXIT_DURATION = 120
    private const val SCALE_ENTER = 0.98f
    private const val SCALE_EXIT = 1.02f

    fun enterTransition(): EnterTransition =
        fadeIn(tween(ENTER_DURATION, easing = FastOutSlowInEasing)) +
            scaleIn(
                tween(ENTER_DURATION, easing = FastOutSlowInEasing),
                initialScale = SCALE_ENTER,
            )

    fun exitTransition(): ExitTransition =
        fadeOut(tween(EXIT_DURATION, easing = EaseOut)) +
            scaleOut(
                tween(EXIT_DURATION, easing = EaseOut),
                targetScale = SCALE_EXIT,
            )

    fun popEnterTransition(): EnterTransition =
        fadeIn(tween(ENTER_DURATION, easing = FastOutSlowInEasing)) +
            scaleIn(
                tween(ENTER_DURATION, easing = FastOutSlowInEasing),
                initialScale = SCALE_EXIT,
            )

    fun popExitTransition(): ExitTransition =
        fadeOut(tween(EXIT_DURATION, easing = EaseOut)) +
            scaleOut(
                tween(EXIT_DURATION, easing = EaseOut),
                targetScale = SCALE_ENTER,
            )
}
