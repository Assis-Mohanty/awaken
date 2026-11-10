package com.assismohanty.awaken.feature.gym

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.audio.ChimeChoice
import com.assismohanty.awaken.core.audio.ChimePlayer
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.work.RestScheduler
import com.assismohanty.awaken.engine.body.DayPlan
import com.assismohanty.awaken.engine.body.Gear
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Gym home state: the selected day, a swap, a plan added from its sheet, the editor and the settings answers (GD §18.1). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GymHomeViewModelTest {
    private val graph = TestGraph()
    private lateinit var viewModel: GymViewModel
    private val store = ViewModelStore()
    private var collector: Job? = null
    private val monday = LocalDate.of(2026, 10, 12)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        // A Monday, so a later day exists in the same week.
        graph.clock.advance(Duration.ofDays(-6))
        viewModel = ViewModelProvider.create(store, viewModelFactory { initializer { GymViewModel(graph.gym, graph.clock, NoRest, Silent, graph.uiPrefs, graph.planner, graph.finishWorkout, graph.deleteWorkout) } })[GymViewModel::class]
        // The state only runs while somebody collects it.
        collector = viewModel.uiState.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() {
        // Stop the state flow and the view model's own scope before Main is reset, or a late tick races the next test's setMain.
        collector?.cancel()
        store.clear()
        Dispatchers.resetMain()
        graph.close()
    }

    private fun home(predicate: (GymHomeUi) -> Boolean = { true }): GymHomeUi = runBlocking {
        kotlinx.coroutines.withTimeout(10_000) { viewModel.uiState.first { s -> s.home?.let(predicate) == true }.home!! }
    }

    @Test
    fun theWeekStartsOnTodayWithNothingPlanned() {
        val h = home()

        assertThat(h.week).hasSize(7)
        assertThat(h.week.first().date).isEqualTo(monday)
        assertThat(h.selected).isEqualTo(monday)
        assertThat(h.week.all { it.plan == DayPlan.Rest }).isTrue()
        assertThat(h.selectedRoutine).isNull()
        assertThat(h.routines).isEmpty()
    }

    @Test
    fun addingAPlanFromItsSheetSchedulesItAndShowsTodaysRoutine() {
        viewModel.onEvent(GymEvent.UpdateAnswers { it.copy(gear = Gear.entries.toSet()) })
        home { it.answers.gearKnown && it.plans.any { fit -> fit.plan.id == "ppl3" } }

        viewModel.onEvent(GymEvent.OpenPlan("ppl3"))
        assertThat(home { it.planDetail != null }.planDetail?.fit?.plan?.id).isEqualTo("ppl3")
        viewModel.onEvent(GymEvent.AddPlan("ppl3"))

        val h = home { it.routines.size == 3 && it.selectedRoutine != null && it.planDetail == null }
        assertThat(viewModel.uiState.value.notice).isEqualTo(GymNotice.PLAN_ADDED)
        // Monday is the first day of the plan, and today.
        assertThat(h.selectedRoutine?.name).isEqualTo("Push / Pull / Legs, 3 days · Push")
        assertThat(h.canSwap).isFalse()
    }

    @Test
    fun aRestDayLaterInTheWeekCanSwapWithToday() {
        val id = runBlocking { graph.planner.createRoutine("Push") }
        runBlocking { graph.planner.setWeekdays(id, setOf(1)) }
        home { it.routines.size == 1 && it.selectedRoutine != null }

        viewModel.onEvent(GymEvent.SelectDay(monday.plusDays(2)))
        val rest = home { it.selected == monday.plusDays(2) }
        assertThat(rest.selectedRoutine).isNull()
        assertThat(rest.canSwap).isTrue()

        viewModel.onEvent(GymEvent.SwapWithToday)
        val swapped = home { h -> h.week.first { it.date == monday.plusDays(2) }.plan is DayPlan.Workout }
        assertThat(swapped.week.first { it.today }.plan).isEqualTo(DayPlan.Rest)
        // The swap clears the selection back to today.
        assertThat(swapped.selected).isEqualTo(monday)
    }

    @Test
    fun theEditorAddsAnExerciseThroughThePickerAndRenames() {
        viewModel.onEvent(GymEvent.NewRoutine)
        val editing = home { it.editor != null }.editor!!
        val bench = runBlocking { graph.gym.searchExercises("barbell bench press medium grip", emptySet(), null).first().id }

        viewModel.onEvent(GymEvent.EditorAddExercise(editing.id))
        assertThat(viewModel.uiState.value.picker).isNotNull()
        viewModel.onEvent(GymEvent.AddExercise(bench))
        viewModel.onEvent(GymEvent.RenameRoutine(editing.id, "Chest day"))

        val h = home { it.editor?.work?.size == 1 && it.editor?.name == "Chest day" }
        assertThat(h.editor?.work?.single()?.sets).isEqualTo(3)
        assertThat(viewModel.uiState.value.picker).isNull()
        // The exercise went to the routine, not to a workout.
        assertThat(runBlocking { graph.gym.activeWorkoutId() }).isNull()

        viewModel.onEvent(GymEvent.AskDeleteRoutine(editing.id))
        assertThat(home { it.delete != null }.delete?.id).isEqualTo(editing.id)
        viewModel.onEvent(GymEvent.DeleteRoutine(editing.id))
        assertThat(home { it.routines.isEmpty() }.editor).isNull()
    }

    @Test
    fun changingAnAnswerFromSettingsRefiltersThePlans() {
        viewModel.onEvent(GymEvent.UpdateAnswers { it.copy(gear = emptySet()) })
        assertThat(home { it.answers.gearKnown && it.plansLoaded }.plans.map { it.plan.id }).containsExactly("bw_home3", "core")

        viewModel.onEvent(GymEvent.UpdateAnswers { it.copy(gear = Gear.entries.toSet()) })
        assertThat(home { h -> h.plans.size > 2 }.plans.map { it.plan.id }).contains("chest")
    }

    private object NoRest : RestScheduler {
        override fun sync(workout: WorkoutEntity?) = Unit
    }

    private object Silent : ChimePlayer {
        override fun play(choice: ChimeChoice) = Unit
    }
}
