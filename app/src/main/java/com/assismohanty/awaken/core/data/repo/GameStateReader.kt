package com.assismohanty.awaken.core.data.repo

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.AchievementDao
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.AchievementSnapshot
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.StreakRules
import com.assismohanty.awaken.engine.model.Balances
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.LocalDate
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class StreakSummary(val streak: Int, val bestStreak: Int)

/** Reads the data the engine needs. All values are derived from rows, never stored (invariant 7). */
@Singleton
class GameStateReader @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val completionDao: CompletionDao,
    private val ledgerDao: LedgerDao,
    private val dayRecordDao: DayRecordDao,
    private val gateRunDao: GateRunDao,
    private val trialDao: TrialDao,
    private val achievementDao: AchievementDao,
    private val clock: Clock,
) {
    /** Streak over finalized days only (ENGINE_DECISIONS #2, #23). */
    suspend fun finalizedStreak(): StreakSummary {
        val (streak, best) = StreakRules.replay(dayRecordDao.finalizedOutcomes())
        return StreakSummary(streak, best)
    }

    suspend fun hunterContext(profile: HunterProfileEntity, gameDate: LocalDate): HunterContext {
        val window = DirectiveGenerator.noveltyWindow(gameDate)
        return HunterContext(
            hunterId = profile.hunterId,
            streak = finalizedStreak().streak,
            todaySelfXp = ledgerDao.netXp(gameDate, VerificationSource.SELF.name).toInt(),
            todayTimerXp = ledgerDao.netXp(gameDate, VerificationSource.TIMER.name).toInt(),
            tiers = hunterDao.getTiers().associate { it.stat to it.tier },
            focusStats = profile.focusStats,
            ownedEquipment = profile.ownedEquipment,
            injuryTags = if (profile.injuryMode) profile.injuryTags else emptySet(),
            recoveryMode = dayRecordDao.get(gameDate)?.recovery ?: false,
            healthAvailable = profile.healthEnabled,
            lastActiveDates = completionDao.lastActivePerStat().associate { it.stat to it.gameDate },
            recentTemplateIds = questDao.templateIdsBetween(window.start, window.endInclusive).toSet(),
            firstDirective = gameDate == profile.firstGameDate,
        )
    }

    /** Last game-date each stat had a (non-undone) completion. */
    suspend fun lastActiveDates(): Map<Stat, LocalDate> = completionDao.lastActivePerStat().associate { it.stat to it.gameDate }

    suspend fun achievementSnapshot(
        profile: HunterProfileEntity,
        level: Int,
        balances: Balances,
        bestStreak: Int,
    ): AchievementSnapshot {
        val completions = completionDao.allActive()
        val gates = gateRunDao.cleared()
        val zone = clock.zone()
        fun Instant.localTime(): LocalTime = atZone(zone).toLocalTime()
        return AchievementSnapshot(
            questsCompleted = completions.size,
            onboardingDone = profile.onboardingDone,
            level = level,
            bestStreak = bestStreak,
            crits = completions.count { it.crit },
            gatesCleared = gates.size,
            gateMinutesCleared = gates.sumOf { it.plannedSec.toLong() } / SECONDS_PER_MINUTE,
            healthVerifiedCompleted = completions.count { it.verificationSource == VerificationSource.HEALTH },
            comebacks = completions.count { it.questType == QuestType.PENALTY },
            statXp = Stat.entries.associateWith { balances.statXp(it) },
            passedTrials = trialDao.passedRanks().toSet(),
            dawnRaidDays = completions
                .filter { AchievementEvaluator.isDawnRaid(it.completedAt.localTime(), profile.dayStartHour) }
                .map { it.gameDate }
                .distinct()
                .size,
            midnightGates = gates.count { gate ->
                gate.endedAt?.let { AchievementEvaluator.isMidnightGate(it.localTime(), profile.dayStartHour) } == true
            },
            cachesOpened = ledgerDao.cachesOpened(),
            alreadyUnlocked = achievementDao.unlockedIds().toSet(),
        )
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
