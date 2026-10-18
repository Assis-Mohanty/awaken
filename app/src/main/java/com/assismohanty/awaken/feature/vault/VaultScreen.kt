package com.assismohanty.awaken.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.PlaceholderPane
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

@Composable
fun VaultScreen(modifier: Modifier = Modifier) {
    PlaceholderPane(
        title = stringResource(R.string.nav_vault),
        body = stringResource(R.string.placeholder_vault),
        modifier = modifier,
    )
}

@PhonePreviews
@Composable
private fun VaultScreenPreview() {
    AwakenTheme { VaultScreen() }
}
