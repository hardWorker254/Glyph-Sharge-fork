package com.bleelblep.glyphsharge.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.cards.ContentCard
import com.bleelblep.glyphsharge.ui.components.controls.MorphingToggleButton
import com.bleelblep.glyphsharge.ui.state.HomeUiState
import com.bleelblep.glyphsharge.ui.state.RuleUiState
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * The constructor's output: every rule the app has — the ones the user built
 * and the ones a feature projects — plus the "＋" that builds another.
 *
 * Shown above the feature cards because a rule is what the app is moving toward,
 * and because the features whose cards are gone are switched from here: their
 * rules *are* their rows, titled with the feature's own name, so nothing is
 * listed twice and nothing is left unreachable. What is still below is the one
 * feature that could not move — the visualiser, whose `MediaProjection` token
 * only an Activity can obtain, so its switch has to be driven from the screen
 * that asks for consent.
 *
 * [onToggleRule] is passed in rather than reached for, so the permission a
 * feature's service needs is asked by the screen that owns the window; see
 * [com.bleelblep.glyphsharge.ui.components.rememberPermissionedRuleToggle].
 *
 * Everything else here is pure wiring off the ViewModel — the list itself is
 * state already resolved on [HomeUiState].
 */
internal fun LazyListScope.homeRuleCards(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
) {
    if (uiState.rules.isEmpty()) {
        item {
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    uiState.rules.forEach { rule ->
        item(key = rule.id) {
            RuleCard(
                rule = rule,
                onToggle = { viewModel.setRuleEnabled(rule.id, it) },
                onTest = { viewModel.testRule(rule.id) },
                onDelete = { viewModel.deleteRule(rule.id) },
                onEdit = { onEditRule(rule.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    item {
        AddRuleCard(onClick = onAddRule, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * One rule: name, "trigger → animation", a switch, a test and a delete.
 *
 * Only the name/description area opens the editor — a card-wide click would
 * fire alongside the switch and the delete that sit inside it. The switch
 * matches the feature cards, because a rule is turned on and off the same way;
 * the delete is a separate target so it cannot be hit while reaching for it,
 * and a feature's row has none — see [homeRuleCards].
 *
 * The test button is what the retired feature cards' "Test" became: it plays
 * the rule through the same call the host makes when the trigger fires, so a
 * test shows what the fire will show.
 */
@Composable
private fun RuleCard(
    rule: RuleUiState,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = rememberVectorPainter(image = Icons.Default.AutoAwesome)

    ContentCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit),
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // A switch, matching the feature cards, because a rule is turned on
            // and off the same way a feature is.
            MorphingToggleButton(
                checked = rule.isEnabled,
                onCheckedChange = onToggle,
            )

            IconButton(onClick = onTest) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.rules_test),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            onDelete?.let { delete ->
                IconButton(onClick = delete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.rules_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** The "＋" that opens the constructor. */
@Composable
private fun AddRuleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentCard(modifier = modifier, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(R.string.rules_add),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
