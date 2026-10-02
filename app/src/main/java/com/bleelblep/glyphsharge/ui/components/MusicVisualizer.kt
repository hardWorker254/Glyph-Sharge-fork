package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.audio.MusicVisualizationMode
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptTarget
import com.bleelblep.glyphsharge.glyph.script.target
import com.bleelblep.glyphsharge.ui.components.dialogs.FeatureConfirmationFlow
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.themeCardContainerColor
import com.bleelblep.glyphsharge.ui.theme.themePrimaryActionColor
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import com.bleelblep.glyphsharge.ui.viewmodel.CustomAnimationsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/** What the configuration dialog hands back when the user saves. */
data class MusicVisualizerConfig(
    val isEnabled: Boolean = false,
    val animationId: String = MusicVisualizationMode.DEFAULT.id,
    val sensitivity: Float = 1.0f,
    val screenOffOnly: Boolean = false,
)

/** The low end of the sensitivity slider; matches `AudioAnalyzer.MIN_GAIN`. */
private const val SENSITIVITY_MIN = 0.5f

/** The high end; matches `AudioAnalyzer.MAX_GAIN`. */
private const val SENSITIVITY_MAX = 3.0f

@Composable
fun MusicVisualizerConfirmationDialog(
    onTestAnimation: () -> Unit,
    onEnableAnimation: () -> Unit,
    onDisableAnimation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeatureConfirmationFlow(
        title = stringResource(id = R.string.music_viz_title),
        subtitle = stringResource(id = R.string.music_viz_description),
        howItWorksTitle = stringResource(id = R.string.music_viz_how_it_works_title),
        howItWorksDescription = stringResource(id = R.string.music_viz_how_it_works_description),
        testLabel = stringResource(id = R.string.music_viz_button_test),
        onTest = onTestAnimation,
        onEnable = onEnableAnimation,
        onDisable = onDisableAnimation,
        onDismiss = onDismiss,
        modifier = modifier,
        settings = { onConfirm, onDisable, onDismissSettings ->
            MusicVisualizerEnableDialog(
                onConfirm = { onConfirm() },
                onDismiss = onDismissSettings,
                onDisable = onDisable,
            )
        },
    )
}

/**
 * Picks the mode, tunes how hard it reacts, and says plainly what the feature
 * does with the audio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicVisualizerEnableDialog(
    onConfirm: (MusicVisualizerConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The store comes from the composition; the card has none to pass on.
    val settingsRepository = LocalSettingsRepository.current
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glyphAnimationManager = rememberGlyphAnimationManager()

    val currentlyEnabled = remember { settingsRepository.isMusicVizEnabled() }

    // The music picker also lists the user's own scripts — but only the ones
    // that declared `glyph.target = "music"`, since a script with no target is
    // offered everywhere and one written for the trigger features belongs there.
    val scriptOptions = rememberMusicScriptOptions()

    var selectedMode by remember {
        mutableStateOf(MusicVisualizationMode.of(settingsRepository.getMusicVizAnimationId()))
    }
    var selectedScriptId by remember {
        mutableStateOf(
            scriptOptions.firstOrNull { it.runtimeId == settingsRepository.getMusicVizAnimationId() },
        )
    }
    var sensitivity by remember {
        mutableFloatStateOf(settingsRepository.getMusicVizSensitivity())
    }
    var screenOffOnly by remember {
        mutableStateOf(settingsRepository.getMusicVizScreenOffOnly())
    }
    // No `isSaving`: the save is four synchronous SharedPreferences writes and
    // an `apply()`, so there is no work for a spinner to describe, and leaving
    // it set on a dialog whose `onConfirm` did not close it was a button that
    // showed "working" forever while nothing was working.
    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(id = R.string.music_viz_configure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(id = R.string.music_viz_configure_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.music_viz_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            MusicVisualizationMode.entries.forEach { mode ->
                                val isSelected = (selectedScriptId == null) && (selectedMode == mode)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                        selectedMode = mode
                                        selectedScriptId = null
                                    },
                                    label = { Text(mode.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }

                            scriptOptions.forEach { script ->
                                FilterChip(
                                    selected = selectedScriptId == script,
                                    onClick = {
                                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                        selectedScriptId = script
                                    },
                                    label = { Text(script.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                )
                            }
                        }

                        // Previewed on made-up audio: a dialog cannot reach the
                        // live capture without a service, and the point here is
                        // to show the *shape* of the mode — with the current
                        // sensitivity applied, so the slider has something to
                        // show for itself.
                        Button(
                            onClick = {
                                val mode = selectedMode ?: MusicVisualizationMode.DEFAULT
                                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                scope.launch {
                                    runCatching {
                                        glyphAnimationManager.previewMusicVisualizer(
                                            mode,
                                            sensitivity,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(id = R.string.music_viz_mode_preview))
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(id = R.string.music_viz_sensitivity_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ThemedValueBadge(String.format(Locale.getDefault(), "%.1f", sensitivity))
                        }

                        Slider(
                            value = sensitivity,
                            // No haptic per pixel: a drag reports a value for every pixel of travel,
                            // so firing on each one buzzes continuously under the thumb and buries the
                            // one that should land at the end. One buzz, at the end.
                            onValueChange = { sensitivity = it },
                            onValueChangeFinished = {
                                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                            },
                            valueRange = SENSITIVITY_MIN..SENSITIVITY_MAX,
                            steps = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(id = R.string.music_viz_sensitivity_min),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(id = R.string.music_viz_sensitivity_max),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.music_viz_screen_off_only),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.music_viz_screen_off_only_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = screenOffOnly,
                            onCheckedChange = {
                                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                screenOffOnly = it
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            FeatureSaveButtons(
                isCurrentlyEnabled = currentlyEnabled,
                enableLabel = stringResource(id = R.string.music_viz_button_enable),
                // All three settings are written together, here. The chip
                // handlers used to write the animation id as soon as a mode was
                // tapped, which meant pressing Cancel left the mode changed on
                // disk while `sensitivity` and `screenOffOnly` were not — and
                // the next run of the feature, possibly started from the Quick
                // Settings tile, used a mix of two abandoned sessions.
                onSave = {
                    settingsRepository.saveMusicVizAnimationId(
                        selectedScriptId?.runtimeId
                            ?: (selectedMode ?: MusicVisualizationMode.DEFAULT).id,
                    )
                    settingsRepository.saveMusicVizSensitivity(sensitivity)
                    settingsRepository.saveMusicVizScreenOffOnly(screenOffOnly)
                    onConfirm(
                        MusicVisualizerConfig(
                            isEnabled = true,
                            animationId = selectedScriptId?.runtimeId
                                ?: (selectedMode ?: MusicVisualizationMode.DEFAULT).id,
                            sensitivity = sensitivity,
                            screenOffOnly = screenOffOnly,
                        ),
                    )
                },
                onDisable = {
                    onDisable()
                    onDismiss()
                },
                onCancel = onDismiss,
            )
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    )
}

/**
 * The studio scripts that declared themselves for the music visualiser.
 *
 * Only the ones that said so: a script with no target is offered by the four
 * trigger features, and listing a screen full of unrelated animations here would
 * bury the six built-in modes.
 */
@Composable
private fun rememberMusicScriptOptions(): List<ScriptAnimation> {
    val viewModel: CustomAnimationsViewModel = hiltViewModel()
    val scripts by viewModel.animations.collectAsStateWithLifecycle()
    return remember(scripts) { scripts.filter { it.target == ScriptTarget.MUSIC } }
}
