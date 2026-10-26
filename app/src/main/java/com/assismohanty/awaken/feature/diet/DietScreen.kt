package com.assismohanty.awaken.feature.diet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.PlaceholderPane

@Composable
fun DietRoute(modifier: Modifier = Modifier) {
    PlaceholderPane(title = stringResource(R.string.diet_title), body = stringResource(R.string.diet_placeholder), modifier = modifier)
}
