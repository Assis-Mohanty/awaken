package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** A short System line plus one action (DESIGN_SYSTEM §3). */
@Composable
fun EmptyState(
    line: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(line, style = AwakenTheme.type.body, color = AwakenTheme.colors.mist, textAlign = TextAlign.Center)
        if (actionLabel != null) SecondaryButton(text = actionLabel, onClick = onAction)
    }
}

@ComponentPreview
@Composable
private fun EmptyStatePreview() {
    AwakenTheme {
        EmptyState(line = "No habits yet. Add one the System should track.", actionLabel = "Add habit", onAction = {})
    }
}
