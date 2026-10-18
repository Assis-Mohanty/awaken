package com.assismohanty.awaken.core.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.AchievementUnlockEntity
import com.assismohanty.awaken.core.data.db.entity.BalanceCacheEntity
import com.assismohanty.awaken.core.data.db.entity.CompletionEntity
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.db.entity.StatTierEntity
import com.assismohanty.awaken.core.data.db.entity.TrialEntity
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface HunterDao {
    @Query("SELECT * FROM hunter_profile WHERE id = 'me'")
    suspend fun getProfile(): HunterProfileEntity?

    @Query("SELECT * FROM hunter_profile WHERE id = 'me'")
    fun observeProfile(): Flow<HunterProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: HunterProfileEntity)

    @Query("SELECT * FROM stat_tier")
    suspend fun getTiers(): List<StatTierEntity>

    @Upsert
    suspend fun upsertTiers(tiers: List<StatTierEntity>)
}

data class StatLastActive(
    val stat: Stat,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
)

@Dao
interface QuestDao {
    /** Returns rowId per draft, -1 where the deterministic id already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(quests: List<QuestInstanceEntity>): List<Long>

    @Query("SELECT * FROM quest_instance WHERE id = :id")
    suspend fun get(id: String): QuestInstanceEntity?

    @Query("SELECT * FROM quest_instance WHERE game_date = :gameDate ORDER BY created_at, id")
    suspend fun getForDate(gameDate: LocalDate): List<QuestInstanceEntity>

    @Query("SELECT * FROM quest_instance WHERE game_date = :gameDate ORDER BY created_at, id")
    fun observeForDate(gameDate: LocalDate): Flow<List<QuestInstanceEntity>>

    @Query("SELECT COUNT(*) FROM quest_instance WHERE game_date = :gameDate AND type IN ('MAIN', 'SIDE')")
    suspend fun countGenerated(gameDate: LocalDate): Int

    @Query("SELECT COUNT(*) FROM quest_instance WHERE game_date = :gameDate AND status = 'REROLLED'")
    suspend fun countRerolled(gameDate: LocalDate): Int

    @Query("SELECT template_id FROM quest_instance WHERE game_date = :gameDate AND status = 'REROLLED' AND template_id IS NOT NULL")
    suspend fun rerolledTemplateIds(gameDate: LocalDate): List<String>

    @Query(
        "SELECT DISTINCT template_id FROM quest_instance " +
            "WHERE game_date BETWEEN :from AND :to AND template_id IS NOT NULL",
    )
    suspend fun templateIdsBetween(from: LocalDate, to: LocalDate): List<String>

    @Query("SELECT * FROM quest_instance WHERE game_date BETWEEN :from AND :to AND type IN ('MAIN', 'SIDE')")
    suspend fun generatedBetween(from: LocalDate, to: LocalDate): List<QuestInstanceEntity>

    @Query("UPDATE quest_instance SET status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: QuestStatus, completedAt: Instant?)

    @Query("UPDATE quest_instance SET status = 'STARTED', started_at = :startedAt WHERE id = :id AND status = 'ACTIVE'")
    suspend fun markStarted(id: String, startedAt: Instant): Int

    /** Day end (GD §6.5): unfinished generated/system quests expire, habits are missed. */
    @Query(
        "UPDATE quest_instance SET status = CASE WHEN type = 'HABIT' THEN 'MISSED' ELSE 'EXPIRED' END " +
            "WHERE game_date = :gameDate AND status IN ('ACTIVE', 'STARTED')",
    )
    suspend fun closeOpen(gameDate: LocalDate)
}

@Dao
interface CompletionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(completion: CompletionEntity)

    @Query("SELECT COUNT(*) FROM completion WHERE quest_instance_id = :questInstanceId")
    suspend fun countFor(questInstanceId: String): Int

    @Query(
        "SELECT * FROM completion WHERE quest_instance_id = :questInstanceId AND undone_at IS NULL " +
            "ORDER BY attempt DESC LIMIT 1",
    )
    suspend fun latestActive(questInstanceId: String): CompletionEntity?

    @Query("UPDATE completion SET undone_at = :undoneAt WHERE id = :id")
    suspend fun markUndone(id: String, undoneAt: Instant)

    @Query("SELECT * FROM completion WHERE undone_at IS NULL")
    suspend fun allActive(): List<CompletionEntity>

    @Query("SELECT stat, MAX(game_date) AS game_date FROM completion WHERE undone_at IS NULL GROUP BY stat")
    suspend fun lastActivePerStat(): List<StatLastActive>
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit WHERE active = 1")
    suspend fun active(): List<HabitEntity>

    @Upsert
    suspend fun upsert(habit: HabitEntity)
}

