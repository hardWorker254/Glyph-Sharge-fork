package com.bleelblep.glyphsharge.ui.screens

import androidx.compose.foundation.clickable
// The List overload of `items`. Only the `items(count: Int)` member was in
// scope before, which is the one that cannot take a key.
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.controls.MorphingToggleButton
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.state.RuleUiState
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.themeCardContainerColor
import com.bleelblep.glyphsharge.ui.theme.themePrimaryActionColor
import com.bleelblep.glyphsharge.ui.utils.HapticUtils

/**
 * The constructor's front door: every rule the user has built, plus the two
 * ways of getting more of them — build one, or import a script someone wrote.
 *
 * **The studio's list, deliberately.** The studio is the screen this app's
 * users have already learned: an import icon in the corner, a ＋ beside it
 * once there is something to add beside, an empty state that carries the one
 * button that matters, and an overflow menu per row instead of a row of
 * buttons. The constructor is now the second place with the same shape, so
 * the arrangement does not have to be relearned — what differs is only what
 * the rows are made of: a rule carries a description and a switch, because a
 * rule is read at a glance and turned off without leaving the list.
 *
 * Export writes the rule's `.servicelua` file — the same bytes the import
 * read, so a script can make the trip to another phone and back. A rule
 * built from chips has no file behind it and shows no export at all rather
 * than an item that would fail at the press.
 *
 * **A migrated feature's row looks like the others and is not one.** It is the
 * feature's own rule, so its switch flips the feature, its form edits the
 * feature's settings, and it offers no Delete — the row is re-derived from those
 * settings on every sync, so a deletion would undo itself. What makes it a row
 * at all is the point of the transition: the constructor is where rules are read
 * and edited, and a rule it refused to show was a rule nobody could edit there.
 *
 * @param onBackClick leaves the constructor
 * @param onOpen opens a rule in the form
 * @param onCreate opens the form for a new rule
 * @param onDelete removes a rule
 * @param onToggle enables or disables one rule
 * @param onPickImportFile opens the system file picker to import a `.servicelua`
 * @param onExportToDownloads writes the rule's script into public Downloads
 * @param onExportToFile writes it to a location the user picks
 * @param onTest plays the rule's animation once, without waiting for its trigger
 */
@Composable
fun RuleListScreen(
    rules: List<RuleUiState>,
    attachFailures: Map<String, String?> = emptyMap(),
    onBackClick: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onPickImportFile: () -> Unit,
    onExportToDownloads: (String) -> Unit,
    onExportToFile: (String) -> Unit,
    onTest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // The user's haptic strength is a setting, so it is read once per
    // composition here and handed to HapticUtils, which cannot fetch it itself.
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    SettingsScaffold(
        title = stringResource(R.string.settings_card_rule_constructor),
        modifier = modifier,
        onBackClick = onBackClick,
        actions = {
            IconButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onPickImportFile()
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.FileOpen,
                    contentDescription = stringResource(R.string.studio_import),
                )
            }
            // With nothing built yet the empty state already carries the one
            // button that matters, in the middle of the screen. A second way
            // to reach it up here would only compete with it.
            if (rules.isNotEmpty()) {
                IconButton(
                    onClick = {
                        HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                        onCreate()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.studio_new),
                    )
                }
            }
        },
    ) {
        if (rules.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.rules_list_empty_title),
                    body = stringResource(R.string.rules_list_empty_body),
                    accent = accent,
                ) {
                    HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                    onCreate()
                }
            }
        }

        // Keyed, because the row keeps `menuOpen` in a `remember` and a
        // LazyColumn preserves slot state *by index*. Unkeyed, deleting row 3
        // left its open overflow menu attached to what used to be row 4 — and
        // "Delete" in that stale menu deleted the wrong rule. The same defect
        // is why the studio keys its rows.
        items(rules, key = { it.id }) { rule ->
            RuleRow(
                rule = rule,
                attachFailure = attachFailures[rule.id],
                cardColor = cardColor,
                onOpen = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onOpen(rule.id)
                },
                onToggle = { enabled -> onToggle(rule.id, enabled) },
                onDelete = { onDelete(rule.id) },
                onExportToDownloads = { onExportToDownloads(rule.id) },
                onExportToFile = {
                    onExportToFile(rule.id)
                },
                onTest = { onTest(rule.id) },
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: RuleUiState,
    attachFailure: String?,
    cardColor: Color,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onExportToDownloads: () -> Unit,
    onExportToFile: () -> Unit,
    onTest: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(value = false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                // The same two facts the home card shows — "Screen off →
                // Spiral" — in the slot the studio gives its meta line, so
                // the row reads without opening it.
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Why the rule is dark — the host's verdict from the last
                // reload. A rule with a reason is a rule the user built and
                // can actually act on, instead of a silent maybe-not-working.
                if (attachFailure != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = attachFailure,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // The same switch the home list flips: a rule is turned on and
            // off the same way from both screens, and it has to be reachable
            // here so the list is not read-only.
            MorphingToggleButton(
                checked = rule.isEnabled,
                onCheckedChange = onToggle,
            )

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.rules_row_menu),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_open)) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    // First, because it is the only action that needs no
                    // explanation: play the rule once, now. The retired feature
                    // cards' "Test" became this, and it runs the very call the
                    // host makes when the trigger fires.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rules_test)) },
                        onClick = {
                            menuOpen = false
                            onTest()
                        },
                    )
                    // Only for a rule whose script is still on the phone: the
                    // export writes that file, and a rule built from chips
                    // never had one. Offering the item anyway would be a menu
                    // entry whose answer is always an error.
                    if (rule.canExportScript) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.studio_action_export_downloads)) },
                            onClick = {
                                menuOpen = false
                                onExportToDownloads()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.studio_action_export_file)) },
                            onClick = {
                                menuOpen = false
                                onExportToFile()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rules_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The studio's empty state, arriving here with the rest of the arrangement.
 *
 * Title, body, and the one button that starts something — the shape a user
 * has already seen in the studio, down to the accent-coloured action row.
 */
@Composable
private fun EmptyState(
    title: String,
    body: String,
    accent: Color,
    onCreate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clickable(onClick = onCreate)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.studio_new),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
            )
        }
    }
}
