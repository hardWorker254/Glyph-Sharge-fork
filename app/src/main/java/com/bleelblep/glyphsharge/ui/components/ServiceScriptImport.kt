package com.bleelblep.glyphsharge.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import com.bleelblep.glyphsharge.glyph.script.ServiceScript
import kotlinx.coroutines.launch

/**
 * The import that stopped at the prompt: what the file asked for, waiting on
 * the user's yes or no. Nothing has been installed yet — a half-granted
 * script is not a state this app has.
 */
private data class PendingImport(
    val uri: Uri,
    val requested: Set<ScriptCapability>,
)

/**
 * The import half of the rule constructor: pick a file, scan it, ask, install.
 *
 * One implementation for the form's "Import script…" button and the list's
 * toolbar icon, because they were about to be two copies of the same
 * sequence — and the sequence is what the capability model rests on. The
 * pre-prompt read answers what the file asks for; the import then re-reads
 * and re-detects on its own bytes and intersects them with what was
 * accepted, so a file swapped under the prompt can only lose grants, never
 * gain them.
 *
 * The prompt renders here as well, as part of the sequence: a caller that
 * could omit it would install whatever was picked without ever asking.
 *
 * @param onReadScript reads the picked file for the pre-prompt detection
 * @param onImportScript installs the file, granting only what it still
 *   declares *and* the user accepted
 * @param onImported called with the freshly installed copy — the form uses it
 *   to select the script it just brought in
 * @return a launcher for the system file picker; wire it to a button
 */
@Composable
fun rememberServiceScriptImport(
    onReadScript: suspend (Uri) -> String,
    onImportScript: suspend (Uri, Set<ScriptCapability>, (ServiceScript) -> Unit) -> Unit,
    onImported: (ServiceScript) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { onReadScript(uri) }.getOrNull() ?: return@launch
            val requested = ScriptCapability.detect(text)
            if (requested.isEmpty()) {
                onImportScript(uri, emptySet(), onImported)
            } else {
                pendingImport = PendingImport(uri, requested)
            }
        }
    }

    pendingImport?.let { pending ->
        CapabilityPromptDialog(
            capabilities = pending.requested,
            onAccept = {
                pendingImport = null
                scope.launch {
                    onImportScript(pending.uri, pending.requested, onImported)
                }
            },
            onDecline = { pendingImport = null },
        )
    }

    return { importLauncher.launch(arrayOf("*/*")) }
}
