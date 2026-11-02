package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.DungeonEntity
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.ProgramEntity
import com.assismohanty.awaken.core.data.db.entity.ProgramMarkEntity
import kotlinx.coroutines.flow.Flow

/** Dungeons (GD §16.14). At most one row is ACTIVE. The fee and reward are ledger rows; nothing here is XP. */
@Dao
interface DungeonDao {
    @Upsert
    suspend fun upsert(dungeon: DungeonEntity)

    @Query("SELECT * FROM dungeon WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun active(): DungeonEntity?

    /** The active Dungeon, else the most recent one, so its result can still be shown. */
    @Query("SELECT * FROM dungeon ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, entered_game_date DESC LIMIT 1")
    fun observeCurrent(): Flow<DungeonEntity?>

    @Query("SELECT * FROM dungeon")
    suspend fun all(): List<DungeonEntity>

    @Query("SELECT COUNT(*) FROM dungeon WHERE status = 'CLEARED'")
    suspend fun clearedCount(): Int

    @Query("SELECT COUNT(*) FROM dungeon WHERE status = 'CLEARED' AND length_days = :lengthDays")
    suspend fun clearedCount(lengthDays: Int): Int
}

/** Programs (GD §16.15). At most one row is ACTIVE. */
@Dao
interface ProgramDao {
    @Upsert
    suspend fun upsert(program: ProgramEntity)

    @Query("SELECT * FROM program WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun active(): ProgramEntity?

    @Query("SELECT * FROM program ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, started_at DESC LIMIT 1")
    fun observeCurrent(): Flow<ProgramEntity?>

    @Query("SELECT DISTINCT program_key FROM program WHERE status = 'COMPLETED'")
    suspend fun completedKeys(): List<String>

    @Query("SELECT * FROM habit WHERE program_id = :programId")
    suspend fun habits(programId: String): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMark(mark: ProgramMarkEntity): Long

    @Query("SELECT * FROM program_mark WHERE program_id = :programId")
    suspend fun marks(programId: String): List<ProgramMarkEntity>

    @Query("SELECT * FROM program_mark WHERE program_id = :programId")
    fun observeMarks(programId: String): Flow<List<ProgramMarkEntity>>
}
