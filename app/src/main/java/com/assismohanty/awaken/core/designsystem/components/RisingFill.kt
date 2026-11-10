package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/**
 * A flat colour that covers the screen from the bottom up as [progress] goes from 0 to 1: the whole timer of the pause
 * screen, with no number. Draw it first in a full-size box and put the content on top. It has no wave and no motion of
 * its own; the caller moves [progress] (smoothly, or in whole steps under reduce-motion). One TalkBack node.
 */
@Composable
fun RisingFill(progress: Float, description: String, modifier: Modifier = Modifier) {
    val color = AwakenTheme.colors.accent.copy(alpha = FILL_ALPHA)
    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics { contentDescription = description }) {
        val covered = size.height * progress.coerceIn(0f, 1f)
        drawRect(color, topLeft = Offset(0f, size.height - covered), size = Size(size.width, covered))
    }
}

private const val FILL_ALPHA = 0.28f

@ComponentPreview
@Composable
private fun RisingFillPreview() {
    AwakenTheme(reduceMotion = true) {
        Box(modifier = Modifier.fillMaxSize()) { RisingFill(progress = 0.55f, description = "Pause") }
    }
}
