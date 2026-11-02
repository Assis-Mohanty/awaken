package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assismohanty.awaken.core.data.db.entity.FocusGoalEntity
import com.assismohanty.awaken.core.data.db.entity.FocusSubjectEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Focus seconds per subject over a period. [subjectId] is null for runs with no subject. */
data class SubjectSeconds(val subjectId: String?, val seconds: Long)

/** Focus seconds on one game-day, for the weekly bars. */
data class DayFocusSeconds(val gameDate: LocalDate, val seconds: Long)

/** Whole focus minutes on one game-day (GD §16.4): the sum of each qualifying run's minutes, floored per run. */
data class DayFocusMinutes(val gameDate: LocalDate, val minutes: Int)

/** Focus subjects (GD §8.1 P7) and the focus-time aggregates behind the Focus card and weekly report. */
@Dao
interface FocusDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubject(subject: FocusSubjectEntity): Long

    @Query("UPDATE focus_subject SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("SELECT * FROM focus_subject WHERE archived = 0 ORDER BY created_at")
    fun observeActive(): Flow<List<FocusSubjectEntity>>

    @Query("SELECT * FROM focus_subject")
    fun observeAll(): Flow<List<FocusSubjectEntity>>

    @Query("SELECT COUNT(*) FROM focus_subject WHERE archived = 0")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM focus_subject WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): FocusSubjectEntity?

    /** Custom and stopwatch runs that were paid on [gameDate]: the daily cap counts these (GD §8.1 P5). */
    @Query(
        "SELECT COUNT(*) FROM gate_run g JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status = 'CLEARED' AND g.mode IN ('CUSTOM', 'STOPWATCH') AND g.habit_quest_id IS NULL AND q.game_date = :gameDate",
    )
    suspend fun paidCustomRuns(gameDate: LocalDate): Int

    @Query(
        "SELECT g.subject_id AS subjectId, SUM(g.planned_sec) AS seconds FROM gate_run g " +
            "JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status = 'CLEARED' AND q.game_date BETWEEN :from AND :to GROUP BY g.subject_id",
    )
    fun observeSecondsBySubject(from: LocalDate, to: LocalDate): Flow<List<SubjectSeconds>>

    /**
     * Focus minutes per game-day for the goal ring and streak (GD §16.4): runs that started that day and ended CLEARED or
     * LOGGED. A stopwatch's planned length is its counted time once stopped. `/ 60` is integer division, so each run floors
     * on its own.
     */
    @Query(
        "SELECT q.game_date AS gameDate, SUM(g.planned_sec / 60) AS minutes FROM gate_run g " +
            "JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status IN ('CLEARED', 'LOGGED') AND q.game_date BETWEEN :from AND :to GROUP BY q.game_date",
    )
    fun observeFocusMinutesByDay(from: LocalDate, to: LocalDate): Flow<List<DayFocusMinutes>>

    @Query(
        "SELECT q.game_date AS gameDate, SUM(g.planned_sec / 60) AS minutes FROM gate_run g " +
            "JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status IN ('CLEARED', 'LOGGED') AND q.game_date BETWEEN :from AND :to GROUP BY q.game_date",
    )
    suspend fun focusMinutesByDay(from: LocalDate, to: LocalDate): List<DayFocusMinutes>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: FocusGoalEntity)

    @Query("SELECT * FROM focus_goal ORDER BY effective_from")
    fun observeGoals(): Flow<List<FocusGoalEntity>>

    @Query("SELECT * FROM focus_goal ORDER BY effective_from")
    suspend fun goals(): List<FocusGoalEntity>

    @Query(
        "SELECT q.game_date AS gameDate, SUM(g.planned_sec) AS seconds FROM gate_run g " +
            "JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status = 'CLEARED' AND q.game_date BETWEEN :from AND :to GROUP BY q.game_date",
    )
    fun observeSecondsByDay(from: LocalDate, to: LocalDate): Flow<List<DayFocusSeconds>>
}
