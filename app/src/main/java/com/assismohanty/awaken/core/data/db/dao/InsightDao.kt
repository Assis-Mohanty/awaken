package com.assismohanty.awaken.core.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assismohanty.awaken.core.data.db.entity.HealthDayCacheEntity
import com.assismohanty.awaken.engine.model.TimeOfDay
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Quests offered and completed in one block on one day (GD §14.3 I3). */
data class BlockDayTally(
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    @ColumnInfo(name = "time_of_day") val timeOfDay: TimeOfDay,
    val offered: Int,
    val completed: Int,
)

data class GateStartRow(
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
)

/** Read-only aggregates for personal insights and recaps, plus the rebuildable Health cache. */
@Dao
interface InsightDao {
    /** Offered = shown that day (not rerolled or reported); only quests that carry a stored block. */
    @Query(
        "SELECT game_date, time_of_day, COUNT(*) AS offered, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed FROM quest_instance " +
            "WHERE game_date BETWEEN :from AND :to AND time_of_day IS NOT NULL AND status NOT IN ('REROLLED', 'REPORTED') " +
            "GROUP BY game_date, time_of_day",
    )
    suspend fun blockTallies(from: LocalDate, to: LocalDate): List<BlockDayTally>

    /** Focus Gates cleared in the window: the game-date of their quest and when they started. */
    @Query(
        "SELECT q.game_date, g.started_at FROM gate_run g JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status = 'CLEARED' AND q.game_date BETWEEN :from AND :to",
    )
    suspend fun clearedGateStarts(from: LocalDate, to: LocalDate): List<GateStartRow>

    /** Finalized days and how they ended (GD §14.1). */
    @Query(
        "SELECT game_date, outcome FROM day_record WHERE finalized_at IS NOT NULL AND outcome IS NOT NULL " +
            "AND game_date BETWEEN :from AND :to ORDER BY game_date",
    )
    suspend fun outcomes(from: LocalDate, to: LocalDate): List<DatedOutcome>

    /** q(d): active (not undone) completions per game-day, every quest type. */
    @Query(
        "SELECT game_date, COUNT(*) AS total FROM completion WHERE undone_at IS NULL " +
            "AND game_date BETWEEN :from AND :to GROUP BY game_date",
    )
    suspend fun completionCounts(from: LocalDate, to: LocalDate): List<DayTotal>

    /** Game-days with an active (not undone) Rank Trial stage completion. */
    @Query(
        "SELECT DISTINCT game_date FROM completion WHERE quest_type = 'TRIAL' AND undone_at IS NULL " +
            "AND game_date BETWEEN :from AND :to",
    )
    suspend fun trialStageDates(from: LocalDate, to: LocalDate): List<LocalDate>

    /** Emits whenever the cache changes, so Status re-derives insights after a Health refresh. */
    @Query("SELECT COUNT(*) FROM health_day_cache")
    fun observeHealthDayCount(): Flow<Int>

    @Query("SELECT * FROM health_day_cache WHERE game_date BETWEEN :from AND :to")
    suspend fun healthDays(from: LocalDate, to: LocalDate): List<HealthDayCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHealthDays(days: List<HealthDayCacheEntity>)
}
