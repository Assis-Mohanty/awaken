package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.FocusGoalEntity
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.FocusGoalRules
import com.assismohanty.awaken.engine.FocusGoalVersion
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/** Today's focus against the daily goal, and the focus streak (GD §16.4). Display only: no XP is paid for any of it. */
data class FocusGoalState(val minutesToday: Int, val goal: Int, val streak: Int) {
    val met: Boolean get() = FocusGoalRules.met(minutesToday, goal)

    /** The ring's fill, 0..1. */
    val progress: Float get() = (minutesToday.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** How far back the streak looks. Longer than any milestone (100), so the achievement can never be cut short. */
private const val HISTORY_DAYS = 400L

private fun build(
    today: LocalDate,
    firstDay: LocalDate,
    minutes: Map<LocalDate, Int>,
    goals: List<FocusGoalVersion>,
    outcomes: Map<LocalDate, DayOutcome>,
) = FocusGoalState(
    minutesToday = minutes[today] ?: 0,
    goal = FocusGoalRules.goalOn(today, goals),
    streak = FocusGoalRules.streak(today, firstDay, minutes, goals, outcomes),
)

private fun List<FocusGoalEntity>.toVersions() = map { FocusGoalVersion(it.effectiveFrom, it.minutes) }

/** The goal ring and streak as a live flow; [dayChanges] re-reads at each new game-day. */
class ObserveFocusGoalUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val focusDao: FocusDao,
    private val dayRecordDao: DayRecordDao,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any>): Flow<FocusGoalState?> =
        combine(hunterDao.observeProfile(), dayChanges.onStart { emit(Unit) }) { profile, _ -> profile }
            .flatMapLatest { profile ->
                if (profile == null) return@flatMapLatest flowOf(null)
                val today = clock.gameDate(profile.dayStartHour)
                val from = today.minusDays(HISTORY_DAYS)
                combine(
                    focusDao.observeFocusMinutesByDay(from, today),
                    focusDao.observeGoals(),
                    dayRecordDao.observeDatedOutcomesFrom(from),
                ) { minutes, goals, outcomes ->
                    build(today, profile.firstGameDate, minutes.associate { it.gameDate to it.minutes }, goals.toVersions(), outcomes.associate { it.gameDate to it.outcome })
                }
            }
}

/** Changes the daily focus goal from today on; past days keep the goal they had (GD §16.4, a versioned row). */
class SetFocusGoalUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val focusDao: FocusDao,
    private val clock: Clock,
) {
    /** Snaps [minutes] to 15-600 in steps of 15 and stores it from today. Returns the goal saved, or null with no hunter. */
    suspend operator fun invoke(minutes: Int): Int? {
        val profile = hunterDao.getProfile() ?: return null
        val goal = FocusGoalRules.clampGoal(minutes)
        focusDao.upsertGoal(FocusGoalEntity(effectiveFrom = clock.gameDate(profile.dayStartHour), minutes = goal))
        return goal
    }
}
