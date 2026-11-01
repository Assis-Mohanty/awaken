package com.assismohanty.awaken.reelcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** API 28 ships SQLite 3.22, which has no ON CONFLICT DO UPDATE: upserts are INSERT OR IGNORE + UPDATE. */
@Dao
interface ReelDao {
    /** Returns -1 when the (pkg, session, index) row already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ReelEventEntity): Long

    @Query("INSERT OR IGNORE INTO daily_total(date, pkg, count, seconds, snoozes) VALUES(:date, :pkg, 0, 0, 0)")
    suspend fun ensureTotal(date: String, pkg: String)

    @Query("UPDATE daily_total SET count = count + 1 WHERE date = :date AND pkg = :pkg")
    suspend fun incrementCount(date: String, pkg: String)

    @Query("UPDATE daily_total SET seconds = seconds + :seconds WHERE date = :date AND pkg = :pkg")
    suspend fun addSeconds(date: String, pkg: String, seconds: Long)

    @Query("UPDATE daily_total SET snoozes = MAX(snoozes, :snoozes) WHERE date = :date AND pkg = :pkg")
    suspend fun raiseSnoozes(date: String, pkg: String, snoozes: Int)

    @Query("SELECT * FROM daily_total WHERE date = :date")
    fun observeDay(date: String): Flow<List<DailyTotalEntity>>

    @Query("SELECT * FROM daily_total WHERE date = :date")
    suspend fun day(date: String): List<DailyTotalEntity>

    @Query("SELECT * FROM daily_total WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<DailyTotalEntity>>

    @Query("SELECT MIN(date) FROM daily_total")
    fun observeFirstDay(): Flow<String?>

    @Query("SELECT * FROM reel_event WHERE id IN (SELECT MAX(id) FROM reel_event GROUP BY pkg)")
    suspend fun lastEventPerPkg(): List<ReelEventEntity>

    @Query("SELECT * FROM daily_total ORDER BY date, pkg")
    suspend fun allTotals(): List<DailyTotalEntity>

    @Query("SELECT * FROM reel_event ORDER BY ts")
    suspend fun allEvents(): List<ReelEventEntity>
}
