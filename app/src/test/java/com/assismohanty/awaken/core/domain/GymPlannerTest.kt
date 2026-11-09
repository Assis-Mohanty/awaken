package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.engine.body.DayPlan
import com.assismohanty.awaken.engine.body.Gear
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Plans, routines and the weekly schedule (GD §18.1) through the real planner and Room. */
@RunWith(AndroidJUnit4::class)
class GymPlannerTest {
    private val graph = TestGraph()
    private val planner = graph.planner
    private val full = GymAnswers(fitness = FitnessLevel.ADVANCED, gear = Gear.entries.toSet(), gearKnown = true)

    @After
    fun tearDown() = graph.close()

    private suspend fun routines() = planner.observeRoutines().first()

    @Test
    fun aMultiDayPlanBecomesOneRoutinePerDayOnItsOwnWeekdays() = runTest {
        val added = checkNotNull(planner.addPlan("ppl3", full, weekdays = emptySet()))

        assertThat(added.routineIds).hasSize(3)
        assertThat(added.weekdays).containsExactly(1, 3, 5)
        val all = routines()
        assertThat(all.map { it.name }).containsExactly("Push / Pull / Legs, 3 days · Push", "Push / Pull / Legs, 3 days · Pull", "Push / Pull / Legs, 3 days · Legs").inOrder()
        assertThat(all.map { it.weekdays }).containsExactly(setOf(1), setOf(3), setOf(5)).inOrder()
        assertThat(all.all { it.sourcePlanId == "ppl3" }).isTrue()
        val push = all.first()
        assertThat(push.warmup).isNotEmpty()
        assertThat(push.work.first().sets).isEqualTo(3)
        assertThat(push.work.first().repMin).isNotNull()
    }

    @Test
    fun aSingleDayPlanGoesOnTheWeekdaysTheHunterPicked() = runTest {
        val added = checkNotNull(planner.addPlan("chest_tri", full, weekdays = setOf(2, 5)))

        assertThat(added.routineIds).hasSize(1)
        assertThat(routines().single().weekdays).containsExactly(2, 5)
        // The superset from the plan came across.
        assertThat(routines().single().work.mapNotNull { it.superset }).containsExactly("A", "A")
    }

    @Test
    fun aPlanThatDoesNotFitIsNotAdded() = runTest {
        // No equipment at all: the barbell plan is hidden, so it cannot be added either.
        assertThat(planner.addPlan("chest", GymAnswers(gearKnown = true), weekdays = setOf(1))).isNull()
        assertThat(routines()).isEmpty()
        assertThat(planner.availablePlans(GymAnswers(gearKnown = true)).map { it.plan.id }).containsExactly("bw_home3", "core")
    }

    @Test
    fun takingAWeekdayFromAnotherRoutineFreesItThere() = runTest {
        val a = planner.createRoutine("A")
        val b = planner.createRoutine("B")
        planner.setWeekdays(a, setOf(1, 3))
        planner.setWeekdays(b, setOf(3))

        val byName = routines().associateBy { it.name }
        assertThat(byName.getValue("A").weekdays).containsExactly(1)
        assertThat(byName.getValue("B").weekdays).containsExactly(3)
        planner.setWeekdays(a, emptySet())
        assertThat(routines().first { it.id == a }.weekdays).isEmpty()
    }

    @Test
    fun theWeekShowsTheScheduleAndItRepeatsNextWeek() = runTest {
        val routine = planner.createRoutine("Push")
        val today = graph.today() // a Sunday
        val monday = LocalDate.of(2026, 10, 12)
        planner.setWeekdays(routine, setOf(today.dayOfWeek.value))

        assertThat(planner.observeWeek(monday).first().first { it.today }.plan).isEqualTo(DayPlan.Workout(routine))
        assertThat(planner.observeWeek(monday).first().count { it.plan is DayPlan.Workout }).isEqualTo(1)
        assertThat(planner.observeWeek(monday.plusDays(7)).first().last().plan).isEqualTo(DayPlan.Workout(routine))
    }

    @Test
    fun swapWithTodayMovesAWorkoutAcrossTheWeek() = runTest {
        // Move the clock to Monday so a later day exists in the same week.
        graph.clock.advance(java.time.Duration.ofDays(-6))
        val routine = planner.createRoutine("Push")
        val monday = LocalDate.of(2026, 10, 12)
        assertThat(graph.today()).isEqualTo(monday)
        planner.setWeekdays(routine, setOf(1))
        val wednesday = monday.plusDays(2)

        planner.swapWithToday(wednesday)

        val week = planner.observeWeek(monday).first()
        assertThat(week.first { it.date == wednesday }.plan).isEqualTo(DayPlan.Workout(routine))
        assertThat(week.first { it.today }.plan).isEqualTo(DayPlan.Rest)
        // Next Monday is untouched: it still repeats.
        assertThat(planner.observeWeek(monday.plusDays(7)).first().first().plan).isEqualTo(DayPlan.Workout(routine))
    }

    @Test
    fun deletingARoutineClearsItsDays() = runTest {
        val id = planner.createRoutine("Push")
        planner.setWeekdays(id, setOf(2))
        planner.deleteRoutine(id)

        assertThat(routines()).isEmpty()
        assertThat(graph.db.gymDao().weekSlots()).isEmpty()
    }

    @Test
    fun duplicateCopiesExercisesAndMoveReorders() = runTest {
        val id = planner.createRoutine("Push")
        val bench = graph.gym.searchExercises("barbell bench press medium grip", emptySet(), null).first().id
        planner.addExercise(id, bench, full)
        planner.setWeekdays(id, setOf(1))

        val copy = checkNotNull(planner.duplicateRoutine(id, "Push copy"))

        val all = routines()
        assertThat(all.map { it.name }).containsExactly("Push", "Push copy").inOrder()
        assertThat(all.first { it.id == copy }.work).hasSize(1)
        assertThat(all.first { it.id == copy }.weekdays).isEmpty()
        planner.moveRoutine(copy, -1)
        assertThat(routines().map { it.name }).containsExactly("Push copy", "Push").inOrder()
    }

