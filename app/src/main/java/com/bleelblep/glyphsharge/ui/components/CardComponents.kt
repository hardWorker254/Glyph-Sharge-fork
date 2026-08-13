package com.bleelblep.glyphsharge.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Base Card Components
// ─────────────────────────────────────────────────────────────────────────────

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
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val themeState = LocalThemeState.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale"
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
            bottomEndPercent = 0
        )
    } else {
        MaterialTheme.shapes.large
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale),
        onClick = {
            if (onClick != null && !isPressed) {
                isPressed = true
                HapticUtils.triggerLightFeedback(haptic, context)
                onClick()
                scope.launch {
                    delay(150)
                    isPressed = false
                }
            }
        },
        enabled = onClick != null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    iconTint: Color? = null
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale"
    )

    ContentCard(
        modifier = modifier.scale(pressScale),
        onClick = {
            if (!isPressed) {
                isPressed = true
                HapticUtils.triggerLightFeedback(haptic, context)
                onClick()
                scope.launch {
                    delay(150)
                    isPressed = false
                }
            }
        },
        contentPadding = contentPadding
    ) {
        Icon(
            painter = icon,
            contentDescription = title,
            tint = iconTint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Feature Cards (Square & Wide)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Square feature card for grid layouts with optional confirmation dialog.
 */
@Composable
fun SquareFeatureCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 40,
    isServiceActive: Boolean = true,
    skipConfirmation: Boolean = false,
    iconTint: Color? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale",
        finishedListener = { finalValue ->
            if (finalValue == 0.95f && isPressed && isServiceActive) {
                coroutineScope.launch {
                    delay(150)
                    if (skipConfirmation) {
                        onClick()
                    } else {
                        showDialog = true
                    }
                    isPressed = false
                }
            }
        }
    )

    val alpha by animateFloatAsState(
        targetValue = if (isServiceActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "alpha"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && !isServiceActive) {
            delay(150)
            isPressed = false
            onClick()
        }
    }

    val resolvedTint = iconTint ?: if (isServiceActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant

    FeatureCard(
        title = title,
        description = description,
        icon = icon,
        onClick = {
            if (!isPressed) {
                isPressed = true
            }
        },
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(alpha),
        iconSize = iconSize,
        contentPadding = PaddingValues(16.dp),
        iconTint = resolvedTint
    )

    if (!skipConfirmation && showDialog && isServiceActive) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        HapticUtils.triggerMediumFeedback(haptic, context)
                        onClick()
                        showDialog = false
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    HapticUtils.triggerLightFeedback(haptic, context)
                    showDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Wide feature card for full-width layouts with optional confirmation dialog.
 */
@Composable
fun WideFeatureCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 140,
    iconSize: Int = 32,
    isServiceActive: Boolean = true,
    skipConfirmation: Boolean = false,
    iconTint: Color? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale",
        finishedListener = { finalValue ->
            if (finalValue == 0.95f && isPressed && isServiceActive) {
                if (skipConfirmation) {
                    onClick()
                    isPressed = false
                } else {
                    showDialog = true
                    isPressed = false
                }
            }
        }
    )

    val alpha by animateFloatAsState(
        targetValue = if (isServiceActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "alpha"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && !isServiceActive) {
            delay(150)
            isPressed = false
            onClick()
        }
    }

    val resolvedTint = iconTint ?: if (isServiceActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant

    FeatureCard(
        title = title,
        description = description,
        icon = icon,
        onClick = {
            if (!isPressed) {
                isPressed = true
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(alpha),
        iconSize = iconSize,
        contentPadding = PaddingValues(16.dp),
        iconTint = resolvedTint
    )

    if (!skipConfirmation && showDialog && isServiceActive) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        HapticUtils.triggerMediumFeedback(haptic, context)
                        onClick()
                        showDialog = false
                    }
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    HapticUtils.triggerLightFeedback(haptic, context)
                    showDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Wide Feature Card with Toggle
// ─────────────────────────────────────────────────────────────────────────────

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
    height: Int = 140
) {
    val resolvedTint = when {
        !isServiceActive -> NothingViolate
        isFeatureEnabled -> NothingGreen
        else             -> NothingRed
    }

    val alpha by animateFloatAsState(
        targetValue = if (isServiceActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "featureAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .alpha(alpha)
    ) {
        FeatureCard(
            title = title,
            description = description,
            icon = icon,
            onClick = onCardClick,
            modifier = Modifier.fillMaxSize(),
            iconSize = iconSize,
            contentPadding = PaddingValues(16.dp),
            iconTint = resolvedTint
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.dp)
    ) {
        MorphingToggleButton(
            checked = isFeatureEnabled,
            onCheckedChange = onFeatureToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-12).dp, y = 12.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Glyph Control Card
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Main glyph control card with morphing toggle button.
 */
@Composable
fun GlyphControlCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes illustrationRes: Int? = null
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
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = if (enabled) stringResource(id = R.string.glyph_service_active)
                    else stringResource(id = R.string.glyph_service_inactive),
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart)
            )

            Text(
                text = "GLYPH LIGHTS",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            MorphingToggleButton(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Morphing Toggle Button
// ─────────────────────────────────────────────────────────────────────────────

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
            modifier = Modifier.size(28.dp)
        )
    },
    disabledIcon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Disabled",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val themeState = LocalThemeState.current

    val width by animateDpAsState(
        targetValue = if (checked) 60.dp else 88.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "width"
    )

    val height by animateDpAsState(
        targetValue = if (checked) 60.dp else 40.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "height"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (checked) 30.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "cornerRadius"
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
        label = "backgroundColor"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale"
    )

    val activeScale by animateFloatAsState(
        targetValue = if (checked) 1.07f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "activeScale"
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
                indication = null
            ) {
                try {
                    isPressed = true
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onCheckedChange(!checked)
                    scope.launch {
                        delay(120)
                        isPressed = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                scaleIn(tween(200)) + fadeIn() with fadeOut()
            },
            label = "iconTransition"
        ) { isChecked ->
            if (isChecked) {
                enabledIcon()
            } else {
                disabledIcon()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Three-State Font Morphing Button
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Three-state morphing button for font family selection.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ThreeStateFontMorphingButton(
    currentVariant: FontVariant,
    onVariantSelected: (FontVariant) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
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
        label = "width"
    )

    val height by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 40.dp
            FontVariant.NDOT -> 60.dp
            FontVariant.SYSTEM -> 60.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "height"
    )

    val cornerRadius by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 12.dp
            FontVariant.NDOT -> 12.dp
            FontVariant.SYSTEM -> 30.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "cornerRadius"
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
        label = "backgroundColor"
    )

    val contentColor = Color.White

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale"
    )

    val activeScale by animateFloatAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 1f
            FontVariant.NDOT -> 1.07f
            FontVariant.SYSTEM -> 1.07f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "activeScale"
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
                indication = null
            ) {
                try {
                    isPressed = true
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onVariantSelected(nextVariant)
                    scope.launch {
                        delay(120)
                        isPressed = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentVariant,
            transitionSpec = {
                scaleIn(tween(200)) + fadeIn() with 
                scaleOut(tween(150)) + fadeOut()
            },
            label = "fontIconTransition"
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
                    }
                ),
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Utility Components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Section header for grouping cards.
 */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(start = 8.dp, bottom = 8.dp, top = 8.dp)
    )
}

/**
 * Feature grid layout for organizing multiple feature cards.
 */
@Composable
fun FeatureGrid(
    modifier: Modifier = Modifier,
    spacing: Int = 16,
    content: @Composable RowScope.() -> Unit
) {
    val themeState = LocalThemeState.current
    
    val enhancedSpacing = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        when (spacing) {
            8 -> 8
            16 -> 16
            24 -> 24
            32 -> 32
            else -> 16
        }
    } else {
        spacing
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(enhancedSpacing.dp),
        content = content
    )
}
