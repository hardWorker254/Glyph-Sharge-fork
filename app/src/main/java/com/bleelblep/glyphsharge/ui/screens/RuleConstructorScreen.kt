package com.bleelblep.glyphsharge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.GlyphServiceSettings
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import com.bleelblep.glyphsharge.rules.GlyphRule
import com.bleelblep.glyphsharge.triggers.TriggerSources
import com.bleelblep.glyphsharge.triggers.TriggerType
import com.bleelblep.glyphsharge.ui.components.CapabilityPromptDialog
import com.bleelblep.glyphsharge.ui.components.CodeEditor
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.components.rememberAnimationOptions
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository

/**
 * The five named steps of the vibration scale, softest first.
 *
 * The same constants Power Peek's slider snaps to: the detector is tuned
 * around these values, so a rule's threshold is one of them rather than a
 * free number the sensor cannot honour.
 */
private val SHAKE_STEPS = floatArrayOf(
    GlyphServiceSettings.SHAKE_SOFT,
    GlyphServiceSettings.SHAKE_EASY,
    GlyphServiceSettings.SHAKE_MEDIUM,
    GlyphServiceSettings.SHAKE_HARD,
    GlyphServiceSettings.SHAKE_HARDEST,
)

/**
 * The battery percentages the threshold slider can show, low to high.
 *
 * Named rather than written into the `Slider`, because they are read in two
 * places that must agree: the range it draws and the clamp applied to whatever
 * the form opens on. Below 5 the alert fires on almost every idle dip; above 50
 * it is a message the phone shows when it is nearly dead.
 */
private const val LOW_BATTERY_MIN_PERCENT = 5
private const val LOW_BATTERY_MAX_PERCENT = 50

/**
 * The one animation a charging rule may play.
 *
 * A charger is not a generic trigger: plugging in should show the battery bar,
 * not whichever sparkle was last selected. The picker stays on the other
 * triggers, and this id is the built-in that reads the live battery level.
 */
private const val FIXED_CHARGING_ANIMATION_ID = "BATTERY"

