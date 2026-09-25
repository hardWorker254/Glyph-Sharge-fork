package com.bleelblep.glyphsharge.ui.components.layout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settings card that navigates on a horizontal swipe.
 *
 * The gesture itself is the app's signature interaction: the card is pulled
 * sideways, tints towards `primaryContainer` and lifts in elevation in
 * proportion to the drag. Past half the screen width it snaps back quickly and
 * fires [onNavigate]; released earlier it springs back with a bouncy curve.
 *
 * Five separate copies of this logic used to live in `SettingsScreen.kt`
 * (~110 lines each) — the typography, theme, quiet hours and language cards
 * plus the "About" card. Only the title, the optional subtitle and the
 * trailing control ever differed, so all of that is now a parameter.
 *
 * @param onNavigate called once the card snaps back after a full swipe. When
 *   `null` the card is inert and does not respond to drags.
 * @param subtitle optional second line under the title.
 * @param subtitleColor applied to [subtitle] only; leave as [Color.Unspecified]
 *   to inherit the theme's default.
 * @param trailing trailing control, e.g. a toggle or the language icon.
 */
@Composable
fun DraggableSettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    onNavigate: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var offsetX by remember { mutableFloatStateOf(0f) }
    var thresholdMet by remember { mutableStateOf(false) }

    // Half the screen width is the distance required to trigger navigation.
    val dragThreshold = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() } / 2f
    }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        // Snap back fast when navigating, spring gently when the user gave up.
        animationSpec = if (thresholdMet) {
            tween(durationMillis = 200, easing = FastOutLinearInEasing)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "draggableCardOffset",
        finishedListener = { finalValue ->
            // Navigate only once the card is back at rest in its original spot.
            if (thresholdMet && finalValue == 0f) {
                thresholdMet = false
                if (onNavigate != null) {
                    HapticUtils.triggerMediumFeedback(haptic, context)
                    onNavigate()
                }
            }
        }
    )

    val dragProgress = (abs(animatedOffsetX) / dragThreshold).coerceIn(0f, 1f)

    val containerColor by animateColorAsState(
        targetValue = lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.primaryContainer,
            dragProgress
        ),
        animationSpec = tween(300),
        label = "draggableCardColor"
    )

    val animatedElevation by animateDpAsState(
        targetValue = (1 + dragProgress * 8).dp,
        animationSpec = tween(300),
        label = "draggableCardElevation"
    )

    val dragModifier = if (onNavigate == null) {
        Modifier
    } else {
        Modifier
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .pointerInput(onNavigate) {
                detectDragGestures(
                    onDragStart = { _ ->
                        HapticUtils.triggerLightFeedback(haptic, context)
                    },
                    onDragEnd = {
                        thresholdMet = abs(offsetX) >= dragThreshold
                        offsetX = 0f
                    }
                ) { _, dragAmount ->
                    offsetX += dragAmount.x
                }
            }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .then(dragModifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        letterSpacing = 0.5.sp,
                        color = subtitleColor
                    )
                }
            }

            trailing?.invoke()
        }
    }
}
