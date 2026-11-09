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
import com.assismohanty.awaken.core.domain.PersonalRecord
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
import com.assismohanty.awaken.core.domain.GymAnswers
import com.assismohanty.awaken.core.domain.GymPlanner
import com.assismohanty.awaken.core.domain.RoutineDetail
import com.assismohanty.awaken.core.domain.WorkoutSummary
import com.assismohanty.awaken.core.domain.WeekDay
import com.assismohanty.awaken.engine.body.DayPlan
import com.assismohanty.awaken.engine.body.ExerciseAlternatives
import com.assismohanty.awaken.engine.body.StepIncrement
import com.assismohanty.awaken.core.domain.CompleteResult
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.engine.body.PlanFit
import com.assismohanty.awaken.engine.body.TrainingMath
import com.assismohanty.awaken.engine.body.BenchmarkData
import com.assismohanty.awaken.engine.body.Standing
import com.assismohanty.awaken.engine.body.StandingRules
import com.assismohanty.awaken.engine.body.StrengthLevel
import com.assismohanty.awaken.engine.body.WeekSchedule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.core.work.RestScheduler
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.RestRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** The exercise picker sheet: search text, equipment chips and an optional muscle from the body map. */
data class ExercisePicker(
    val query: String = "",
    val equipment: Set<Equipment> = emptySet(),
    val muscle: Muscle? = null,
    val results: List<Exercise> = emptyList(),
    val showCustomForm: Boolean = false,
    /** Reading the library from Gym home: no Add to workout and no custom exercise. */
    val browse: Boolean = false,
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
    val plates: PlatesFlow? = null,
    val settingsOpen: Boolean = false,
    val history: HistoryFlow? = null,
    val strength: StrengthUi? = null,
    // Gym home (GD §18.1): which day is open and which sheet is showing.
    val selectedDate: LocalDate? = null,
    val planDetailId: String? = null,
    val planWeekdays: Set<Int> = emptySet(),
    val editorId: String? = null,
    val menuId: String? = null,
    val assignId: String? = null,
    val deleteId: String? = null,
    /** The picker adds to this routine instead of the running workout. */
    val pickerRoutineId: String? = null,
    // The running workout (GD §18.2)
    val labels: Map<String, ExerciseLabel> = emptyMap(),
    val numberPad: NumberPadUi? = null,
    /** The ⋯ menu is open for the exercise at this position. */
    val workoutMenu: Int? = null,
    val replace: ReplaceUi? = null,
    val confirmFinish: Boolean = false,
    val lastSetSheet: Boolean = false,
    /** The rest screen was minimised for the rest that ends at this time; a new rest shows it again. */
    val restMinimisedFor: Instant? = null,
)

/** The field the number pad is editing. */
enum class PadField { WEIGHT, REPS, HOLD }

data class NumberPadUi(val setId: String, val field: PadField)

/** How an exercise of the running workout is shown: a clean name, its grip or setup, and how far the stepper moves (GD §18.2). */
data class ExerciseLabel(val name: String, val variant: String?, val stepKg: Double)

data class ExerciseChoice(val id: String, val name: String, val variant: String?)

/** "Swap with" for one exercise: harder, similar and easier library alternatives; an empty list shows no option (GD §18.2). */
data class ReplaceUi(
    val position: Int,
    val name: String,
    val easier: List<ExerciseChoice>,
    val similar: List<ExerciseChoice>,
    val harder: List<ExerciseChoice>,
) {
    val isEmpty: Boolean get() = easier.isEmpty() && similar.isEmpty() && harder.isEmpty()
}

/** The plans that fit the hunter and the exercise names they refer to. */
data class PlansData(val fits: List<PlanFit>, val names: Map<String, String>, val loaded: Boolean = true)

/** A plan open in its detail sheet; [weekdays] is the choice for a single-day plan. */
data class PlanDetailUi(val fit: PlanFit, val weekdays: Set<Int>, val names: Map<String, String>)

