package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The Gym tracker logs sessions and derives volume, PRs and muscle load (GD §13.2) without touching the ledger. */
@RunWith(AndroidJUnit4::class)
class GymTrackerTest {
    private val graph = TestGraph()
    private val gym = graph.gym

    @After
    fun tearDown() = graph.close()

    private suspend fun benchId(): String =
        gym.searchExercises("barbell bench press medium grip", setOf(Equipment.BARBELL), Muscle.CHEST).first().id

    /** One finished session: bench 60×10 and 70×8 done, a third set left undone. */
    private suspend fun logBenchSession(): String {
        val workoutId = gym.start("Push")
        gym.addExercise(workoutId, benchId())
        gym.addSet(workoutId, position = 0)
        gym.addSet(workoutId, position = 0)
        val sets = checkNotNull(gym.observeActive().first()).exercises.single().sets
        gym.updateSet(sets[0], weightKg = 60.0, reps = 10, done = true)
        gym.updateSet(sets[1], weightKg = 70.0, reps = 8, done = true)
        graph.clock.advance(Duration.ofMinutes(45))
        assertThat(gym.finish(workoutId)).isEqualTo(FinishResult.Finished(workoutId))
        return workoutId
    }

    @Test
    fun aFinishedSessionFeedsTheWeekHistoryAndRecords() = runTest {
        logBenchSession()

        val overview = gym.observeOverview().first()

        assertThat(gym.observeActive().first()).isNull()
        assertThat(overview.weekStart).isEqualTo(LocalDate.of(2026, 10, 12))
        assertThat(overview.weekWorkouts).isEqualTo(1)
        assertThat(overview.weekSets).isEqualTo(2)
        assertThat(overview.weekVolumeKg).isWithin(1e-9).of(1_160.0)
        assertThat(overview.weekMuscleLoad[Muscle.CHEST]).isEqualTo(2.0)
        assertThat(overview.weekMuscleLoad[Muscle.TRICEPS]).isEqualTo(1.0)
        val session = overview.history.single()
        assertThat(session.sets).isEqualTo(2)
        assertThat(session.duration).isEqualTo(Duration.ofMinutes(45))
        val record = overview.records.single()
        assertThat(record.weightKg).isEqualTo(70.0)
        assertThat(record.reps).isEqualTo(8)
        assertThat(record.estimatedMaxKg).isWithin(1e-9).of(70.0 * (1 + 8 / 30.0))
        assertThat(graph.db.ledgerDao().all()).isEmpty()
    }

    @Test
    fun theNextSessionIsPrefilledFromThePreviousOne() = runTest {
        logBenchSession()

        val workoutId = gym.start("Push again")
        gym.addExercise(workoutId, benchId())

        val sets = checkNotNull(gym.observeActive().first()).exercises.single().sets
        assertThat(sets.map { it.weightKg to it.reps }).containsExactly(60.0 to 10, 70.0 to 8).inOrder()
        assertThat(sets.none { it.done }).isTrue()
    }

    @Test
    fun finishingWithNothingDoneDiscardsTheSession() = runTest {
        val workoutId = gym.start("Lazy")
        gym.addExercise(workoutId, benchId())

        assertThat(gym.finish(workoutId)).isEqualTo(FinishResult.DiscardedEmpty)
        assertThat(gym.observeActive().first()).isNull()
        assertThat(gym.observeOverview().first().history).isEmpty()
    }

    @Test
    fun onlyOneSessionRunsAtATime() = runTest {
        val first = gym.start("A")

        assertThat(gym.start("B")).isEqualTo(first)
    }

    @Test
    fun aRoutineRestartsTheSameExercisesAndSetCounts() = runTest {
        val workoutId = logBenchSession()
        val routineId = checkNotNull(gym.saveAsRoutine(workoutId, "Push day"))

        val routine = gym.observeOverview().first().routines.single()
        assertThat(routine.name).isEqualTo("Push day")
        assertThat(routine.sets).isEqualTo(2)

        gym.start("Push day", routineId)
        assertThat(checkNotNull(gym.observeActive().first()).exercises.single().sets).hasSize(2)
    }

