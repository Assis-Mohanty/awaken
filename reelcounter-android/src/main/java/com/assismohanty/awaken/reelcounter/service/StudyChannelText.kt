package com.assismohanty.awaken.reelcounter.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * The only place in Reel Counter that reads the text of a node (Study mode, docs/protect/PLAN.md 4.2). It reads the text of
 * the first node with one verified channel-name view id and returns it; the caller compares it and drops it.
 * `NoNodeTextAccessTest` allows exactly this file and exactly one read here.
 */
internal fun channelNameText(roots: List<AccessibilityNodeInfo>, viewId: String): String? =
    roots.firstNotNullOfOrNull { root ->
        runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrDefault(emptyList())
            .firstNotNullOfOrNull { it.text?.toString() }
    }
