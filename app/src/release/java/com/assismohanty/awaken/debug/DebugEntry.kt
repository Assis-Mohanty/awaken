package com.assismohanty.awaken.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Release twin of the debug-build hooks: no debug UI ships in release. */
object DebugEntry {
    const val ENABLED = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun LauncherButton(onClick: () -> Unit, modifier: Modifier = Modifier) = Unit

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun Screen(onBack: () -> Unit) = Unit
}
