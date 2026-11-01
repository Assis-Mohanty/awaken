package com.assismohanty.awaken.core.reelcounter

import com.assismohanty.awaken.reelcounter.data.BubbleEdge
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BubbleGeometryTest {
    private val width = 1080
    private val size = 132
    private val margin = 24
    private val top = 100
    private val bottom = 2300

    @Test
    fun startEdgeSitsAtTheMargin() {
        assertThat(BubbleGeometry.x(BubbleEdge.START, width, size, margin)).isEqualTo(24)
    }

    @Test
    fun endEdgeSitsFlushWithTheRightMargin() {
        assertThat(BubbleGeometry.x(BubbleEdge.END, width, size, margin)).isEqualTo(width - size - margin)
    }

    @Test
    fun heightFractionMapsIntoTheUsableBand() {
        assertThat(BubbleGeometry.y(0f, top, bottom, size)).isEqualTo(top)
        assertThat(BubbleGeometry.y(1f, top, bottom, size)).isEqualTo(bottom - size)
        assertThat(BubbleGeometry.y(5f, top, bottom, size)).isEqualTo(bottom - size)
    }

    @Test
    fun droppingLeftOfCentreSnapsToTheStartEdge() {
        assertThat(BubbleGeometry.snap(left = 200, top = 500, screenWidth = width, usableTop = top, usableBottom = bottom, size = size).edge)
            .isEqualTo(BubbleEdge.START)
    }

    @Test
    fun droppingRightOfCentreSnapsToTheEndEdge() {
        assertThat(BubbleGeometry.snap(left = 700, top = 500, screenWidth = width, usableTop = top, usableBottom = bottom, size = size).edge)
            .isEqualTo(BubbleEdge.END)
    }

    @Test
    fun droppedHeightIsClampedToTheUsableBand() {
        val above = BubbleGeometry.snap(left = 0, top = -500, screenWidth = width, usableTop = top, usableBottom = bottom, size = size)
        val below = BubbleGeometry.snap(left = 0, top = 9_000, screenWidth = width, usableTop = top, usableBottom = bottom, size = size)
        assertThat(above.yFraction).isEqualTo(0f)
        assertThat(below.yFraction).isEqualTo(1f)
    }

    @Test
    fun snappingThenPlacingKeepsTheHeight() {
        val placed = BubbleGeometry.snap(left = 50, top = 1_200, screenWidth = width, usableTop = top, usableBottom = bottom, size = size)
        val y = BubbleGeometry.y(placed.yFraction, top, bottom, size)
        assertThat(kotlin.math.abs(y - 1_200)).isAtMost(1)
    }
}
