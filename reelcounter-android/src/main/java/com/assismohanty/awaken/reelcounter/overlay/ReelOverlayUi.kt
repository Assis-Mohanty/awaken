package com.assismohanty.awaken.reelcounter.overlay

import android.accessibilityservice.AccessibilityService
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.service.OverlaySink

/** What the overlay windows can ask the counter to do. */
interface ReelOverlayActions {
    /** The "5 more" button on the limit nudge. */
    fun snooze(app: ShortVideoApp)
}

/**
 * The windows Reel Counter draws over other apps: the counter bubble, its panel and the limit nudge. The host app
 * implements this with its own design system, so this module carries no visual design of its own.
 * All calls arrive on the main thread (the controller marshals them).
 */
interface ReelOverlayUi : OverlaySink {
    fun attach(service: AccessibilityService, actions: ReelOverlayActions)
    fun detach()
}
