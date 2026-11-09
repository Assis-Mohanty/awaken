package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Gear
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Coached logging rules (GD §18.2): minimum rest, rest after a round, field-level writes, swap and "Last time". */
@RunWith(AndroidJUnit4::class)
class GymSessionTest {
    private val graph = TestGraph()
    private val gym = graph.gym
    private val dao get() = graph.db.gymDao()

    @After
    fun tearDown() = graph.close()

    private suspend fun benchId() = gym.searchExercises("barbell bench press medium grip", setOf(Equipment.BARBELL), Muscle.CHEST).first().id

    private suspend fun sets(workoutId: String) = dao.sets(workoutId)

    private suspend fun startWithBench(): Pair<String, List<WorkoutSetEntity>> {
        val id = gym.start("Push")
        gym.addExercise(id, benchId())
        gym.addSet(id, 0)
        gym.addSet(id, 0)
        val rows = sets(id)
        gym.setRest(id, 0, 120) // 120 s planned rest: the minimum rest is 30 s
        return id to sets(id)
    }

    @Test
    fun theNextSetWaitsForTheMinimumRestAndTheRestTimerStartsOnCompletion() = runTest {
        val (id, rows) = startWithBench()
        gym.setWeight(rows[0].id, 60.0)
        gym.setReps(rows[0].id, 10)
        gym.setWeight(rows[1].id, 60.0)
        gym.setReps(rows[1].id, 10)

        assertThat(gym.completeSet(rows[0].id)).isEqualTo(CompleteResult.Completed)
        val workout = dao.workout(id)!!
        assertThat(workout.restTotalSec).isEqualTo(120)
        assertThat(workout.restEndsAt).isEqualTo(graph.clock.now().plusSeconds(120))
        assertThat(sets(id)[0].completedAt).isEqualTo(graph.clock.now())

        graph.clock.advance(Duration.ofSeconds(10))
        assertThat(gym.completeSet(rows[1].id)).isEqualTo(CompleteResult.TooSoon(20))
        assertThat(sets(id)[1].done).isFalse()

        graph.clock.advance(Duration.ofSeconds(20))
        assertThat(gym.completeSet(rows[1].id)).isEqualTo(CompleteResult.Completed)
        // Completing the next set restarts the rest.
        assertThat(dao.workout(id)!!.restEndsAt).isEqualTo(graph.clock.now().plusSeconds(120))
    }

    @Test
    fun aSetWithNothingLoggedCannotBeCompleted() = runTest {
        val (_, rows) = startWithBench()
        gym.setReps(rows[0].id, 0)

        assertThat(gym.completeSet(rows[0].id)).isEqualTo(CompleteResult.NothingLogged)
        assertThat(gym.completeSet("missing")).isEqualTo(CompleteResult.NotFound)
    }

    @Test
    fun aLateWriteOfOneFieldNeverUndoesACompletedSet() = runTest {
        val (id, rows) = startWithBench()
        gym.setReps(rows[0].id, 8)
        gym.completeSet(rows[0].id)

        // The old whole-row write from a stale screen could put done = false back; field writes cannot.
        gym.setWeight(rows[0].id, 62.5)
        gym.setReps(rows[0].id, 9)

        val saved = sets(id)[0]
        assertThat(saved.done).isTrue()
        assertThat(saved.weightKg).isEqualTo(62.5)
        assertThat(saved.reps).isEqualTo(9)
        assertThat(saved.completedAt).isNotNull()
        gym.reopenSet(rows[0].id)
        assertThat(sets(id)[0].done).isFalse()
        assertThat(sets(id)[0].completedAt).isNull()
    }

    @Test
    fun lastTimeIsThereForTheNextWorkoutAfterAFinishedOne() = runTest {
        val (id, rows) = startWithBench()
        gym.setWeight(rows[0].id, 60.0)
        gym.setReps(rows[0].id, 10)
        gym.completeSet(rows[0].id)
        graph.clock.advance(Duration.ofMinutes(40))
        assertThat(gym.finish(id)).isInstanceOf(FinishResult.Finished::class.java)

        // A second workout the next day: "Last time" is the 60 x 10 set and the new rows are prefilled from it.
        graph.clock.advance(Duration.ofDays(1))
        val next = gym.start("Push again")
        gym.addExercise(next, benchId())
        val last = gym.previousSets(benchId())
        assertThat(last.map { it.weightKg to it.reps }).containsExactly(60.0 to 10)
        assertThat(sets(next).single().weightKg).isEqualTo(60.0)
        // 10 reps is inside the 8-12 range, so the hint asks for one more (GD §16.6).
        assertThat(sets(next).single().reps).isEqualTo(11)
    }

    @Test
    fun warmUpsAndUndoneSetsAreNotLastTime() = runTest {
        val id = gym.start("Push")
        gym.addExercise(id, benchId())
        val row = sets(id).single()
        // The only finished set is a warm-up; the work set was never done.
        dao.upsertSets(listOf(row.copy(section = RoutineExerciseEntity.SECTION_WARMUP, weightKg = 20.0, reps = 10, done = true)))
        dao.upsertSets(listOf(row.copy(id = "work", setIndex = 1, weightKg = 60.0, reps = 8, done = false)))
        gym.finish(id)

        assertThat(gym.previousSets(benchId())).isEmpty()
    }

