package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.assismohanty.awaken.core.data.db.entity.PauseEventEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Finished pauses (GD §19.1). Display data and the cooldown only; not a ledger table, so rows may be deleted. */
@Dao
interface PauseDao {
    @Insert
    suspend fun insert(event: PauseEventEntity)

    /** Pauses of [packageName] on [gameDay], continued and Not now: the `n` of the next one is this plus one (ENGINE_DECISIONS #166). */
    @Query("SELECT COUNT(*) FROM pause_event WHERE package_name = :packageName AND game_day = :gameDay")
    suspend fun countOn(packageName: String, gameDay: LocalDate): Int

    /** When the hunter last went on into [packageName]; Not now does not count (it starts no cooldown). */
    @Query("SELECT MAX(occurred_at) FROM pause_event WHERE package_name = :packageName AND outcome = 'CONTINUED'")
    suspend fun lastContinued(packageName: String): Instant?

    @Query("SELECT * FROM pause_event WHERE game_day >= :since ORDER BY occurred_at")
    fun observeSince(since: LocalDate): Flow<List<PauseEventEntity>>

    /** Rows older than [before] (PZ9: kept 8 game-days). */
    @Query("DELETE FROM pause_event WHERE game_day < :before")
    suspend fun deleteBefore(before: LocalDate)
}
