package com.assismohanty.awaken.engine.onboarding

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Why a target weight and date cannot be used (GD §17 OB17). */
enum class TargetProblem { NOT_OFFERED, DATE_TOO_SOON, WEIGHT_OUT_OF_RANGE, BELOW_HEALTHY_FLOOR }

/** The optional target on the plan screen and the estimate drawn from it (GD §17 OB17, OB18). Pure. */
object WeightTargetRules {

    /** How far away the date can be set: presets, so there is no calendar to get wrong. */
    val WEEK_CHOICES: List<Int> = listOf(4, 8, 12, 26)

    /** OB17: not offered under 18, and not without the height and weight it is measured against. */
    fun isOffered(age: Int?, heightCm: Double?, weightKg: Double?): Boolean =
        age != null && age >= BodyMath.TARGET_MIN_AGE && heightCm != null && weightKg != null

    fun target(weightKg: Double, weeks: Int, today: LocalDate): WeightTarget = WeightTarget(weightKg, today.plusWeeks(weeks.toLong()).toString())

    /** OB17: at least four weeks away; inside the weight range; and no lower than BMI 18.5 for the height. */
    fun problems(target: WeightTarget, answers: OnboardingAnswers, today: LocalDate): Set<TargetProblem> = buildSet {
        if (!isOffered(answers.age, answers.heightCm, answers.weightKg)) {
            add(TargetProblem.NOT_OFFERED)
            return@buildSet
        }
        val date = runCatching { LocalDate.parse(target.date) }.getOrNull()
        if (date == null || ChronoUnit.WEEKS.between(today, date) < BodyMath.TARGET_MIN_WEEKS) add(TargetProblem.DATE_TOO_SOON)
        if (target.weightKg !in BodyMath.WEIGHT_KG_MIN..BodyMath.WEIGHT_KG_MAX) {
            add(TargetProblem.WEIGHT_OUT_OF_RANGE)
        } else if (target.weightKg < BodyMath.minTargetKg(requireNotNull(answers.heightCm))) {
            add(TargetProblem.BELOW_HEALTHY_FLOOR)
        }
    }

    /** The lowest weight the ruler may offer for this height (OB17), and the highest. */
    fun allowedRangeKg(heightCm: Double): ClosedFloatingPointRange<Double> = BodyMath.minTargetKg(heightCm)..BodyMath.WEIGHT_KG_MAX

    /**
     * OB18: a smooth curve from [startKg] to [targetKg] in [samples] points (first is the start, last is the target). It
     * never overshoots or doubles back, so it is an estimate between two numbers and nothing more.
     */
    fun curve(startKg: Double, targetKg: Double, samples: Int = CURVE_SAMPLES): List<Double> {
        require(samples >= 2) { "a curve needs at least two points" }
        return List(samples) { i ->
            val t = i.toDouble() / (samples - 1)
            // smoothstep: eases out of the start and into the target.
            val eased = t * t * (3 - 2 * t)
            startKg + (targetKg - startKg) * eased
        }
    }

    const val CURVE_SAMPLES = 24
}
