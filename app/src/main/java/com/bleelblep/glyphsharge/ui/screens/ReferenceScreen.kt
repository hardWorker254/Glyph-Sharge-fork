package com.bleelblep.glyphsharge.ui.screens

import android.util.Log
import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.triggers.TriggerType
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.themeCardContainerColor

/**
 * Settings → «Reference»: how to write the two scripts the app runs.
 *
 * One scrollable page with two cards, because the two documents answer the
 * same question in the same session — "what do I actually type?" — and a tab
 * bar would only hide the one the reader is not currently on. The service
 * card comes first: a rule needs its *when* before its *what*, and a reader
 * who arrived from the constructor's Advanced mode came looking for exactly
 * that order.
 *
 * The animation card carries the `glyph` cheat sheet that used to live at the
 * bottom of the studio editor. It moved here rather than being duplicated:
 * the editor is where a script is written, and a reference folded under the
 * code it documents is a reference a first-time author never opens — while
 * this screen is the place someone goes *looking* for documentation.
 */
@Composable
fun ReferenceScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_card_reference),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        item { ServiceReferenceCard() }
        item { AnimationReferenceCard() }
    }
}

/**
 * How to create a `.servicelua`: the script that says *when* a rule fires.
 *
 * The starter is the same string the constructor's Advanced mode seeds the
 * buffer with, so the example here and the example in the form cannot drift
 * apart — a reader who copies this into the constructor gets what they read.
 */
@Composable
private fun ServiceReferenceCard(modifier: Modifier = Modifier) {
    ReferenceCard(modifier = modifier) {
        ReferenceSectionTitle(stringResource(R.string.reference_service_title))

        ReferenceParagraph(stringResource(R.string.reference_service_intro))
        ReferenceParagraph(stringResource(R.string.reference_service_where))
        ReferenceParagraph(stringResource(R.string.reference_service_api))

        ReferenceSubtitle(stringResource(R.string.reference_service_triggers))
        // Read from the enum rather than written out: a trigger added here is
        // a row here, and a row here that the engine does not know would be
        // the one thing on this screen a copy-paste cannot fix.
        TriggerType.entries.forEach { trigger ->
            ReferenceRow(trigger.id to stringResource(trigger.labelRes))
        }

        ReferenceSubtitle(stringResource(R.string.reference_starter_title))
        CodeBlock(stringResource(R.string.rules_starter_script))

        ReferenceParagraph(stringResource(R.string.reference_service_sandbox))
    }
}

/**
 * How to create a `.glyphlua`: the script that says *what* lights up.
 *
 * The cheat sheet's rows are the same `<string-array>` resources the editor
 * used, so the reference and any future in-editor help read from one list.
 */
@Composable
private fun AnimationReferenceCard(modifier: Modifier = Modifier) {
    ReferenceCard(modifier = modifier) {
        ReferenceSectionTitle(stringResource(R.string.reference_animation_title))

        ReferenceParagraph(stringResource(R.string.reference_animation_intro))
        ReferenceParagraph(stringResource(R.string.reference_animation_where))

        ReferenceSubtitle(stringResource(R.string.reference_starter_title))
        CodeBlock(stringResource(R.string.studio_starter_script))

        ReferenceSubtitle(stringResource(R.string.studio_reference_title))
        ReferenceRows(
            R.array.studio_api_calls,
            R.array.studio_api_descriptions,
        )

        ReferenceSubtitle(stringResource(R.string.studio_reference_modules_title))
        ReferenceRows(
            R.array.studio_module_calls,
            R.array.studio_module_descriptions,
        )
        ReferenceParagraph(stringResource(R.string.studio_reference_modules_note))

        ReferenceSubtitle(stringResource(R.string.studio_reference_example_title))
        CodeBlock(MODULE_REFERENCE_EXAMPLE)
    }
}

/** The card shell both sections share: one rounded container, 16.dp inside. */
@Composable
private fun ReferenceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = themeCardContainerColor()),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** A section heading: the two cards' titles. */
@Composable
private fun ReferenceSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A heading *inside* a card: the starter script, the cheat sheet, and so on. */
@Composable
private fun ReferenceSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** One paragraph of prose. */
@Composable
private fun ReferenceParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A block of Lua, monospaced and left alone to wrap: it is meant to be read literally. */
@Composable
private fun CodeBlock(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * One line of the reference: the call on the left, what it does on the right.
 *
 * Extracted because the cheat sheet has two sections and a reader must not be
 * able to tell them apart by accident.
 */
@Composable
private fun ReferenceRows(
    @ArrayRes callsId: Int,
    @ArrayRes descriptionsId: Int,
) {
    val calls = stringArrayResource(callsId)
    val descriptions = stringArrayResource(descriptionsId)

    // The two arrays are index-aligned, which means a row added to one and not
    // the other would silently pair a call with the wrong description — and a
    // *missing* translation row would shift every description below it by one.
    // Truncating to the shorter pair fails as one absent row rather than as a
    // screen where every explanation belongs to the call above it.
    val rows = minOf(calls.size, descriptions.size)
    if ((rows != calls.size) || (rows != descriptions.size)) {
        Log.w(
            "ReferenceScreen",
            "Reference arrays disagree: $rows of ${calls.size}/${descriptions.size}",
        )
    }

    for (index in 0 until rows) {
        ReferenceRow(calls[index] to descriptions[index])
    }
}

@Composable
private fun ReferenceRow(entry: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.first,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = entry.second,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The worked example, because six module names and a warning do not tell a
 * reader what `require` is *for*.
 *
 * The three-step day/night dimming is the shape most people reach a script
 * for: the feature woke it, and the script decides how loudly to answer. It
 * needs no new service and no permission — the same source runs unchanged on
 * every feature that can host a script, which is the point a list of module
 * names cannot make on its own.
 *
 * **Not a resource, on purpose.** This is code, not prose: it is meant to be
 * copied out of the app and pasted into the editor, and a translation inside it
 * would travel with that paste. An example whose comments changed language
 * halfway through would also be a second thing to keep in step — so it stays in
 * one place, in one language, byte-for-byte the same on every device. The
 * prose around it is translated; the code is not.
 *
 * Kept out of the module rows deliberately: those are one line per module in a
 * two-column layout, and this needs the lines it actually takes.
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
