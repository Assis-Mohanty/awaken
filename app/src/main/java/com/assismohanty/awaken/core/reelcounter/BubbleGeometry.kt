package com.assismohanty.awaken.core.reelcounter

import com.assismohanty.awaken.reelcounter.data.BubbleEdge
import kotlin.math.roundToInt

/** Where the counter bubble sits: left, top middle or right, at a fraction of the usable height. */
data class BubblePlacement(val edge: BubbleEdge, val yFraction: Float)

/** Pure pixel maths for the draggable bubble, so it is unit-tested without a window. */
object BubbleGeometry {

    /** [size] is the bubble's width. */
    fun x(edge: BubbleEdge, screenWidth: Int, size: Int, margin: Int): Int =
        when (edge) {
            BubbleEdge.START -> margin
            BubbleEdge.CENTER -> (screenWidth - size) / 2
            BubbleEdge.END -> screenWidth - size - margin
        }

    /** [size] is the bubble's height. */
    fun y(yFraction: Float, usableTop: Int, usableBottom: Int, size: Int): Int {
        val room = (usableBottom - usableTop - size).coerceAtLeast(0)
        return usableTop + (room * yFraction.coerceIn(0f, 1f)).roundToInt()
    }

    /**
     * The nearest anchor and a clamped height for a bubble whose top-left corner was dropped at ([left], [top]).
     * [size] is its width; [height] defaults to a square bubble.
     */
    fun snap(left: Int, top: Int, screenWidth: Int, usableTop: Int, usableBottom: Int, size: Int, height: Int = size): BubblePlacement {
        // The screen's three equal columns: a bubble whose middle lands in the centre one snaps to top middle.
        val middle = left + size / 2
        val edge = when {
            middle < screenWidth / 3 -> BubbleEdge.START
            middle > screenWidth * 2 / 3 -> BubbleEdge.END
            else -> BubbleEdge.CENTER
        }
        val room = (usableBottom - usableTop - height).coerceAtLeast(1)
        val fraction = ((top - usableTop).toFloat() / room).coerceIn(0f, 1f)
        return BubblePlacement(edge, fraction)
    }
}
