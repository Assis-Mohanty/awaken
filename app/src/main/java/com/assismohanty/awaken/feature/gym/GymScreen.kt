package com.assismohanty.awaken.feature.gym

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.PlaceholderPane

@Composable
fun GymRoute(modifier: Modifier = Modifier) {
    PlaceholderPane(title = stringResource(R.string.gym_title), body = stringResource(R.string.gym_placeholder), modifier = modifier)
}
