package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.dao.WeeklyReviewDao
import com.assismohanty.awaken.core.data.db.entity.WeeklyReviewEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.Consistency
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.StatusMath
import com.assismohanty.awaken.engine.WeeklyReport
import com.assismohanty.awaken.engine.WeeklyReviewRules
import com.assismohanty.awaken.engine.model.Stat
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Step 1 of the review plus what the other steps need (GD §14.4). */
data class WeeklyReviewModel(
    val isoWeek: String,
    val report: WeeklyReport,
    val consistency: ConsistencyScore,
    val insights: InsightsModel,
    val focus: List<Stat>,
    /** Reviewed on Saturday or Sunday: the week is not over and the numbers are "so far". */
    val soFar: Boolean = false,
)

/** What the hunter wrote and picked; text is cleaned again on submit. */
data class WeeklyReviewInput(
    val wentWell: String?,
    val slipped: String?,
    val focus: List<Stat>,
    val intention: String?,
)

sealed interface ReviewResult {
    /** Saved. */
    data object Saved : ReviewResult
    /** This week already has a review: nothing changed. */
    data object AlreadySubmitted : ReviewResult
    /** No review is open today (outside Saturday to Monday). */
    data object Closed : ReviewResult
    data object InvalidFocus : ReviewResult
}

/** Builds the review for the week open today, or null when none is open or it was already submitted. */
class LoadWeeklyReviewUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val ledgerDao: LedgerDao,
    private val completionDao: CompletionDao,
    private val dayRecordDao: DayRecordDao,
    private val weeklyReviewDao: WeeklyReviewDao,
    private val loadInsights: LoadInsightsUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(): WeeklyReviewModel? {
        val profile = hunterDao.getProfile() ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val week = WeeklyReviewRules.reviewedWeek(today) ?: return null
        val key = WeeklyReviewRules.isoKey(week)
        if (key in weeklyReviewDao.submittedWeeks()) return null
        // On Saturday and Sunday the numbers are "so far" (GD §14.4); the report reads whatever rows exist.
        val statXp = ledgerDao.observeStatXpBetween(week.monday, week.sunday).first().associate { it.stat to it.total }
        val report = StatusMath.weeklyReport(
            week,
            Stat.entries.associateWith { statXp[it.name] ?: 0L },
            ledgerDao.observeXpPerDay(week.monday, week.sunday).first().associate { it.gameDate to it.total },
            completionDao.observeCountsPerDay(week.monday, week.sunday).first().sumOf { it.total }.toInt(),
        )
        val outcomes = dayRecordDao.observeDatedOutcomesFrom(today.minusDays(Consistency.WINDOW_DAYS)).first()
            .associate { it.gameDate to it.outcome }
        return WeeklyReviewModel(
            isoWeek = key,
            report = report,
            consistency = Consistency.score(outcomes, today, profile.firstGameDate),
            insights = loadInsights(profile, today),
            focus = profile.focusStats,
            soFar = today in week,
        )
    }
}

/**
 * Saves a review in one transaction (invariant 3): the row (unique per ISO week, a second submit is a no-op), next
 * week's focus stats on the profile (applied from the next generated directive; today's is never regenerated). No XP row is ever written for a review.
 */
class SubmitWeeklyReviewUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val weeklyReviewDao: WeeklyReviewDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: WeeklyReviewInput): ReviewResult {
        if (input.focus.size != FOCUS_STATS || input.focus[0] == input.focus[1]) return ReviewResult.InvalidFocus
        return transactions.run {
            val profile = hunterDao.getProfile() ?: return@run ReviewResult.Closed
            val today = clock.gameDate(profile.dayStartHour)
            val week = WeeklyReviewRules.reviewedWeek(today) ?: return@run ReviewResult.Closed
            val now = clock.now()
            val inserted = weeklyReviewDao.insert(
                WeeklyReviewEntity(
                    id = UUID.randomUUID().toString(),
                    isoWeek = WeeklyReviewRules.isoKey(week),
                    submittedAt = now,
                    wentWell = WeeklyReviewRules.cleanText(input.wentWell, WeeklyReviewRules.TEXT_MAX),
                    slipped = WeeklyReviewRules.cleanText(input.slipped, WeeklyReviewRules.TEXT_MAX),
                    focusStat1 = input.focus[0],
                    focusStat2 = input.focus[1],
                    intention = WeeklyReviewRules.cleanText(input.intention, WeeklyReviewRules.INTENTION_MAX),
                ),
            )
            if (inserted == -1L) return@run ReviewResult.AlreadySubmitted
            hunterDao.setFocusStats(input.focus[0], input.focus[1])
            ReviewResult.Saved
        }
    }

    private companion object {
        const val FOCUS_STATS = 2
    }
}
