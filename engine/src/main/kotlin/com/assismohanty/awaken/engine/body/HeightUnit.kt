package com.assismohanty.awaken.engine.body

import kotlin.math.floor
import kotlin.math.roundToInt

/** How heights and body measurements are shown and typed. Everything is stored in centimetres; a unit only changes what the hunter sees. Pure. */
enum class HeightUnit {
    CM,
    FT_IN,
    ;

    /** A body measurement in the unit's own length: centimetres, or inches. */
    fun lengthFromCm(cm: Double): Double = if (this == FT_IN) cm / CM_PER_INCH else cm

    fun lengthToCm(value: Double): Double = if (this == FT_IN) value * CM_PER_INCH else value

    companion object {
        /** The international inch, exactly. */
        const val CM_PER_INCH = 2.54
        const val INCHES_PER_FOOT = 12

        /** A height as whole feet and inches to one decimal, with 11.95 inches rolling up to the next foot. */
        fun feetInches(cm: Double): Pair<Int, Double> {
            val totalInches = (cm / CM_PER_INCH * TENTHS).roundToInt() / TENTHS
            val feet = floor(totalInches / INCHES_PER_FOOT).toInt()
            return feet to (totalInches - feet * INCHES_PER_FOOT)
        }

        fun cmOf(feet: Int, inches: Double): Double = (feet * INCHES_PER_FOOT + inches) * CM_PER_INCH

        private const val TENTHS = 10.0
    }
}
