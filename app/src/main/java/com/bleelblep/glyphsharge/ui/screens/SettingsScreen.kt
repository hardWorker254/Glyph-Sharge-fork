package com.bleelblep.glyphsharge.ui.screens

import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.CustomAnimationsActivity
import com.bleelblep.glyphsharge.RuleConstructorActivity
import com.bleelblep.glyphsharge.ScriptStoreActivity
import com.bleelblep.glyphsharge.ui.components.controls.MorphingToggleButton
import com.bleelblep.glyphsharge.ui.components.controls.SettingsTrailingIcon
import com.bleelblep.glyphsharge.ui.components.controls.ThreeStateFontMorphingButton
import com.bleelblep.glyphsharge.ui.components.layout.DraggableSettingsCard
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.LocalFontState
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState
import com.bleelblep.glyphsharge.ui.viewmodel.CustomAnimationsViewModel
import com.bleelblep.glyphsharge.utils.LoggingManager

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
    onLanguageSettingsClick: () -> Unit = {},
    onScriptPermissionsClick: () -> Unit = {},
) {
    val fontState = LocalFontState.current
    val themeState = LocalThemeState.current
    val context = LocalContext.current

    // The studio is a separate Activity, so the count is observed straight from
    // the repository rather than through a callback the nav host would have to
    // keep up to date.
    val customAnimations: CustomAnimationsViewModel = hiltViewModel()
    val customAnimationCount by customAnimations.animationCount.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(id = R.string.settings_title),
        onBackClick = onBackClick,
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
                            },
                        )
                    }
                },
            )
        }

        item {
            ThemeSettingsCard(
                isDarkTheme = themeState.isDarkTheme,
                onToggleTheme = themeState::toggleTheme,
                onNavigate = onThemeSettingsClick,
            )
        }

        item {
            LanguageSettingsCard(
                onNavigate = onLanguageSettingsClick,
            )
        }

        item {
            RuleConstructorCard(
                onClick = { context.startActivity(RuleConstructorActivity.intent(context)) },
            )
        }

        item {
            CustomAnimationsCard(
                animationCount = customAnimationCount,
            ) {
                context.startActivity(CustomAnimationsActivity.intent(context))
            }
        }

        item {
            StoreCard(
                onClick = { context.startActivity(ScriptStoreActivity.intent(context)) },
            )
        }

        item {
            ScriptPermissionsCard(
                onNavigate = onScriptPermissionsClick,
            )
        }

        item {
            LogExportCard()
        }

        item {
            AppInfoSection()
        }
    }
}

/**
 * The way to get the app's log off the phone.
 *
 * The log is a file in the app's own directory and nothing else can read it:
 * on a phone with no shell attached, logcat is not an option, so the log is
 * the only way to find out what the app was doing — which trigger fired,
 * which rule was attached, whether the Glyph session was open.
 *
 * The user picks where it goes (a file in Downloads, something they can share
 * from there), and every outcome is said out loud: a log that exports as an
 * empty file looks exactly like an app that did nothing.
 */
@Composable
private fun LogExportCard() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val message = when (LoggingManager.exportTo(uri)) {
            LoggingManager.ExportResult.DONE -> R.string.log_export_done
            LoggingManager.ExportResult.EMPTY -> R.string.log_export_empty
            LoggingManager.ExportResult.FAILED -> R.string.log_export_failed
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_export_logs),
        subtitle = stringResource(id = R.string.settings_card_export_logs_subtitle),
        onNavigate = { launcher.launch(LOG_FILE_NAME) },
        onClick = { launcher.launch(LOG_FILE_NAME) },
        trailing = {
            SettingsTrailingIcon()
        },
    )
}

/** The name the exported log is offered under. */
private const val LOG_FILE_NAME = "glyphsharge-log.txt"

/**
 * The entry point to the constructor — the same door the home list's "＋"
 * opens, offered here so the app's main feature is reachable from Settings
 * the way the studio is.
 *
 * The subtitle is the constructor's own line rather than a count: what is in
 * there is rules the user built, they are already listed on the home screen,
 * and a number here would add nothing the list does not say better.
 */
@Composable
private fun RuleConstructorCard(
    onClick: () -> Unit,
) {
    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_rule_constructor),
        subtitle = stringResource(id = R.string.rules_constructor_subtitle),
        onNavigate = onClick,
        onClick = onClick,
        trailing = {
            SettingsTrailingIcon()
        },
    )
}

@Composable
private fun CustomAnimationsCard(
    animationCount: Int,
    onClick: () -> Unit,
) {
    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_custom_animations),
        subtitle = if (animationCount == 0) {
            stringResource(id = R.string.settings_card_custom_animations_subtitle)
        } else {
            pluralStringResource(
                id = R.plurals.settings_card_custom_animations_count,
                count = animationCount,
                animationCount,
            )
        },
        onNavigate = onClick,
        onClick = onClick,
        trailing = {
            // No control of its own, so the chip draws its own mark rather
            // than leaving the right edge of the card empty.
            SettingsTrailingIcon()
        },
    )
}

