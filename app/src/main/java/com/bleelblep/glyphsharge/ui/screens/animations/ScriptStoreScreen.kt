package com.bleelblep.glyphsharge.ui.screens.animations

import androidx.compose.foundation.clickable
// The List overload of `items`. Only the `items(count: Int)` member was in
// scope before, which is the one that cannot take a key.
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.ScriptStoreRepository
import com.bleelblep.glyphsharge.data.StoreItem
import com.bleelblep.glyphsharge.data.StoreItemKind
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import com.bleelblep.glyphsharge.ui.components.CapabilityPromptDialog
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.theme.NothingGreen
import com.bleelblep.glyphsharge.ui.theme.themeCardContainerColor
import com.bleelblep.glyphsharge.ui.theme.themePrimaryActionColor
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
import com.bleelblep.glyphsharge.ui.viewmodel.StoreFilter
import com.bleelblep.glyphsharge.ui.viewmodel.StoreKind

/**
 * The animation store: a catalogue of scripts other people wrote.
 *
 * **Its own Activity now**, reached from Settings rather than from the studio's
 * toolbar, for two reasons the studio used to give it for free:
 *
 *  * it has its own back stack, and its downloads have no business interleaving
 *    with the studio's file pickers; and
 *  * "Test on glyph" needs a session. The studio opened one in `onStart` and
 *    handed it to the store, which is why a preview worked with the master
 *    switch off. [ScriptStoreActivity][com.bleelblep.glyphsharge.ScriptStoreActivity]
 *    does the same for itself — a nav route inside `MainActivity` would inherit
 *    `maybeRestoreSession`'s gate and report a preview that drew nothing.
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
 *   [outdated], downloads, checks and replaces the copy already on the phone.
 *   A service script stops at the capability prompt instead: see
 *   [pendingCapabilities]
 * @param onTest downloads, checks and plays an item without saving it
 * @param isSupported whether the connected phone is one this item claims to work on
 * @param installed names of the animations the phone already has; those sort first
 * @param installedServices names of the installed *service scripts*, folded the
 *   same way — a badge reads the half that matches the card's own kind
 * @param outdated ids of the installed items the catalogue has something newer
 *   for; those offer "Update" where the others say "Installed". Animations
 *   only: a service script has nothing an update could overwrite
 * @param filter which part of the catalogue is on screen; see [StoreFilter]
 * @param onFilterChange moves to another [StoreFilter]
 * @param pendingCapabilities what the service script under the prompt reaches
 *   for, or `null` when nothing is waiting on an answer
 * @param onAcceptInstall installs the script already downloaded, with
 *   everything it declared
 * @param onDeclineInstall installs nothing and closes the question
 */
