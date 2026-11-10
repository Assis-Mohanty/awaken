package com.assismohanty.awaken.feature.gym

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.Standing
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The strength tracker and an exercise's "Where do I stand?" through the view model (GD §18.3). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GymStrengthViewModelTest {
    private val graph = TestGraph()
    private lateinit var viewModel: GymViewModel
    private val store = ViewModelStore()
    private var collector: Job? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        viewModel = ViewModelProvider.create(store, viewModelFactory { initializer { GymViewModel(graph.gym, graph.clock, NoRestScheduler, SilentChimePlayer, graph.uiPrefs, graph.planner, graph.finishWorkout, graph.deleteWorkout) } })[GymViewModel::class]
        collector = viewModel.uiState.onEach { }.launchIn(CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() {
        // Stop the state flow and the view model's own scope before Main is reset, or a late tick races the next test's setMain.
        collector?.cancel()
        store.clear()
        Dispatchers.resetMain()
        graph.close()
    }

    private fun state(predicate: (GymUiState) -> Boolean): GymUiState = runBlocking {
        withTimeout(10_000) { viewModel.uiState.first { !it.loading && predicate(it) } }
    }

    /** One finished bench session: 100 kg for 5 reps. Returns the exercise id. */
    private fun logBench(): String = runBlocking {
        val exerciseId = graph.gym.searchExercises("barbell bench press medium grip", setOf(Equipment.BARBELL), Muscle.CHEST).first().id
        val workoutId = graph.gym.start("Push")
        graph.gym.addExercise(workoutId, exerciseId)
        val set = graph.gym.observeActive().first()!!.exercises.single().sets.first()
        graph.gym.setWeight(set.id, 100.0)
        graph.gym.setReps(set.id, 5)
        graph.gym.completeSet(set.id)
        graph.gym.finish(workoutId)
        exerciseId
    }

    private fun answers(sex: String?, weight: Double?) = runBlocking {
        graph.seedHunter()
        graph.planner.saveAnswers(graph.planner.observeAnswers().first().copy(sex = sex, bodyWeightKg = weight))
    }

    @Test
    fun theTrackerGroupsBestLiftsByMuscleAndShowsALevelOnceBodyWeightAndSexAreSet() {
        val id = logBench()
        answers("M", 80.0)
        state { it.overview?.records?.isNotEmpty() == true && it.home?.answers?.sex == "M" }

        viewModel.onEvent(GymEvent.OpenStrength)

        val groups = state { it.strength != null }.strength!!.groups
        assertThat(groups.map { it.first }).contains(Muscle.CHEST)
        val row = groups.flatMap { it.second }.single { it.record.exerciseId == id }
        assertThat(row.level).isNotNull()

        viewModel.onEvent(GymEvent.CloseStrength)
        assertThat(state { it.strength == null }.strength).isNull()
    }

    @Test
    fun withoutBodyWeightTheTrackerStillListsTheLiftButHasNoLevel() {
        val id = logBench()
        state { it.overview?.records?.isNotEmpty() == true }

        viewModel.onEvent(GymEvent.OpenStrength)

        val row = state { it.strength != null }.strength!!.groups.flatMap { it.second }.single { it.record.exerciseId == id }
        assertThat(row.level).isNull()
    }

    @Test
    fun anExerciseWithoutAProfileAsksForBodyWeightAndSex() {
        val id = logBench()
        state { it.overview?.records?.isNotEmpty() == true }

        viewModel.onEvent(GymEvent.OpenHistory(id, "Bench press"))

        val standing = state { it.history?.standing != null }.history!!.standing!!
        assertThat(standing.bestE1rmKg).isWithin(0.1).of(100.0 * (1 + 5 / 30.0))
        assertThat(standing.standing).isEqualTo(Standing.NeedsProfile)
    }

    @Test
    fun theCurvePointerStartsOnTheHuntersMarkAndFollowsADrag() {
        val id = logBench()
        answers("M", 80.0)
        state { it.home?.answers?.sex == "M" }

        viewModel.onEvent(GymEvent.OpenHistory(id, "Bench press"))
        val start = state { it.history?.standing?.standing is Standing.Percentile }.history!!.standing!!
        assertThat(start.pointer).isEqualTo(start.marker)
        assertThat(start.pointerKg).isWithin(0.1).of(start.bestE1rmKg!!)

        viewModel.onEvent(GymEvent.MovePointer(1f))
        val end = state { it.history?.standing?.pointer == 1f }.history!!.standing!!
        assertThat(end.pointerPercentile).isEqualTo(99.0)
        assertThat(end.pointerKg!!).isGreaterThan(start.pointerKg!!)
    }
}
