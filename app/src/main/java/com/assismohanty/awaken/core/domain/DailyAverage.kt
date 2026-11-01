package com.assismohanty.awaken.core.domain

/**
 * The Week views' "Daily average" (Screen time, Health): the mean over the days that recorded something, rounded half
 * up, so days before a source existed (all zero) don't drag it down. Null when no day has data. Display only.
 */
object DailyAverage {
    fun of(values: List<Double>): Double? {
        val recorded = values.filter { it > 0.0 }
        return if (recorded.isEmpty()) null else recorded.sum() / recorded.size
    }

    /** Values are positive, so truncating after +0.5 rounds half up. */
    fun ofWhole(values: List<Long>): Long? = of(values.map(Long::toDouble))?.let { (it + HALF).toLong() }

    private const val HALF = 0.5
}
