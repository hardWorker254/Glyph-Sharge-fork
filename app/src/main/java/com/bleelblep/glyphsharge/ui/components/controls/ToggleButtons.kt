package com.bleelblep.glyphsharge.ui.components.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.FontVariant
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.NothingGray
import com.bleelblep.glyphsharge.ui.theme.NothingRed
import com.bleelblep.glyphsharge.ui.theme.NothingViolate
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reusable morphing toggle button with smooth shape transitions.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MorphingToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabledIcon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Enabled",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    },
    disabledIcon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Disabled",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    },
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val themeState = LocalThemeState.current

    val width by animateDpAsState(
        targetValue = if (checked) 60.dp else 88.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "width",
    )

    val height by animateDpAsState(
        targetValue = if (checked) 60.dp else 40.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "height",
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (checked) 30.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "cornerRadius",
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (checked) {
            if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
                MaterialTheme.colorScheme.primary
            } else {
                NothingRed
            }
        } else {
            if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                NothingGray
            }
        },
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor",
    )

    var isPressed by remember { mutableStateOf(value = false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale",
    )

    val activeScale by animateFloatAsState(
        targetValue = if (checked) 1.07f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "activeScale",
    )

    val totalScale = pressScale * activeScale

    Box(
        modifier = modifier
            .size(width, height)
            .scale(totalScale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                try {
                    isPressed = true
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onCheckedChange(!checked)
                    scope.launch {
                        delay(120.milliseconds)
                        isPressed = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                (scaleIn(tween(200)) + fadeIn()) togetherWith fadeOut()
            },
            label = "iconTransition",
        ) { isChecked ->
            if (isChecked) {
                enabledIcon()
            } else {
                disabledIcon()
            }
        }
    }
}

/**
 * Three-state morphing button for font family selection.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ThreeStateFontMorphingButton(
    currentVariant: FontVariant,
    onVariantSelected: (FontVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val themeState = LocalThemeState.current

    val nextVariant = when (currentVariant) {
        FontVariant.HEADLINE -> FontVariant.NDOT
        FontVariant.NDOT -> FontVariant.SYSTEM
        FontVariant.SYSTEM -> FontVariant.HEADLINE
    }

    val width by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 88.dp
            FontVariant.NDOT -> 60.dp
            FontVariant.SYSTEM -> 60.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "width",
    )

    val height by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 40.dp
            FontVariant.NDOT -> 60.dp
            FontVariant.SYSTEM -> 60.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "height",
    )

    val cornerRadius by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 12.dp
            FontVariant.NDOT -> 12.dp
            FontVariant.SYSTEM -> 30.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "cornerRadius",
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
            when (currentVariant) {
                FontVariant.HEADLINE -> MaterialTheme.colorScheme.surfaceContainerHigh
                FontVariant.NDOT -> MaterialTheme.colorScheme.secondary
                FontVariant.SYSTEM -> MaterialTheme.colorScheme.primary
            }
        } else {
            when (currentVariant) {
                FontVariant.HEADLINE -> NothingGray
                FontVariant.NDOT -> NothingViolate
                FontVariant.SYSTEM -> NothingRed
            }
        },
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor",
    )

    val contentColor = Color.White

    var isPressed by remember { mutableStateOf(value = false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale",
    )

    val activeScale by animateFloatAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 1f
            FontVariant.NDOT -> 1.07f
            FontVariant.SYSTEM -> 1.07f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "activeScale",
    )

    val totalScale = pressScale * activeScale

    Box(
        modifier = modifier
            .size(width, height)
            .scale(totalScale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                try {
                    isPressed = true
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onVariantSelected(nextVariant)
                    scope.launch {
                        delay(120.milliseconds)
                        isPressed = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = currentVariant,
            transitionSpec = {
                (scaleIn(tween(200)) + fadeIn()) togetherWith
                    (scaleOut(tween(150)) + fadeOut())
            },
            label = "fontIconTransition",
        ) { variant ->
            Text(
                text = when (variant) {
                    FontVariant.HEADLINE -> "T"
                    FontVariant.NDOT -> "N"
                    FontVariant.SYSTEM -> "S"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = when (variant) {
                        FontVariant.HEADLINE -> FontFamily(Font(R.font.ntype_82_headline))
                        FontVariant.NDOT -> FontFamily(Font(R.font.ndot55caps))
                        FontVariant.SYSTEM -> FontFamily.Default
                    },
                    fontSize = when (variant) {
                        FontVariant.HEADLINE -> 18.sp
                        FontVariant.NDOT -> 18.sp
                        FontVariant.SYSTEM -> 20.sp
                    },
                ),
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