/** Everything the Gym home shows (GD §18.1). */
data class GymHomeUi(
    val week: List<WeekDay>,
    val selected: LocalDate,
    val selectedRoutine: RoutineDetail?,
    val selectedFinished: List<WorkoutSummary> = emptyList(),
    /** The selected day is later this week, so it can trade places with today. */
    val canSwap: Boolean = false,
    val routines: List<RoutineDetail> = emptyList(),
    val plans: List<PlanFit> = emptyList(),
    val answers: GymAnswers = GymAnswers(),
    val planDetail: PlanDetailUi? = null,
    val editor: RoutineDetail? = null,
    val menu: RoutineDetail? = null,
    val assign: RoutineDetail? = null,
    val delete: RoutineDetail? = null,
    val names: Map<String, String> = emptyMap(),
    /** False while the plans are still being worked out, so the empty message does not flash. */
    val plansLoaded: Boolean = true,
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
    val benchmarks: BenchmarkData? = null,
    /** The pointer on the distribution curve, 0..1; null until it is touched (it then rests on the hunter's own mark). */
    val pointer: Float? = null,
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
    val standing: StandingUi? = null,
)

/** The best result of an exercise and where it stands (GD §18.3). [standing] is null when the exercise has no credible source. */
data class StandingUi(
    val bestSet: LoggedSet?,
    val bestE1rmKg: Double?,
    val standing: Standing?,
    /** Where the pointer sits along the curve, 0..1, and what it reads there. */
    val pointer: Float = 0f,
    val marker: Float = 0f,
    val pointerKg: Double? = null,
    val pointerPercentile: Double? = null,
)

/** One row of the strength tracker: the best set of an exercise, and its level when it is benchmarked. */
data class StrengthRow(val record: PersonalRecord, val level: StrengthLevel?)

/** The strength tracker: rows grouped by primary muscle (null = no muscle known), in the order muscles were first met. */
data class StrengthUi(val groups: List<Pair<Muscle?, List<StrengthRow>>>)

enum class GymNotice { FINISHED, DISCARDED_EMPTY, ROUTINE_SAVED, PLAN_ADDED }

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
    val prefs: GymPrefs = GymPrefs(),
    val plates: PlatesUi? = null,
    val settingsOpen: Boolean = false,
    val history: HistoryUi? = null,
    val strength: StrengthUi? = null,
    val home: GymHomeUi? = null,
    val labels: Map<String, ExerciseLabel> = emptyMap(),
    val numberPad: NumberPadUi? = null,
    val workoutMenu: Int? = null,
    val replace: ReplaceUi? = null,
    val confirmFinish: Boolean = false,
    val lastSetSheet: Boolean = false,
    val restMinimisedFor: Instant? = null,
    /** The running workout was started on an earlier game-day: offer to finish or discard it instead of carrying on. */
    val staleWorkout: Boolean = false,
)

sealed interface GymEvent {
    // Gym home: week, plans, routines, settings answers (GD §18.1)
    data class SelectDay(val date: LocalDate) : GymEvent
    data object SwapWithToday : GymEvent
    data class OpenPlan(val planId: String) : GymEvent
    data object ClosePlan : GymEvent
    data class TogglePlanDay(val weekday: Int) : GymEvent
    data class AddPlan(val planId: String) : GymEvent
    data object NewRoutine : GymEvent
    data class OpenEditor(val routineId: String) : GymEvent
    data object CloseEditor : GymEvent
    data class RenameRoutine(val routineId: String, val name: String) : GymEvent
    data class OpenRoutineMenu(val routineId: String) : GymEvent
    data object CloseRoutineMenu : GymEvent
    data class DuplicateRoutine(val routineId: String, val copyName: String) : GymEvent
    data class MoveRoutine(val routineId: String, val delta: Int) : GymEvent
    data class OpenAssign(val routineId: String) : GymEvent
    data object CloseAssign : GymEvent
    data class SetRoutineDay(val routineId: String, val weekday: Int, val on: Boolean) : GymEvent
    data class AskDeleteRoutine(val routineId: String) : GymEvent
    data object DismissDeleteRoutine : GymEvent
    data class EditorAddExercise(val routineId: String) : GymEvent
    data class EditorRemove(val routineId: String, val position: Int) : GymEvent
    data class EditorMove(val routineId: String, val position: Int, val delta: Int) : GymEvent
    data class EditorUpdate(val routineId: String, val position: Int, val sets: Int, val repMin: Int?, val repMax: Int?, val restSec: Int?) : GymEvent
    data class EditorSuperset(val routineId: String, val position: Int) : GymEvent
    data class UpdateAnswers(val change: (GymAnswers) -> GymAnswers) : GymEvent

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
    data object OpenStrength : GymEvent
    data object CloseStrength : GymEvent
    /** The pointer on the distribution curve moved to [fraction] (0..1) along it. */
    data class MovePointer(val fraction: Float) : GymEvent
    data class PickHistoryRange(val range: HistoryRange) : GymEvent
    data class SetExerciseRange(val range: RepRange?) : GymEvent
    data class SetExerciseIncrement(val kg: Double?) : GymEvent
    data object ResetExercisePrefs : GymEvent

