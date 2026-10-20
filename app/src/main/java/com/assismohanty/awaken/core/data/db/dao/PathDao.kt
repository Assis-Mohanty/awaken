package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.AiReportEntity
import com.assismohanty.awaken.core.data.db.entity.PathEntity
import com.assismohanty.awaken.core.data.db.entity.PathMilestoneEntity
import com.assismohanty.awaken.core.data.db.entity.PathSlotEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** AI Paths storage (ARCH §2). Nothing here touches a balance: rewards only flow through quests and the ledger. */
@Dao
interface PathDao {
    // ---- path ----------------------------------------------------------------------------------------------------

    @Query("SELECT * FROM path WHERE id = :id")
    suspend fun get(id: String): PathEntity?

    @Query("SELECT * FROM path WHERE id = :id")
    fun observe(id: String): Flow<PathEntity?>

    @Query("SELECT * FROM path ORDER BY created_at, id")
    suspend fun all(): List<PathEntity>

    /** Paths the hunter still sees: everything but ARCHIVED, oldest first (the daily-cap priority order). */
    @Query("SELECT * FROM path WHERE status != 'ARCHIVED' ORDER BY created_at, id")
    fun observeVisible(): Flow<List<PathEntity>>

    @Query("SELECT * FROM path WHERE status = 'ACTIVE' ORDER BY created_at, id")
    suspend fun active(): List<PathEntity>

    /** PLANNING counts toward the 3-Path cap, PAUSED and ARCHIVED don't (ENGINE_DECISIONS #72). */
    @Query("SELECT COUNT(*) FROM path WHERE status IN ('PLANNING', 'ACTIVE')")
    suspend fun countActiveOrPlanning(): Int

    @Query("SELECT COUNT(*) FROM path WHERE created_game_date = :gameDate")
    suspend fun countCreatedOn(gameDate: LocalDate): Int

    @Upsert
    suspend fun upsert(path: PathEntity)

    // ---- milestones ----------------------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestones(milestones: List<PathMilestoneEntity>)

    @Query("SELECT COALESCE(MAX(plan_version), 0) FROM path_milestone WHERE path_id = :pathId")
    suspend fun latestPlanVersion(pathId: String): Int

    @Query(
        "SELECT * FROM path_milestone WHERE path_id = :pathId AND plan_version = " +
            "(SELECT MAX(plan_version) FROM path_milestone WHERE path_id = :pathId) ORDER BY idx",
    )
    suspend fun currentMilestones(pathId: String): List<PathMilestoneEntity>

    @Query(
        "SELECT * FROM path_milestone WHERE path_id = :pathId AND plan_version = " +
            "(SELECT MAX(plan_version) FROM path_milestone WHERE path_id = :pathId) ORDER BY idx",
    )
    fun observeCurrentMilestones(pathId: String): Flow<List<PathMilestoneEntity>>

    // ---- weeks ---------------------------------------------------------------------------------------------------

    @Query("SELECT * FROM path_week WHERE path_id = :pathId AND iso_week = :isoWeek")
    suspend fun week(pathId: String, isoWeek: String): PathWeekEntity?

    @Query("SELECT * FROM path_week WHERE path_id = :pathId ORDER BY iso_week DESC")
    fun observeWeeks(pathId: String): Flow<List<PathWeekEntity>>

    @Query("SELECT * FROM path_week ORDER BY requested_at DESC LIMIT :limit")
    suspend fun recentWeeks(limit: Int): List<PathWeekEntity>

    @Upsert
    suspend fun upsertWeek(week: PathWeekEntity)

    /** Inserts only when no row exists for `(path, week)`; -1 means the weekly request was already made (idempotent). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWeekIfAbsent(week: PathWeekEntity): Long

    @Query("UPDATE path_week SET status = :status WHERE path_id = :pathId AND iso_week = :isoWeek")
    suspend fun setWeekStatus(pathId: String, isoWeek: String, status: PathWeekStatus)

    @Query("SELECT * FROM path_week WHERE status = 'REQUESTED'")
    suspend fun requestedWeeks(): List<PathWeekEntity>

    /** Consent revoked: nothing is waiting for an answer any more. */
    @Query("UPDATE path_week SET status = 'FAILED' WHERE status = 'REQUESTED'")
    suspend fun failAllRequested()

    // ---- slots ---------------------------------------------------------------------------------------------------

    /** Returns -1 per slot whose `(path, week, blueprint)` already exists (vector 32: applying a week twice is a no-op). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSlots(slots: List<PathSlotEntity>): List<Long>

    @Upsert
    suspend fun upsertSlot(slot: PathSlotEntity)

    @Query("SELECT * FROM path_slot WHERE id = :id")
    suspend fun slot(id: String): PathSlotEntity?

    @Query("SELECT * FROM path_slot WHERE id = :id")
    fun observeSlot(id: String): Flow<PathSlotEntity?>

    @Query("SELECT * FROM path_slot WHERE path_id = :pathId AND iso_week = :isoWeek ORDER BY blueprint_index")
    suspend fun slotsForWeek(pathId: String, isoWeek: String): List<PathSlotEntity>

    @Query("SELECT * FROM path_slot WHERE path_id = :pathId AND iso_week = :isoWeek ORDER BY blueprint_index")
    fun observeSlotsForWeek(pathId: String, isoWeek: String): Flow<List<PathSlotEntity>>

    @Query("SELECT * FROM path_slot WHERE iso_week = :isoWeek ORDER BY path_id, blueprint_index")
    suspend fun slotsForIsoWeek(isoWeek: String): List<PathSlotEntity>

    @Query("SELECT * FROM path_slot WHERE assigned_game_date = :gameDate AND outcome = 'PENDING' ORDER BY path_id, blueprint_index")
    suspend fun pendingOn(gameDate: LocalDate): List<PathSlotEntity>

    @Query("SELECT * FROM path_slot WHERE assigned_game_date = :gameDate ORDER BY path_id, blueprint_index")
    suspend fun assignedOn(gameDate: LocalDate): List<PathSlotEntity>

    @Query("SELECT COUNT(*) FROM path_slot WHERE path_id = :pathId AND outcome = 'COMPLETED' AND assigned_game_date >= :from")
    suspend fun completedSince(pathId: String, from: LocalDate): Int

    @Query("SELECT COUNT(*) FROM path_slot WHERE path_id = :pathId AND outcome = 'COMPLETED'")
    suspend fun completedCount(pathId: String): Int

    @Query("UPDATE path_slot SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: String, outcome: com.assismohanty.awaken.engine.path.SlotOutcome)

    @Query("UPDATE path_slot SET feedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: String, feedback: com.assismohanty.awaken.engine.path.Feedback?)

    /** Titles of the Path's quests since [from] (V7: no repeats within 14 days). */
    @Query("SELECT title FROM path_slot WHERE path_id = :pathId AND assigned_game_date >= :from")
    suspend fun titlesSince(pathId: String, from: LocalDate): List<String>

    // ---- reports (outbox) ----------------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReport(report: AiReportEntity): Long

    @Query("SELECT * FROM ai_report WHERE sent_at IS NULL ORDER BY created_at")
    suspend fun unsentReports(): List<AiReportEntity>

    @Query("UPDATE ai_report SET sent_at = :sentAt WHERE id = :id")
    suspend fun markReportSent(id: String, sentAt: Instant)
}
