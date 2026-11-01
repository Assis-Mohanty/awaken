package com.assismohanty.awaken.reelcounter.core.model

/** A text-free view of one UI node. There is deliberately no text or content-description field. */
data class NodeSnapshot(
    val key: String,
    val viewId: String?,
    val className: String?,
    val bounds: Bounds,
    val scrollable: Boolean,
    val visibleToUser: Boolean,
    val children: List<NodeSnapshot> = emptyList(),
)
