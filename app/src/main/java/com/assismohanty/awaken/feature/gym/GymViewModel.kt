package com.assismohanty.awaken.feature.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.catalog.Exercise
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.domain.ActiveWorkout
import com.assismohanty.awaken.core.domain.FinishResult
import com.assismohanty.awaken.core.domain.GymOverview
import com.assismohanty.awaken.core.domain.GymTracker
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The exercise picker sheet: search text, equipment chips and an optional muscle from the body map. */
data class ExercisePicker(
    val query: String = "",
    val equipment: Set<Equipment> = emptySet(),
    val muscle: Muscle? = null,
    val results: List<Exercise> = emptyList(),
    val showCustomForm: Boolean = false,
)

/** Transient UI on top of the data: sheets, prompts and the last notice. */
data class GymFlow(
    val picker: ExercisePicker? = null,
    val detail: Exercise? = null,
    val confirmDiscard: Boolean = false,
    val routinePrompt: Boolean = false,
    val notice: GymNotice? = null,
    /** Last session's sets per exercise id, for the "previous" column. */
    val previous: Map<String, List<WorkoutSetEntity>> = emptyMap(),
)

enum class GymNotice { FINISHED, DISCARDED_EMPTY, ROUTINE_SAVED }

data class GymUiState(
    val loading: Boolean = true,
    val overview: GymOverview? = null,
    val active: ActiveWorkout? = null,
    val now: Instant = Instant.EPOCH,
    val picker: ExercisePicker? = null,
    val detail: Exercise? = null,
    val confirmDiscard: Boolean = false,
    val routinePrompt: Boolean = false,
    val notice: GymNotice? = null,
    val previous: Map<String, List<WorkoutSetEntity>> = emptyMap(),
)

sealed interface GymEvent {
    data object StartEmpty : GymEvent
    data class StartRoutine(val routineId: String, val name: String) : GymEvent
    data class DeleteRoutine(val routineId: String) : GymEvent
    data class DeleteWorkout(val workoutId: String) : GymEvent

    data object OpenPicker : GymEvent
    data object ClosePicker : GymEvent
    data class Query(val text: String) : GymEvent
    data class ToggleEquipment(val equipment: Equipment) : GymEvent
    data class PickMuscle(val muscle: Muscle?) : GymEvent
    data class AddExercise(val exerciseId: String) : GymEvent
    data class ShowExercise(val exercise: Exercise) : GymEvent
    data object CloseExercise : GymEvent
    data object OpenCustomForm : GymEvent
    data object CloseCustomForm : GymEvent
    data class CreateCustom(val name: String, val equipment: Equipment, val primary: Set<Muscle>, val secondary: Set<Muscle>) : GymEvent

    data class UpdateSet(val set: WorkoutSetEntity, val weightKg: Double, val reps: Int, val done: Boolean) : GymEvent
    data class AddSet(val position: Int) : GymEvent
    data class RemoveSet(val setId: String) : GymEvent
    data class RemoveExercise(val position: Int) : GymEvent

    data object Finish : GymEvent
    data object AskDiscard : GymEvent
    data object DismissDiscard : GymEvent
    data object ConfirmDiscard : GymEvent
    data object AskSaveRoutine : GymEvent
    data object DismissSaveRoutine : GymEvent
    data class SaveRoutine(val name: String) : GymEvent
    data object DismissNotice : GymEvent
}

