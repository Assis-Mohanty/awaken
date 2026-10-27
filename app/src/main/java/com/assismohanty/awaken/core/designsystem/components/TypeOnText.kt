package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Event titles (DESIGN_SYSTEM §2.5). v2 retired the type-on effect: the text appears whole and [onFinished] fires once
 * it is shown. Kept as a component so callers that sequence on [onFinished] keep working.
 */
@Composable
fun TypeOnText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") animate: Boolean = true,
    onFinished: () -> Unit = {},
) {
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(text) { finished() }
    Text(text = text, style = style, color = color, modifier = modifier)
}
