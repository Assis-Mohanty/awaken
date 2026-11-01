package com.assismohanty.awaken.reelcounter.inspector

import androidx.compose.runtime.Composable
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import java.time.Instant

/**
 * What the service tells the Detector Inspector. The debug build records and dumps; the release build
 * binds [NoOpInspectorHooks]. Nothing here ever carries node text.
 */
interface InspectorHooks {
    /** The inspector screen, or null when this build has none (release). */
    val screen: (@Composable (onBack: () -> Unit) -> Unit)?

    fun onWindowEvent(pkg: String, nodes: NodeSource?, at: Instant)
    fun onScroll(pkg: String, scroll: ScrollSignal, at: Instant)
    fun onServiceStopped()
}
