package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.onboarding.BodyMath
import java.time.LocalDate

/**
 * Who sees calorie targets (GD section 20.2, ENGINE_DECISIONS #188). Only a birth year is stored, so "18 or older" is
 * approximated by year difference: a few 18-year-olds are hidden for part of a year, no 17-year-old ever gets targets.
 * Hidden targets leave logging, totals, micros, water and the weight log untouched.
 */
object DietGuard {
    /** Targets need a year difference above this (the onboarding target age, `BodyMath.TARGET_MIN_AGE`). */
    private const val MAX_HIDDEN_YEAR_DIFFERENCE = BodyMath.TARGET_MIN_AGE

    fun bmi(heightCm: Double, weightKg: Double): Double {
        val m = heightCm / CM_PER_M
        return weightKg / (m * m)
    }

    fun targetsVisible(birthYear: Int, gameDate: LocalDate, heightCm: Double, weightKg: Double): Boolean =
        gameDate.year - birthYear > MAX_HIDDEN_YEAR_DIFFERENCE && bmi(heightCm, weightKg) >= BodyMath.BMI_BELOW

    private const val CM_PER_M = 100.0
}
