package com.bleelblep.glyphsharge.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.ui.components.cards.WideFeatureCardWithToggle

/**
 * The three feature cards that are left on the home screen.
 *
 * Three, because a card is for a feature the rule list cannot reach. Every
 * other feature has become a rule: its switch, its trigger and its animation are
 * edited there, and a card beside it would be one preference with two switches
 * and one knob in two places. Charging and Power Peek stayed because of one
 * thing the rule form does not carry — the arcs they draw around the battery —
 * and the visualiser because its `MediaProjection` token can only be obtained
 * by the screen that asks for it. `HomeRuleVisibilityTest` fails if this
 * paragraph and the card block ever disagree.
 *
 * State arrives as `isEnabled` and leaves through [onEnabledChange], which both
 * persists the preference and starts or stops the backing service, so the
 * store is touched only by whoever owns the state.
 *
 * The store itself is read from [LocalSettingsRepository] by the configuration
 * dialog, which makes a card callable on its own.
 */

/**
 * The music visualiser card.
 *
 * Shaped like every other card, with one difference that is not cosmetic: the
 * permission is asked for by `HomeFeatures` before [onEnabledChange] is ever
 * called, because a visualiser that cannot read the audio is a card the user
 * switched on and nothing else.
 */
@Composable
fun MusicVisualizerCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestAnimation: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.music_viz_title),
    description: String = stringResource(id = R.string.music_viz_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    // Saveable: whether this card's dialog is open has to survive a
    // configuration change, and it did not. Rotating with a confirmation
    // dialog open dismissed it and left the card looking untouched, which for
    // a dialog whose second step is a settings form meant the form went with
    // it. `remember` is right for state that *should* reset — scroll position,
    // a search field the user has left — and wrong for "is this open".
    var showDialog by rememberSaveable { mutableStateOf(value = false) }
    val toastText = stringResource(id = R.string.music_viz_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = onEnabledChange,
        onCardClick = {
            if (isServiceActive) showDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize,
    )

    if (showDialog && isServiceActive) {
        MusicVisualizerConfirmationDialog(
            onTestAnimation = { onTestAnimation(); showDialog = false },
            onEnableAnimation = { onEnabledChange(true); showDialog = false },
            onDisableAnimation = { onEnabledChange(false); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}
