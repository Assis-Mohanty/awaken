package com.assismohanty.awaken.feature.gate

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.PlaceholderPane
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

@Composable
fun GatesScreen(modifier: Modifier = Modifier) {
    PlaceholderPane(
        title = stringResource(R.string.nav_gates),
        body = stringResource(R.string.placeholder_gates),
        modifier = modifier,
    )
}

@PhonePreviews
@Composable
private fun GatesScreenPreview() {
    AwakenTheme { GatesScreen() }
}
