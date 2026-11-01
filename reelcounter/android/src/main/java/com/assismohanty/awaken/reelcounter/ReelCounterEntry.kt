package com.assismohanty.awaken.reelcounter

import android.content.ComponentName
import android.content.Context
import com.assismohanty.awaken.reelcounter.service.ReelAccessibilityService

/** Small public surface for host apps. The UI entry point is `ui.ReelCounterNavGraph`. */
object ReelCounterEntry {
    fun serviceComponent(context: Context): ComponentName =
        ComponentName(context, ReelAccessibilityService::class.java)
}
