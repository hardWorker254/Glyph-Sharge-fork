package com.bleelblep.glyphsharge.ui.screens.animations
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
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.themeCardContainerColor
import com.bleelblep.glyphsharge.ui.theme.themePrimaryActionColor
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import androidx.compose.ui.platform.LocalHapticFeedback
import java.text.DateFormat
import java.util.Date

/**
 * The studio's front door: every animation the user has written, plus the two
 * ways of getting more of them — write one, or import one.
 *
 * A separate Activity from the settings flow: the studio has its own back
 * stack, its own file pickers, and a code editor that has no business sharing
 * a `NavHost` with the theme settings.
 *
 * Renaming happens in the editor's name field, not from here: a row is a
 * target, not a form, and a second editable surface next to it would make it
 * ambiguous which value is live.
 *
 * @param onBackClick leaves the studio
 * @param onOpen opens an animation in the editor
 * @param onCreate starts a new animation
 * @param onPickImportFile opens the system file picker to import a `.glyphlua`
 * @param onExportToDownloads writes straight into the public Downloads folder
 * @param onExportToFile writes to a location the user picks
 * @param onOpenStore opens the animation store
 */
@Composable
fun AnimationListScreen(
    animations: List<ScriptAnimation>,
    onBackClick: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onPickImportFile: () -> Unit,
    onExportToDownloads: (String) -> Unit,
    onExportToFile: (String) -> Unit,
    onOpenStore: () -> Unit,
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
        title = stringResource(R.string.studio_title),
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
            // With nothing saved yet the empty state already carries the one
            // button that matters, in the middle of the screen. A second way to
            // reach it up here would only compete with it.
            if (animations.isNotEmpty()) {
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
            // Always present, unlike New: the store is the way to get a script
            // without writing one, which is exactly what an empty studio needs.
            IconButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onOpenStore()
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = stringResource(R.string.store_open),
                )
            }
        },
    ) {
        if (animations.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.studio_empty_title),
                    body = stringResource(R.string.studio_empty_body),
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
        // "Duplicate" in that stale menu duplicated the wrong script.
        items(animations, key = { it.id }) { animation ->
            AnimationRow(
                animation = animation,
                cardColor = cardColor,
                onOpen = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onOpen(animation.id)
                },
                onDelete = { onDelete(animation.id) },
                onDuplicate = { onDuplicate(animation.id) },
                onExportToDownloads = { onExportToDownloads(animation.id) },
            ) {
                onExportToFile(animation.id)
            }
        }
    }
}

@Composable
private fun AnimationRow(
    animation: ScriptAnimation,
    cardColor: Color,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onExportToDownloads: () -> Unit,
    onExportToFile: () -> Unit,
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
                // The real length, not a clamped one.
                //
                // This was `coerceAtMost(9999)`, and the clamped value was fed
                // to `pluralStringResource` as the *quantity* — so a
                // 12 000-character script reported itself as 9 999, in the
                // plural form belonging to a number it did not have, on a row
                // whose only job is to say what is on the phone.
                //
                // The clamp bought one character of line width on a line that
                // already ellipsises. What it cost was a wrong number, which is
                // not a trade worth making.
                val charCount = animation.source.length
                Text(
                    text = animation.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.studio_row_meta,
                        charCount,
                        charCount,
                        formatDate(animation.updatedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.studio_row_menu),
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_duplicate)) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_delete)) },
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

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
