package com.assismohanty.awaken.reelcounter.core.model

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height

    /** Intersection area divided by [screen] area; 0.0 when the screen is empty. */
    fun coverage(screen: Bounds): Double {
        if (screen.area == 0L) return 0.0
        val w = (minOf(right, screen.right) - maxOf(left, screen.left)).coerceAtLeast(0)
        val h = (minOf(bottom, screen.bottom) - maxOf(top, screen.top)).coerceAtLeast(0)
        return (w.toLong() * h).toDouble() / screen.area.toDouble()
    }
}