@Composable
fun ScriptStoreScreen(
    items: List<StoreItem>,
    loadState: ScriptStoreRepository.LoadState,
    downloading: Set<String>,
    testing: String?,
    installed: Set<String>,
    installedServices: Set<String>,
    outdated: Set<String>,
    filter: StoreFilter,
    pendingCapabilities: Set<ScriptCapability>?,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (StoreItem) -> Unit,
    onTest: (StoreItem) -> Unit,
    isSupported: (StoreItem) -> Boolean,
    onFilterChange: (StoreFilter) -> Unit,
    onAcceptInstall: () -> Unit,
    onDeclineInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val vibrationIntensity = LocalVibrationIntensity.current
    val context = LocalContext.current
    val cardColor = themeCardContainerColor()
    val accent = themePrimaryActionColor()

    // Derived here rather than in the ViewModel so that `items` stays the whole
    // catalogue: changing a filter must not have to remember what it threw away.
    // The per-item answers (supported, installed, update-available) are the same
    // ones the rows render, so filtering here cannot disagree with what a row
    // shows about itself.
    val shown = items.filter { item ->
        val isService = item.kind == StoreItemKind.SERVICE
        val onPhone = if (isService) installedServices else installed
        filter.matches(
            item = item,
            supported = isSupported(item),
            installed = item.matchKey in onPhone,
            updateAvailable = item.id in outdated,
        )
    }

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
                item {
                    StoreEmptyState(accent = accent) {
                        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                        onRefresh()
                    }
                }
            }

            else -> {
                // Controls first: a filter belongs above the list it narrows,
                // and above the "nothing matches" answer too — otherwise a user
                // who narrows to nothing has no way back without leaving.
                item {
                    FilterControls(filter = filter, onFilterChange = onFilterChange)
                }

                // The offline note sits above the list rather than replacing
                // it: a cached catalogue is worth showing, and the only thing
                // missing is whatever was published since it was written.
                if (loadState is ScriptStoreRepository.LoadState.Stale) {
                    item { OfflineNote() }
                }

                if (shown.isEmpty()) {
                    item { FilterEmptyState() }
                } else {
                    // Keyed for the same reason as the animation list: a row
                    // holds its own UI state, and an unkeyed LazyColumn
                    // preserves that by index — so a catalogue that reorders or
                    // drops an entry moves one row's state onto another.
                    items(shown, key = { it.id }) { item ->
                        val isService = item.kind == StoreItemKind.SERVICE
                        // Which half of the phone the name has to match
                        // against. Two different things can carry one name, and
                        // a badge lit off the other half would hide the install
                        // button that would actually fix it.
                        val onPhone = if (isService) installedServices else installed
                        StoreItemRow(
                            item = item,
                            supported = isSupported(item),
                            installed = item.matchKey in onPhone,
                            updateAvailable = item.id in outdated,
                            cardColor = cardColor,
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

    pendingCapabilities?.let { capabilities ->
        CapabilityPromptDialog(
            capabilities = capabilities,
            onAccept = onAcceptInstall,
            onDecline = onDeclineInstall,
        )
    }
}

/**
 * One catalogue entry, shaped like a studio row: name, one grey meta line,
 * the description under it, and everything actionable behind a ⋮.
 *
 * The buttons are gone rather than moved under the text — a row of buttons
 * is what made this card look like a form. What the studio's rows have instead
 * is an overflow menu, and the store's actions fit it: Test, Install, Update.
 * The row itself is not clickable, because there is nothing to open: it is
 * information, and every action it offers is one press deeper.
 */
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
     * without the other cannot produce a menu offering to update something that
     * is not there.
     */
    updateAvailable: Boolean,
    cardColor: Color,
    busy: Boolean,
    testing: Boolean,

    /**
     * Whether a preview is running for *any* item, not necessarily this
     * one. The ViewModel runs one preview at a time, so while one is in
     * flight every other "Test" press would be refused — the menu item is
     * disabled here rather than left enabled but silent.
     */
    anyTesting: Boolean,
    onInstall: () -> Unit,
    onTest: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(value = false) }

    // The meta slot the studio gives "1234 chars · 12 March": the facts to
    // check *before* installing — who wrote it, under what licence, which
    // version the catalogue published. An item this phone cannot run says so
    // here instead and names the models it does work on, so the entry
    // explains itself rather than only admitting it cannot be had.
    val meta = if (supported) {
        listOfNotNull(
            item.author.takeIf { it.isNotBlank() },
            item.license.takeIf { it.isNotBlank() },
            "v${item.version}",
        ).joinToString(" · ")
    } else {
        listOfNotNull(
            stringResource(R.string.store_unsupported_label),
            item.deviceNames
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?.let { stringResource(R.string.store_unsupported_devices, it) },
        ).joinToString(" · ")
    }

    // What the menu would hold, decided before it is drawn: an item with
    // nothing to act on is not a menu but a dead end. Animations always offer
    // Test; services always offer Install, which is idempotent and heals the
    // case where the script is in the library but no rule was created for it.
    val hasActions = supported

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (supported) cardColor else cardColor.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (installed) {
                    // The row's only status, in the slot where the studio's
                    // row is empty. Green is the answer, not an invitation —
                    // the same green the "Installed" button used to be, now
                    // that there is no button to be.
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.store_action_installed),
                        tint = NothingGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Every action moved here when the card became a row; the
                // unsupported card has none, and a service already installed
                // with no update behind it has neither — its ✓ said it.
                if (hasActions) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.store_row_menu),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // Animations only, and the ViewModel refuses the
                            // kind as well: a service script draws nothing
                            // until a rule names an animation for it, so
                            // there is nothing here to run.
                            if (item.kind == StoreItemKind.ANIMATION) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (testing) R.string.store_loading else R.string.store_action_test,
                                            ),
                                        )
                                    },
                                    enabled = !busy && !anyTesting,
                                    onClick = {
                                        menuOpen = false
                                        onTest()
                                    },
                                )
                            }
                            when {
                                // The one case where "already on the phone" is
                                // not the end of it. Pressing it replaces the
                                // copy on the phone instead of adding a second
                                // one beside it.
                                installed && updateAvailable -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (busy) R.string.store_loading else R.string.store_action_update,
                                                ),
                                            )
                                        },
                                        enabled = !busy && !testing,
                                        onClick = {
                                            menuOpen = false
                                            onInstall()
                                        },
                                    )
                                }

                                // Installed and current. An animation already
                                // has Test above and nothing else to do; a service
                                // keeps Install so a library script with no rule
                                // on the home screen can be healed.
                                installed && item.kind == StoreItemKind.ANIMATION -> Unit

                                installed -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (busy) R.string.store_loading else R.string.store_action_install,
                                                ),
                                            )
                                        },
                                        enabled = !busy && !testing,
                                        onClick = {
                                            menuOpen = false
                                            onInstall()
                                        },
                                    )
                                }

                                else -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (busy) R.string.store_loading else R.string.store_action_install,
                                                ),
                                            )
                                        },
                                        enabled = !busy && !testing,
                                        onClick = {
                                            menuOpen = false
                                            onInstall()
                                        },
                                    )
                                }
                            }
                        }
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

