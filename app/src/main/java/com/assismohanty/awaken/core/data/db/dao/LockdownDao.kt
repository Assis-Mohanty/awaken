package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.LockdownSessionEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Lockdown sessions (GD §19.2). At most one is RUNNING. Never a ledger table: rewards go through the gate run. */
@Dao
interface LockdownDao {
    @Upsert
    suspend fun upsert(session: LockdownSessionEntity)

    @Query("SELECT * FROM lockdown_session WHERE status = 'RUNNING' ORDER BY started_at DESC LIMIT 1")
    suspend fun running(): LockdownSessionEntity?

    @Query("SELECT * FROM lockdown_session WHERE status = 'RUNNING' ORDER BY started_at DESC LIMIT 1")
    fun observeRunning(): Flow<LockdownSessionEntity?>

    /** The newest session of any status: what the result screen shows. */
    @Query("SELECT * FROM lockdown_session ORDER BY started_at DESC LIMIT 1")
    fun observeLatest(): Flow<LockdownSessionEntity?>

    /** When the last broken session ended; the cooldown counts from it (LD4). */
    @Query("SELECT MAX(ended_at) FROM lockdown_session WHERE status = 'BROKEN'")
    suspend fun lastBrokenAt(): Instant?

    @Query("SELECT * FROM lockdown_session ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LockdownSessionEntity>>
}
