package com.bleelblep.glyphsharge.ui.screens.animations

import androidx.compose.foundation.BorderStroke
// The List overload of `items`. Only the `items(count: Int)` member was in
// scope before, which is the one that cannot take a key.
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.bleelblep.glyphsharge.data.ScriptStoreRepository
import com.bleelblep.glyphsharge.data.StoreItem
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.NothingGreen
import com.bleelblep.glyphsharge.ui.theme.themeCardContainerColor
import com.bleelblep.glyphsharge.ui.theme.themePrimaryActionColor
import com.bleelblep.glyphsharge.ui.utils.HapticUtils

/**
 * The animation store: a catalogue of scripts other people wrote.
 *
 * A separate screen from the list, in the same Activity, for the same reason
 * the editor is one: it has its own back stack, and its downloads have no
 * business interleaving with file pickers.
 *
 * ### What an item this phone cannot run looks like
 *
 * **It stays in the list.** A Phone (1) would otherwise see a nearly empty
 * store, with no way to tell that apart from a store that is not working. What
 * changes is that the card dims and both action buttons are replaced by one
 * line of text — naming the models it *does* work on, so the entry says why it
 * is here rather than only that it cannot be had.
 *
 * The buttons are *replaced*, not disabled. A greyed-out button on a dimmed
 * card reads as an invitation, and the answer is always no.
 *
 * @param onBackClick leaves the store
 * @param onRefresh re-fetches the index
 * @param onInstall downloads, checks and saves an item — or, for an item in
 *   [outdated], downloads, checks and replaces the copy already on the phone
 * @param onTest downloads, checks and plays an item without saving it
 * @param isSupported whether the connected phone is one this item claims to work on
 * @param installed names of the animations the phone already has; those sort first
 * @param outdated ids of the installed items the catalogue has something newer
 *   for; those offer "Update" where the others say "Installed"
 */
@Composable
fun ScriptStoreScreen(
    items: List<StoreItem>,
    loadState: ScriptStoreRepository.LoadState,
    downloading: Set<String>,
    testing: String?,
    installed: Set<String>,
    outdated: Set<String>,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (StoreItem) -> Unit,
    onTest: (StoreItem) -> Unit,
    isSupported: (StoreItem) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    SettingsScaffold(
        title = stringResource(R.string.store_title),
        modifier = modifier,
        onBackClick = onBackClick,
        actions = {
            IconButton(
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    onRefresh()
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.store_refresh),
                )
            }
        },
    ) {
        when {
            (loadState is ScriptStoreRepository.LoadState.Loading) && (items.isEmpty()) -> {
                item { LoadingState() }
            }

            (loadState is ScriptStoreRepository.LoadState.Failed) && (items.isEmpty()) -> {
                item {
                    FailedState(
                        reason = loadState.reason,
                    ) {
                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                        onRefresh()
                    }
                }
            }

            items.isEmpty() -> {
                item { StoreEmptyState() }
            }

            else -> {
                // The offline note sits above the list rather than replacing
                // it: a cached catalogue is worth showing, and the only thing
                // missing is whatever was published since it was written.
                if (loadState is ScriptStoreRepository.LoadState.Stale) {
                    item { OfflineNote() }
                }

                // Keyed for the same reason as the animation list: a row holds
                // its own UI state, and an unkeyed LazyColumn preserves that by
                // index — so a catalogue that reorders or drops an entry moves
                // one row's state onto another.
                items(items, key = { it.id }) { item ->
                    StoreItemRow(
                        item = item,
                        supported = isSupported(item),
                        installed = item.matchKey in installed,
                        updateAvailable = item.id in outdated,
                        cardColor = cardColor,
                        accent = accent,
                        busy = item.id in downloading,
                        testing = testing == item.id,
                        anyTesting = testing != null,
                        onInstall = {
                            HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)
                            onInstall(item)
                        },
                    ) {
                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                        onTest(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreItemRow(
    item: StoreItem,
    supported: Boolean,
    installed: Boolean,

    /**
     * Whether the catalogue has published a newer version than the phone holds.
     *
     * Only ever true for an item that is also [installed] — the two come from
     * the same rule — but combined with it again below, so a caller passing one
     * without the other cannot produce a card offering to update something that
     * is not there.
     */
    updateAvailable: Boolean,
    cardColor: Color,
    accent: Color,
    busy: Boolean,
    testing: Boolean,

    /**
     * Whether a preview is running for *any* item, not necessarily this
     * one. The ViewModel runs one preview at a time, so while one is in
     * flight every other "Test" press would be refused — the button is
     * disabled here rather than left enabled but silent.
     */
    anyTesting: Boolean,
    onInstall: () -> Unit,
    onTest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (supported) cardColor else cardColor.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Author and licence on one line: a script carrying someone else's
            // name is the one a user should be able to trace before installing.
            val byline = buildList {
                if (item.author.isNotBlank()) {
                    add(stringResource(R.string.store_by_author, item.author))
                }
                if (item.license.isNotBlank()) {
                    add(stringResource(R.string.store_msg_license, item.license))
                }
            }
            if (byline.isNotEmpty()) {
                Text(
                    text = byline.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = "v${item.version}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (supported) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onTest, enabled = !busy && !anyTesting) {
                        Text(
                            stringResource(
                                if (testing) R.string.store_loading else R.string.store_action_test,
                            ),
                        )
                    }
                    when {
                        // The one case where "already on the phone" is not the
                        // end of it. Accent rather than green, because it is an
                        // invitation again — and it is the *same* button as the
                        // install: pressing it replaces the copy on the phone
                        // instead of adding a second one beside it.
                        installed && updateAvailable -> {
                            OutlinedButton(
                                onClick = onInstall,
                                enabled = !busy && !testing,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdateAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(
                                        if (busy) R.string.store_loading else R.string.store_action_update,
                                    ),
                                )
                            }
                        }

                        installed -> {
                            // Not a button any more: the animation is on the
                            // phone and it is the version the catalogue
                            // publishes, so there is nothing left to press.
                            // Green because it is the answer, not an invitation.
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    disabledContainerColor = NothingGreen.copy(alpha = 0.16f),
                                    disabledContentColor = NothingGreen,
                                ),
                                border = BorderStroke(1.dp, NothingGreen.copy(alpha = 0.55f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.store_action_installed))
                            }
                        }

                        else -> {
                            OutlinedButton(
                                onClick = onInstall,
                                enabled = !busy && !testing,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            ) {
                                Text(
                                    stringResource(
                                        if (busy) R.string.store_loading else R.string.store_action_install,
                                    ),
                                )
                            }
                        }
                    }
                }
            } else {
                // One line where both buttons were, naming the models it does
                // work on — which is what makes this entry worth leaving on
                // screen at all.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.store_unsupported_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.deviceNames.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.store_unsupported_devices,
                                item.deviceNames.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
        Text(
            text = stringResource(R.string.store_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedState(reason: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.store_msg_failed),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.store_retry))
        }
    }
}

@Composable
private fun OfflineNote() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.store_offline_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StoreEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Storefront,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = themePrimaryActionColor(),
        )
        Text(
            text = stringResource(R.string.store_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.store_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}