/**
 * The constructor: a name, a shape, and the facts that shape is made of.
 *
 * **A screen, not a dialog.** It used to be an `AlertDialog` opened from the
 * home list, which meant the form's fields, its scroll and its two buttons had
 * to fit inside a fixed-height box, and there was nowhere for the thing to live
 * except over the list that owns it. As a screen it gets the shared
 * [SettingsScaffold] — the same chrome as every other destination — a Save
 * action in the top bar where it stays reachable whatever the list has
 * scrolled to, and a back arrow that discards exactly the way the dialog's
 * Cancel did. [RuleConstructorActivity] hosts it and owns the ViewModel that
 * writes the rule; this composable takes plain parameters so it can be drawn
 * from a preview or a test.
 *
 * **Simple mode** is what it always was — name, trigger, animation, duration
 * — for the rule that fires one animation when one thing happens.
 *
 * **Advanced mode** hands the "when" to a `.servicelua` script, written in a
 * code field lifted straight out of the studio — the same monospace surface,
 * the same starter a new script begins from. The buffer *is* the rule's
 * service, and the only place it is chosen: saving a rule that has no script
 * yet mints one named after the rule, saving a rule that has one rewrites
 * that script in place. The chips row that used to sit above the field — the
 * installed scripts you could pick from — is gone: it answered the same
 * question in the wrong direction, showing a *name* where the field already
 * shows the code that will actually run. Import is deliberately *not* here —
 * a file enters the app through the constructor's list screen, so this form
 * stays a form and not also a file manager. The
 * animation and duration stay here on purpose — the whole design contract of
 * `.servicelua` is that the script cannot draw and never names an animation;
 * the app resolves it from what the user picked here. The trigger chips
 * disappear in advanced mode because the trigger field would be a fallback
 * nothing consults, and a control that does nothing is worse than no control.
 *
 * **The prompt before a save that carries code** is the import's prompt: the
 * buffer is scanned the same way a file is, and anything it reaches for is
 * listed under the same labels the Settings screen will show later, because
 * where the code came from changes nothing about what the app is about to
 * use. It only ever shows what the script does not *already* hold — a resave
 * of allowed code (a name edit, a threshold nudge) is not a new ask, and a
 * prompt on every save trains the user to dismiss the ones that are.
 * Declining leaves the form unsaved, exactly as declining an import
 * installs nothing. Prompting a *picked file* is still not rendered here:
 * that sequence lives in `rememberServiceScriptImport` and has one caller,
 * the list screen behind this form.
 *
 * [existing] turns this from "create" into "edit" for either shape: a
 * scripted rule reopens in advanced mode with its own script's code in the
 * field.
 *
 * Every rule is the user's own, so every part of the form is drawn: the name,
 * the trigger, the animation, the duration, and the two numbers the
 * parameterised triggers carry — the battery level that triggers a low-battery
 * rule and the jolt that triggers a shake rule. They travel with the rule into
 * the host, which is what makes a rule self-contained: nothing outside it has
 * to be consulted to know when it should fire.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleConstructorScreen(
    existing: GlyphRule? = null,
    onSave: (
        existingId: String?,
        name: String,
        triggerId: String,
        animationId: String,
        durationMs: Long,
        lowBatteryThreshold: Int,
        shakeThreshold: Float,
        scriptId: String?,
        code: String?,
        accepted: Set<ScriptCapability>,
    ) -> Unit,
    onBackClick: () -> Unit,
    serviceScripts: List<ServiceScript> = emptyList(),
    grantedFor: (String) -> Set<ScriptCapability> = { emptySet() },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val availableTriggers = remember(context) { TriggerSources.available(context) }
    val animationOptions = rememberAnimationOptions()

    // A scripted rule reopens in advanced mode; a simple one does not.
    var isAdvanced by remember(existing) {
        mutableStateOf(existing?.isScripted == true)
    }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var trigger by remember(existing, availableTriggers) {
        mutableStateOf(
            TriggerType.fromId(existing?.triggerId ?: "")
                ?: availableTriggers.firstOrNull()
                ?: TriggerType.SCREEN_OFF,
        )
    }
    var animation by remember(existing, animationOptions) {
        mutableStateOf(
            animationOptions.firstOrNull { it.id == existing?.animationId }
                ?: animationOptions.first(),
        )
    }
    val isChargingTrigger = trigger == TriggerType.POWER_CONNECTED ||
        trigger == TriggerType.POWER_DISCONNECTED
    val selectedAnimation = if (isChargingTrigger) {
        animationOptions.firstOrNull { it.id == FIXED_CHARGING_ANIMATION_ID } ?: animation
    } else {
        animation
    }
    var durationSeconds by remember(existing) {
        mutableFloatStateOf(((existing?.durationMs ?: 3000L) / 1000f).coerceIn(1f, 10f))
    }
    var threshold by remember(existing) {
        // Clamped where it is read, like the duration above, because a rule can
        // arrive from outside the app: an imported `.servicelua` is JSON and its
        // threshold is whatever its author wrote, and `Slider` throws rather than
        // snapping when its `value` is outside `valueRange` — a crash on opening
        // a rule that had been working, which is a poor price for a value nobody
        // chose.
        val stored = existing?.lowBatteryThreshold ?: GlyphRule.DEFAULT_LOW_BATTERY_THRESHOLD
        mutableStateOf(stored.coerceIn(LOW_BATTERY_MIN_PERCENT, LOW_BATTERY_MAX_PERCENT))
    }
    var shakeStep by remember(existing) {
        val stored = existing?.shakeThreshold ?: GlyphRule.DEFAULT_SHAKE_THRESHOLD
        // Snap whatever is stored to the nearest step at or below it, so a
        // threshold from a build with a different scale still opens on a
        // position the slider can represent. `indexOfFirst` rather than a
        // search on the raw array: the step list is the scale, and a lookup
        // that returns -1 is handled here rather than in the slider.
        val nearest = SHAKE_STEPS.indexOfFirst { it <= stored }.let { if (it == -1) 0 else it }
        mutableFloatStateOf(nearest.toFloat())
    }
    // The rule's own script — not a choice the form offers any more, since the
    // code field is the script and the row of chips that used to sit above it
    // was a second way to say the same thing with less context. What it is
    // still needed for is the save: the permissions already granted against
    // this script, and rewriting it in place rather than minting a second copy
    // of it every time the rule is edited.
    val ownScriptId = existing?.scriptId ?: ""

    // The advanced-mode code buffer: the rule's own script's source when it
    // has one, the starter otherwise — the way the studio seeds a new
    // animation. `rememberSaveable` rather than `remember` because this is
    // the one buffer on this form worth surviving a rotation: everything
    // else here is cheap to retype, code is not. The repository reads its
    // list synchronously, so "the rule's script" is answerable on the first
    // frame — which is why the lookup is here and not in an effect.
    //
    // The auto-select that used to sit next door went with it: seeding a
    // script id the buffer does not describe would let the next save write
    // this starter into somebody else's installed script.
    val starterSource = stringResource(R.string.rules_starter_script)
    var code by rememberSaveable(existing, starterSource) {
        mutableStateOf(
            existing?.scriptId
                ?.let { id -> serviceScripts.firstOrNull { it.runtimeId == id }?.source }
                ?: starterSource,
        )
    }

    // The buffer only counts as a script's text in advanced mode: a rule the
    // user made simple carries no code, whatever the field still holds from
    // a mode it was shown in. Blank keeps the old fallback — an advanced
    // rule with nothing written saves as the simple shape rather than as a
    // rule that cannot fire.
    val performSave: (Set<ScriptCapability>) -> Unit = { accepted ->
        val source = if (isAdvanced) code.takeIf { it.isNotBlank() } else null
        val scripted = isAdvanced && (source != null || ownScriptId.isNotBlank())
        onSave(
            existing?.id,
            name.trim(),
            trigger.id,
            selectedAnimation.id,
            (durationSeconds * 1000).toLong(),
            threshold,
            SHAKE_STEPS[shakeStep.toInt()],
            if (scripted) ownScriptId.takeIf { it.isNotBlank() } else null,
            source,
            accepted,
        )
    }

    // The prompt before a save that carries code: the import's dialog and
    // the import's question, because where the code came from changes
    // nothing about what the app is about to use. Declining saves nothing —
    // the form stays exactly as it is, same as declining an import
    // installs nothing.
    var pendingCapabilities by remember { mutableStateOf<Set<ScriptCapability>?>(null) }
    val submit = {
        val source = if (isAdvanced) code.takeIf { it.isNotBlank() } else null
        // Only what the script does not already hold: a resave of
        // already-allowed code — a name edit, a threshold nudge — is not a
        // new ask, and a prompt on every save trains the user to dismiss
        // the ones that are.
        val requested = source
            ?.let { ScriptCapability.detect(it) - grantedFor(ownScriptId) }
            .orEmpty()
        if (requested.isEmpty()) performSave(emptySet()) else pendingCapabilities = requested
    }

    // Read here rather than only inside the list's content lambda:
    // `LazyListScope` is not a composable scope, so a branch decided there is
    // only re-run when its enclosing composition re-runs. Reading both in the
    // body makes the screen recompose on the mode or the trigger changing, and
    // with it the content lambda — which is what adds or removes the parameter
    // sections below.
    val advanced = isAdvanced
    val activeTrigger = trigger
    // Hoisted for the same reason as the two above: the branch that draws the
    // duration slider is decided inside the list's content lambda. It reads the
    // animation the rule will actually save, not the picker's stale selection.
    val customAnimation = selectedAnimation.isCustom

    pendingCapabilities?.let { requested ->
        CapabilityPromptDialog(
            capabilities = requested,
            onAccept = {
                pendingCapabilities = null
                performSave(requested)
            },
            onDecline = { pendingCapabilities = null },
        )
    }

    SettingsScaffold(
        title = stringResource(
            if (existing == null) R.string.rules_constructor_title
            else R.string.rules_edit_title,
        ),
        modifier = modifier.imePadding(),
        onBackClick = onBackClick,
        actions = {
            // The one action on this screen, in the top bar so it is never
            // below the fold of a form that grows with the trigger palette.
            IconButton(onClick = submit) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(R.string.rules_save),
                )
            }
        },
    ) {
        item {
            Text(
                text = stringResource(R.string.rules_constructor_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The two modes, for a rule that has a choice of them.
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !advanced,
                    onClick = { isAdvanced = false },
                    label = { Text(stringResource(R.string.rules_mode_simple)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = advanced,
                    onClick = { isAdvanced = true },
                    label = { Text(stringResource(R.string.rules_mode_advanced)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.rules_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (advanced) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Just the code, on the studio's own surface: a plain
                    // monospace field, bounded because it scrolls in both
                    // axes by itself and the animation this rule fires
                    // should not be pushed off the screen by its editor.
                    //
                    // The chips row that used to sit above this one is gone.
                    // It asked which installed script the rule should run and
                    // answered the same question this field already answers —
                    // only in the wrong direction: the field shows the code
                    // that will run, the chips showed a name for it, and the
                    // two could disagree.
                    CodeEditor(
                        source = code,
                        onSourceChange = { code = it },
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp),
                    )
                }
            }
        } else {
            // The trigger, and the numbers the two parameterised triggers carry:
            // a battery level and a jolt. Both are the rule's own data, so both
            // travel with it into the host.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.rules_trigger_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        availableTriggers.forEach { type ->
                            FilterChip(
                                selected = type == activeTrigger,
                                onClick = { trigger = type },
                                label = { Text(stringResource(type.labelRes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            if (activeTrigger == TriggerType.LOW_BATTERY) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.rules_threshold_label) +
                                " — " + threshold + "%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Slider(
                            value = threshold.toFloat(),
                            onValueChange = { threshold = it.toInt() },
                            valueRange = LOW_BATTERY_MIN_PERCENT.toFloat()..LOW_BATTERY_MAX_PERCENT.toFloat(),
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            if (activeTrigger == TriggerType.SHAKE) {
                // The vibration scale's five named steps, not a free
                // number: the detector is tuned around them, and a
                // threshold it cannot tell from its neighbour would be a
                // control that lies. The label is the same word Power
                // Peek shows for the same value.
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val steps = SHAKE_STEPS
                        val levelRes = LocalSettingsRepository.current
                            .getShakeIntensityLevel(steps[shakeStep.toInt()])
                        Text(
                            text = stringResource(R.string.rules_shake_label) +
                                " — " + stringResource(levelRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Slider(
                            value = shakeStep,
                            onValueChange = { shakeStep = it },
                            valueRange = 0f..(steps.size - 1).toFloat(),
                            steps = steps.size - 2,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }

        // Charging triggers always play the battery bar, so a picker here would
        // be a control that changes nothing. The fixed id is what `performSave`
        // writes, which is also why the duration slider below stays: the bar
        // fills over that duration.
        if (!isChargingTrigger) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.rules_animation_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        animationOptions.forEach { anim ->
                            FilterChip(
                                selected = anim == selectedAnimation,
                                onClick = { animation = anim },
                                label = { Text(anim.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Not shown for a user's script: a script lasts exactly as long as
        // its own code, so a duration here would be a control that changes
        // nothing. Every feature card already drops its slider for the same
        // reason — the constructor was the last screen still showing one, and
        // `GlyphAnimationManager` never passes this value to a script anyway.
        if (!customAnimation) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.rules_duration_label) +
                            " — " +
                            durationSeconds.toInt().toString() +
                            stringResource(R.string.glyph_seconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Slider(
                        value = durationSeconds,
                        onValueChange = { durationSeconds = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
