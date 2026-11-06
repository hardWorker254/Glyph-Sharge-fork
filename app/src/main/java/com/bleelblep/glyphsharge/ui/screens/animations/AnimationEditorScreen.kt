package com.bleelblep.glyphsharge.ui.screens.animations


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.LogLevel
import com.bleelblep.glyphsharge.glyph.script.ScriptTarget
import com.bleelblep.glyphsharge.ui.components.CodeEditor
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.themeCardContainerColor
import com.bleelblep.glyphsharge.ui.theme.themePrimaryActionColor
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import com.bleelblep.glyphsharge.ui.viewmodel.ConsoleLine

/**
 * The editor: a name, a Lua source, and two ways to find out whether it works.
 *
 * **Check** compiles it and reports the first syntax error. **Glyph** plays it
 * on the real hardware, which is the only verdict that counts. There is no
 * on-screen preview: a schematic of the LED layout is not the same as the
 * phone lighting up, and a second "run" mode invites mistaking one for the
 * other.
 *
 * @param isRunning `true` while a glyph run is in flight
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationEditorScreen(
    name: String,
    source: String,
    console: List<ConsoleLine>,
    isDirty: Boolean,
    isRunning: Boolean,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onSave: () -> Unit,
    onCheck: () -> Unit,
    onRunGlyph: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    // Derived from the buffer, not stored: the target lives in the source, so
    // this has to track every keystroke to stay honest.
    val detectedTarget = remember(source) { ScriptTarget.detectIn(source) }

    // The page's own scroll state. One for the screen, separate from the two
    // the code editor keeps for itself — a script is taller than any phone, and
    // its two axes have to scroll independently of everything around them.
    val page = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isDirty) stringResource(R.string.studio_editor_title_dirty)
                        else stringResource(R.string.studio_editor_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            onSave()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.studio_action_save),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                // The page scrolls, because the reference card below does not
                // fit. It used not to: the editor below took `weight(1f)` and
                // therefore every pixel that was left, so an expanded reference
                // was measured into a zero-height slot and clipped — reachable
                // by no gesture at all.
                .verticalScroll(page),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.studio_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Which picker offers this script is decided by `glyph.target` in
            // the source, so it is read back from the source rather than
            // stored. Showing it here means the author finds out that the line
            // has to go *before* the first glyph.set(), instead of wondering
            // why nothing ever picked it up.
            Text(
                text = stringResource(
                    if (detectedTarget == ScriptTarget.MUSIC) {
                        R.string.studio_target_music
                    } else {
                        R.string.studio_target_any
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioButton(
                            icon = Icons.Filled.Check,
                            label = stringResource(R.string.studio_action_check),
                            onClick = {
                                HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                                onCheck()
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StudioButton(
                            icon = if (isRunning) Icons.Filled.Stop else Icons.Filled.PhoneAndroid,
                            label = if (isRunning) stringResource(R.string.studio_action_stop)
                            else stringResource(R.string.studio_action_run_glyph),
                            onClick = {
                                HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                                if (isRunning) onStop() else onRunGlyph()
                            },
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    if (isRunning) {
                        RunningHint()
                    }

                    if (console.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            console.takeLast(CONSOLE_VISIBLE_LINES).forEach { line ->
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = line.color(),
                                )
                            }
                        }
                    }
                }
            }

            CodeEditor(
                source = source,
                onSourceChange = onSourceChange,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    // Bounded rather than `weight(1f)`. The editor scrolls in
                    // both axes by itself, so it does not need to grow to fill
                    // the screen — and giving it the leftover height is what
                    // pushed the reference off the bottom in the first place.
                    .heightIn(min = 200.dp, max = 420.dp),
            )
        }
    }
}

/**
 * The result of the last run.
 *
 * A button that silently does nothing is the worst outcome here, so the glyph
 * run says what it is doing while it is doing it.
 */
@Composable
private fun RunningHint() {
    Text(
        text = stringResource(R.string.studio_running_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The colour a console line is drawn in.
 *
 * Three roles out of the existing scheme rather than three literal colours, so
 * a line keeps its meaning in the AMOLED and classic themes. The progression
 * is deliberate: ordinary output is the dimmest thing on the card, a warning is
 * an accent, and only a failure is red — so the eye is drawn to the one line
 * that ended the run.
 */
@Composable
private fun ConsoleLine.color(): Color = when (level) {
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

/** A [RowScope] extension so the action buttons share the width evenly. */
@Composable
private fun RowScope.StudioButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** How many console lines stay on screen. */
private const val CONSOLE_VISIBLE_LINES = 4
