package com.bleelblep.glyphsharge.ui.screens

import androidx.compose.foundation.layout.*
import com.bleelblep.glyphsharge.ui.components.cards.ContentCard
import com.bleelblep.glyphsharge.ui.components.cards.FeatureCard
import com.bleelblep.glyphsharge.ui.components.cards.SquareFeatureCard
import com.bleelblep.glyphsharge.ui.components.layout.FeatureGrid
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.bleelblep.glyphsharge.R
import androidx.compose.ui.res.painterResource
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import androidx.compose.ui.graphics.painter.Painter
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.sp
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.res.stringResource

/**
 * Theme-specific feature card that bypasses service checks and dialogs
 * Provides immediate execution for theme switching
 */
@Composable
private fun ThemeFeatureCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 40
) {
    val themeState = LocalThemeState.current
    FeatureCard(
        title = title,
        description = description,
        icon = icon,
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        iconSize = iconSize,
        contentPadding = PaddingValues(16.dp),
        iconTint = when (themeState.themeStyle) {
            AppThemeStyle.Y2K -> MaterialTheme.colorScheme.primary
            AppThemeStyle.NEON -> MaterialTheme.colorScheme.primary
            AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
            else -> MaterialTheme.colorScheme.primary
        }
    )
}

/**
 * Theme reset card that bypasses dialogs and directly resets theme
 */
@Composable
private fun ThemeResetCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 140,
    iconSize: Int = 32
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val themeState = LocalThemeState.current
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale"
    )

    ContentCard(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = {
            isPressed = true
            HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
            onClick()
            // Reset pressed state after animation
            coroutineScope.launch {
                delay(150)
                isPressed = false
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = when (themeState.themeStyle) {
                    AppThemeStyle.Y2K -> MaterialTheme.colorScheme.primary
                    AppThemeStyle.NEON -> MaterialTheme.colorScheme.primary
                    AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(iconSize.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeState = LocalThemeState.current
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    SettingsScaffold(
        title = stringResource(id = R.string.theme_settings_title),
        modifier = modifier,
        onBackClick = onBackClick
    ) {
            // Theme Mode Section Header
            item {
                Text(
                    text = stringResource(id = R.string.theme_section_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp)
                )
            }

            // Light/Dark Theme Cards Grid
            item {
                FeatureGrid {
                    // Light Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_mode_light_title),
                        description = stringResource(id = R.string.theme_mode_light_desc),
                        icon = painterResource(id = R.drawable.light_mode_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setDarkTheme(false) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )

                    // Dark Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_mode_dark_title),
                        description = stringResource(id = R.string.theme_mode_dark_desc),
                        icon = painterResource(id = R.drawable.dark_mode_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setDarkTheme(true) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )
                }
            }

            // Theme Style Section Header
            item {
                Text(
                    text = stringResource(id = R.string.theme_section_styles),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp)
                )
            }

            // Theme Style Cards Grid - Row 1
            item {
                FeatureGrid {
                    // Y2K Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_style_y2k),
                        description = stringResource(id = R.string.theme_style_y2k_desc),
                        icon = painterResource(id = R.drawable.palette_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setThemeStyle(AppThemeStyle.Y2K) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )

                    // Neon Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_style_neon),
                        description = stringResource(id = R.string.theme_style_neon_desc),
                        icon = painterResource(id = R.drawable.invert_colors_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setThemeStyle(AppThemeStyle.NEON) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )
                }
            }

            // Theme Style Cards Grid - Row 2
            item {
                FeatureGrid {
                    // AMOLED Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_style_nothing),
                        description = stringResource(id = R.string.theme_style_nothing_desc),
                        icon = painterResource(id = R.drawable.dark_mode_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setThemeStyle(AppThemeStyle.AMOLED) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )

                    // Pastel Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_style_pastel),
                        description = stringResource(id = R.string.theme_style_pastel_desc),
                        icon = painterResource(id = R.drawable.light_mode_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setThemeStyle(AppThemeStyle.PASTEL) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )
                }
            }

            // Theme Style Cards Grid - Row 3
            item {
                FeatureGrid {
                    // Classic Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_style_classic),
                        description = stringResource(id = R.string.theme_style_classic_desc),
                        icon = painterResource(id = R.drawable.palette_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setThemeStyle(AppThemeStyle.CLASSIC) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )

                    // Expressive Theme Card
                    ThemeFeatureCard(
                        title = stringResource(id = R.string.theme_style_expressive),
                        description = stringResource(id = R.string.theme_style_expressive_desc),
                        icon = painterResource(id = R.drawable.extension_24px),
                        onClick = { 
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            themeState.setThemeStyle(AppThemeStyle.EXPRESSIVE) 
                        },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )
                }
            }

            // Feature Card Section
            item {
                ThemeResetCard(
                    title = stringResource(id = R.string.theme_button_reset),
                    description = stringResource(id = R.string.theme_reset_desc),
                    icon = painterResource(id = R.drawable.palette_24px),
                    onClick = { 
                        HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                        // Reset to default Classic theme and light mode
                        themeState.setThemeStyle(AppThemeStyle.CLASSIC)
                        themeState.setDarkTheme(false) // Always set light mode
                    }
                )
            }

            // Additional Feature Cards Section Header
            item {
                Text(
                    text = stringResource(id = R.string.theme_section_additional),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp)
                )
            }

            // Additional Feature Cards Grid
            item {
                FeatureGrid {
                    // First Additional Card
                    SquareFeatureCard(
                        title = stringResource(id = R.string.theme_option_color_scheme),
                        description = stringResource(id = R.string.theme_option_color_scheme_desc),
                        icon = painterResource(id = R.drawable.palette_24px),
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )

                    // Second Additional Card
                    SquareFeatureCard(
                        title = stringResource(id = R.string.theme_option_accent_colors),
                        description = stringResource(id = R.string.theme_option_accent_colors_desc),
                        icon = painterResource(id = R.drawable.invert_colors_24px),
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        iconSize = 40
                    )
                }
            }
    }
} 