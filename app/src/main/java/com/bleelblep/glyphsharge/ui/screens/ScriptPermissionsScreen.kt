package com.bleelblep.glyphsharge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.viewmodel.HomeViewModel

/**
 * Settings → «Script permissions»: the withdraw half of the install-time ask.
 *
 * One card per installed script, and under it every [ScriptCapability] that
 * script's *source* reaches for — detected the same way the import prompt
 * detects, so a permission listed here is exactly a permission that was (or
 * should have been) asked about. A script that declares nothing gets the one
 * honest caption instead of an empty card.
 *
 * The screen is deliberately not a list of decisions but a list of *scripts*:
 * the user's mental model after granting is "that battery script", not "the
 * device_state capability", and a screen that mirrors the prompt's framing is
 * the one that can be trusted to withdraw from it.
 *
 * Like every screen that drives the shared home state, [viewModel] is a
 * parameter, not a `hiltViewModel()` — the same instance `MainActivity`
 * drives, so a toggle here reaches the same host the home screen re-syncs.
 */
@Composable
fun ScriptPermissionsScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val scripts by viewModel.serviceScripts.collectAsStateWithLifecycle()
    // Re-read after each toggle rather than a flow: the store is small,
    // synchronous and already the single source of truth behind it.
    var grants by remember { mutableStateOf(viewModel.scriptGrants()) }

    SettingsScaffold(
        title = stringResource(R.string.settings_card_script_permissions),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        item {
            Text(
                text = stringResource(R.string.script_permissions_applied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (scripts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.script_permissions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        scripts.forEach { script ->
            item {
                ScriptPermissionCard(
                    script = script,
                    granted = grants[script.id].orEmpty(),
                    onToggle = { capability, now ->
                        viewModel.setScriptCapability(script.id, capability, now)
                        grants = viewModel.scriptGrants()
                    },
                )
            }
        }
    }
}

@Composable
private fun ScriptPermissionCard(
    script: ServiceScript,
    granted: Set<ScriptCapability>,
    onToggle: (ScriptCapability, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requested = remember(script.id, script.source) { ScriptCapability.detect(script.source) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = script.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (requested.isEmpty()) {
                Text(
                    text = stringResource(R.string.script_permissions_no_requests),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            requested.forEach { capability ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(capability.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(capability.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = capability in granted,
                        onCheckedChange = { onToggle(capability, it) },
                        colors = SwitchDefaults.colors(),
                    )
                }
            }
        }
    }
}
