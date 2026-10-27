package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.model.Rank

/**
 * The page (DESIGN_SYSTEM §2.1): a flat fill in the page colour. No gradients, no glow; [tint] is kept for callers
 * written before v2 and ignored.
 */
@Composable
fun GateBackground(
    @Suppress("UNUSED_PARAMETER") tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(AwakenTheme.colors.surface), content = content)
}

@Composable
fun GateBackground(@Suppress("UNUSED_PARAMETER") rank: Rank, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) =
    GateBackground(tint = Color.Unspecified, modifier = modifier, content = content)

@PhonePreviews
@Composable
private fun GateBackgroundPreview() {
    AwakenTheme { GateBackground(rank = Rank.C) }
}
