package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assismohanty.awaken.core.data.db.entity.FocusSubjectEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Focus seconds per subject over a period. [subjectId] is null for runs with no subject. */
data class SubjectSeconds(val subjectId: String?, val seconds: Long)

/** Focus seconds on one game-day, for the weekly bars. */
data class DayFocusSeconds(val gameDate: LocalDate, val seconds: Long)

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
            "WHERE g.status = 'CLEARED' AND g.mode IN ('CUSTOM', 'STOPWATCH') AND q.game_date = :gameDate",
    )
    suspend fun paidCustomRuns(gameDate: LocalDate): Int

    @Query(
        "SELECT g.subject_id AS subjectId, SUM(g.planned_sec) AS seconds FROM gate_run g " +
            "JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status = 'CLEARED' AND q.game_date BETWEEN :from AND :to GROUP BY g.subject_id",
    )
    fun observeSecondsBySubject(from: LocalDate, to: LocalDate): Flow<List<SubjectSeconds>>

    @Query(
        "SELECT q.game_date AS gameDate, SUM(g.planned_sec) AS seconds FROM gate_run g " +
            "JOIN quest_instance q ON q.id = g.quest_instance_id " +
            "WHERE g.status = 'CLEARED' AND q.game_date BETWEEN :from AND :to GROUP BY q.game_date",
    )
    fun observeSecondsByDay(from: LocalDate, to: LocalDate): Flow<List<DayFocusSeconds>>
}
