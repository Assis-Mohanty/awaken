package com.assismohanty.awaken.reelcounter.core.model

interface NodeSource {
    val packageName: String?
    val screen: Bounds

    /** Nodes with this view id, each carrying its direct children only. */
    fun findById(viewId: String): List<NodeSnapshot>

    /** Whole tree in pre-order, capped by depth and node count. Null if there is no window. */
    fun tree(maxDepth: Int = 40, maxNodes: Int = 400): NodeSnapshot?
}

/** In-memory [NodeSource] used by tests and the inspector. */
class TreeNodeSource(
    private val root: NodeSnapshot?,
    override val packageName: String?,
    override val screen: Bounds,
) : NodeSource {

    override fun findById(viewId: String): List<NodeSnapshot> {
        val found = mutableListOf<NodeSnapshot>()
        fun walk(n: NodeSnapshot) {
            if (n.viewId == viewId) found += n.copy(children = n.children.map { it.copy(children = emptyList()) })
            n.children.forEach(::walk)
        }
        root?.let(::walk)
        return found
    }

    override fun tree(maxDepth: Int, maxNodes: Int): NodeSnapshot? {
        val r = root ?: return null
        val budget = intArrayOf(maxNodes)
        fun cap(n: NodeSnapshot, depth: Int): NodeSnapshot {
            budget[0] -= 1
            if (depth >= maxDepth) return n.copy(children = emptyList())
            val kept = mutableListOf<NodeSnapshot>()
            for (c in n.children) {
                if (budget[0] <= 0) break
                kept += cap(c, depth + 1)
            }
            return n.copy(children = kept)
        }
        return cap(r, 1)
    }
}
