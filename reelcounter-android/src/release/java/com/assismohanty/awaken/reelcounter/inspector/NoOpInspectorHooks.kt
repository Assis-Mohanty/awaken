package com.assismohanty.awaken.reelcounter.inspector

import androidx.compose.runtime.Composable
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import java.time.Instant
import javax.inject.Inject

/** Release build: there is no inspector. */
class NoOpInspectorHooks @Inject constructor() : InspectorHooks {
    override val screen: (@Composable (onBack: () -> Unit) -> Unit)? = null
    override fun onWindowEvent(pkg: String, nodes: NodeSource?, at: Instant) = Unit
    override fun onScroll(pkg: String, scroll: ScrollSignal, at: Instant) = Unit
    override fun onServiceStopped() = Unit
}
