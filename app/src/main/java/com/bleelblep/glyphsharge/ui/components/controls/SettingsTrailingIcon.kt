package com.bleelblep.glyphsharge.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bleelblep.glyphsharge.ui.theme.AppThemeStyle
import com.bleelblep.glyphsharge.ui.theme.LocalThemeState
import com.bleelblep.glyphsharge.ui.theme.NothingGray

/**
 * The plate a settings row's trailing icon sits in.
 *
 * A row that navigates somewhere and has no toggle of its own still wants a
 * mark on the right, so the list reads as one column of controls rather than
 * three rows with chips and two without. This is that plate: the same rounded
 * chip the toggle buttons use, with no behaviour of its own.
 *
 * It exists because the alternative is a bare `Icon` against the card, which is
 * what the language row used to be — and a row whose trailing control is the
 * only one without the frame does not read as deliberate. It reads as a
 * rendering fault, which is exactly what it was.
 *
 * Not a button: the card itself is the tap target, and a second hit target on
 * the same gesture is a bug waiting for a fat thumb.
 *
 * ### Matching the row above
 *
 * The plate is [MorphingToggleButton] at rest — 88 × 40 dp, not a square — and
 * the default [content] is the same `titleLarge` emoji every other row uses. A
 * square plate beside three wide ones is not a subtle difference; it is the
 * one row the eye lands on.
 *
 * @param content the mark; `null` for the default emoji slot
 * @param width plate width, 88 dp to match [MorphingToggleButton] at rest
 * @param height plate height, 40 dp for the same reason
 * @param tint icon colour, or `null` to leave it to [content]
 */
@Composable
fun SettingsTrailingIcon(
    modifier: Modifier = Modifier,
    width: Dp = 88.dp,
    height: Dp = 40.dp,
    tint: Color? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val themeState = LocalThemeState.current

    // The same rule [MorphingToggleButton] uses for its off state: EXPRESSIVE
    // keeps the plate in the theme's own surface, every other style gets the
    // Nothing grey. A plate that picked its own colour would be the one chip on
    // the screen that did not match the button above it.
    val plate = when (themeState.themeStyle) {
        AppThemeStyle.EXPRESSIVE -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> NothingGray
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(color = plate, shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            if (tint != null) {
                // Wrapping rather than asking each caller to remember: two rows
                // using this chip should not be able to drift apart.
                CompositionLocalProvider(LocalContentColor provides tint) {
                    content()
                }
            } else {
                content()
            }
        } else {
            // `titleLarge` is what every other row's emoji is set in, so the
            // mark lands at the same optical size rather than merely the same
            // dp.
            Text(text = DEFAULT_MARK, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** The mark a row gets when it names none: a sparkle, matching the studio. */
private const val DEFAULT_MARK = "✨"