/**
 * The studio's empty state, restyled onto the store.
 *
 * The storefront icon went with the buttons: what the studio shows when it has
 * nothing is a title, a body, and one accent-coloured row — and this screen is
 * meant to read as that screen's sibling. The row still does something: an
 * empty catalogue is most often a fetch that came back empty or half-empty, so
 * the action it offers is the refresh the top-right icon already performs.
 */
@Composable
private fun StoreEmptyState(
    accent: Color,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clickable(onClick = onRefresh)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.store_retry),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
            )
        }
    }
}

/**
 * Search, kind, and the toggles that narrow the catalogue further.
 *
 * The kind row stays mutually exclusive — "animations and services" is [StoreKind.ALL],
 * not two chips fighting — while the toggles below it are independent and ANDed,
 * because "installed, for this phone, with an update" is a real question a
 * single choice cannot ask. The search field is the open-ended version of the
 * same filter: it matches name, description and author, folded to compare, so
 * a catalogue that grows past a screenful stays navigable without a picker for
 * every field.
 *
 * Rendered as its own item above the list (and above the "nothing matches"
 * answer) so a user who narrows to nothing still has the controls that got
 * them there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterControls(
    filter: StoreFilter,
    onFilterChange: (StoreFilter) -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilterChange(filter.copy(query = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.store_filter_search)) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onFilterChange(filter.copy(query = "")) }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.store_filter_clear_search),
                        )
                    }
                }
            },
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoreKind.entries.forEach { option ->
                FilterChip(
                    selected = option == filter.kind,
                    onClick = { onFilterChange(filter.copy(kind = option)) },
                    label = {
                        Text(
                            text = stringResource(
                                when (option) {
                                    StoreKind.ALL -> R.string.store_filter_all
                                    StoreKind.ANIMATION -> R.string.store_filter_animations
                                    StoreKind.SERVICE -> R.string.store_filter_services
                                },
                            ),
                        )
                    },
                    colors = chipColors,
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter.onlyCompatible,
                onClick = { onFilterChange(filter.copy(onlyCompatible = !filter.onlyCompatible)) },
                label = { Text(stringResource(R.string.store_filter_compatible)) },
                colors = chipColors,
            )
            FilterChip(
                selected = filter.onlyUpdates,
                onClick = { onFilterChange(filter.copy(onlyUpdates = !filter.onlyUpdates)) },
                label = { Text(stringResource(R.string.store_filter_updates)) },
                colors = chipColors,
            )
            FilterChip(
                selected = filter.onlyInstalled,
                onClick = { onFilterChange(filter.copy(onlyInstalled = !filter.onlyInstalled)) },
                label = { Text(stringResource(R.string.store_filter_installed)) },
                colors = chipColors,
            )
        }
    }
}

/**
 * The catalogue has entries, but none that pass what the user asked for.
 *
 * Its own line rather than the store's empty state, which says nothing has been
 * published at all — a statement this screen knows to be false, because the
 * list it just narrowed was full a moment ago. The controls stay on screen
 * above it, so getting back is a tap rather than a guess.
 */
@Composable
private fun FilterEmptyState() {
    Text(
        text = stringResource(R.string.store_filter_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    )
}