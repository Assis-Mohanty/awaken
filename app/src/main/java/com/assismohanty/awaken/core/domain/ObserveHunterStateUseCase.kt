package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.StatLevel
import com.assismohanty.awaken.engine.StreakRules
import com.assismohanty.awaken.engine.StreakState
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Everything the header/status UI shows, derived from rows (invariant 7). */
data class HunterState(
    val name: String,
    val gameDate: LocalDate,
    val totalXp: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForLevel: Long,
    /** Displayed rank: highest passed trial. */
    val rank: Rank,
    /** The band the level is in; when above [rank], a Rank Trial is waiting. */
    val band: Rank,
    val gold: Long,
    val fragments: Long,
    val shields: Long,
    val statXp: Map<Stat, Long>,
    val statLevels: Map<Stat, Int>,
    val streak: Int,
    val bestStreak: Int,
    val todayCleared: Boolean,
    /** Equipped title id (GD §10); display text is resolved in the UI. */
    val equippedTitleId: String? = null,
)

class ObserveHunterStateUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val dayRecordDao: DayRecordDao,
    private val trialDao: TrialDao,
    private val ledger: LedgerRepository,
    private val clock: Clock,
) {
    /**
     * [dayChanges] re-derives "today" whenever it emits (debug time travel now, the day-start tick in Phase 7);
     * otherwise the game-date is fixed per profile emission.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any> = flowOf(Unit)): Flow<HunterState?> =
        combine(hunterDao.observeProfile(), dayChanges) { profile, _ -> profile }.flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(null)
            val today = clock.gameDate(profile.dayStartHour)
            combine(
                ledger.observeBalances(),
                dayRecordDao.observeFinalizedOutcomes(),
                questDao.observeForDate(today),
                trialDao.observePassedRanks(),
            ) { balances, outcomes, todayQuests, passed ->
                val progress = LevelCurve.progress(balances.xpTotal)
                val (streak, best) = StreakRules.replay(outcomes)
                val todayRecord = todayQuests.toDayRecord(today)
                val todayCleared = StreakRules.isCleared(todayRecord)
                val displayStreak = StreakRules.displayStreak(StreakState(streak = streak), todayRecord)
                val statXp = Stat.entries.associateWith { balances.statXp(it) }
                HunterState(
                    name = profile.name,
                    gameDate = today,
                    totalXp = balances.xpTotal,
                    level = progress.level,
                    xpIntoLevel = progress.xpIntoLevel,
                    xpForLevel = progress.xpForLevel,
                    rank = RankRules.currentRank(passed.toSet()),
                    band = RankRules.bandFor(progress.level),
                    gold = balances.gold,
                    fragments = balances.fragments,
                    shields = balances.item(LedgerRepository.STREAK_SHIELD_ITEM_ID),
                    statXp = statXp,
                    statLevels = statXp.mapValues { (_, xp) -> StatLevel.level(xp) },
                    streak = displayStreak,
                    bestStreak = maxOf(best, displayStreak),
                    todayCleared = todayCleared,
                    equippedTitleId = profile.equippedTitleId,
                )
            }
        }
}
