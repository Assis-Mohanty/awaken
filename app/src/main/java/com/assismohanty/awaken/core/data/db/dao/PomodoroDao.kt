package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow

/** Pomodoro sessions (GD §16.3). Their focus rounds are gate runs, found by [GateRunEntity.pomodoroId]. */
@Dao
interface PomodoroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PomodoroSessionEntity)

    @Query("SELECT * FROM pomodoro_session WHERE status = 'RUNNING' ORDER BY started_at DESC LIMIT 1")
    suspend fun running(): PomodoroSessionEntity?

    @Query("SELECT * FROM pomodoro_session WHERE status = 'RUNNING' ORDER BY started_at DESC LIMIT 1")
    fun observeRunning(): Flow<PomodoroSessionEntity?>

    @Query("SELECT * FROM pomodoro_session WHERE id = :id")
    suspend fun get(id: String): PomodoroSessionEntity?

    /** The gate run of focus round [round] of session [sessionId], or null before it starts. */
    @Query("SELECT * FROM gate_run WHERE pomodoro_id = :sessionId AND pomodoro_round = :round LIMIT 1")
    suspend fun roundRun(sessionId: String, round: Int): GateRunEntity?
}
