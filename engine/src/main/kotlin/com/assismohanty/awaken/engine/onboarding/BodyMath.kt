package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.BedtimeRules
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** BMI band by OB7. Information only: these words describe a number, never the person. */
enum class BmiBand { BELOW, WITHIN, ABOVE }

/** [showScale] is false under 20 or with no age: the number and a neutral line only (OB7). */
data class Bmi(val value: Double, val band: BmiBand, val showScale: Boolean) {
    /** Where the badge sits on the scale, 0..1. */
    val scaleFraction: Double
        get() = ((value - BodyMath.BMI_SCALE_MIN) / (BodyMath.BMI_SCALE_MAX - BodyMath.BMI_SCALE_MIN)).coerceIn(0.0, 1.0)
}

/**
 * One ruler: whole ticks from [first] to [last], each worth [perTick] of the stored unit (cm or kg). Snapping to a tick and
 * reading it back returns the same tick, so repeated snapping never drifts (OB11, vector #135).
 */
class MeasureScale internal constructor(val first: Int, val last: Int, private val perTick: Double) {
    val count: Int get() = last - first + 1

    /** The stored value (cm or kg) at [tick]. */
    fun valueAt(tick: Int): Double = tick.coerceIn(first, last) * perTick

    /** The nearest tick to a stored [value], clamped to the scale. */
    fun tickOf(value: Double): Int = (value / perTick).roundToInt().coerceIn(first, last)
}

/** Body measurements for onboarding (GD §17). Pure; the UI only formats. */
object BodyMath {
    const val AGE_MIN = 13
    const val AGE_MAX = 100
    const val HEIGHT_CM_MIN = 120
    const val HEIGHT_CM_MAX = 230
    const val WEIGHT_KG_MIN = 30.0
    const val WEIGHT_KG_MAX = 300.0

    /** OB10 */
    const val DEFAULT_AGE = 25
    const val DEFAULT_HEIGHT_CM = 170.0
    const val DEFAULT_WEIGHT_KG = 70.0

    const val CM_PER_INCH = 2.54
    const val KG_PER_LB = 0.45359237
    const val INCHES_PER_FOOT = 12

    /** OB7: scale marks, and the fixed span the badge moves along. */
    const val BMI_BELOW = 18.5
    const val BMI_ABOVE = 25.0
    const val BMI_HIGH = 30.0
    const val BMI_SCALE_MIN = 15.0
    const val BMI_SCALE_MAX = 40.0
    const val BMI_SCALE_MIN_AGE = 20

    /** OB17 */
    const val TARGET_MIN_WEEKS = 4
    const val TARGET_MIN_AGE = 18

    private val cm = MeasureScale(HEIGHT_CM_MIN, HEIGHT_CM_MAX, 1.0)
    private val inches = MeasureScale(
        first = ceil(HEIGHT_CM_MIN / CM_PER_INCH).toInt(),
        last = floor(HEIGHT_CM_MAX / CM_PER_INCH).toInt(),
        perTick = CM_PER_INCH,
    )
    private val kg = MeasureScale((WEIGHT_KG_MIN * 2).toInt(), (WEIGHT_KG_MAX * 2).toInt(), 0.5)
    private val lb = MeasureScale(
        first = ceil(WEIGHT_KG_MIN / KG_PER_LB).toInt(),
        last = floor(WEIGHT_KG_MAX / KG_PER_LB).toInt(),
        perTick = KG_PER_LB,
    )

    fun heightScale(unit: LengthUnit): MeasureScale = if (unit == LengthUnit.CM) cm else inches

    fun weightScale(unit: MassUnit): MeasureScale = if (unit == MassUnit.KG) kg else lb

    /** Whole feet and inches for a tick of the inches scale. */
    fun feetAndInches(inchTick: Int): Pair<Int, Int> = inchTick / INCHES_PER_FOOT to inchTick % INCHES_PER_FOOT

    /** The label of a weight tick: kilograms with a half only when needed ("70", "70.5"), or whole pounds. */
    fun weightTickLabel(unit: MassUnit, tick: Int): String = when (unit) {
        MassUnit.KG -> if (tick % 2 == 0) (tick / 2).toString() else "${tick / 2}.5"
        MassUnit.LB -> tick.toString()
    }

    /** OB7. The band is read from the rounded value, so the label always matches the number shown. */
    fun bmi(heightCm: Double, weightKg: Double, age: Int?): Bmi {
        val meters = heightCm / 100.0
        val value = (weightKg / (meters * meters) * 10).roundToInt() / 10.0
        val band = when {
            value < BMI_BELOW -> BmiBand.BELOW
            value < BMI_ABOVE -> BmiBand.WITHIN
            else -> BmiBand.ABOVE
        }
        return Bmi(value, band, showScale = age != null && age >= BMI_SCALE_MIN_AGE)
    }

    /** OB10: ft and lb when the locale's country is the US, else cm and kg. */
    fun defaultUnits(country: String?): Pair<LengthUnit, MassUnit> =
        if (country.equals("US", ignoreCase = true)) LengthUnit.FEET_INCHES to MassUnit.LB else LengthUnit.CM to MassUnit.KG

    /** The lowest weight an entered height allows for a target: BMI 18.5 (OB17). */
    fun minTargetKg(heightCm: Double): Double {
        val meters = heightCm / 100.0
        return ceil(BMI_BELOW * meters * meters * 10) / 10.0
    }
}

/** The sleep wheels (GD §16.12 via `BedtimeRules`): only values the phone-down night accepts are offered. */
object SleepWheels {
    private const val DAY = 24 * 60

    /** 20:00 to 02:00 in 15-minute steps, as minutes of the day. */
    val bedtimes: List<Int> =
        (BedtimeRules.BEDTIME_EARLIEST until DAY step BedtimeRules.STEP_MINUTES).toList() +
            (0..BedtimeRules.BEDTIME_LATEST step BedtimeRules.STEP_MINUTES).toList()

    /** Wake times that make a valid window with [bedtimeMinute] (5-12 h, after the day start). */
    fun wakeTimesFor(bedtimeMinute: Int, dayStartHour: Int): List<Int> =
        (BedtimeRules.WAKE_RANGE step BedtimeRules.STEP_MINUTES).filter {
            BedtimeRules.isValidWindow(bedtimeMinute, it, dayStartHour)
        }

    /** Keeps [wakeMinute] when it still fits [bedtimeMinute], else the nearest wake time that does; null if none does. */
    fun fitWake(bedtimeMinute: Int, wakeMinute: Int, dayStartHour: Int): Int? =
        wakeTimesFor(bedtimeMinute, dayStartHour).minByOrNull { abs(it - wakeMinute) }
}
