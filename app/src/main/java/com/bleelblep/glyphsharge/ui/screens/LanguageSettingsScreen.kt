package com.bleelblep.glyphsharge.ui.screens

import android.content.Context
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import androidx.compose.ui.res.stringResource
import com.bleelblep.glyphsharge.R
import java.util.*

// Language option data class

data class LanguageOption(
    val code: String,
    val nativeName: String,   // Shown in the dropdown
    val englishName: String,  // For internal reference / sorting
)

// Available languages list

private val availableLanguages = listOf(
    LanguageOption("en", "English", "English"),
    LanguageOption("ru", "Русский", "Russian"),
    LanguageOption("system", "🔄 System Default", "System"),
)

// Language settings screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLanguageChanged: (String) -> Unit = {}, // Callback for app-wide locale update
) {
    // Read from the composition: the nav host holds no store to hand down.
    val settingsRepository = LocalSettingsRepository.current
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current

    // Current language state
    var currentLanguageCode by remember {
        mutableStateOf(settingsRepository.getAppLanguageCode())
    }

    // Dropdown state
    var expanded by remember { mutableStateOf(value = false) }
    val selectedLanguage = remember(currentLanguageCode) {
        availableLanguages.find { it.code == currentLanguageCode }
            ?: availableLanguages.first { it.code == "system" }
    }

    SettingsScaffold(
        title = stringResource(R.string.language_selector_title),
        modifier = modifier,
        onBackClick = onBackClick,
    ) {
            // Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.language_settings_current_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = selectedLanguage.nativeName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }

            // Language Selection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.language_settings_select_label),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Exposed Dropdown Menu
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = {
                                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                expanded = it
                            },
                        ) {
                            OutlinedTextField(
                                value = selectedLanguage.nativeName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                availableLanguages.forEach { language ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    text = language.nativeName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                                if (language.code == currentLanguageCode) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                                            currentLanguageCode = language.code
                                            settingsRepository.saveAppLanguageCode(language.code)
                                            onLanguageChanged(language.code)
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Info text
                        Text(
                            text = stringResource(R.string.language_settings_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            // Restart Notice Card (shown when language differs from system)
            if (currentLanguageCode != "system") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.language_settings_restart_note),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.language_restart_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
    }
}

// Apply the selected locale to a Context

fun Context.applyLocale(languageCode: String): Context {
    return if (languageCode == "system") {
        // Use system default
        val config = resources.configuration
        config.setLocales(LocaleList.getDefault())
        createConfigurationContext(config)
    } else {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocales(LocaleList(locale))
        createConfigurationContext(config)
    }
}