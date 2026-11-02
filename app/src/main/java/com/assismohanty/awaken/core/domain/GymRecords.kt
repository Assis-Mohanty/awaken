package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.dao.ExerciseSetRow
import com.assismohanty.awaken.core.data.db.dao.GymDao
import com.assismohanty.awaken.engine.body.ExerciseSession
import com.assismohanty.awaken.engine.body.LoggedSet
import com.assismohanty.awaken.engine.body.RecordRules
import javax.inject.Inject

internal fun ExerciseSetRow.toLogged() = LoggedSet(weightKg, reps, done)

/** One exercise's finished sessions, oldest first. */
internal fun List<ExerciseSetRow>.toSessions(): List<ExerciseSession> =
    groupBy { it.workoutId }.values.map { rows -> ExerciseSession(rows.first().gameDate, rows.sortedBy { it.setIndex }.map { it.toLogged() }) }

/**
 * Counts personal-record events across every finished workout by replaying them oldest first (ARCHITECTURE §3.x: no
 * stored counter, so deleting a workout or importing a backup can never leave it wrong). Feeds `pr1 / pr25 / pr100`.
 */
class PersonalRecordCounter @Inject constructor(private val gymDao: GymDao) {
    suspend fun events(): Int =
        gymDao.allFinishedSetRows().groupBy { it.exerciseId }.values.sumOf { rows ->
            RecordRules.eventCount(rows.toSessions().map { it.sets })
        }
}
