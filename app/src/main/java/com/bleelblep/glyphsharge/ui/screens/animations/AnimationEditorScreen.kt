package com.bleelblep.glyphsharge.ui.screens.animations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.LogLevel
import com.bleelblep.glyphsharge.glyph.script.ScriptTarget
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
    modifier: Modifier = Modifier
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

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isDirty) stringResource(R.string.studio_editor_title_dirty)
                        else stringResource(R.string.studio_editor_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                        onSave()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.studio_action_save)
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.studio_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                            contentColor = MaterialTheme.colorScheme.onPrimary
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
                                    color = line.color()
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
                    .weight(1f)
                    .heightIn(min = 160.dp)
            )

            ApiReference()
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
        color = MaterialTheme.colorScheme.primary
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

/**
 * A plain monospace text field.
 *
 * `BasicTextField` rather than `OutlinedTextField` because the decoration of a
 * form control is noise on a code surface, and because the field has to scroll
 * in both axes without a label floating over the first line.
 */
@Composable
private fun CodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        BasicTextField(
            value = source,
            onValueChange = onSourceChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vertical)
                .horizontalScroll(horizontal)
        )
    }
}

/**
 * The `glyph` API, on screen.
 *
 * Worth more than a help page here: a script is a handful of lines, and the
 * only thing a first-time author needs is the list of calls and what they do.
 * The `require` modules are in the same card for the same reason — an author
 * who cannot find the module list has no way to discover that one exists.
 */
@Composable
private fun ApiReference(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val cardColor = themeCardContainerColor()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.studio_reference_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    API_REFERENCE.forEach { entry ->
                        ReferenceRow(entry)
                    }
                    Text(
                        text = stringResource(R.string.studio_reference_modules_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    MODULE_REFERENCE.forEach { entry ->
                        ReferenceRow(entry)
                    }
                    Text(
                        text = MODULE_REFERENCE_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.studio_reference_example_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = MODULE_REFERENCE_EXAMPLE,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * One line of the reference: the call on the left, what it does on the right.
 *
 * Extracted because the cheat sheet now has two sections and a reader must
 * not be able to tell them apart by accident.
 */
@Composable
private fun ReferenceRow(entry: Pair<String, String>) {
    Row {
        Text(
            text = entry.first,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.width(170.dp)
        )
        Text(
            text = entry.second,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A [RowScope] extension so the action buttons share the width evenly. */
@Composable
private fun RowScope.StudioButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** How many console lines stay on screen. */
private const val CONSOLE_VISIBLE_LINES = 4

/**
 * Every entry of the `glyph` table, in the order a script author meets them.
 *
 * Kept next to the editor so that adding a binding to `GlyphLuaApi` and
 * documenting it here stays one edit.
 */
private val API_REFERENCE = listOf(
    "glyph.set(list, brightness, [hold])" to "Light channels and keep them lit",
    "glyph.setAll(brightness, [hold])" to "Light every channel",
    "glyph.off([holdMs])" to "Blank the strip",
    "glyph.hold(ms)" to "Wait, interruptibly",
    "glyph.pulse(list, onMs, offMs, [b])" to "On, all off, then pause",
    "glyph.blinkAll(onMs, offMs, [b], [times])" to "Blink the whole strip",
    "glyph.sweep(list, stepMs, [b], [rev])" to "Progressive sweep",
    "glyph.wave(list, stepMs, [b], [trail])" to "Travelling wave with a tail",
    "glyph.spiral([cycles], stepMs, [b])" to "Sweep out and back",
    "glyph.heartbeat([n], beatMs, gapMs, [b])" to "Lub-dub",
    "glyph.batteryBar(percent, ms)" to "Fill the C strip like charging",
    "glyph.ch.c / .a / .b / .d / .e" to "Channel groups for this phone",
    "glyph.ch.all / .nonC / .spiral" to "Whole strip, non-C, spiral order",
    "glyph.group(\"c\")" to "The same list, by name",
    "glyph.MAX" to "4000 — the brightest a channel goes",
    "glyph.battery / .charging" to "Snapshot while the script runs",
    "glyph.elapsed() / glyph.frame()" to "Milliseconds elapsed, frames drawn",
    "glyph.running" to "False once the animation is stopped",
    "glyph.rnd(a, b) / .seed(n)" to "Reproducible randomness",
    "glyph.ease(t, kind)" to "linear, in, out, inout, bounce, wave, pulse",
    "glyph.exit()" to "Finish early",
    "glyph.log(text) / print(text)" to "Write to this console"
)

/**
 * The six modules `require` can resolve, in the alphabetical order the
 * registry reports them in, so a name read here is the name on the error
 * message a mistyped one produces.
 *
 * Hardcoded English for the same reason [API_REFERENCE] is: the whole cheat
 * sheet is one card of developer-facing text, and half of it translated would
 * read worse than none of it. The module names themselves are identifiers and
 * are never reworded.
 */
private val MODULE_REFERENCE = listOf(
    "local time = require(\"glyph.time\")" to "How a require call reads: bind it to a local",
    "glyph.time" to "The wall clock, live: hour, minute, minuteOfDay, isNight",
    "glyph.battery" to "Charge, live: percent, charging, level() segments",
    "glyph.net" to "Network, live: connected, wifi, metered, vpn",
    "glyph.sensor" to "Movement, live: x, y, z, magnitude, shaken",
    "glyph.log" to "This console: info, warn, and error — which stops the run",
    "glyph.util" to "Pure arithmetic: clamp, lerp, mapRange, shuffle"
)

/**
 * The one thing about `require` an author will otherwise get wrong.
 *
 * The registry is a closed list, and the error a mistyped name produces is
 * the only place the app ever says so — at run time, on a phone, in the
 * middle of an animation. A line of prose in the one place the author is
 * already looking costs nothing and prevents that.
 */
private const val MODULE_REFERENCE_NOTE =
    "require resolves these six names and nothing else. There is no " +
        "require(\"os\"), and no way to read a file: that is the sandbox, and " +
        "it is deliberate."

    /**
     * One worked example, because six names and a warning do not tell a reader
     * what `require` is *for*.
     *
     * The three-step day/night dimming is the shape most people reach a script
     * for: the feature woke it, and the script decides how loudly to answer.
     * It needs no new service and no permission — the same source runs
     * unchanged on every feature that can host a script, which is the point a
     * list of module names cannot make on its own.
     *
     * Kept out of [MODULE_REFERENCE] deliberately: that list is one line per
     * module in a two-column layout, and this needs the lines it actually
     * takes. Hardcoded for the same reason as everything else on this card.
     */
    private val MODULE_REFERENCE_EXAMPLE = """
        local time = require("glyph.time")

        -- 22:00–06:00 the strip stays dark, 12:00–22:00 it dims,
        -- 06:00–12:00 it runs at full brightness.
        local level = time.DAY
        if time.hour >= 12 then level = time.DUSK end
        if time.isNight then level = time.NIGHT end

        if level == 0 then
          glyph.off()
          return
        end

        glyph.setAll(level)
    """.trimIndent()
