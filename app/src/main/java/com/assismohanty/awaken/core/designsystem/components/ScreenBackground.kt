package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/** The page (DESIGN_SYSTEM §2.1): a flat fill in the page colour. No gradients, no glow, nothing rank-dependent. */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier = modifier.fillMaxSize().background(AwakenTheme.colors.surface), content = content)
}

@PhonePreviews
@Composable
private fun ScreenBackgroundPreview() {
    AwakenTheme { ScreenBackground() }
}
