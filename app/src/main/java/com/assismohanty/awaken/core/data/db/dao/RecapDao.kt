package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assismohanty.awaken.core.data.db.entity.RecapEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Stored recaps and the period aggregates they are built from (GD §14.6). Read-only over game tables. */
@Dao
interface RecapDao {
    /** -1 when the period already has a recap (idempotent generation). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(recap: RecapEntity): Long

    @Query("SELECT * FROM recap WHERE period_key = :periodKey")
    suspend fun get(periodKey: String): RecapEntity?

    @Query("SELECT period_key FROM recap")
    suspend fun keys(): List<String>

    /** Newest first, for the RECAPS list on Status. */
    @Query("SELECT * FROM recap ORDER BY generated_at DESC")
    fun observeAll(): Flow<List<RecapEntity>>

    @Query("SELECT COUNT(*) FROM day_record WHERE finalized_at IS NOT NULL AND game_date BETWEEN :from AND :to")
    suspend fun finalizedDays(from: LocalDate, to: LocalDate): Int

    @Query("SELECT COALESCE(SUM(delta), 0) FROM ledger_entry WHERE kind = 'XP_TOTAL' AND game_date < :before")
    suspend fun xpBefore(before: LocalDate): Long

    @Query("SELECT COALESCE(SUM(delta), 0) FROM ledger_entry WHERE kind = 'XP_TOTAL' AND game_date BETWEEN :from AND :to")
    suspend fun xpBetween(from: LocalDate, to: LocalDate): Long

    @Query("SELECT stat, SUM(delta) AS total FROM ledger_entry WHERE kind = 'XP_STAT' AND game_date < :before GROUP BY stat")
    suspend fun statXpBefore(before: LocalDate): List<StatTotal>

    @Query(
        "SELECT stat, SUM(delta) AS total FROM ledger_entry WHERE kind = 'XP_STAT' " +
            "AND game_date BETWEEN :from AND :to GROUP BY stat",
    )
    suspend fun statXpBetween(from: LocalDate, to: LocalDate): List<StatTotal>

    /** Planned seconds of Focus Gates cleared in the period (the `gate_hours*` basis, GD §10). */
    @Query(
        "SELECT COALESCE(SUM(g.planned_sec), 0) FROM gate_run g JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status = 'CLEARED' AND q.game_date BETWEEN :from AND :to",
    )
    suspend fun clearedGateSeconds(from: LocalDate, to: LocalDate): Long

    @Query("SELECT COUNT(*) FROM achievement_unlock WHERE unlocked_at >= :from AND unlocked_at < :until")
    suspend fun achievementsBetween(from: Instant, until: Instant): Int
}
