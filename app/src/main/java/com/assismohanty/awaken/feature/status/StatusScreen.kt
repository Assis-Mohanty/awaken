package com.assismohanty.awaken.feature.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.PlaceholderPane
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    PlaceholderPane(
        title = stringResource(R.string.nav_status),
        body = stringResource(R.string.placeholder_status),
        modifier = modifier,
    )
}

@PhonePreviews
@Composable
private fun StatusScreenPreview() {
    AwakenTheme { StatusScreen() }
}
