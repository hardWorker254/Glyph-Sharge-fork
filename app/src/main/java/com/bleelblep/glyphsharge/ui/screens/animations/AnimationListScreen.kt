package com.bleelblep.glyphsharge.ui.screens.animations
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
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
 * Deliberately a separate Activity from the main settings flow: the studio has
 * its own back stack, its own file pickers, and a code editor that has no
 * business sharing a `NavHost` with the theme settings.
 *
 * Renaming happens in the editor's name field, not from here: a row is a target,
 * not a form, and a second editable surface next to it would make it ambiguous
 * which value is live.
 *
 * @param onBackClick leaves the studio
 * @param onOpen opens an animation in the editor
 * @param onCreate starts a new animation
 * @param onPickImportFile opens the system file picker to import a `.glyphlua`
 * @param onExportToDownloads writes straight into the public Downloads folder
 * @param onExportToFile writes to a location the user picks
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
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    SettingsScaffold(
        title = stringResource(R.string.studio_title),
        modifier = modifier,
        onBackClick = onBackClick,
        actions = {
            IconButton(onClick = {
                HapticUtils.triggerLightFeedback(haptic, context)
                onPickImportFile()
            }) {
                Icon(
                    imageVector = Icons.Filled.FileOpen,
                    contentDescription = stringResource(R.string.studio_import)
                )
            }
            // With nothing saved yet the empty state already carries the one
            // button that matters, in the middle of the screen. A second way to
            // reach it up here would only compete with it.
            if (animations.isNotEmpty()) {
                IconButton(onClick = {
                    HapticUtils.triggerMediumFeedback(haptic, context)
                    onCreate()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.studio_new)
                    )
                }
            }
        }
    ) {
        if (animations.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.studio_empty_title),
                    body = stringResource(R.string.studio_empty_body),
                    accent = accent,
                    onCreate = {
                        HapticUtils.triggerMediumFeedback(haptic, context)
                        onCreate()
                    }
                )
            }
        }

        items(animations.size) { index ->
            val animation = animations[index]
            AnimationRow(
                animation = animation,
                cardColor = cardColor,
                onOpen = {
                    HapticUtils.triggerLightFeedback(haptic, context)
                    onOpen(animation.id)
                },
                onDelete = { onDelete(animation.id) },
                onDuplicate = { onDuplicate(animation.id) },
                onExportToDownloads = { onExportToDownloads(animation.id) },
                onExportToFile = { onExportToFile(animation.id) }
            )
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
    onExportToFile: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = animation.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.studio_row_meta,
                        animation.source.length.coerceAtMost(9999),
                        formatDate(animation.updatedAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.studio_row_menu)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_open)) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_duplicate)) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_export_downloads)) },
                        onClick = {
                            menuOpen = false
                            onExportToDownloads()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_export_file)) },
                        onClick = {
                            menuOpen = false
                            onExportToFile()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.studio_action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
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
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clickable(onClick = onCreate)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.studio_new),
                style = MaterialTheme.typography.titleSmall,
                color = accent
            )
        }
    }
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
