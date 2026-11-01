package com.bleelblep.glyphsharge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A plain monospace text field.
 *
 * `BasicTextField` rather than `OutlinedTextField` because the decoration of a
 * form control is noise on a code surface, and because the field has to scroll
 * in both axes without a label floating over the first line.
 *
 * Shared by the studio's editor and the constructor's advanced mode: the app's
 * two code surfaces should not read as two different kinds of thing — a
 * script is a script wherever it is written.
 */
@Composable
fun CodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        BasicTextField(
            value = source,
            onValueChange = onSourceChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vertical)
                .horizontalScroll(horizontal),
        )
    }
}