    @Test
    fun supersetPartnersRestOnlyAfterTheLastOfTheRound() = runTest {
        val planner = graph.planner
        planner.addPlan("chest_tri", GymAnswers(fitness = FitnessLevel.ADVANCED, gear = Gear.entries.toSet(), gearKnown = true), setOf(1))
        val routine = planner.observeRoutines().first().single()
        val id = gym.start(routine.name, routine.id)
        val work = sets(id).filter { it.section == RoutineExerciseEntity.SECTION_WORK }
        val pair = work.filter { it.superset == "A" }.groupBy { it.position }.values.map { it.sortedBy { s -> s.setIndex } }
        val (first, second) = pair
        listOf(first[0], second[0]).forEach { gym.setReps(it.id, 10) }

        gym.completeSet(first[0].id)
        assertThat(dao.workout(id)!!.restEndsAt).isNull()
        gym.completeSet(second[0].id)
        assertThat(dao.workout(id)!!.restEndsAt).isNotNull()
    }

    @Test
    fun aOneSidedSetCreatesBothSidesAndRestsAfterTheSecond() = runTest {
        val routine = graph.planner.createRoutine("Rows")
        val row = graph.gym.searchExercises("one-arm dumbbell row", emptySet(), null).first().id
        dao.insertRoutineExercises(listOf(RoutineExerciseEntity(routine, 0, row, 2, restSec = 60, repMin = 8, repMax = 10, unilateral = true)))

        val id = gym.start("Rows", routine)
        val rows = sets(id)
        assertThat(rows.map { it.setIndex to it.side }).containsExactly(0 to "LEFT", 0 to "RIGHT", 1 to "LEFT", 1 to "RIGHT").inOrder()
        assertThat(rows.all { it.reps == 8 }).isTrue()

        gym.setWeight(rows[0].id, 30.0)
        assertThat(sets(id)[1].weightKg).isEqualTo(30.0) // the other side follows while it is not done
        gym.completeSet(rows[0].id)
        assertThat(dao.workout(id)!!.restEndsAt).isNull()
        gym.completeSet(rows[1].id)
        assertThat(dao.workout(id)!!.restEndsAt).isNotNull()

        gym.addSet(id, 0)
        assertThat(sets(id).count { it.setIndex == 2 }).isEqualTo(2)
        gym.removeLastSet(id, 0)
        assertThat(sets(id).count { it.setIndex == 2 }).isEqualTo(0)
    }

    @Test
    fun aTimedExerciseKeepsItsHoldTargetAndCompletesOnSeconds() = runTest {
        val routine = graph.planner.createRoutine("Core")
        val plank = graph.gym.searchExercises("plank", emptySet(), null).first { it.name == "Plank" }.id
        dao.insertRoutineExercises(listOf(RoutineExerciseEntity(routine, 0, plank, 2, restSec = 45, repMin = 30, repMax = 60, measure = RoutineExerciseEntity.MEASURE_TIME)))

        val id = gym.start("Core", routine)
        val rows = sets(id)
        assertThat(rows.map { it.durationSec }).containsExactly(30, 30)
        gym.setHold(rows[0].id, 45)
        assertThat(gym.completeSet(rows[0].id)).isEqualTo(CompleteResult.Completed)
        gym.setHold(rows[1].id, 0)
        graph.clock.advance(Duration.ofMinutes(2))
        assertThat(gym.completeSet(rows[1].id)).isEqualTo(CompleteResult.NothingLogged)
    }

    @Test
    fun theRestCanBeLengthenedShortenedAndSkipped() = runTest {
        val (id, rows) = startWithBench()
        gym.setReps(rows[0].id, 8)
        gym.completeSet(rows[0].id)

        gym.adjustRest(id, 15)
        assertThat(dao.workout(id)!!.restTotalSec).isEqualTo(135)
        assertThat(dao.workout(id)!!.restEndsAt).isEqualTo(graph.clock.now().plusSeconds(135))
        gym.adjustRest(id, -15)
        gym.adjustRest(id, -15)
        assertThat(dao.workout(id)!!.restTotalSec).isEqualTo(105)
        gym.skipRest(id)
        assertThat(dao.workout(id)!!.restEndsAt).isNull()
        gym.adjustRest(id, 15) // nothing running: nothing to change
        assertThat(dao.workout(id)!!.restEndsAt).isNull()
    }

    @Test
    fun swapKeepsDoneSetsAndMovesTheRestToTheNewExercise() = runTest {
        val (id, rows) = startWithBench()
        gym.setReps(rows[0].id, 8)
        gym.completeSet(rows[0].id)
        val dumbbell = gym.searchExercises("dumbbell bench press", setOf(Equipment.DUMBBELL), Muscle.CHEST).first().id
        gym.addExercise(id, benchId()) // a later exercise that must shift down

        gym.replaceExercise(id, 0, dumbbell)

        val all = sets(id)
        assertThat(all.filter { it.position == 0 }.map { it.exerciseName to it.done }.distinct()).containsExactly("Barbell Bench Press - Medium Grip" to true)
        assertThat(all.filter { it.position == 1 }.map { it.exerciseId }.distinct()).containsExactly(dumbbell)
        assertThat(all.filter { it.position == 1 }).hasSize(2)
        assertThat(all.filter { it.position == 2 }.map { it.exerciseId }.distinct()).containsExactly(benchId())

        gym.skipExercise(id, 1)
        assertThat(sets(id).none { it.position == 1 }).isTrue()
    }
}
