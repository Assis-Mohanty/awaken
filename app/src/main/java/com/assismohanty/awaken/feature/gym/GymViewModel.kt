package com.assismohanty.awaken.feature.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.audio.ChimePlayer
import com.assismohanty.awaken.core.data.catalog.Exercise
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.domain.ActiveWorkout
import com.assismohanty.awaken.core.domain.ExerciseProgression
import com.assismohanty.awaken.core.domain.ExerciseRecords
import com.assismohanty.awaken.core.domain.GymPrefs
import com.assismohanty.awaken.core.domain.toLogged
import com.assismohanty.awaken.engine.body.ExerciseHistory
import com.assismohanty.awaken.engine.body.ExerciseSession
import com.assismohanty.awaken.engine.body.HistoryMetric
import com.assismohanty.awaken.engine.body.HistoryRange
import com.assismohanty.awaken.engine.body.LoggedSet
import com.assismohanty.awaken.engine.body.PlateMath
import com.assismohanty.awaken.engine.body.PlatePlan
import com.assismohanty.awaken.engine.body.PlateSetup
import com.assismohanty.awaken.engine.body.PlateUnit
import com.assismohanty.awaken.engine.body.ProgressionHint
import com.assismohanty.awaken.engine.body.ProgressionRules
import com.assismohanty.awaken.engine.body.RecordRules
import com.assismohanty.awaken.engine.body.RepRange
import com.assismohanty.awaken.engine.body.TrendPoint
import com.assismohanty.awaken.engine.body.WorkingSets
import com.assismohanty.awaken.core.domain.FinishResult
import com.assismohanty.awaken.core.domain.GymOverview
import com.assismohanty.awaken.core.domain.GymTracker
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.core.work.RestScheduler
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.RestRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
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
    /** The session just finished, shown as a summary until dismissed. */
    val summaryId: String? = null,
    /** Last session's sets per exercise id, for the "previous" column. */
    val previous: Map<String, List<WorkoutSetEntity>> = emptyMap(),
    /** Hint above set 1 per exercise id (GD §16.6). */
    val hints: Map<String, HintUi> = emptyMap(),
    /** Earlier finished sessions per exercise id: what a set is compared with for a record (GD §16.7). */
    val recordHistory: Map<String, List<List<LoggedSet>>> = emptyMap(),
    val summaryRecords: List<ExerciseRecords> = emptyList(),
    val summaryUnlocked: List<String> = emptyList(),
    val plates: PlatesFlow? = null,
    val settingsOpen: Boolean = false,
    val history: HistoryFlow? = null,
)

/** What the last session did and what to try now. */
data class HintUi(val last: WorkingSets, val hint: ProgressionHint)

/** The plate calculator's inputs: a target in the chosen unit. */
data class PlatesFlow(val target: Double, val unit: PlateUnit)

/** The plate calculator with its result. [plan] is null until a target is typed. */
data class PlatesUi(val target: Double, val unit: PlateUnit, val setup: PlateSetup, val plan: PlatePlan?)

/** An exercise's history screen inputs. */
data class HistoryFlow(
    val exerciseId: String,
    val name: String,
    val sessions: List<ExerciseSession>,
    val range: HistoryRange,
    val progression: ExerciseProgression,
    val today: java.time.LocalDate,
)

/** One trend card of the history screen. */
data class TrendCardUi(val metric: HistoryMetric, val points: List<TrendPoint>)

/** The exercise history screen (GD §16.9). [sessions] are newest first. */
data class HistoryUi(
    val exerciseId: String,
    val name: String,
    val range: HistoryRange,
    val cards: List<TrendCardUi>,
    val sessions: List<ExerciseSession>,
    val progression: ExerciseProgression,
    val defaultRange: RepRange,
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
    val summaryId: String? = null,
    val previous: Map<String, List<WorkoutSetEntity>> = emptyMap(),
    val hints: Map<String, HintUi> = emptyMap(),
    /** Ids of set rows that broke a personal record, for the "New record" chip. */
    val prSetIds: Set<String> = emptySet(),
    val summaryRecords: List<ExerciseRecords> = emptyList(),
    val summaryUnlocked: List<String> = emptyList(),
    val prefs: GymPrefs = GymPrefs(),
    val plates: PlatesUi? = null,
    val settingsOpen: Boolean = false,
    val history: HistoryUi? = null,
)