    data object StartEmpty : GymEvent
    data class StartRoutine(val routineId: String, val name: String) : GymEvent
    data class DeleteRoutine(val routineId: String) : GymEvent
    data class DeleteWorkout(val workoutId: String) : GymEvent

    data object OpenPicker : GymEvent

    /** The exercise library from Gym home, for reading only. */
    data object OpenBrowse : GymEvent
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

    // The running workout: field-level writes, never a whole-row snapshot (GD §18.2)
    data class SetWeight(val setId: String, val kg: Double) : GymEvent
    data class SetReps(val setId: String, val reps: Int) : GymEvent
    data class SetHold(val setId: String, val seconds: Int) : GymEvent
    data class CompleteSet(val setId: String) : GymEvent
    data class ReopenSet(val setId: String) : GymEvent
    data class AdjustRest(val deltaSec: Int) : GymEvent
    data object MinimiseRest : GymEvent
    data object ShowRest : GymEvent
    data class OpenNumberPad(val setId: String, val field: PadField) : GymEvent
    data object CloseNumberPad : GymEvent
    data class OpenWorkoutMenu(val position: Int) : GymEvent
    data object CloseWorkoutMenu : GymEvent
    data class OpenReplace(val position: Int) : GymEvent
    data object CloseReplace : GymEvent
    data class ReplaceWith(val position: Int, val exerciseId: String) : GymEvent
    data class SkipExercise(val position: Int) : GymEvent
    data class AddSet(val position: Int) : GymEvent
    data class RemoveLastSet(val position: Int) : GymEvent
    data class RemoveExercise(val position: Int) : GymEvent
    data object ToggleMode : GymEvent
    data object AskFinish : GymEvent
    data object DismissFinish : GymEvent
    data object OpenLastSet : GymEvent
    data object CloseLastSet : GymEvent

