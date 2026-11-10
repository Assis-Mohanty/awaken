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

    /** Sessions ended by a shield since [since] (the start of the game-day): one shield exit a day (LD3). */
    @Query("SELECT COUNT(*) FROM lockdown_session WHERE broken_reason = 'EMERGENCY_EXIT' AND ended_at >= :since")
    suspend fun shieldExitsSince(since: Instant): Int
}
