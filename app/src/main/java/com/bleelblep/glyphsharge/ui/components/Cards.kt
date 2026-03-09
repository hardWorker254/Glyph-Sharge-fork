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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

// Project imports
import com.bleelblep.glyphsharge.di.GlyphComponent
import com.bleelblep.glyphsharge.ui.theme.*
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*


/**
 * Home-specific card templates for the GlyphZen app
 * These templates are optimized for the main homepage layout and interactions
 */

/**
 * Toggle card template for switches and boolean controls
 */
@Composable
fun ToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    statusText: (Boolean) -> String = { if (it) "Enabled" else "Disabled" },
    customContent: (@Composable ColumnScope.() -> Unit)? = null,
    @DrawableRes illustrationRes: Int? = null
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    ContentCard(
        title = title,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Toggle section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText(checked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onCheckedChange(newValue)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        // Custom content section
        if (customContent != null) {
            Spacer(modifier = Modifier.weight(1f))
            customContent()
        }

        // Illustration section
        if (illustrationRes != null) {
            if (customContent == null) {
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = illustrationRes),
                    contentDescription = "Illustration for $title",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Feature card template for action-based cards with icons
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
    val themeState = LocalThemeState.current
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale"
    )

    ContentCard(
        modifier = modifier
            .scale(pressScale),
        onClick = {
            isPressed = true
            HapticUtils.triggerLightFeedback(haptic, context)
            onClick()
            // Reset pressed state after animation
            GlobalScope.launch {
                delay(150)
                isPressed = false
            }
        },
        contentPadding = contentPadding
    ) {
        Icon(
            painter = icon,
            contentDescription = title,
            tint = iconTint ?: when {
                title.contains("Breathing", ignoreCase = true) && themeState.themeStyle == AppThemeStyle.CLASSIC -> NothingViolate
                title.contains("Guard", ignoreCase = true) && themeState.themeStyle == AppThemeStyle.CLASSIC -> NothingViolate
                title.contains("Peek", ignoreCase = true) && themeState.themeStyle == AppThemeStyle.CLASSIC -> NothingViolate
                title.contains("Pulse", ignoreCase = true) && themeState.themeStyle == AppThemeStyle.CLASSIC -> NothingViolate
                title.contains("Battery", ignoreCase = true) && themeState.themeStyle == AppThemeStyle.CLASSIC -> NothingViolate
                title.contains("Information", ignoreCase = true) && themeState.themeStyle == AppThemeStyle.CLASSIC -> NothingViolate
                else -> MaterialTheme.colorScheme.primary
            },
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

/**
 * Square feature card template for grid layouts
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
    val glyphAnimationManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GlyphComponent::class.java
        ).glyphAnimationManager()
    }

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

    // Handle press animation and dialog timing
    LaunchedEffect(isPressed) {
        if (isPressed && !isServiceActive) {
            // Brief animation then trigger onClick for toast
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
            if (!isPressed) { // Prevent multiple rapid clicks
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

    // Simple confirmation dialog
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
 * Wide feature card template for full-width actions
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
    val themeState = LocalThemeState.current
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

/**
 * Section header for grouping cards
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
 * Feature grid layout for organizing multiple feature cards
 */
@Composable
fun FeatureGrid(
    modifier: Modifier = Modifier,
    spacing: Int = 16,
    content: @Composable RowScope.() -> Unit
) {
    val themeState = LocalThemeState.current
    
    // Use Material 3's 8dp grid system for expressive theme
    val enhancedSpacing = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        // Material 3 spacing: 8dp, 16dp, 24dp, 32dp
        when (spacing) {
            8 -> 8
            16 -> 16
            24 -> 24
            32 -> 32
            else -> 16 // Default to 16dp for expressive theme
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

/**
 * Main glyph control card template (specialized for the main toggle)
 * Inspired by Nothing OS interface design with Material You theming
 * Features a morphing button with repositioned text elements
 */
@Composable
fun GlyphControlCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes illustrationRes: Int? = null
) {
    val themeState = LocalThemeState.current
    
    // Use Material 3 surface container colors for expressive theme
    val backgroundColor = if (themeState.themeStyle == AppThemeStyle.EXPRESSIVE) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

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
            // Service status text - top left
            Text(
                text = "Service is ${if (enabled) "Active" else "Inactive"}",
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Glyph lights label - bottom left
            Text(
                text = "GLYPH LIGHTS",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            // Morphing toggle button - top right
            MorphingToggleButton(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * Reusable morphing toggle button component
 * Features smooth shape transitions and consistent theming
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

/**
 * Three-state morphing toggle button for font family selection
 * Features smooth shape transitions, distinct colors, and typography preview
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

    // Cycle through variants: HEADLINE -> NDOT -> SYSTEM -> HEADLINE
    val nextVariant = when (currentVariant) {
        FontVariant.HEADLINE -> FontVariant.NDOT
        FontVariant.NDOT -> FontVariant.SYSTEM
        FontVariant.SYSTEM -> FontVariant.HEADLINE
    }

    // Use exact standard morphing toggle dimensions - matches other settings cards
    val width by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 88.dp  // Wide rectangle (unchecked state)
            FontVariant.NDOT -> 60.dp      // Square (checked state)
            FontVariant.SYSTEM -> 60.dp    // Circle (checked state)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "width"
    )

    val height by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 40.dp  // Standard rectangle height
            FontVariant.NDOT -> 60.dp      // Square height (checked state)
            FontVariant.SYSTEM -> 60.dp    // Circle height (checked state)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "height"
    )

    // Exact standard morphing toggle corner radius
    val cornerRadius by animateDpAsState(
        targetValue = when (currentVariant) {
            FontVariant.HEADLINE -> 12.dp  // Standard rounded rectangle
            FontVariant.NDOT -> 12.dp      // Standard rounded rectangle
            FontVariant.SYSTEM -> 30.dp    // Full circle (checked state)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "cornerRadius"
    )

    // Three distinct colors for each font variant
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
                FontVariant.SYSTEM -> NothingRed                       // Red for System Default
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
            FontVariant.HEADLINE -> 1f      // Normal scale (unchecked state)
            FontVariant.NDOT -> 1.07f       // Enlarged scale (checked state)
            FontVariant.SYSTEM -> 1.07f     // Enlarged scale (checked state)
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
                        FontVariant.HEADLINE -> FontFamily(Font(com.bleelblep.glyphsharge.R.font.ntype_82_headline))
                        FontVariant.NDOT -> FontFamily(Font(com.bleelblep.glyphsharge.R.font.ndot55caps))
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