sealed interface GymEvent {
    // Plate calculator (GD §16.8), Gym settings, exercise history (GD §16.9)
    /** Opens the calculator; [initialKg] prefills the target when the unit is kg. */
    data class OpenPlates(val initialKg: Double?) : GymEvent
    data object ClosePlates : GymEvent
    data class PlateTarget(val target: Double) : GymEvent
    data class PlateUnitPicked(val unit: PlateUnit) : GymEvent
    data object OpenGymSettings : GymEvent
    data object CloseGymSettings : GymEvent
    data class SetBar(val unit: PlateUnit, val weight: Double) : GymEvent
    data class SetPlateOwned(val unit: PlateUnit, val plate: Double, val owned: Boolean) : GymEvent
    data class SetDefaultRepRange(val range: RepRange) : GymEvent
    data class OpenHistory(val exerciseId: String, val name: String) : GymEvent
    data object CloseHistory : GymEvent
    data class PickHistoryRange(val range: HistoryRange) : GymEvent
    data class SetExerciseRange(val range: RepRange?) : GymEvent
    data class SetExerciseIncrement(val kg: Double?) : GymEvent
    data object ResetExercisePrefs : GymEvent

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
    data class StepRest(val position: Int, val current: Int?, val up: Boolean) : GymEvent
    data object SkipRest : GymEvent
    data object DismissSummary : GymEvent
}

