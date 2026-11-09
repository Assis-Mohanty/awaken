package com.assismohanty.awaken.feature.gym

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Gear
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/** The running workout through the view model: completing, the minimum rest, the rest screen, swap, finish and mode (GD §18.2). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GymWorkoutViewModelTest {
    private val graph = TestGraph()
    private lateinit var viewModel: GymViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        viewModel = GymViewModel(graph.gym, graph.clock, NoRestScheduler, SilentChimePlayer, graph.uiPrefs, graph.planner)
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        graph.close()
    }

    private fun state(predicate: (GymUiState) -> Boolean): GymUiState = runBlocking {
        withTimeout(10_000) { viewModel.uiState.first { !it.loading && predicate(it) } }
    }

    private suspend fun benchId() = graph.gym.searchExercises("barbell bench press medium grip", setOf(Equipment.BARBELL), Muscle.CHEST).first().id

    /** A started workout with a bench exercise of three sets and a 120 s rest. */
    private fun startBench(): String = runBlocking {
        val id = graph.gym.start("Push")
        graph.gym.addExercise(id, benchId())
        graph.gym.addSet(id, 0)
        graph.gym.addSet(id, 0)
        graph.gym.setRest(id, 0, 120)
        id
    }

    @Test
    fun completingASetStartsTheRestAndTheRestScreenCanBeMinimisedAndShownAgain() {
        startBench()
        val set = state { it.active != null }.active!!.exercises.single().sets.first()
        viewModel.onEvent(GymEvent.SetReps(set.id, 10))
        viewModel.onEvent(GymEvent.CompleteSet(set.id))

        val resting = state { it.active?.workout?.restEndsAt != null }
        assertThat(resting.active?.workout?.restTotalSec).isEqualTo(120)
        assertThat(resting.restMinimisedFor).isNull()
        viewModel.onEvent(GymEvent.AdjustRest(15))
        assertThat(state { it.active?.workout?.restTotalSec == 135 }.active?.workout?.restTotalSec).isEqualTo(135)

        viewModel.onEvent(GymEvent.MinimiseRest)
        val ends = state { it.restMinimisedFor != null }.restMinimisedFor
        assertThat(ends).isEqualTo(resting.active?.workout?.restEndsAt?.plusSeconds(15))
        viewModel.onEvent(GymEvent.ShowRest)
        assertThat(state { it.restMinimisedFor == null }.restMinimisedFor).isNull()
        viewModel.onEvent(GymEvent.SkipRest)
        assertThat(state { it.active?.workout?.restEndsAt == null }.active?.workout?.restEndsAt).isNull()
    }

    @Test
    fun aSetTooSoonAfterTheLastIsNotCompleted() {
        startBench()
        val sets = state { it.active != null }.active!!.exercises.single().sets
        viewModel.onEvent(GymEvent.SetReps(sets[0].id, 10))
        viewModel.onEvent(GymEvent.SetReps(sets[1].id, 10))
        viewModel.onEvent(GymEvent.CompleteSet(sets[0].id))
        state { s -> s.active?.exercises?.single()?.sets?.first()?.done == true }
        graph.clock.advance(Duration.ofSeconds(5))

        viewModel.onEvent(GymEvent.CompleteSet(sets[1].id))
        // Commands run in order, so once this later one has landed the refused Complete has been handled.
        viewModel.onEvent(GymEvent.SetReps(sets[2].id, 7))
        state { s -> s.active?.exercises?.single()?.sets?.get(2)?.reps == 7 }

        val after = state { it.active != null }.active!!.exercises.single().sets
        assertThat(after.map { it.done }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun swapWithOffersOnlyLibraryAlternativesAndKeepsDoneSets() {
        viewModel.onEvent(GymEvent.UpdateAnswers { it.copy(gear = Gear.entries.toSet(), gearKnown = true) })
        startBench()
        val sets = state { it.active != null }.active!!.exercises.single().sets
        viewModel.onEvent(GymEvent.SetReps(sets[0].id, 10))
        viewModel.onEvent(GymEvent.CompleteSet(sets[0].id))

        viewModel.onEvent(GymEvent.OpenReplace(0))
        val replace = state { it.replace != null }.replace!!
        assertThat(replace.name).isEqualTo("Bench press")
        val choices = replace.easier + replace.similar + replace.harder
        assertThat(choices).isNotEmpty()
        assertThat(choices.map { it.id }).doesNotContain(sets[0].exerciseId)

        viewModel.onEvent(GymEvent.ReplaceWith(0, choices.first().id))
        val after = state { s -> s.replace == null && s.active?.exercises?.size == 2 }.active!!
        assertThat(after.exercises[0].sets.map { it.done }).containsExactly(true)
        assertThat(after.exercises[1].exerciseId).isEqualTo(choices.first().id)
    }

    @Test
    fun theMenuModeSwitchFinishConfirmAndDiscardAllWork() {
        val id = startBench()
        assertThat(state { it.active != null }.active?.workout?.mode).isEqualTo(WorkoutEntity.MODE_COACHED)

        viewModel.onEvent(GymEvent.ToggleMode)
        assertThat(state { it.active?.workout?.mode == WorkoutEntity.MODE_LIST }.active?.workout?.mode).isEqualTo(WorkoutEntity.MODE_LIST)

        viewModel.onEvent(GymEvent.OpenWorkoutMenu(0))
        assertThat(state { it.workoutMenu == 0 }.workoutMenu).isEqualTo(0)
        viewModel.onEvent(GymEvent.AskFinish)
        assertThat(state { it.confirmFinish }.confirmFinish).isTrue()
        viewModel.onEvent(GymEvent.DismissFinish)
        assertThat(state { !it.confirmFinish }.confirmFinish).isFalse()

        viewModel.onEvent(GymEvent.AskDiscard)
        viewModel.onEvent(GymEvent.ConfirmDiscard)
        assertThat(state { it.active == null }.active).isNull()
        assertThat(runBlocking { graph.db.gymDao().workout(id) }).isNull()
    }

    @Test
    fun aWorkoutFromAnEarlierDayIsOfferedForFinishingNotResumedSilently() {
        startBench()
        assertThat(state { it.active != null }.staleWorkout).isFalse()

        graph.clock.advance(Duration.ofDays(1))

        assertThat(state { it.staleWorkout }.staleWorkout).isTrue()
    }

    @Test
    fun labelsUseTheCleanNameAndAStepThatFollowsTheEquipment() {
        startBench()

        val label = state { it.labels.isNotEmpty() }.labels.values.single()

        assertThat(label.name).isEqualTo("Bench press")
        assertThat(label.variant).isEqualTo("Barbell · medium grip")
        assertThat(label.stepKg).isEqualTo(2.5)
    }
}
