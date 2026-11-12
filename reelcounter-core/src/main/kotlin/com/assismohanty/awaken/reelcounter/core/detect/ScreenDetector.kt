package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot
import com.assismohanty.awaken.reelcounter.core.model.NodeSource

/** A page of a child-swap list that is on screen right now: its identity and where its top edge sits. */
data class PageHint(val key: String, val top: Int)

sealed interface Detection {
    data object NotShortVideo : Detection
    data class ShortVideo(
        val app: ShortVideoApp,
        val rule: ScreenRule,
        val viaFallback: Boolean,
        val childSignature: String?,
        /** The pages visible in the list. Two of them mean a swipe is under way, and which one is on top says which way. */
        val pages: List<PageHint> = emptyList(),
    ) : Detection
}

class ScreenDetector(config: DetectorConfig) {
    private val byApp = config.apps.associateBy { it.app }

    fun detect(packageName: String?, nodes: NodeSource?): Detection {
        val app = ShortVideoApp.fromPackage(packageName) ?: return Detection.NotShortVideo
        if (nodes == null) return Detection.NotShortVideo
        val detector = byApp[app] ?: return Detection.NotShortVideo
        val screen = nodes.screen

        for (rule in detector.screens) {
            if (rule.excludeIds.any { id -> nodes.findById(id).any { it.visibleToUser } }) continue
            for (id in rule.containerIds) {
                for (n in nodes.findById(id)) {
                    if (n.visibleToUser && n.bounds.coverage(screen) >= rule.minCoverage) {
                        val child = n.children.firstOrNull {
                            it.visibleToUser && it.bounds.coverage(screen) >= rule.minCoverage
                        }
                        val pages = if (rule.itemIsContainer) emptyList() else n.children.filter { it.visibleToUser }.map { PageHint(it.key, it.bounds.top) }
                        return Detection.ShortVideo(app, rule, viaFallback = false, childSignature = if (rule.itemIsContainer) n.key else child?.key, pages = pages)
                    }
                }
            }
        }

        if (detector.usesFallback()) {
            val hit = nodes.tree()?.let { findFullScreenPager(it, screen) }
            if (hit != null) {
                val (pager, child) = hit
                val rule = ScreenRule(
                    containerIds = listOf(pager.viewId ?: "*"),
                    signal = ItemSignal.SCROLL_INDEX,
                    signalSourceIds = listOfNotNull(pager.viewId),
                )
                return Detection.ShortVideo(app, rule, viaFallback = true, childSignature = child.key)
            }
        }
        return Detection.NotShortVideo
    }

    private fun findFullScreenPager(n: NodeSnapshot, screen: Bounds): Pair<NodeSnapshot, NodeSnapshot>? {
        if (n.scrollable && n.visibleToUser && n.bounds.coverage(screen) >= FALLBACK_COVERAGE) {
            val child = n.children.firstOrNull {
                it.visibleToUser && it.bounds.coverage(screen) >= FALLBACK_COVERAGE
            }
            if (child != null) return n to child
        }
        for (c in n.children) findFullScreenPager(c, screen)?.let { return it }
        return null
    }

    companion object {
        const val FALLBACK_COVERAGE = 0.85
    }
}
