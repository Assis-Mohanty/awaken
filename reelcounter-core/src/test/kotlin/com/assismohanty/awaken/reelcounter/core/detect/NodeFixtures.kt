package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.model.TreeNodeSource

val SCREEN_1080x2400 = Bounds(0, 0, 1080, 2400)

const val IG_PKG = "com.instagram.android"
const val YT_PKG = "com.google.android.youtube"
const val SNAP_PKG = "com.snapchat.android"
const val IG_PAGER = "com.instagram.android:id/pager"
const val YT_LIST = "com.google.android.youtube:id/list"
const val SNAP_CONTAINER = "com.snapchat.android:id/spotlight"

fun node(
    key: String,
    id: String? = null,
    bounds: Bounds = SCREEN_1080x2400,
    scrollable: Boolean = false,
    visible: Boolean = true,
    children: List<NodeSnapshot> = emptyList(),
    className: String? = null,
) = NodeSnapshot(key, id, className, bounds, scrollable, visible, children)

fun source(root: NodeSnapshot?, pkg: String?): NodeSource = TreeNodeSource(root, pkg, SCREEN_1080x2400)

fun rule(
    containerId: String,
    signal: ItemSignal,
    sources: List<String> = emptyList(),
    exclude: List<String> = emptyList(),
    minCoverage: Double = 0.85,
) = ScreenRule(listOf(containerId), exclude, signal, sources, minCoverage)

fun appDetector(app: ShortVideoApp, verified: Boolean, vararg rules: ScreenRule, fallback: Boolean? = null) =
    AppDetector(app, verified, fallback, rules.toList())

fun detectorConfig(vararg apps: AppDetector) = DetectorConfig(1, apps.toList())

/** An Instagram-like full-screen pager with one full-screen child. */
fun igPagerScreen(childKey: String = "c1"): NodeSource = source(
    node("root", children = listOf(node("p", id = IG_PAGER, scrollable = true, children = listOf(node(childKey))))),
    IG_PKG,
)

fun plainScreen(pkg: String): NodeSource = source(node("root"), pkg)
