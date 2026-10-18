package com.assismohanty.awaken.debug

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R

/** Debug-build hooks into main navigation. The release source set has a no-op twin with the same API. */
object DebugEntry {
    const val ENABLED = true

    @Composable
    fun LauncherButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        val description = stringResource(R.string.debug_open_description)
        TextButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
            Text(stringResource(R.string.debug_open))
        }
    }

    @Composable
    fun Screen(onBack: () -> Unit) {
        DebugRoute(onBack = onBack)
    }
}
