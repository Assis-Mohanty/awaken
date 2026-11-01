package com.assismohanty.awaken.reelcounter

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.assismohanty.awaken.reelcounter.service.NodeSnapshotMapping
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AccessibilityNodeSourceMappingTest {

    @Suppress("DEPRECATION")
    private fun node(id: String?, scrollable: Boolean = false): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain().apply {
            viewIdResourceName = id
            className = "android.widget.FrameLayout"
            setBoundsInScreen(Rect(0, 0, 1080, 2400))
            isScrollable = scrollable
            isVisibleToUser = true
        }

    private fun parentWithChildren(count: Int): AccessibilityNodeInfo {
        val parent = node("a.b:id/parent", scrollable = true)
        repeat(count) { shadowOf(parent).addChild(node("a.b:id/child$it")) }
        return parent
    }

    @Test
    fun copiesIdClassBoundsAndFlags() {
        val snap = NodeSnapshotMapping.map(parentWithChildren(1), depth = 1, budget = intArrayOf(400))
        assertThat(snap.viewId).isEqualTo("a.b:id/parent")
        assertThat(snap.className).isEqualTo("android.widget.FrameLayout")
        assertThat(snap.bounds.width).isEqualTo(1080)
        assertThat(snap.bounds.height).isEqualTo(2400)
        assertThat(snap.scrollable).isTrue()
        assertThat(snap.visibleToUser).isTrue()
        assertThat(snap.children.single().viewId).isEqualTo("a.b:id/child0")
    }

    @Test
    fun nodeBudgetCapsTheTree() {
        val snap = NodeSnapshotMapping.map(parentWithChildren(3), depth = 1, budget = intArrayOf(2))
        assertThat(snap.children).hasSize(1)
    }

    @Test
    fun depthCapStopsDescending() {
        val snap = NodeSnapshotMapping.map(parentWithChildren(2), depth = 1, budget = intArrayOf(400), maxDepth = 1)
        assertThat(snap.children).isEmpty()
    }

    @Test
    fun shallowMappingKeepsDirectChildrenOnly() {
        val parent = node("a.b:id/parent")
        val child = node("a.b:id/child")
        shadowOf(child).addChild(node("a.b:id/grandchild"))
        shadowOf(parent).addChild(child)
        val snap = NodeSnapshotMapping.mapShallow(parent)
        assertThat(snap.children).hasSize(1)
        assertThat(snap.children.single().children).isEmpty()
    }
}
