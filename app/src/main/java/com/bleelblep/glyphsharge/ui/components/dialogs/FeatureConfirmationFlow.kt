package com.bleelblep.glyphsharge.ui.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bleelblep.glyphsharge.ui.components.FeatureConfirmationButtons

/**
 * The confirmation-then-configure flow shared by all six features.
 *
 * Each feature used to hand-write the same ~85 lines: a confirmation dialog
 * with a "how it works" card, a gear button that swaps it for a configuration
 * dialog, and the wiring to close both. Only the strings and the body of the
 * configuration dialog differed.
 *
 * @param dismissible whether back press and an outside tap close the
 *   confirmation. Most features are deliberately modal; Screen Off is not.
 * @param settings the feature's configuration dialog. It receives the three
 *   callbacks it needs to close itself and report the outcome, so the feature
 *   does not have to think about this dialog's internal state.
 */
@Composable
fun FeatureConfirmationFlow(
    title: String,
    subtitle: String,
    howItWorksTitle: String,
    howItWorksDescription: String,
    testLabel: String,
    onTest: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    settings: @Composable (
        onConfirm: () -> Unit,
        onDisable: () -> Unit,
        onDismiss: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = false
) {
    var showSettings by remember { mutableStateOf(false) }

    if (!showSettings) {
        FeatureDialogScaffold(
            title = title,
            subtitle = subtitle,
            onDismissRequest = { if (dismissible) onDismiss() },
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            howItWorksTitle = howItWorksTitle,
            howItWorksDescription = howItWorksDescription,
            modifier = modifier,
            confirmButton = {
                FeatureConfirmationButtons(
                    primaryLabel = testLabel,
                    onPrimary = onTest,
                    onSettings = { showSettings = true },
                    onCancel = onDismiss
                )
            }
        )
    }

    if (showSettings) {
        settings(
            { showSettings = false; onEnable() },
            { showSettings = false; onDisable() },
            { showSettings = false }
        )
    }
}
