package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability

/**
 * The import-time ask, and the honest framing of what a service script is.
 *
 * Shared by the two places a `.servicelua` reaches the phone — the rule
 * constructor's "Import script…" and the store's install — because they are the
 * same decision wearing two entry points: a file declares what it reaches for,
 * and the user says yes or no once, before anything is written. A second copy of
 * this dialog would be a second place for the wording to drift, and the wording
 * is the only audit a user gets.
 *
 * The disclaimer is doing the real work: a user has no way to audit a script
 * they did not write, so what they *can* be told truthfully is the shape of
 * the cage — background-only, grant-limited, incapable of drawing, and
 * revocable in Settings. Each requested capability is listed with the same
 * strings the permissions screen uses, so "what I allowed there" and "what I
 * can withdraw here" read identically.
 */
@Composable
fun CapabilityPromptDialog(
    capabilities: Set<ScriptCapability>,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = stringResource(R.string.rules_import_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.rules_import_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                )
                capabilities.forEach { capability ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(capability.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(capability.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(stringResource(R.string.rules_import_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.rules_import_decline))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