@Dao
interface LedgerDao {
    /** Returns -1 when the idempotency key already exists (invariant 2). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LedgerEntryEntity): Long

    @Query("SELECT * FROM ledger_entry ORDER BY id")
    suspend fun all(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entry ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entry WHERE source_type IN (:sourceTypes) AND source_id = :sourceId ORDER BY id")
    suspend fun bySource(sourceTypes: List<SourceType>, sourceId: String): List<LedgerEntryEntity>

    /** Net quest XP for a game-day and verification source; undo rows carry the original date and source. */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM ledger_entry WHERE kind = 'XP_TOTAL' AND game_date = :gameDate AND xp_source = :xpSource")
    suspend fun netXp(gameDate: LocalDate, xpSource: String): Long

    @Query("SELECT COUNT(DISTINCT source_id) FROM ledger_entry WHERE source_type = 'CACHE_OPEN'")
    suspend fun cachesOpened(): Int

    @Query("SELECT * FROM balance_cache")
    suspend fun balances(): List<BalanceCacheEntity>

    @Query("SELECT * FROM balance_cache")
    fun observeBalances(): Flow<List<BalanceCacheEntity>>

    @Query("SELECT value FROM balance_cache WHERE `key` = :key")
    suspend fun balance(key: String): Long?

    @Upsert
    suspend fun upsertBalances(balances: List<BalanceCacheEntity>)

    @Query("DELETE FROM balance_cache")
    suspend fun clearBalances()
}

@Dao
interface DayRecordDao {
    @Query("SELECT * FROM day_record WHERE game_date = :gameDate")
    suspend fun get(gameDate: LocalDate): DayRecordEntity?

    @Upsert
    suspend fun upsert(record: DayRecordEntity)

    @Query("SELECT MAX(game_date) FROM day_record WHERE finalized_at IS NOT NULL")
    suspend fun lastFinalizedDate(): LocalDate?

    @Query("SELECT outcome FROM day_record WHERE finalized_at IS NOT NULL AND outcome IS NOT NULL ORDER BY game_date")
    suspend fun finalizedOutcomes(): List<DayOutcome>

    @Query(
        "SELECT outcome FROM day_record WHERE finalized_at IS NOT NULL AND outcome IS NOT NULL " +
            "AND game_date <= :gameDate ORDER BY game_date",
    )
    suspend fun finalizedOutcomesThrough(gameDate: LocalDate): List<DayOutcome>

    @Query("SELECT outcome FROM day_record WHERE finalized_at IS NOT NULL AND outcome IS NOT NULL ORDER BY game_date")
    fun observeFinalizedOutcomes(): Flow<List<DayOutcome>>

    @Query(
        "SELECT COUNT(*) FROM day_record WHERE finalized_at IS NOT NULL AND recovery = 1 " +
            "AND game_date BETWEEN :from AND :to",
    )
    suspend fun finalizedRecoveryDays(from: LocalDate, to: LocalDate): Int
}

@Dao
interface GateRunDao {
    @Query("SELECT * FROM gate_run WHERE status = 'CLEARED'")
    suspend fun cleared(): List<GateRunEntity>
}

@Dao
interface TrialDao {
    @Query("SELECT target_rank FROM trial WHERE status = 'PASSED'")
    suspend fun passedRanks(): List<Rank>

    @Query("SELECT target_rank FROM trial WHERE status = 'PASSED'")
    fun observePassedRanks(): Flow<List<Rank>>

    @Query("SELECT COUNT(*) FROM trial WHERE status = 'ACTIVE'")
    suspend fun activeCount(): Int

    @Query("SELECT MAX(ended_at) FROM trial WHERE status = 'FAILED'")
    suspend fun lastFailedAt(): Instant?

    @Upsert
    suspend fun upsert(trial: TrialEntity)
}

@Dao
interface AchievementDao {
    @Query("SELECT achievement_id FROM achievement_unlock")
    suspend fun unlockedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(unlock: AchievementUnlockEntity): Long
}