    @Test
    fun customExercisesComeFirstAndFilterByMuscle() = runTest {
        gym.createCustomExercise("Landmine press", Equipment.BARBELL, setOf(Muscle.SHOULDERS), setOf(Muscle.CHEST, Muscle.SHOULDERS))

        val results = gym.searchExercises("", emptySet(), Muscle.SHOULDERS)

        assertThat(results.first().name).isEqualTo("Landmine press")
        assertThat(results.first().secondary).containsExactly(Muscle.CHEST)
        assertThat(results.all { Muscle.SHOULDERS in it.primary || Muscle.SHOULDERS in it.secondary }).isTrue()
    }

    @Test
    fun tickingASetDoneStartsTheExercisesRestAndItSurvivesAReload() = runTest {
        val workoutId = gym.start("Push")
        gym.addExercise(workoutId, benchId())
        gym.setRest(workoutId, position = 0, restSec = 90)
        val set = checkNotNull(gym.observeActive().first()).exercises.single().sets.single()
        assertThat(set.restSec).isEqualTo(90)

        gym.updateSet(set, weightKg = 60.0, reps = 10, done = true)

        // Read back from Room, as a fresh process would: the end is stored, not held in memory.
        val workout = graph.db.gymDao().workout(workoutId)
        assertThat(workout?.restEndsAt).isEqualTo(graph.clock.now().plusSeconds(90))
        assertThat(workout?.restTotalSec).isEqualTo(90)
    }

    @Test
    fun aRestIsClearedOnceItsTimeIsUpAndOnlyOnce() = runTest {
        val workoutId = gym.start("Push")
        gym.addExercise(workoutId, benchId())
        gym.setRest(workoutId, position = 0, restSec = 60)
        gym.updateSet(checkNotNull(gym.observeActive().first()).exercises.single().sets.single(), 60.0, 10, done = true)

        assertThat(gym.finishRestIfDue(workoutId)).isNull() // still resting
        graph.clock.advance(Duration.ofSeconds(61))
        assertThat(gym.finishRestIfDue(workoutId)).isEqualTo(RestEnd(chime = true))
        assertThat(gym.finishRestIfDue(workoutId)).isNull() // the other caller finds nothing to do
        assertThat(graph.db.gymDao().workout(workoutId)?.restEndsAt).isNull()
    }

    @Test
    fun aRestThatEndedLongAgoClearsWithoutChiming() = runTest {
        val workoutId = gym.start("Push")
        gym.addExercise(workoutId, benchId())
        gym.setRest(workoutId, position = 0, restSec = 30)
        gym.updateSet(checkNotNull(gym.observeActive().first()).exercises.single().sets.single(), 60.0, 10, done = true)

        graph.clock.advance(Duration.ofMinutes(10))

        assertThat(gym.finishRestIfDue(workoutId)).isEqualTo(RestEnd(chime = false))
    }

    @Test
    fun anExerciseWithNoRestStartsNoTimer() = runTest {
        val workoutId = gym.start("Push")
        gym.addExercise(workoutId, benchId())
        gym.updateSet(checkNotNull(gym.observeActive().first()).exercises.single().sets.single(), 60.0, 10, done = true)

        assertThat(graph.db.gymDao().workout(workoutId)?.restEndsAt).isNull()
    }

    @Test
    fun restTravelsThroughARoutineAndSkipClearsIt() = runTest {
        val workoutId = gym.start("Push")
        gym.addExercise(workoutId, benchId())
        gym.setRest(workoutId, position = 0, restSec = 120)
        gym.updateSet(checkNotNull(gym.observeActive().first()).exercises.single().sets.single(), 60.0, 10, done = true)
        gym.skipRest(workoutId)
        assertThat(graph.db.gymDao().workout(workoutId)?.restEndsAt).isNull()

        val routineId = checkNotNull(gym.saveAsRoutine(workoutId, "Push day"))
        gym.finish(workoutId)
        val next = gym.start("Push", routineId)

        assertThat(checkNotNull(gym.observeActive().first()).workout.id).isEqualTo(next)
        assertThat(checkNotNull(gym.observeActive().first()).exercises.single().restSec).isEqualTo(120)
    }
}
