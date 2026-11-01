package com.assismohanty.awaken.reelcounter.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot
import com.assismohanty.awaken.reelcounter.core.model.NodeSource

/**
 * Maps live accessibility nodes to text-free [NodeSnapshot]s.
 * Only ids, class names, bounds and flags are read: never node text or descriptions.
 */
internal object NodeSnapshotMapping {

    /** Maps [node] and, unless the caps are hit, its whole subtree. [budget] holds the nodes still allowed. */
    fun map(node: AccessibilityNodeInfo, depth: Int, budget: IntArray, maxDepth: Int = 40): NodeSnapshot {
        budget[0] -= 1
        val children = mutableListOf<NodeSnapshot>()
        if (depth < maxDepth) {
            for (i in 0 until node.childCount) {
                if (budget[0] <= 0) break
                val child = node.getChild(i) ?: continue
                children += map(child, depth + 1, budget, maxDepth)
            }
        }
        return snapshot(node, children)
    }

    /** The node with its direct children only. */
    fun mapShallow(node: AccessibilityNodeInfo): NodeSnapshot {
        val children = (0 until node.childCount).mapNotNull { i -> node.getChild(i)?.let { snapshot(it, emptyList()) } }
        return snapshot(node, children)
    }

    private fun snapshot(node: AccessibilityNodeInfo, children: List<NodeSnapshot>): NodeSnapshot {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return NodeSnapshot(
            key = Integer.toHexString(node.hashCode()),
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            scrollable = node.isScrollable,
            visibleToUser = node.isVisibleToUser,
            children = children,
        )
    }
}

/** A [NodeSource] over the live windows of one package. Nothing is fetched until a method is called. */
class AccessibilityNodeSource(
    private val service: AccessibilityService,
    private val pkg: String,
    override val screen: Bounds,
) : NodeSource {

    private val roots: List<AccessibilityNodeInfo> by lazy {
        val fromWindows = runCatching {
            service.windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
                .filter { it.packageName?.toString() == pkg }
        }.getOrDefault(emptyList())
        fromWindows.ifEmpty {
            listOfNotNull(runCatching { service.rootInActiveWindow }.getOrNull()?.takeIf { it.packageName?.toString() == pkg })
        }
    }

    override val packageName: String? get() = if (roots.isNotEmpty()) pkg else null

    override fun findById(viewId: String): List<NodeSnapshot> =
        roots.flatMap { root ->
            runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrDefault(emptyList())
                .map(NodeSnapshotMapping::mapShallow)
        }

    override fun tree(maxDepth: Int, maxNodes: Int): NodeSnapshot? {
        if (roots.isEmpty()) return null
        val budget = intArrayOf(maxNodes)
        val mapped = roots.map { NodeSnapshotMapping.map(it, depth = 1, budget = budget, maxDepth = maxDepth) }
        if (mapped.size == 1) return mapped.single()
        return NodeSnapshot(
            key = "windows",
            viewId = null,
            className = null,
            bounds = screen,
            scrollable = false,
            visibleToUser = true,
            children = mapped,
        )
    }
}
