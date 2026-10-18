package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.dao.AchievementDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.AchievementUnlockEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.StreakRules
import com.assismohanty.awaken.engine.StreakState
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Evaluates achievements after a write (GD §10) and records unlocks plus their gold. Call inside the action's
 * transaction. Unlocks are permanent: undo never revokes them (GD §6.5).
 */
class AchievementUnlocker @Inject constructor(
    private val questDao: QuestDao,
    private val achievementDao: AchievementDao,
    private val ledger: LedgerRepository,
    private val reader: GameStateReader,
) {
    /** Returns the ids unlocked by this call, in definition order. */
    suspend fun unlockNew(profile: HunterProfileEntity, today: LocalDate, now: Instant): List<String> {
        val balances = ledger.balances()
        val finalized = reader.finalizedStreak()
        val todayRecord = questDao.getForDate(today).toDayRecord(today)
        val bestStreak = maxOf(finalized.bestStreak, StreakRules.displayStreak(StreakState(streak = finalized.streak), todayRecord))
        val snapshot = reader.achievementSnapshot(profile, LevelCurve.level(balances.xpTotal), balances, bestStreak)
        val ids = AchievementEvaluator.evaluate(snapshot)
        ids.forEach { id ->
            achievementDao.insert(AchievementUnlockEntity(id, now))
            val gold = AchievementEvaluator.byId.getValue(id).gold
            if (gold > 0) {
                ledger.insertIdempotent(
                    listOf(
                        LedgerEntryEntity(
                            kind = LedgerKind.GOLD,
                            delta = gold.toLong(),
                            sourceType = SourceType.ACHIEVEMENT,
                            sourceId = id,
                            gameDate = today,
                            createdAt = now,
                        ),
                    ),
                )
            }
        }
        return ids
    }
}
