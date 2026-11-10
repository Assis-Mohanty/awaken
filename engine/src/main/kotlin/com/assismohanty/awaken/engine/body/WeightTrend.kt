package com.assismohanty.awaken.engine.body

import java.time.LocalDate
import kotlin.math.abs

data class WeighIn(val gameDate: LocalDate, val kg: Double)

/** Smoothing for the weight log and the "update your targets?" offer (GD section 20.2). Nothing here changes a target by itself. */
object WeightTrend {
    const val WINDOW_DAYS = 7L
    const val MIN_WEIGH_INS = 3
    const val PROMPT_DIFFERENCE_KG = 1.0
    const val DISMISS_DAYS = 7L
    private const val EPSILON = 1e-9

    /** Mean of the weigh-ins in the last [WINDOW_DAYS] game-days up to [today], or null with fewer than [MIN_WEIGH_INS]. */
    fun trend(weighIns: List<WeighIn>, today: LocalDate): Double? {
        val recent = weighIns.filter { !it.gameDate.isAfter(today) && it.gameDate.isAfter(today.minusDays(WINDOW_DAYS)) }
        return if (recent.size < MIN_WEIGH_INS) null else recent.sumOf { it.kg } / recent.size
    }

    /** Offer new targets when the trend is [PROMPT_DIFFERENCE_KG] or more from the plan weight and the offer is not dismissed. */
    fun shouldPrompt(trendKg: Double?, planWeightKg: Double, dismissedOn: LocalDate?, today: LocalDate): Boolean =
        trendKg != null &&
            abs(trendKg - planWeightKg) >= PROMPT_DIFFERENCE_KG - EPSILON &&
            (dismissedOn == null || !today.isBefore(dismissedOn.plusDays(DISMISS_DAYS)))
}
