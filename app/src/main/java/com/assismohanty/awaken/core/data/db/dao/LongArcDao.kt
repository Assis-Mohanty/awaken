package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.ProgramEntity
import com.assismohanty.awaken.core.data.db.entity.ProgramMarkEntity
import kotlinx.coroutines.flow.Flow

/** Programs (GD §16.15). At most one row is ACTIVE. */
@Dao
interface ProgramDao {
    @Upsert
    suspend fun upsert(program: ProgramEntity)

    @Query("SELECT * FROM program WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun active(): ProgramEntity?

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun get(id: String): ProgramEntity?

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
