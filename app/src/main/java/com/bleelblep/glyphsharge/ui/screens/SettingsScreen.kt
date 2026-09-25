package com.bleelblep.glyphsharge.ui.screens

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.ui.components.MorphingToggleButton
import com.bleelblep.glyphsharge.ui.components.ThreeStateFontMorphingButton
import com.bleelblep.glyphsharge.ui.components.layout.DraggableSettingsCard
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.LocalFontState
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState

/**
 * Settings screen for the GlyphZen app.
 * Allows users to modify app preferences and view app information.
 *
 * Every destination here is a [DraggableSettingsCard]: the card itself hosts a
 * quick toggle, while swiping it sideways opens the full screen for that
 * section. [AppInfoSection] at the bottom is informational only.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onThemeSettingsClick: () -> Unit = {},
    onFontSettingsClick: () -> Unit = {},
    onQuietHoursSettingsClick: () -> Unit = {},
    onLanguageSettingsClick: () -> Unit = {},
    settingsRepository: SettingsRepository
) {
    val fontState = LocalFontState.current
    val themeState = LocalThemeState.current

    SettingsScaffold(
        title = stringResource(id = R.string.settings_title),
        onBackClick = onBackClick
    ) {
        item {
            TypographySettingsCard(
                onNavigate = onFontSettingsClick,
                trailing = {
                    if (fontState.useCustomFonts) {
                        ThreeStateFontMorphingButton(
                            currentVariant = fontState.currentVariant,
                            onVariantSelected = { variant ->
                                fontState.setFontVariant(variant)
                            }
                        )
                    }
                }
            )
        }

        item {
            ThemeSettingsCard(
                isDarkTheme = themeState.isDarkTheme,
                onToggleTheme = themeState::toggleTheme,
                onNavigate = onThemeSettingsClick
            )
        }

        item {
            QuietHoursSettingsCard(
                settingsRepository = settingsRepository,
                onNavigate = onQuietHoursSettingsClick
            )
        }

        item {
            LanguageSettingsCard(
                settingsRepository = settingsRepository,
                onNavigate = onLanguageSettingsClick
            )
        }

        item {
            AppInfoSection()
        }
    }
}

@Composable
private fun TypographySettingsCard(
    onNavigate: () -> Unit,
    trailing: @Composable () -> Unit
) {
    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_typography),
        onNavigate = onNavigate,
        trailing = trailing
    )
}

@Composable
private fun ThemeSettingsCard(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigate: () -> Unit
) {
    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_theme),
        subtitle = if (isDarkTheme) {
            stringResource(id = R.string.settings_theme_status_dark)
        } else {
            stringResource(id = R.string.settings_theme_status_light)
        },
        onNavigate = onNavigate,
        trailing = {
            MorphingToggleButton(
                checked = isDarkTheme,
                onCheckedChange = { onToggleTheme() },
                enabledIcon = {
                    Text(text = "🌑", style = MaterialTheme.typography.titleLarge)
                },
                disabledIcon = {
                    Text(text = "☀️", style = MaterialTheme.typography.titleLarge)
                }
            )
        }
    )
}

@Composable
private fun QuietHoursSettingsCard(
    settingsRepository: SettingsRepository,
    onNavigate: () -> Unit
) {
    var quietHoursEnabled by remember {
        mutableStateOf(settingsRepository.isQuietHoursEnabled())
    }

    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_quiet_hours),
        subtitle = if (quietHoursEnabled) {
            stringResource(id = R.string.settings_quiet_hours_status_on)
        } else {
            stringResource(id = R.string.settings_quiet_hours_status_off)
        },
        onNavigate = onNavigate,
        trailing = {
            MorphingToggleButton(
                checked = quietHoursEnabled,
                onCheckedChange = { enabled ->
                    quietHoursEnabled = enabled
                    settingsRepository.saveQuietHoursEnabled(enabled)
                },
                enabledIcon = {
                    Text(text = "🔇", style = MaterialTheme.typography.titleLarge)
                },
                disabledIcon = {
                    Text(text = "💡", style = MaterialTheme.typography.titleLarge)
                }
            )
        }
    )
}

@Composable
private fun LanguageSettingsCard(
    settingsRepository: SettingsRepository,
    onNavigate: () -> Unit
) {
    val currentLanguageName = remember {
        when (settingsRepository.getAppLanguageCode()) {
            "system" -> "System Default"
            "en" -> "English"
            "ru" -> "Русский"
            else -> "System Default"
        }
    }

    DraggableSettingsCard(
        title = stringResource(id = R.string.language_selector_title),
        subtitle = currentLanguageName,
        subtitleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        onNavigate = onNavigate,
        trailing = {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = stringResource(id = R.string.language_selector_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    )
}

/**
 * Informational block at the bottom of the settings list: the "About" blurb
 * and the build/version card.
 */
@Composable
private fun AppInfoSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AboutCard()
        BetaAttributeCard()
    }
}

@Composable
private fun AboutCard(modifier: Modifier = Modifier) {
    val themeState = LocalThemeState.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = when (themeState.themeStyle) {
                        AppThemeStyle.Y2K,
                        AppThemeStyle.NEON -> MaterialTheme.colorScheme.primary
                        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "From live battery glyphs to USB-theft alarms, charging history " +
                    "and unlock light-shows—unlock the full power of the NOTHING glyphs.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

// ------------------------------------------------------------
// Custom attribute card that toggles its supporting text when tapped
// ------------------------------------------------------------

@Composable
private fun BetaAttributeCard(modifier: Modifier = Modifier) {
    val themeState = LocalThemeState.current
    val context = LocalContext.current
    var toggled by rememberSaveable { mutableStateOf(false) }

    // Get version information dynamically
    val versionInfo = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown Version"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { toggled = !toggled },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row with icon and title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = when (themeState.themeStyle) {
                        AppThemeStyle.Y2K,
                        AppThemeStyle.NEON -> MaterialTheme.colorScheme.primary
                        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Glyph Sharge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary attribute label
            Text(
                text = "v$versionInfo",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Supporting text that toggles on tap
            AnimatedContent(targetState = toggled, label = "betaToggle") { isAlt ->
                val body = if (isAlt) {
                    "Special thanks to beedah for the countless installs and quick beta testing."
                } else {
                    "🧪 You're using a public release build. Expect occasional quirks and bugs."
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Requires Android 14+",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}
