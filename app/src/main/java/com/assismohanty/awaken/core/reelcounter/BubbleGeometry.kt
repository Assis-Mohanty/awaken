package com.assismohanty.awaken.core.reelcounter

import com.assismohanty.awaken.reelcounter.data.BubbleEdge
import kotlin.math.roundToInt

/** Where the counter bubble sits: on the left or right edge, at a fraction of the usable height. */
data class BubblePlacement(val edge: BubbleEdge, val yFraction: Float)

/** Pure pixel maths for the draggable bubble, so it is unit-tested without a window. */
object BubbleGeometry {

    fun x(edge: BubbleEdge, screenWidth: Int, size: Int, margin: Int): Int =
        if (edge == BubbleEdge.START) margin else screenWidth - size - margin

    fun y(yFraction: Float, usableTop: Int, usableBottom: Int, size: Int): Int {
        val room = (usableBottom - usableTop - size).coerceAtLeast(0)
        return usableTop + (room * yFraction.coerceIn(0f, 1f)).roundToInt()
    }

    /** The nearest edge and a clamped height for a bubble whose top-left corner was dropped at ([left], [top]). */
    fun snap(left: Int, top: Int, screenWidth: Int, usableTop: Int, usableBottom: Int, size: Int): BubblePlacement {
        val edge = if (left + size / 2 < screenWidth / 2) BubbleEdge.START else BubbleEdge.END
        val room = (usableBottom - usableTop - size).coerceAtLeast(1)
        val fraction = ((top - usableTop).toFloat() / room).coerceIn(0f, 1f)
        return BubblePlacement(edge, fraction)
    }
}