    data object Finish : GymEvent
    data object AskDiscard : GymEvent
    data object DismissDiscard : GymEvent
    data object ConfirmDiscard : GymEvent
    data object AskSaveRoutine : GymEvent
    data object DismissSaveRoutine : GymEvent
    data class SaveRoutine(val name: String) : GymEvent
    data object DismissNotice : GymEvent
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
    private val planner: GymPlanner,
) : ViewModel() {

    private val flow = MutableStateFlow(GymFlow())

    /** One tick a second for the session timer, only while the screen is subscribed. */
    private val ticks = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }

    private val core: Flow<GymUiState> = combine(
        gym.observeOverview(),
        gym.observeActive(),
        ticks,
        flow,
        combine(gym.observePrefs(), planner.observeAnswers(), ::Pair),
    ) { overview, active, now, f, (prefs, answers) ->
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
            prefs = prefs,
            plates = f.plates?.let { PlatesUi(it.target, it.unit, prefs.setup(it.unit), if (it.target > 0.0) PlateMath.plan(it.target, prefs.setup(it.unit)) else null) },
            settingsOpen = f.settingsOpen,
            history = f.history?.toUi(prefs, answers),
            strength = f.strength,
            labels = f.labels,
            numberPad = f.numberPad,
            workoutMenu = f.workoutMenu,
            replace = f.replace,
            confirmFinish = f.confirmFinish,
            lastSetSheet = f.lastSetSheet,
            restMinimisedFor = f.restMinimisedFor,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val week: Flow<List<WeekDay>> = ticks.map { TrainingMath.weekStart(gym.today()) }.distinctUntilChanged().flatMapLatest { planner.observeWeek(it) }

    private val answers: Flow<GymAnswers> = planner.observeAnswers()

    @OptIn(ExperimentalCoroutinesApi::class)
    // The plans take a moment the first time (the catalog has to load); the home shows without them meanwhile.
    private val plans: Flow<PlansData> = answers.mapLatest { a ->
        PlansData(planner.availablePlans(a), planner.infos().mapValues { it.value.name })
    }.onStart { emit(PlansData(emptyList(), emptyMap(), loaded = false)) }

    private val home: Flow<GymHomeUi?> = combine(week, planner.observeRoutines(), answers, plans, flow) { days, routines, a, planData, f ->
        val today = days.firstOrNull { it.today }?.date ?: days.firstOrNull()?.date ?: return@combine null
        val selected = f.selectedDate?.takeIf { d -> days.any { it.date == d } } ?: today
        val day = days.first { it.date == selected }
        val routineId = (day.plan as? DayPlan.Workout)?.routineId
        val todayHolds = days.first { it.date == today }.plan
        GymHomeUi(
            week = days,
            selected = selected,
            selectedRoutine = routines.firstOrNull { it.id == routineId },
            canSwap = selected.isAfter(today) && WeekSchedule.sameWeek(selected, today) && (day.plan != DayPlan.Rest || todayHolds != DayPlan.Rest),
            routines = routines,
            plans = planData.fits,
            answers = a,
            planDetail = f.planDetailId?.let { id -> planData.fits.firstOrNull { it.plan.id == id } }?.let { PlanDetailUi(it, f.planWeekdays, planData.names) },
            editor = routines.firstOrNull { it.id == f.editorId },
            menu = routines.firstOrNull { it.id == f.menuId },
            assign = routines.firstOrNull { it.id == f.assignId },
            delete = routines.firstOrNull { it.id == f.deleteId },
            names = planData.names,
            plansLoaded = planData.loaded,
        )
    }

    val uiState: StateFlow<GymUiState> = combine(core, home) { state, h ->
        // A finished workout on the selected day shows under it; the overview already holds every summary.
        val today = h?.week?.firstOrNull { it.today }?.date
        state.copy(
            home = h?.copy(selectedFinished = state.overview?.history.orEmpty().filter { it.gameDate == h.selected }),
            staleWorkout = today != null && state.active?.workout?.gameDate?.isBefore(today) == true,
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
                flow.update { it.copy(previous = previous, recordHistory = records, labels = labelsFor(active)) }
                reloadHints(ids)
            }
        }
    }

    /** Clean names, variants and the stepper size of every exercise in the running workout (workouts stay in kg, so the step is in kg). */
    private suspend fun labelsFor(active: ActiveWorkout?): Map<String, ExerciseLabel> {
        val exercises = active?.exercises ?: return emptyMap()
        val infos = planner.infos()
        val prefs = gym.prefs()
        return exercises.associate { e ->
            val info = infos[e.exerciseId]
            val own = gym.progression(e.exerciseId, info?.primary.orEmpty()).takeIf { it.customIncrement }?.incrementKg
            val step = own ?: info?.let { StepIncrement.step(it.equipment, PlateUnit.KG, prefs.kg) } ?: StepIncrement.step(Equipment.OTHER, PlateUnit.KG, prefs.kg)
            e.exerciseId to ExerciseLabel(info?.name ?: e.name, info?.variant, step)
        }
    }

    private suspend fun openReplace(position: Int) {
        val exercise = uiState.value.active?.exercises?.firstOrNull { it.position == position } ?: return
        val infos = planner.infos()
        val info = infos[exercise.exerciseId]
        val answers = answers.first()
        val alternatives = info?.let { ExerciseAlternatives.of(it, infos.values, answers.gear, answers.injuries) }
        fun choices(list: List<com.assismohanty.awaken.engine.body.ExerciseInfo>?) = list.orEmpty().map { ExerciseChoice(it.id, it.name, it.variant) }
        val ui = ReplaceUi(position, info?.name ?: exercise.name, choices(alternatives?.easier), choices(alternatives?.similar), choices(alternatives?.harder))
        flow.update { it.copy(replace = ui) }
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

    private fun HistoryFlow.standingUi(answers: GymAnswers): StandingUi? {
        val logged = sessions.flatMap { it.sets }.filter { it.done }
        val bestSet = TrainingMath.bestSet(logged)
        val e1rm = bestSet?.let(TrainingMath::estimatedOneRepMax)
        val bestReps = logged.filter { it.weightKg <= 0.0 }.maxOfOrNull { it.reps }
        val age = answers.birthYear?.let { today.year - it }
        val standing = benchmarks?.let { StandingRules.standing(it, exerciseId, e1rm, bestReps, null, answers.sex, answers.bodyWeightKg, age) }
        if (bestSet == null && standing == null) return null
        val percentile = standing as? Standing.Percentile ?: return StandingUi(bestSet, e1rm, standing)
        val marker = StandingRules.markerFraction(percentile)
        val at = pointer ?: marker
        val (kg, p) = StandingRules.atPointer(percentile, at)
        return StandingUi(bestSet, e1rm, standing, pointer = at, marker = marker, pointerKg = kg, pointerPercentile = p)
    }

    private fun HistoryFlow.toUi(prefs: GymPrefs, answers: GymAnswers): HistoryUi {
        return HistoryUi(
            standing = standingUi(answers),
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
            is GymEvent.SelectDay -> flow.update { it.copy(selectedDate = event.date) }
            GymEvent.SwapWithToday -> launch {
                val selected = uiState.value.home?.selected ?: return@launch
                planner.swapWithToday(selected)
                flow.update { it.copy(selectedDate = null) }
            }
            is GymEvent.OpenPlan -> launch {
                val a = answers.first()
                flow.update { it.copy(planDetailId = event.planId, planWeekdays = com.assismohanty.awaken.core.domain.defaultPlanWeekdays(a.trainingDays)) }
            }
            GymEvent.ClosePlan -> flow.update { it.copy(planDetailId = null) }
            is GymEvent.TogglePlanDay -> flow.update { f ->
                f.copy(planWeekdays = if (event.weekday in f.planWeekdays) f.planWeekdays - event.weekday else f.planWeekdays + event.weekday)
            }
            is GymEvent.AddPlan -> launch {
                planner.addPlan(event.planId, answers.first(), flow.value.planWeekdays)
                flow.update { it.copy(planDetailId = null, notice = GymNotice.PLAN_ADDED) }
            }
            GymEvent.NewRoutine -> launch {
                val id = planner.createRoutine("")
                flow.update { it.copy(editorId = id) }
            }
            is GymEvent.OpenEditor -> flow.update { it.copy(editorId = event.routineId, menuId = null) }
            GymEvent.CloseEditor -> flow.update { it.copy(editorId = null) }
            is GymEvent.RenameRoutine -> launch { planner.renameRoutine(event.routineId, event.name) }
            is GymEvent.OpenRoutineMenu -> flow.update { it.copy(menuId = event.routineId) }
            GymEvent.CloseRoutineMenu -> flow.update { it.copy(menuId = null) }
            is GymEvent.DuplicateRoutine -> launch {
                planner.duplicateRoutine(event.routineId, event.copyName)
                flow.update { it.copy(menuId = null) }
            }
            is GymEvent.MoveRoutine -> launch { planner.moveRoutine(event.routineId, event.delta) }
            is GymEvent.OpenAssign -> flow.update { it.copy(assignId = event.routineId, menuId = null) }
            GymEvent.CloseAssign -> flow.update { it.copy(assignId = null) }
            is GymEvent.SetRoutineDay -> launch {
                val current = uiState.value.home?.routines?.firstOrNull { it.id == event.routineId }?.weekdays.orEmpty()
                planner.setWeekdays(event.routineId, if (event.on) current + event.weekday else current - event.weekday)
            }
            is GymEvent.AskDeleteRoutine -> flow.update { it.copy(deleteId = event.routineId, menuId = null) }
            GymEvent.DismissDeleteRoutine -> flow.update { it.copy(deleteId = null) }
            is GymEvent.EditorAddExercise -> {
                flow.update { it.copy(picker = ExercisePicker(), pickerRoutineId = event.routineId) }
                refreshPicker()
            }
            is GymEvent.EditorRemove -> launch { planner.removeExercise(event.routineId, event.position) }
            is GymEvent.EditorMove -> launch { planner.moveExercise(event.routineId, event.position, event.delta) }
            is GymEvent.EditorUpdate -> launch { planner.updateExercise(event.routineId, event.position, event.sets, event.repMin, event.repMax, event.restSec) }
            is GymEvent.EditorSuperset -> launch { planner.toggleSuperset(event.routineId, event.position) }
            is GymEvent.UpdateAnswers -> launch { planner.saveAnswers(event.change(answers.first())) }

            GymEvent.StartEmpty -> launch { gym.start(name = "") }
            is GymEvent.StartRoutine -> launch { gym.start(event.name, event.routineId) }
            is GymEvent.DeleteRoutine -> launch {
                planner.deleteRoutine(event.routineId)
                flow.update { it.copy(deleteId = null, editorId = if (it.editorId == event.routineId) null else it.editorId) }
            }
            is GymEvent.DeleteWorkout -> launch { gym.deleteWorkout(event.workoutId) }

            GymEvent.OpenPicker -> {
                flow.update { it.copy(picker = ExercisePicker()) }
                refreshPicker()
            }
            GymEvent.OpenBrowse -> {
                flow.update { it.copy(picker = ExercisePicker(browse = true)) }
                refreshPicker()
            }
            GymEvent.ClosePicker -> flow.update { it.copy(picker = null, pickerRoutineId = null) }
            is GymEvent.Query -> updatePicker { it.copy(query = event.text) }
            is GymEvent.ToggleEquipment -> updatePicker { p ->
                p.copy(equipment = if (event.equipment in p.equipment) p.equipment - event.equipment else p.equipment + event.equipment)
            }
            is GymEvent.PickMuscle -> updatePicker { p -> p.copy(muscle = if (p.muscle == event.muscle) null else event.muscle) }
            is GymEvent.AddExercise -> launch {
                val routineId = flow.value.pickerRoutineId
                if (routineId != null) {
                    planner.addExercise(routineId, event.exerciseId, answers.first())
                    flow.update { it.copy(picker = null, detail = null, pickerRoutineId = null) }
                    return@launch
                }
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
                val routineId = flow.value.pickerRoutineId
                if (routineId != null) {
                    planner.addExercise(routineId, exercise.id, answers.first())
                    flow.update { it.copy(picker = null, pickerRoutineId = null) }
                    return@launch
                }
                val workoutId = gym.activeWorkoutId() ?: gym.start(name = "")
                gym.addExercise(workoutId, exercise.id)
                flow.update { it.copy(picker = null) }
            }

            is GymEvent.SetWeight -> ordered { gym.setWeight(event.setId, event.kg) }
            is GymEvent.SetReps -> ordered { gym.setReps(event.setId, event.reps) }
            is GymEvent.SetHold -> ordered { gym.setHold(event.setId, event.seconds) }
            is GymEvent.CompleteSet -> ordered {
                // The rest screen shows again for the new rest.
                if (gym.completeSet(event.setId) == CompleteResult.Completed) flow.update { it.copy(restMinimisedFor = null) }
            }
            is GymEvent.ReopenSet -> ordered { gym.reopenSet(event.setId) }
            is GymEvent.AdjustRest -> ordered { gym.activeWorkoutId()?.let { gym.adjustRest(it, event.deltaSec) } }
            GymEvent.MinimiseRest -> launch {
                val ends = gym.activeWorkoutId()?.let { gym.workout(it) }?.restEndsAt
                flow.update { it.copy(restMinimisedFor = ends) }
            }
            GymEvent.ShowRest -> flow.update { it.copy(restMinimisedFor = null) }
            is GymEvent.OpenNumberPad -> flow.update { it.copy(numberPad = NumberPadUi(event.setId, event.field)) }
            GymEvent.CloseNumberPad -> flow.update { it.copy(numberPad = null) }
            is GymEvent.OpenWorkoutMenu -> flow.update { it.copy(workoutMenu = event.position) }
            GymEvent.CloseWorkoutMenu -> flow.update { it.copy(workoutMenu = null) }
            is GymEvent.OpenReplace -> launch { openReplace(event.position) }
            GymEvent.CloseReplace -> flow.update { it.copy(replace = null) }
            is GymEvent.ReplaceWith -> withActive {
                gym.replaceExercise(it, event.position, event.exerciseId)
                flow.update { f -> f.copy(replace = null, workoutMenu = null) }
                lastLoadedPrevious = emptySet()
            }
            is GymEvent.SkipExercise -> withActive {
                gym.skipExercise(it, event.position)
                flow.update { f -> f.copy(workoutMenu = null) }
            }
            is GymEvent.AddSet -> withActive {
                gym.addSet(it, event.position)
                flow.update { f -> f.copy(workoutMenu = null) }
            }
            is GymEvent.RemoveLastSet -> withActive {
                gym.removeLastSet(it, event.position)
                flow.update { f -> f.copy(workoutMenu = null) }
            }
            is GymEvent.RemoveExercise -> withActive {
                gym.removeExercise(it, event.position)
                flow.update { f -> f.copy(workoutMenu = null) }
            }
            GymEvent.ToggleMode -> withActive { id ->
                val coached = gym.workout(id)?.mode == WorkoutEntity.MODE_COACHED
                gym.setMode(id, if (coached) WorkoutEntity.MODE_LIST else WorkoutEntity.MODE_COACHED)
                flow.update { f -> f.copy(workoutMenu = null) }
            }
            GymEvent.AskFinish -> flow.update { it.copy(confirmFinish = true) }
            GymEvent.DismissFinish -> flow.update { it.copy(confirmFinish = false) }
            GymEvent.OpenLastSet -> flow.update { it.copy(lastSetSheet = true) }
            GymEvent.CloseLastSet -> flow.update { it.copy(lastSetSheet = false) }

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
                            benchmarks = planner.benchmarks(),
                        ),
                    )
                }
            }
            GymEvent.CloseHistory -> flow.update { it.copy(history = null) }
            GymEvent.CloseStrength -> flow.update { it.copy(strength = null) }
            is GymEvent.MovePointer -> flow.update { f -> f.history?.let { f.copy(history = it.copy(pointer = event.fraction.coerceIn(0f, 1f))) } ?: f }
            GymEvent.OpenStrength -> launch {
                val data = planner.benchmarks()
                val answers = planner.observeAnswers().first()
                val age = answers.birthYear?.let { gym.today().year - it }
                val records = uiState.value.overview?.records.orEmpty()
                val rows = records.map { record ->
                    val level = (StandingRules.standing(data, record.exerciseId, record.estimatedMaxKg, null, null, answers.sex, answers.bodyWeightKg, age) as? Standing.Percentile)?.level
                    StrengthRow(record, level) to gym.exercise(record.exerciseId)?.primary?.firstOrNull()
                }
                val groups = rows.groupBy({ it.second }, { it.first }).map { (muscle, list) -> muscle to list.sortedByDescending { it.record.estimatedMaxKg } }
                flow.update { it.copy(strength = StrengthUi(groups)) }
            }
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
                        it.copy(summaryId = workoutId, notice = null, summaryRecords = result.records, confirmFinish = false, restMinimisedFor = null)
                    }
                    FinishResult.DiscardedEmpty -> flow.update { it.copy(notice = GymNotice.DISCARDED_EMPTY, confirmFinish = false) }
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
            is GymEvent.SaveRoutine -> launch {
                if (event.name.isBlank()) return@launch
                val workoutId = flow.value.summaryId ?: gym.activeWorkoutId() ?: return@launch
                val saved = gym.saveAsRoutine(workoutId, event.name) != null
                flow.update { it.copy(routinePrompt = false, notice = if (saved) GymNotice.ROUTINE_SAVED else it.notice) }
            }
            GymEvent.DismissNotice -> flow.update { it.copy(notice = null) }
            GymEvent.SkipRest -> withActive { gym.skipRest(it) }
            GymEvent.DismissSummary -> flow.update { it.copy(summaryId = null, summaryRecords = emptyList()) }
        }
    }

    private fun handleRestDue(workoutId: String) = launch {
        val end = gym.finishRestIfDue(workoutId) ?: return@launch
        if (end.chime && gym.restAlerts().sound) chimePlayer.play(uiPrefs.prefs.first().chime)
    }

    /** Workout edits run one after another in the order they were made, so a quick weight, reps and Complete never overtake each other. */
    private val commands = kotlinx.coroutines.sync.Mutex()

    private fun ordered(block: suspend () -> Unit) {
        viewModelScope.launch { commands.withLock { block() } }
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
