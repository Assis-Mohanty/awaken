package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * Debug builds only (through `DebugEntry.OverrideBanner`): a warning-coloured strip saying which data is faked, with a
 * reset. Never shown in release.
 */
@Composable
fun DebugBanner(text: String, action: String, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    // Fixed dark ink: the warning fill is a light yellow in both themes.
    val ink = AwakenTheme.palette.void
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.warning)
            .padding(start = Spacing.md, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text, style = AwakenTheme.type.label, color = ink, modifier = Modifier.weight(1f))
        TextButton(
            onClick = onReset,
            colors = ButtonDefaults.textButtonColors(contentColor = ink),
            modifier = Modifier.defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
        ) {
            Text(action, style = AwakenTheme.type.label)
        }
    }
}

@ComponentPreview
@Composable
private fun DebugBannerPreview() {
    AwakenTheme(reduceMotion = true) {
        DebugBanner(text = "Debug: fake steps · clock +3 days", action = "Use real data", onReset = {})
    }
}
