package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.QuitCounterEntity
import com.assismohanty.awaken.core.data.db.entity.QuitDayEntity
import com.assismohanty.awaken.core.data.db.entity.RelapseEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Quit counters (GD §16.13). Not a ledger: nothing here is ever XP. Restarts and day verdicts are only added. */
@Dao
interface QuitDao {
    @Upsert
    suspend fun upsertCounter(counter: QuitCounterEntity)

    @Query("SELECT * FROM quit_counter ORDER BY created_at")
    suspend fun counters(): List<QuitCounterEntity>

    @Query("SELECT * FROM quit_counter ORDER BY created_at")
    fun observeCounters(): Flow<List<QuitCounterEntity>>

    @Query("SELECT * FROM quit_counter WHERE id = :id")
    suspend fun counter(id: String): QuitCounterEntity?

    @Query("SELECT COUNT(*) FROM quit_counter WHERE archived_at IS NULL")
    suspend fun activeCount(): Int

    @Query("UPDATE quit_counter SET archived_at = :at WHERE id = :id AND archived_at IS NULL")
    suspend fun archive(id: String, at: Instant)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelapse(relapse: RelapseEntity): Long

    @Query("SELECT * FROM relapse ORDER BY at")
    suspend fun relapses(): List<RelapseEntity>

    @Query("SELECT * FROM relapse ORDER BY at")
    fun observeRelapses(): Flow<List<RelapseEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDay(day: QuitDayEntity): Long

    @Query("SELECT * FROM quit_day ORDER BY game_date")
    suspend fun days(): List<QuitDayEntity>

    @Query("SELECT * FROM quit_day ORDER BY game_date")
    fun observeDays(): Flow<List<QuitDayEntity>>

    @Query("SELECT MAX(game_date) FROM quit_day WHERE counter_id = :counterId")
    suspend fun lastDay(counterId: String): LocalDate?
}
