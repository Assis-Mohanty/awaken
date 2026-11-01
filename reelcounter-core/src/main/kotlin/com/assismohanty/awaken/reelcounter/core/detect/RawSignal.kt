package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import java.time.Instant

/** What the Android adapter hands over. No Android types, no text. */
sealed interface RawSignal {
    val at: Instant

    data class WindowStateChanged(val packageName: String, val nodes: NodeSource?, override val at: Instant) : RawSignal
    data class WindowContentChanged(val packageName: String, val nodes: NodeSource?, override val at: Instant) : RawSignal
    data class ViewScrolled(
        val packageName: String,
        val scroll: ScrollSignal,
        val nodes: NodeSource?,
        override val at: Instant,
    ) : RawSignal

    /** Sent every second while a short-video screen is active, and once at a requested recheck time. */
    data class Probe(val foregroundPackage: String?, val nodes: NodeSource?, override val at: Instant) : RawSignal
    data class ScreenOff(override val at: Instant) : RawSignal
    data class ScreenOn(val foregroundPackage: String?, val nodes: NodeSource?, override val at: Instant) : RawSignal
}
