package com.bleelblep.glyphsharge.ui.components.cards

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.controls.MorphingToggleButton
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.NothingGreen
import com.bleelblep.glyphsharge.ui.theme.NothingRed
import com.bleelblep.glyphsharge.ui.theme.NothingViolate
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Base content card with consistent styling and press animation.
 * Supports both CLASSIC and EXPRESSIVE theme styles.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val themeState = LocalThemeState.current
    var isPressed by remember { mutableStateOf(value = false) }
    val scope = rememberCoroutineScope()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale",
    )

    val containerColor = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val shape = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        CutCornerShape(
            topStartPercent = 0,
            topEndPercent = 15,
            bottomStartPercent = 15,
            bottomEndPercent = 0,
        )
    } else {
        MaterialTheme.shapes.large
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale),
        onClick = {
            if ((onClick != null) && !isPressed) {
                isPressed = true
                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                onClick()
                scope.launch {
                    delay(150.milliseconds)
                    isPressed = false
                }
            }
        },
        enabled = onClick != null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
    }
}

/**
 * Feature card with icon, title, description and press animation.
 * Used as building block for SquareFeatureCard and WideFeatureCard.
 */
@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 32,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    iconTint: Color? = null,
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(value = false) }
    val scope = rememberCoroutineScope()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale",
    )

    ContentCard(
        modifier = modifier.scale(pressScale),
        onClick = {
            if (!isPressed) {
                isPressed = true
                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                onClick()
                scope.launch {
                    delay(150.milliseconds)
                    isPressed = false
                }
            }
        },
        contentPadding = contentPadding,
    ) {
        Icon(
            painter = icon,
            contentDescription = title,
            tint = iconTint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Wide feature card with integrated toggle switch.
 * Used for features that can be enabled/disabled (PowerPeek, PulseLock, etc.)
 */
@Composable
fun WideFeatureCardWithToggle(
    title: String,
    description: String,
    icon: Painter,
    isServiceActive: Boolean,
    isFeatureEnabled: Boolean,
    onFeatureToggle: (Boolean) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 32,
    height: Int = 140,
) {
    val resolvedTint = when {
        !isServiceActive -> NothingViolate
        isFeatureEnabled -> NothingGreen
        else -> NothingRed
    }

    val alpha by animateFloatAsState(
        targetValue = if (isServiceActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "featureAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .alpha(alpha),
    ) {
        FeatureCard(
            title = title,
            description = description,
            icon = icon,
            onClick = onCardClick,
            modifier = Modifier.fillMaxSize(),
            iconSize = iconSize,
            contentPadding = PaddingValues(16.dp),
            iconTint = resolvedTint,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.dp),
    ) {
        MorphingToggleButton(
            checked = isFeatureEnabled,
            onCheckedChange = onFeatureToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-12).dp, y = 12.dp),
        )
    }
}

/**
 * Main glyph control card with morphing toggle button.
 */
@Composable
fun GlyphControlCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeState = LocalThemeState.current

    val backgroundColor = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(
                text = if (enabled) stringResource(id = R.string.glyph_service_active)
                else stringResource(id = R.string.glyph_service_inactive),
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart),
            )

            Text(
                text = stringResource(R.string.home_glyph_lights),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart),
            )

            MorphingToggleButton(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
