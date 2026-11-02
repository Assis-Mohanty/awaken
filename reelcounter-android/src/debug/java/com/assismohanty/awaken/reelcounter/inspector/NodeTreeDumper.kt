package com.assismohanty.awaken.reelcounter.inspector

import com.assismohanty.awaken.reelcounter.core.detect.Detection
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot
import java.time.Instant

/** Renders a [NodeSnapshot] tree as ids-only text. There is no text field to leak: the model has none. */
object NodeTreeDumper {

    fun dump(
        pkg: String,
        at: Instant,
        screen: Bounds,
        detection: Detection,
        root: NodeSnapshot?,
        scrollLog: List<String>,
    ): String = buildString {
        appendLine("# pkg=$pkg at=$at screen=${screen.width}x${screen.height} detection=${describe(detection)}")
        if (root == null) appendLine("(no window)") else appendNode(root, 0)
        appendLine("# scroll log (last $MAX_SCROLL_LOG for this package)")
        scrollLog.forEach { appendLine(it) }
    }

    fun describe(detection: Detection): String = when (detection) {
        Detection.NotShortVideo -> "NotShortVideo"
        is Detection.ShortVideo ->
            "ShortVideo(${detection.app}, viaFallback=${detection.viaFallback}, signal=${detection.rule.signal})"
    }

    private fun StringBuilder.appendNode(n: NodeSnapshot, depth: Int) {
        repeat(depth) { append("  ") }
        val b = n.bounds
        appendLine(
            "${n.className ?: "-"} id=${n.viewId ?: "-"} bounds=${b.left},${b.top},${b.right},${b.bottom} " +
                "scroll=${n.scrollable} vis=${n.visibleToUser} children=${n.children.size}",
        )
        n.children.forEach { appendNode(it, depth + 1) }
    }

    const val MAX_SCROLL_LOG = 50
}
