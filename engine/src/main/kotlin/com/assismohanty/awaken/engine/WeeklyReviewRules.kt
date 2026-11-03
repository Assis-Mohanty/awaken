package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.path.PathText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * The weekly review (GD §14.4). Reflection grants nothing: no XP, no ledger rows except the review achievements'
 * gold through the normal achievement pipeline. Text never leaves the phone.
 */
object WeeklyReviewRules {
    const val TEXT_MAX = 280
    const val INTENTION_MAX = 120

    /**
     * The ISO week a review on [gameDate] is about: Saturday and Sunday of week W review W, Monday of W+1 still reviews
     * W; any other day has no review open.
     */
    fun reviewedWeek(gameDate: LocalDate): GameWeek? = when (gameDate.dayOfWeek) {
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> GameWeek.of(gameDate)
        DayOfWeek.MONDAY -> GameWeek.of(gameDate).previous
        else -> null
    }

    /** Open on [gameDate] and not yet submitted for that week. */
    fun isOpen(gameDate: LocalDate, submittedWeeks: Set<String>): Boolean =
        reviewedWeek(gameDate)?.let { isoKey(it) !in submittedWeeks } ?: false

    /** "2026-W44": the stored key of a week (unique per review) and the seed of weekly rotations. */
    fun isoKey(week: GameWeek): String = String.format(
        Locale.ROOT,
        "%d-W%02d",
        week.monday.get(IsoFields.WEEK_BASED_YEAR),
        week.monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
    )

    /** Free text cleaned like Path text (ENGINE_DECISIONS #59); blank becomes null. */
    fun cleanText(raw: String?, max: Int): String? = raw?.let { PathText.clean(it, max) }?.takeIf { it.isNotEmpty() }
}
