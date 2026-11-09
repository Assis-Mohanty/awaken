package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.GymContentRepository
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.GymDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.body.BenchmarkData
import com.assismohanty.awaken.engine.body.LiftKind
import com.assismohanty.awaken.engine.body.Plausibility
import com.assismohanty.awaken.engine.body.PlausibilitySession
import com.assismohanty.awaken.engine.body.SetVerdict
import com.assismohanty.awaken.engine.body.TrainingXp
import com.assismohanty.awaken.engine.body.XpSet
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import javax.inject.Inject

/**
 * Finishes a workout and pays its STR XP in one transaction (GD §18.4, invariants 1-3): the tracker ends the session, the
 * engine judges every set ([TrainingXp], [Plausibility]) and the two ledger rows are inserted idempotently, keyed by the
 * workout, so finishing twice pays once. What earlier workouts of the day already counted is derived from the finished
 * sessions (invariant 7); the daily caps are read from the ledger.
 */
class FinishWorkoutUseCase @Inject constructor(
    private val gym: GymTracker,
    private val gymDao: GymDao,
    private val hunterDao: HunterDao,
    private val ledgerDao: LedgerDao,
    private val ledger: LedgerRepository,
    private val content: GymContentRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: String): FinishResult = transactions.run {
        val finished = gym.finish(workoutId)
        if (finished !is FinishResult.Finished) return@run finished
        val workout = gymDao.workout(workoutId) ?: return@run finished
        val benchmarks = content.benchmarks()
        val bodyWeightKg = hunterDao.getProfile()?.weightKg

        // Sets the day's earlier workouts already counted, per exercise, so the per-exercise limit holds across workouts.
        val earlier = gymDao.finishedOn(workout.gameDate, workoutId)
        val prior = mutableMapOf<String, Int>()
        val earlierIds = mutableSetOf<String>()
        earlier.forEach { before ->
            val judged = judge(before, earlierIds, benchmarks, bodyWeightKg, prior, gymEarned = 0, selfEarned = 0)
            judged.countedByExercise.forEach { (exerciseId, count) -> prior.merge(exerciseId, count, Int::plus) }
            earlierIds += before.id
        }
        val gymEarned = ledgerDao.netGymXp(workout.gameDate).toInt()
        val selfEarned = ledgerDao.netXp(workout.gameDate, SELF).toInt()
        val judged = judge(workout, earlierIds, benchmarks, bodyWeightKg, prior, gymEarned, selfEarned)

        val now = clock.now()
        ledger.insertIdempotent(
            TrainingXp.ledger(workoutId, judged.result.xp).map { row ->
                LedgerEntryEntity(
                    kind = row.kind,
                    stat = row.stat,
                    itemId = row.itemId,
                    delta = row.delta,
                    sourceType = row.sourceType,
                    sourceId = row.sourceId,
                    gameDate = workout.gameDate,
                    // Counts toward the self-report cap of 600 (G16).
                    xpSource = if (row.kind == LedgerKind.XP_TOTAL) SELF else "",
                    createdAt = now,
                )
            },
        )
        finished.copy(
            xp = GymXpResult(judged.result.xp, judged.result.fullSets, judged.result.lightSets, judged.unconfirmed, judged.result.cappedBy != null),
        )
    }

    private class Judged(val result: com.assismohanty.awaken.engine.body.TrainingXpResult, val countedByExercise: Map<String, Int>, val unconfirmed: Int)

    private suspend fun judge(
        workout: WorkoutEntity,
        earlierSameDay: Set<String>,
        benchmarks: BenchmarkData,
        bodyWeightKg: Double?,
        prior: Map<String, Int>,
        gymEarned: Int,
        selfEarned: Int,
    ): Judged {
        val sets = gymDao.sets(workout.id).filter { it.done }
        val xpSets = mutableListOf<XpSet>()
        sets.groupBy { it.exerciseId }.forEach { (exerciseId, rows) ->
            val kind = when (benchmarks.keyFor(exerciseId)) {
                "squat" -> LiftKind.SQUAT
                "bench" -> LiftKind.BENCH
                "deadlift" -> LiftKind.DEADLIFT
                else -> LiftKind.OTHER
            }
            // Sessions before this workout: earlier game-days, and earlier workouts of this day.
            val history = gymDao.exerciseSets(exerciseId)
                .filter { it.done && it.workoutId != workout.id && (it.gameDate < workout.gameDate || it.workoutId in earlierSameDay) }
                .groupBy { it.workoutId }.values
                .map { group -> PlausibilitySession(group.first().gameDate, group.sortedBy { it.setIndex }.map { it.toLogged() }) }
            val work = rows.filter { it.section == RoutineExerciseEntity.SECTION_WORK }.sortedWith(compareBy({ it.setIndex }, { it.side ?: "" }, { it.id }))
            val before = Plausibility.replay(history, bodyWeightKg, kind)
            val flags = Plausibility.replay(history + PlausibilitySession(workout.gameDate, work.map { it.toLogged() }), bodyWeightKg, kind).flags.last()
            val flagged = work.zip(flags).filter { it.second }.map { it.first.id }.toSet()
            rows.forEach { set ->
                xpSets += XpSet(
                    id = set.id,
                    exerciseId = exerciseId,
                    setIndex = set.setIndex,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    durationSec = set.durationSec,
                    warmup = set.section != RoutineExerciseEntity.SECTION_WORK,
                    completedAt = set.completedAt,
                    plannedRestSec = set.restSec,
                    flagged = set.id in flagged,
                    bestConfirmedE1rmKg = before.bestConfirmedKg,
                )
            }
        }
        val result = TrainingXp.evaluate(xpSets, prior, gymEarned, selfEarned)
        val counted = xpSets.filter { result.verdicts[it.id] in COUNTED }.groupingBy { it.exerciseId }.eachCount()
        return Judged(result, counted, unconfirmed = xpSets.count { it.flagged })
    }

    private companion object {
        const val SELF = "SELF"
        val COUNTED = setOf(SetVerdict.COUNTED_FULL, SetVerdict.COUNTED_LIGHT)
    }
}

/**
 * Deletes a workout. A finished one that paid XP gets reversing ledger rows first (the originals stay, invariant 1), so the
 * day's caps and the balances follow the ledger (GD §18.4).
 */
class DeleteWorkoutUseCase @Inject constructor(
    private val gym: GymTracker,
    private val ledgerDao: LedgerDao,
    private val ledger: LedgerRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: String) = transactions.run {
        val originals = ledgerDao.bySource(listOf(SourceType.GYM_WORKOUT), workoutId)
        if (originals.isNotEmpty()) ledger.insertIdempotent(ledger.reversalsOf(originals, clock.now()))
        gym.deleteWorkout(workoutId)
    }
}