@HiltViewModel
class GymViewModel @Inject constructor(
    private val gym: GymTracker,
    private val clock: Clock,
) : ViewModel() {

    private val flow = MutableStateFlow(GymFlow())

    /** One tick a second for the session timer, only while the screen is subscribed. */
    private val ticks = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<GymUiState> = combine(
        gym.observeOverview(),
        gym.observeActive(),
        ticks,
        flow,
    ) { overview, active, now, f ->
        GymUiState(
            loading = false,
            overview = overview,
            active = active,
            now = now,
            picker = f.picker,
            detail = f.detail,
            confirmDiscard = f.confirmDiscard,
            routinePrompt = f.routinePrompt,
            notice = f.notice,
            previous = f.previous,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GymUiState())

    private var lastLoadedPrevious: Set<String> = emptySet()

    init {
        // Keep the "previous" column in step with the exercises in the running session.
        viewModelScope.launch {
            gym.observeActive().collect { active ->
                val ids = active?.exercises?.map { it.exerciseId }?.toSet().orEmpty()
                if (ids == lastLoadedPrevious) return@collect
                lastLoadedPrevious = ids
                val previous = ids.associateWith { gym.previousSets(it) }
                flow.update { it.copy(previous = previous) }
            }
        }
    }

    fun onEvent(event: GymEvent) {
        when (event) {
            GymEvent.StartEmpty -> launch { gym.start(name = "") }
            is GymEvent.StartRoutine -> launch { gym.start(event.name, event.routineId) }
            is GymEvent.DeleteRoutine -> launch { gym.deleteRoutine(event.routineId) }
            is GymEvent.DeleteWorkout -> launch { gym.deleteWorkout(event.workoutId) }

            GymEvent.OpenPicker -> {
                flow.update { it.copy(picker = ExercisePicker()) }
                refreshPicker()
            }
            GymEvent.ClosePicker -> flow.update { it.copy(picker = null) }
            is GymEvent.Query -> updatePicker { it.copy(query = event.text) }
            is GymEvent.ToggleEquipment -> updatePicker { p ->
                p.copy(equipment = if (event.equipment in p.equipment) p.equipment - event.equipment else p.equipment + event.equipment)
            }
            is GymEvent.PickMuscle -> updatePicker { p -> p.copy(muscle = if (p.muscle == event.muscle) null else event.muscle) }
            is GymEvent.AddExercise -> launch {
                val workoutId = gym.activeWorkoutId() ?: gym.start(name = "")
                gym.addExercise(workoutId, event.exerciseId)
                flow.update { it.copy(picker = null, detail = null) }
            }
            is GymEvent.ShowExercise -> flow.update { it.copy(detail = event.exercise) }
            GymEvent.CloseExercise -> flow.update { it.copy(detail = null) }
            GymEvent.OpenCustomForm -> updatePicker(refresh = false) { it.copy(showCustomForm = true) }
            GymEvent.CloseCustomForm -> updatePicker(refresh = false) { it.copy(showCustomForm = false) }
            is GymEvent.CreateCustom -> launch {
                if (event.name.isBlank() || event.primary.isEmpty()) return@launch
                val exercise = gym.createCustomExercise(event.name, event.equipment, event.primary, event.secondary)
                val workoutId = gym.activeWorkoutId() ?: gym.start(name = "")
                gym.addExercise(workoutId, exercise.id)
                flow.update { it.copy(picker = null) }
            }

            is GymEvent.UpdateSet -> launch { gym.updateSet(event.set, event.weightKg, event.reps, event.done) }
            is GymEvent.AddSet -> withActive { gym.addSet(it, event.position) }
            is GymEvent.RemoveSet -> launch { gym.removeSet(event.setId) }
            is GymEvent.RemoveExercise -> withActive { gym.removeExercise(it, event.position) }

            GymEvent.Finish -> withActive { workoutId ->
                val notice = when (gym.finish(workoutId)) {
                    is FinishResult.Finished -> GymNotice.FINISHED
                    FinishResult.DiscardedEmpty -> GymNotice.DISCARDED_EMPTY
                }
                flow.update { it.copy(notice = notice) }
            }
            GymEvent.AskDiscard -> flow.update { it.copy(confirmDiscard = true) }
            GymEvent.DismissDiscard -> flow.update { it.copy(confirmDiscard = false) }
            GymEvent.ConfirmDiscard -> withActive { workoutId ->
                gym.discard(workoutId)
                flow.update { it.copy(confirmDiscard = false) }
            }
            GymEvent.AskSaveRoutine -> flow.update { it.copy(routinePrompt = true) }
            GymEvent.DismissSaveRoutine -> flow.update { it.copy(routinePrompt = false) }
            is GymEvent.SaveRoutine -> withActive { workoutId ->
                if (event.name.isBlank()) return@withActive
                val saved = gym.saveAsRoutine(workoutId, event.name) != null
                flow.update { it.copy(routinePrompt = false, notice = if (saved) GymNotice.ROUTINE_SAVED else it.notice) }
            }
            GymEvent.DismissNotice -> flow.update { it.copy(notice = null) }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun withActive(block: suspend (workoutId: String) -> Unit) = launch {
        gym.activeWorkoutId()?.let { block(it) }
    }

    private fun updatePicker(refresh: Boolean = true, change: (ExercisePicker) -> ExercisePicker) {
        flow.update { f -> f.picker?.let { f.copy(picker = change(it)) } ?: f }
        if (refresh) refreshPicker()
    }

    private fun refreshPicker() = launch {
        val picker = flow.value.picker ?: return@launch
        val results = gym.searchExercises(picker.query, picker.equipment, picker.muscle)
        // Only apply if the filters haven't moved on while searching.
        flow.update { f ->
            val now = f.picker
            if (now != null && now.query == picker.query && now.equipment == picker.equipment && now.muscle == picker.muscle) {
                f.copy(picker = now.copy(results = results))
            } else {
                f
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 1_000L
    }
}