    @Test
    fun theEditorKeepsTargetsInRangeAndLinksSupersets() = runTest {
        val id = planner.createRoutine("Arms")
        val ids = graph.gym.searchExercises("curl", emptySet(), null).map { it.id }.take(3)
        ids.forEach { planner.addExercise(id, it, full) }
        val start = routines().single().work
        assertThat(start).hasSize(3)
        assertThat(start.all { it.sets == 3 && it.repMin == 8 && it.repMax == 12 }).isTrue()

        planner.updateExercise(id, 0, sets = 4, repMin = 6, repMax = 8, restSec = 90)
        planner.updateExercise(id, 1, sets = 0, repMin = 6, repMax = 8, restSec = 90) // no sets: ignored
        planner.updateExercise(id, 1, sets = 3, repMin = 12, repMax = 8, restSec = 90) // min above max: ignored
        val edited = routines().single().work
        assertThat(edited[0].sets).isEqualTo(4)
        assertThat(edited[0].repMax).isEqualTo(8)
        assertThat(edited[1].sets).isEqualTo(3)
        assertThat(edited[1].repMin).isEqualTo(8)

        planner.toggleSuperset(id, 0)
        assertThat(routines().single().work.map { it.superset }).containsExactly("A", "A", null).inOrder()
        planner.toggleSuperset(id, 1) // joins the third into the group
        assertThat(routines().single().work.map { it.superset }).containsExactly("A", "A", "A").inOrder()
        planner.toggleSuperset(id, 0) // unlinks the first from the second
        assertThat(routines().single().work.map { it.superset }.first()).isNull()

        planner.moveExercise(id, 2, -1)
        planner.removeExercise(id, 0)
        assertThat(routines().single().work).hasSize(2)
        assertThat(routines().single().work.map { it.position }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun startingARoutineCopiesItsTargetsAndSectionsOntoTheSets() = runTest {
        planner.addPlan("chest_tri", full, weekdays = setOf(1))
        val routine = routines().single()

        val workoutId = graph.gym.start(routine.name, routine.id)

        val workout = graph.db.gymDao().workout(workoutId)!!
        assertThat(workout.routineId).isEqualTo(routine.id)
        assertThat(workout.mode).isEqualTo("COACHED")
        val sets = graph.db.gymDao().sets(workoutId)
        assertThat(sets.map { it.section }.distinct()).containsExactly(RoutineExerciseEntity.SECTION_WARMUP, RoutineExerciseEntity.SECTION_WORK)
        val first = sets.first { it.section == RoutineExerciseEntity.SECTION_WORK }
        assertThat(first.repMin).isEqualTo(8)
        assertThat(first.reps).isEqualTo(8) // no history yet: the target's low end
        assertThat(sets.mapNotNull { it.superset }.distinct()).containsExactly("A")
    }

    @Test
    fun savingAnswersKeepsThePlatesAndWritesBodyWeightToTheDietProfileToo() = runTest {
        graph.seedHunter()
        graph.db.dietDao().upsertProfile(
            com.assismohanty.awaken.core.data.db.entity.NutritionProfileEntity(
                sex = com.assismohanty.awaken.engine.body.Sex.MALE, birthYear = 1999, heightCm = 180.0, weightKg = 75.0,
                activity = com.assismohanty.awaken.engine.body.ActivityLevel.MODERATE, goal = com.assismohanty.awaken.engine.body.WeightGoal.MAINTAIN,
                overridesJson = "{}", updatedAt = graph.clock.now(),
            ),
        )
        graph.gym.savePrefs(GymPrefs(unit = com.assismohanty.awaken.engine.body.PlateUnit.LB))

        planner.saveAnswers(full.copy(bodyWeightKg = 82.0, sex = "F", restCompoundSec = 150, coached = false, trainingDays = setOf(1, 3)))

        val saved = planner.observeAnswers().first()
        assertThat(saved.fitness).isEqualTo(FitnessLevel.ADVANCED)
        assertThat(saved.gear).containsExactlyElementsIn(Gear.entries)
        assertThat(saved.sex).isEqualTo("F")
        assertThat(saved.restCompoundSec).isEqualTo(150)
        assertThat(saved.coached).isFalse()
        assertThat(saved.trainingDays).containsExactly(1, 3)
        assertThat(saved.bodyWeightKg).isEqualTo(82.0)
        assertThat(graph.db.dietDao().profile()?.weightKg).isEqualTo(82.0)
        assertThat(graph.gym.prefs().unit).isEqualTo(com.assismohanty.awaken.engine.body.PlateUnit.LB)
        // A later plate change keeps the answers.
        graph.gym.savePrefs(graph.gym.prefs().copy(unit = com.assismohanty.awaken.engine.body.PlateUnit.KG))
        assertThat(planner.observeAnswers().first().sex).isEqualTo("F")
    }

    @Test
    fun withNoStoredEquipmentTheProfileStandsIn() = runTest {
        graph.seedHunter()
        graph.db.hunterDao().getProfile()?.let { graph.db.hunterDao().upsertProfile(it.copy(ownedEquipment = setOf("dumbbells"))) }

        val answers = planner.observeAnswers().first()

        assertThat(answers.gearKnown).isFalse()
        assertThat(answers.gear).containsExactly(Gear.DUMBBELLS, Gear.BENCH)
    }
}
