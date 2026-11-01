package com.assismohanty.awaken.reelcounter.service

import android.view.accessibility.AccessibilityEvent
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.detect.RawSignal
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import com.assismohanty.awaken.reelcounter.core.time.ReelClock

/**
 * Thin translation from [AccessibilityEvent] to [RawSignal]. Reads only the event type, the package name,
 * the source's view id and the scroll indices: never event text or descriptions.
 */
class EventAdapter(
    private val clock: ReelClock,
    private val nodesFor: (String) -> NodeSource?,
) {
    fun toSignal(event: AccessibilityEvent): RawSignal? {
        val pkg = event.packageName?.toString() ?: return null
        if (pkg !in ShortVideoApp.allPackages) return null
        val at = clock.now()
        return when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> RawSignal.WindowStateChanged(pkg, nodesFor(pkg), at)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> RawSignal.WindowContentChanged(pkg, nodesFor(pkg), at)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> RawSignal.ViewScrolled(
                packageName = pkg,
                scroll = ScrollSignal(
                    sourceViewId = event.source?.viewIdResourceName,
                    fromIndex = event.fromIndex,
                    toIndex = event.toIndex,
                    itemCount = event.itemCount,
                    scrollDeltaY = event.scrollDeltaY,
                ),
                nodes = nodesFor(pkg),
                at = at,
            )
            else -> null
        }
    }
}
