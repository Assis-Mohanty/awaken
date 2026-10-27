package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** Temporary body for screens not yet built. Replaced by real System windows in later phases. */
@Composable
fun PlaceholderPane(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = AwakenTheme.type.metricL,
            color = AwakenTheme.palette.system,
        )
        Text(
            text = body,
            style = AwakenTheme.type.body,
            color = AwakenTheme.palette.mist,
            textAlign = TextAlign.Center,
        )
    }
}