@HiltViewModel
class GymViewModel @Inject constructor(
    private val gym: GymTracker,
    private val clock: Clock,
    private val restScheduler: RestScheduler,
    private val chimePlayer: ChimePlayer,
    private val uiPrefs: UiPrefsRepository,
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
        gym.observePrefs(),
    ) { overview, active, now, f, prefs ->
        val prSetIds = active?.exercises.orEmpty().flatMap { exercise ->
            val flags = RecordRules.setFlags(f.recordHistory[exercise.exerciseId].orEmpty(), exercise.sets.map { it.toLogged() })
            exercise.sets.zip(flags).filter { (_, types) -> types.isNotEmpty() }.map { (set, _) -> set.id }
        }.toSet()
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
            summaryId = f.summaryId,
            previous = f.previous,
            hints = f.hints,
            prSetIds = prSetIds,
            summaryRecords = f.summaryRecords,
            summaryUnlocked = f.summaryUnlocked,
            prefs = prefs,
            plates = f.plates?.let { PlatesUi(it.target, it.unit, prefs.setup(it.unit), if (it.target > 0.0) PlateMath.plan(it.target, prefs.setup(it.unit)) else null) },
            settingsOpen = f.settingsOpen,
            history = f.history?.toUi(prefs),
        )
    }.onEach { state ->
        val workout = state.active?.workout
        val endsAt = workout?.restEndsAt
        if (workout != null && endsAt != null && !state.now.isBefore(endsAt)) handleRestDue(workout.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GymUiState())

    private var lastLoadedPrevious: Set<String> = emptySet()

    init {
        // Keep the "previous" column in step with the exercises in the running session.
        viewModelScope.launch {
            gym.observeActive().collect { active ->
                restScheduler.sync(active?.workout)
                val ids = active?.exercises?.map { it.exerciseId }?.toSet().orEmpty()
                if (ids == lastLoadedPrevious) return@collect
                lastLoadedPrevious = ids
                val previous = ids.associateWith { gym.previousSets(it) }
                val records = ids.associateWith { gym.recordHistory(it) }
                flow.update { it.copy(previous = previous, recordHistory = records) }
                reloadHints(ids)
            }
        }
    }

    /** Hints depend on each exercise's last session and its overrides, so they reload when either can have changed. */
    private suspend fun reloadHints(ids: Set<String> = lastLoadedPrevious) {
        val hints = ids.mapNotNull { id ->
            val hint = gym.hint(id) ?: return@mapNotNull null
            val working = ProgressionRules.workingSets(gym.previousSets(id).map { it.toLogged() }) ?: return@mapNotNull null
            id to HintUi(working, hint)
        }.toMap()
        flow.update { it.copy(hints = hints) }
    }

    private fun HistoryFlow.toUi(prefs: GymPrefs): HistoryUi {
        return HistoryUi(
            exerciseId = exerciseId,
            name = name,
            range = range,
            cards = HistoryMetric.entries.map { TrendCardUi(it, ExerciseHistory.series(sessions, it, range, today)) },
            sessions = sessions.sortedByDescending { it.date },
            progression = progression,
            defaultRange = prefs.repRange,
        )
    }

    private fun updatePrefs(change: (GymPrefs) -> GymPrefs) = launch {
        gym.savePrefs(change(gym.prefs()))
        reloadHints()
    }

    private fun refreshHistoryProgression() = launch {
        val open = flow.value.history ?: return@launch
        val primary = gym.exercise(open.exerciseId)?.primary.orEmpty()
        val progression = gym.progression(open.exerciseId, primary)
        flow.update { f -> f.history?.let { f.copy(history = it.copy(progression = progression)) } ?: f }
        reloadHints()
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

            is GymEvent.OpenPlates -> launch {
                val unit = gym.prefs().unit
                val target = if (unit == PlateUnit.KG) event.initialKg ?: 0.0 else 0.0
                flow.update { it.copy(plates = PlatesFlow(target, unit)) }
            }
            GymEvent.ClosePlates -> flow.update { it.copy(plates = null) }
            is GymEvent.PlateTarget -> flow.update { f -> f.plates?.let { f.copy(plates = it.copy(target = event.target)) } ?: f }
            is GymEvent.PlateUnitPicked -> {
                flow.update { f -> f.plates?.let { f.copy(plates = it.copy(unit = event.unit)) } ?: f }
                updatePrefs { it.copy(unit = event.unit) }
            }
            GymEvent.OpenGymSettings -> flow.update { it.copy(settingsOpen = true) }
            GymEvent.CloseGymSettings -> flow.update { it.copy(settingsOpen = false) }
            is GymEvent.SetBar -> updatePrefs { p ->
                if (event.unit == PlateUnit.KG) p.copy(kg = p.kg.copy(barWeight = event.weight)) else p.copy(lb = p.lb.copy(barWeight = event.weight))
            }
            is GymEvent.SetPlateOwned -> updatePrefs { p ->
                val current = p.setup(event.unit)
                val plates = if (event.owned) current.plates + event.plate else current.plates - event.plate
                val next = current.copy(plates = plates)
                if (event.unit == PlateUnit.KG) p.copy(kg = next) else p.copy(lb = next)
            }
            is GymEvent.SetDefaultRepRange -> updatePrefs { it.copy(repRange = event.range) }
            is GymEvent.OpenHistory -> launch {
                val primary = gym.exercise(event.exerciseId)?.primary.orEmpty()
                flow.update {
                    it.copy(
                        history = HistoryFlow(
                            event.exerciseId, event.name, gym.history(event.exerciseId), HistoryRange.ALL, gym.progression(event.exerciseId, primary), gym.today(),
                        ),
                    )
                }
            }
            GymEvent.CloseHistory -> flow.update { it.copy(history = null) }
            is GymEvent.PickHistoryRange -> flow.update { f -> f.history?.let { f.copy(history = it.copy(range = event.range)) } ?: f }
            is GymEvent.SetExerciseRange -> launch {
                val open = flow.value.history ?: return@launch
                val current = gym.progression(open.exerciseId, gym.exercise(open.exerciseId)?.primary.orEmpty())
                gym.setExercisePref(open.exerciseId, event.range, if (current.customIncrement) current.incrementKg else null)
                refreshHistoryProgression()
            }
            is GymEvent.SetExerciseIncrement -> launch {
                val open = flow.value.history ?: return@launch
                val current = gym.progression(open.exerciseId, gym.exercise(open.exerciseId)?.primary.orEmpty())
                gym.setExercisePref(open.exerciseId, if (current.customRange) current.range else null, event.kg)
                refreshHistoryProgression()
            }

            GymEvent.ResetExercisePrefs -> launch {
                val open = flow.value.history ?: return@launch
                gym.setExercisePref(open.exerciseId, null, null)
                refreshHistoryProgression()
            }

            GymEvent.Finish -> withActive { workoutId ->
                when (val result = gym.finish(workoutId)) {
                    is FinishResult.Finished -> flow.update {
                        it.copy(summaryId = workoutId, notice = null, summaryRecords = result.records, summaryUnlocked = result.unlocked)
                    }
                    FinishResult.DiscardedEmpty -> flow.update { it.copy(notice = GymNotice.DISCARDED_EMPTY) }
                }
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
            is GymEvent.StepRest -> withActive { workoutId ->
                gym.setRest(workoutId, event.position, if (event.up) RestRules.stepUp(event.current) else RestRules.stepDown(event.current))
            }
            GymEvent.SkipRest -> withActive { gym.skipRest(it) }
            GymEvent.DismissSummary -> flow.update { it.copy(summaryId = null, summaryRecords = emptyList(), summaryUnlocked = emptyList()) }
        }
    }

    private fun handleRestDue(workoutId: String) = launch {
        val end = gym.finishRestIfDue(workoutId) ?: return@launch
        if (end.chime) chimePlayer.play(uiPrefs.prefs.first().chime)
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