/**
 * The entry point to the catalogue.
 *
 * Its own door, and outside the studio's, because it is no longer the studio's
 * to host: it publishes `.servicelua` rules as much as `.glyphlua` animations,
 * and someone looking for a rule should not have to open a code editor to get
 * one. Placed after the studio and before permissions on purpose — the three
 * read as *write one* / *get one* / *decide what they may do*.
 *
 * No count in the subtitle: what the catalogue holds is not what this phone
 * holds, and a number on this screen would be a network call on every visit.
 */
@Composable
private fun StoreCard(
    onClick: () -> Unit,
) {
    DraggableSettingsCard(
        title = stringResource(id = R.string.store_title),
        subtitle = stringResource(id = R.string.settings_card_store_subtitle),
        onNavigate = onClick,
        onClick = onClick,
        trailing = {
            SettingsTrailingIcon()
        },
    )
}

/**
 * The entry point to the withdraw side of the capability model.
 *
 * No count in the subtitle: the number that matters here is neither the
 * scripts nor their grants but the decisions a user may want to revisit, and
 * a "3" would not say which. The screen itself is the honest preview — it
 * lists exactly what each script reaches for.
 */
@Composable
private fun ScriptPermissionsCard(
    onNavigate: () -> Unit,
) {
    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_script_permissions),
        subtitle = stringResource(id = R.string.settings_card_script_permissions_subtitle),
        onNavigate = onNavigate,
        onClick = onNavigate,
        trailing = {
            SettingsTrailingIcon()
        },
    )
}

@Composable
private fun TypographySettingsCard(
    onNavigate: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    DraggableSettingsCard(
        title = stringResource(id = R.string.settings_card_typography),
        onNavigate = onNavigate,
        trailing = trailing,
    )
}

@Composable
private fun ThemeSettingsCard(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigate: () -> Unit,
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
                },
            )
        },
    )
}

@Composable
private fun LanguageSettingsCard(
    onNavigate: () -> Unit,
) {
    val settingsRepository = LocalSettingsRepository.current
    // Through resources, not literals.
    //
    // This row is the language picker, so "System Default" written in English
    // here was a Russian user reading English *inside their own language
    // settings* — and `language_option_system` had been translated in both
    // locales the whole time, unused.
    //
    // "English" and "Русский" are proper nouns and read the same in every
    // language, but they go through resources anyway: a language name is
    // exactly the sort of string that gets a translator to second-guess it,
    // and having one place to change is worth more than the certainty that
    // neither will.
    val currentLanguageName = stringResource(
        when (settingsRepository.getAppLanguageCode()) {
            "en" -> R.string.language_option_en
            "ru" -> R.string.language_option_ru
            else -> R.string.language_option_system
        },
    )

    DraggableSettingsCard(
        title = stringResource(id = R.string.language_selector_title),
        subtitle = currentLanguageName,
        subtitleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        onNavigate = onNavigate,
        trailing = {
            // In the same plate every other row uses, rather than a bare icon
            // against the card: a row whose trailing control is the only one
            // without the frame does not read as deliberate. The globe is an
            // emoji set in `titleLarge` because every other row's mark is —
            // the same material icon at the same dp reads a size smaller.
            SettingsTrailingIcon {
                Text(text = "🌐", style = MaterialTheme.typography.titleLarge)
            }
        },
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
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = when (themeState.themeStyle) {
                        AppThemeStyle.Y2K -> MaterialTheme.colorScheme.primary
                        AppThemeStyle.NEON -> MaterialTheme.colorScheme.primary
                        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(id = R.string.settings_card_about),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.settings_about_description),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
            )
        }
    }
}

// Custom attribute card that toggles its supporting text when tapped

@Composable
private fun BetaAttributeCard(modifier: Modifier = Modifier) {
    val themeState = LocalThemeState.current
    val context = LocalContext.current
    var toggled by rememberSaveable { mutableStateOf(value = false) }

    // Get version information dynamically
    val versionInfo = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (_: PackageManager.NameNotFoundException) {
            context.getString(R.string.settings_about_unknown_version)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { toggled = !toggled },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row with icon and title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = when (themeState.themeStyle) {
                        AppThemeStyle.Y2K -> MaterialTheme.colorScheme.primary
                        AppThemeStyle.NEON -> MaterialTheme.colorScheme.primary
                        AppThemeStyle.CLASSIC -> Color(0xFF674FA3)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary attribute label
            Text(
                text = "v$versionInfo",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Supporting text that toggles on tap
            AnimatedContent(targetState = toggled, label = "betaToggle") { isAlt ->
                val body = if (isAlt) {
                    stringResource(id = R.string.settings_about_beta_alt)
                } else {
                    stringResource(id = R.string.settings_about_beta_notice)
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.settings_about_requires),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}
