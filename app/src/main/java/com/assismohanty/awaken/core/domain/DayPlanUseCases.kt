package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.StreakRules
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

/** Rest days ahead and Recovery Mode for today (GD §7.2). */
data class DayPlan(
    val today: LocalDate,
    /** The days a rest day can be picked for: tomorrow through [REST_PLAN_DAYS] days ahead. */
    val upcoming: List<LocalDate>,
    val restDays: Set<LocalDate>,
    val recoveryToday: Boolean,
    val recoveryRemaining: Int,
) {
    fun canToggleRest(date: LocalDate): Boolean =
        date in restDays || StreakRules.canMarkRest(date, today, restDays)

    companion object {
        /** Phase 7 spec: the Rest Day picker offers the next 7 days. */
        const val REST_PLAN_DAYS = 7L
    }
}

class ObserveDayPlanUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val dayRecordDao: DayRecordDao,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any>): Flow<DayPlan?> =
        combine(hunterDao.observeProfile(), dayChanges) { profile, _ -> profile }.flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(null)
            val today = clock.gameDate(profile.dayStartHour)
            val upcoming = (1..DayPlan.REST_PLAN_DAYS).map { today.plusDays(it) }
            // A week either side covers every ISO week the picker touches.
            combine(
                dayRecordDao.observeRestDays(today.minusDays(DayPlan.REST_PLAN_DAYS), today.plusDays(2 * DayPlan.REST_PLAN_DAYS)),
                dayRecordDao.observe(today),
            ) { restDays, todayRecord -> restDays to todayRecord }
                .mapLatest { (restDays, todayRecord) ->
                    DayPlan(
                        today = today,
                        upcoming = upcoming,
                        restDays = restDays.toSet(),
                        recoveryToday = todayRecord?.recovery == true,
                        recoveryRemaining = StreakRules.recoveryRemaining(recoveryUsed(dayRecordDao, today)),
                    )
                }
        }
}

/** Finalized recovery days so far this month (today isn't finalized, so it is not counted). */
private suspend fun recoveryUsed(dayRecordDao: DayRecordDao, today: LocalDate): Int {
    val month = YearMonth.from(today)
    return StreakRules.recoveryUsed(dayRecordDao.finalizedRecoveryDays(month.atDay(1), today.minusDays(1)))
}

sealed interface DayPlanResult {
    data object Saved : DayPlanResult
    /** Rest: not a future day in range, or another rest day is already set in that ISO week. */
    data object NotAllowed : DayPlanResult
    /** Recovery: no days left this month. */
    data object NoRecoveryLeft : DayPlanResult
}

/** Marks or clears a rest day in advance (GD §7.2: before the day starts, at most one per ISO week). */
class SetRestDayUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val dayRecordDao: DayRecordDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate, rest: Boolean): DayPlanResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run DayPlanResult.NotAllowed
        val today = clock.gameDate(profile.dayStartHour)
        if (date <= today || date > today.plusDays(DayPlan.REST_PLAN_DAYS)) return@run DayPlanResult.NotAllowed
        val existing = dayRecordDao.get(date)
        if (rest) {
            val nearby = dayRecordDao.restDays(date.minusDays(DayPlan.REST_PLAN_DAYS), date.plusDays(DayPlan.REST_PLAN_DAYS))
            if (!StreakRules.canMarkRest(date, today, nearby)) return@run DayPlanResult.NotAllowed
        }
        dayRecordDao.upsert((existing ?: DayRecordEntity(gameDate = date)).copy(restDay = rest))
        DayPlanResult.Saved
    }
}

/**
 * Turns Recovery Mode on or off from today (GD §7.2). Today's streak is protected from now; the VIT/WIL-only directive
 * starts with the next generated day. The mode then carries over day to day (ENGINE_DECISIONS #33).
 */
class SetRecoveryModeUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val dayRecordDao: DayRecordDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(on: Boolean): DayPlanResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run DayPlanResult.NotAllowed
        val today = clock.gameDate(profile.dayStartHour)
        if (on && StreakRules.recoveryRemaining(recoveryUsed(dayRecordDao, today)) == 0) return@run DayPlanResult.NoRecoveryLeft
        val existing = dayRecordDao.get(today)
        dayRecordDao.upsert((existing ?: DayRecordEntity(gameDate = today)).copy(recovery = on))
        DayPlanResult.Saved
    }
}
