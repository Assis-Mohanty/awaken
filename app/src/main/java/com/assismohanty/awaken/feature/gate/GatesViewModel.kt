package com.assismohanty.awaken.feature.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.FocusSubjectEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.PomodoroSessionEntity
import com.assismohanty.awaken.core.data.repo.LastFocusStore
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.FocusGoalState
import com.assismohanty.awaken.core.domain.ObserveFocusGoalUseCase
import com.assismohanty.awaken.core.domain.PomodoroSessions
import com.assismohanty.awaken.core.domain.PomodoroStartResult
import com.assismohanty.awaken.core.domain.breakOver
import com.assismohanty.awaken.core.domain.config
import com.assismohanty.awaken.engine.PomodoroConfig
import com.assismohanty.awaken.engine.PomodoroPhase
import com.assismohanty.awaken.engine.PomodoroRules
import kotlinx.coroutines.flow.first
import com.assismohanty.awaken.core.domain.FocusLockStatus
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.domain.GATE_LABEL_MAX
import com.assismohanty.awaken.core.domain.GATE_STATS
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.core.work.GateCoordinator
import com.assismohanty.awaken.engine.FocusRules
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GatePhase
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class GateTile(val rank: GateRank, val minutes: Int, val available: Boolean)

/** The running gate as the dial shows it. */
data class ActiveGate(
    val rank: GateRank,
    val mode: GateMode = GateMode.PRESET,
    /** Focus time so far; what a stopwatch shows (counts up). */
    val elapsed: Duration = Duration.ZERO,
    val subject: String? = null,
    val stat: Stat,
    val label: String?,
    val remaining: Duration,
    val total: Duration,
    val breachUsed: Duration,
    val paused: Boolean,
    val pauseLeft: Duration,
    val canPause: Boolean,
    /** Focus Lock is switched on but its service is not running (GD 8.2): apps are not being covered. */
    val lockWarning: Boolean = false,
)

/** Picking a gate: the chosen rank, stat and optional label (GD §8). */
data class GateSetup(
    val rank: GateRank,
    val stat: Stat = Stat.INT,
    val label: String = "",
    val mode: GateMode = GateMode.PRESET,
    val minutes: Int = FocusRules.CUSTOM_DEFAULT_MINUTES,
    val subjectId: String? = null,
)

data class SubjectUi(val id: String, val name: String)

/** The Pomodoro setup window (GD §16.3): the four settings, what the rounds train and what they are labelled. */
data class PomodoroSetupUi(
    val focusMinutes: Int = PomodoroRules.DEFAULT_FOCUS,
    val shortBreakMinutes: Int = PomodoroRules.DEFAULT_SHORT_BREAK,
    val longBreakMinutes: Int = PomodoroRules.DEFAULT_LONG_BREAK,
    val rounds: Int = PomodoroRules.DEFAULT_ROUNDS,
    val stat: Stat = Stat.INT,
    val subjectId: String? = null,
) {
    val config: PomodoroConfig get() = PomodoroRules.safeConfig(focusMinutes, shortBreakMinutes, longBreakMinutes, rounds)
}

enum class PomodoroField { FOCUS, SHORT_BREAK, LONG_BREAK, ROUNDS }

/** A running Pomodoro session as the screen shows it (GD §16.3). [breakLeft] is zero once a break is over. */
data class PomodoroUi(
    val phase: PomodoroPhase,
    val round: Int,
    val rounds: Int,
    val breakLeft: Duration = Duration.ZERO,
    val breakOver: Boolean = false,
    val config: PomodoroConfig = PomodoroConfig(),
    val stat: Stat = Stat.INT,
    val subjectId: String? = null,
) {
    /** The long break ran out, or the session already moved on: nothing is left to start, only to acknowledge. */
    val finished: Boolean get() = phase == PomodoroPhase.DONE || (phase == PomodoroPhase.LONG_BREAK && breakOver)
}

data class GatesUiState(
    val loading: Boolean = true,
    val tiles: List<GateTile> = emptyList(),
    val setup: GateSetup? = null,
    val active: ActiveGate? = null,
    val confirmAbandon: Boolean = false,
    val subjects: List<SubjectUi> = emptyList(),
    val addingSubject: Boolean = false,
    val subjectDraft: String = "",
    val canAddSubject: Boolean = true,
    /** Today's focus against the daily goal and the focus streak (GD §16.4); null until a hunter exists. */
    val focusGoal: FocusGoalState? = null,
    val pomodoroSetup: PomodoroSetupUi? = null,
    val pomodoro: PomodoroUi? = null,
)

sealed interface GatesEvent {
    data object OpenPomodoro : GatesEvent
    data class PomodoroAdjust(val field: PomodoroField, val direction: Int) : GatesEvent
    data object PomodoroSkipBreak : GatesEvent
    data object PomodoroNextRound : GatesEvent
    data object PomodoroEnd : GatesEvent
    data object PomodoroDone : GatesEvent
    data object PomodoroAnother : GatesEvent

    data class Pick(val rank: GateRank) : GatesEvent
    data class PickMode(val mode: GateMode) : GatesEvent
    data class MinutesChanged(val minutes: Int) : GatesEvent
    data class SubjectPicked(val subjectId: String?) : GatesEvent
    data object StartAddSubject : GatesEvent
    data class SubjectDraftChanged(val text: String) : GatesEvent
    data object SaveSubject : GatesEvent
    data object CancelAddSubject : GatesEvent
    data class ArchiveSubject(val subjectId: String) : GatesEvent
    data object Stop : GatesEvent
    data object CloseSetup : GatesEvent
    data class StatChanged(val stat: Stat) : GatesEvent
    data class LabelChanged(val label: String) : GatesEvent
    data object Enter : GatesEvent
    data object Pause : GatesEvent
    data object Resume : GatesEvent
    data object Abandon : GatesEvent
    data object ConfirmAbandon : GatesEvent
    data object CancelAbandon : GatesEvent
    data object FixFocusLock : GatesEvent
}

private data class Base(
    val profile: com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity?,
    val run: GateRunEntity?,
    val formAndLock: Pair<FormState, Boolean>,
    val subjects: List<FocusSubjectEntity>,
    val now: Instant,
)

private data class FormState(
    val setup: GateSetup? = null,
    val pomodoro: PomodoroSetupUi? = null,
    val confirmAbandon: Boolean = false,
    val addingSubject: Boolean = false,
    val subjectDraft: String = "",
)

@HiltViewModel
class GatesViewModel @Inject constructor(
    hunterDao: HunterDao,
    gateRunDao: GateRunDao,
    private val focusDao: FocusDao,
    private val coordinator: GateCoordinator,
    private val completionCenter: CompletionCenter,
    private val clock: Clock,
    uiPrefs: UiPrefsRepository,
    focusLockStatus: FocusLockStatus,
    private val pomodoroSessions: PomodoroSessions,
    observeFocusGoal: ObserveFocusGoalUseCase,
    dayRefresher: DayRefresher,
    private val lastFocusStore: LastFocusStore,
) : ViewModel() {

    private val form = MutableStateFlow(FormState())

    private val lockMissing = combine(uiPrefs.prefs, focusLockStatus.serviceConnected) { prefs, connected -> prefs.focusLock && !connected }
    private val stopwatchRunning = MutableStateFlow(false)

    // Collected only while the screen is visible; it drives the dial and the claim at the planned end.
    private val ticks = flow {
        while (true) {
            emit(clock.now())
            // A running stopwatch shows milliseconds, so it redraws about 30 times a second; everything else once a second.
            delay(if (stopwatchRunning.value) STOPWATCH_TICK_MS else TICK_MS)
        }
    }

    private val focusGoal = observeFocusGoal(dayRefresher.ticks)
    private var breakWasOver = false

    val uiState: StateFlow<GatesUiState> =
        combine(
            combine(hunterDao.observeProfile(), gateRunDao.observeRunning(), combine(form, lockMissing) { f, l -> f to l }, focusDao.observeAll(), ticks) { profile, run, formAndLock, allSubjects, now ->
                Base(profile, run, formAndLock, allSubjects, now)
            },
            combine(focusGoal, pomodoroSessions.observe()) { goal, session -> goal to session },
        ) { base, extras ->
            val (profile, run, formAndLock, allSubjects, now) = base
            val (goal, session) = extras
            val (form, lockWarning) = formAndLock
            val subjects = allSubjects.filterNot { it.archived }
            val dayStartHour = profile?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR
            val nextDayStart = GameDay.endOf(clock.gameDate(dayStartHour), clock.zone(), dayStartHour)
            GatesUiState(
                loading = false,
                tiles = GateRank.entries.map { GateTile(it, it.durationMinutes, GateRunMath.canStart(it, now, nextDayStart)) },
                setup = form.setup.takeIf { run == null },
                active = run?.toActive(now, allSubjects.firstOrNull { it.id == run.subjectId }?.name)?.copy(lockWarning = lockWarning),
                confirmAbandon = form.confirmAbandon && run != null,
                subjects = subjects.map { SubjectUi(it.id, it.name) },
                addingSubject = form.addingSubject,
                subjectDraft = form.subjectDraft,
                canAddSubject = FocusRules.canAddSubject(subjects.size),
                focusGoal = goal,
                pomodoroSetup = form.pomodoro.takeIf { session == null && run == null },
                pomodoro = session?.toUi(now),
            )
        }.onEach { state ->
            stopwatchRunning.value = state.active?.mode == GateMode.STOPWATCH && !state.active.paused
            if (state.active != null && state.active.mode != GateMode.STOPWATCH && state.active.remaining.isZero) safely("Claiming a gate") { coordinator.tick() }
            followPomodoro(state)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GatesUiState())

    fun onEvent(event: GatesEvent) {
        when (event) {
            is GatesEvent.Pick -> form.update { it.copy(setup = GateSetup(event.rank), pomodoro = null) }
            is GatesEvent.PickMode -> form.update { it.copy(setup = GateSetup(GateRank.E, mode = event.mode), pomodoro = null) }
            GatesEvent.OpenPomodoro -> viewModelScope.launch {
                safely("Opening the Pomodoro setup") {
                    val saved = lastFocusStore.pomodoroDefaults.first()
                    form.update {
                        it.copy(
                            setup = null,
                            pomodoro = PomodoroSetupUi(saved.focusMinutes, saved.shortBreakMinutes, saved.longBreakMinutes, saved.rounds),
                        )
                    }
                }
            }
            is GatesEvent.PomodoroAdjust -> form.update { state -> state.copy(pomodoro = state.pomodoro?.adjusted(event.field, event.direction)) }
            GatesEvent.PomodoroSkipBreak -> viewModelScope.launch { safely("Skipping a break") { pomodoroSessions.skipBreak() } }
            GatesEvent.PomodoroNextRound -> viewModelScope.launch { safely("Starting the next round") { reportStart(pomodoroSessions.startNextRound()) } }
            GatesEvent.PomodoroEnd -> viewModelScope.launch { safely("Ending the session") { pomodoroSessions.end() } }
            GatesEvent.PomodoroDone -> viewModelScope.launch { safely("Closing the session") { pomodoroSessions.dismissFinished() } }
            GatesEvent.PomodoroAnother -> viewModelScope.launch {
                safely("Starting another cycle") {
                    val finished = pomodoroSessions.running() ?: return@safely
                    pomodoroSessions.dismissFinished()
                    reportStart(pomodoroSessions.start(finished.config, finished.stat, finished.subjectId))
                }
            }
            is GatesEvent.MinutesChanged -> updateSetup { it.copy(minutes = FocusRules.clampCustomMinutes(event.minutes)) }
            is GatesEvent.SubjectPicked -> setSubject(event.subjectId)
            GatesEvent.StartAddSubject -> form.update { it.copy(addingSubject = true, subjectDraft = "") }
            is GatesEvent.SubjectDraftChanged -> form.update { it.copy(subjectDraft = event.text.take(FocusRules.SUBJECT_NAME_MAX)) }
            GatesEvent.CancelAddSubject -> form.update { it.copy(addingSubject = false, subjectDraft = "") }
            GatesEvent.SaveSubject -> viewModelScope.launch { safely("Adding a subject") { saveSubject() } }
            is GatesEvent.ArchiveSubject -> viewModelScope.launch {
                safely("Archiving a subject") {
                    focusDao.setArchived(event.subjectId, true)
                    form.update { state ->
                        state.copy(
                            setup = state.setup?.let { if (it.subjectId == event.subjectId) it.copy(subjectId = null) else it },
                            pomodoro = state.pomodoro?.let { if (it.subjectId == event.subjectId) it.copy(subjectId = null) else it },
                        )
                    }
                }
            }
            GatesEvent.Stop -> viewModelScope.launch { safely("Stopping the stopwatch") { coordinator.stopStopwatch() } }
            GatesEvent.CloseSetup -> form.update { it.copy(setup = null, pomodoro = null) }
            is GatesEvent.StatChanged -> setStat(event.stat)
            is GatesEvent.LabelChanged -> updateSetup { it.copy(label = event.label.take(GATE_LABEL_MAX)) }
            GatesEvent.Enter -> {
                form.value.pomodoro?.let { setup ->
                    viewModelScope.launch {
                        safely("Starting a Pomodoro session") {
                            val result = pomodoroSessions.start(setup.config, setup.stat, setup.subjectId)
                            reportStart(result)
                            if (result is PomodoroStartResult.Started) form.update { it.copy(pomodoro = null) }
                        }
                    }
                    return
                }
                val setup = form.value.setup ?: return
                viewModelScope.launch {
                    safely("Opening a gate") {
                        if (setup.mode == GateMode.PRESET) {
                            coordinator.start(setup.rank, setup.stat, setup.label)
                        } else {
                            coordinator.startFocus(setup.mode, setup.minutes, setup.stat, setup.label, setup.subjectId)
                        }
                        form.update { it.copy(setup = null) }
                    }
                }
            }
            GatesEvent.Pause -> viewModelScope.launch { safely("Pausing a gate") { coordinator.pause() } }
            GatesEvent.Resume -> viewModelScope.launch { safely("Resuming a gate") { coordinator.resume() } }
            GatesEvent.Abandon -> form.update { it.copy(confirmAbandon = true) }
            GatesEvent.CancelAbandon -> form.update { it.copy(confirmAbandon = false) }
            GatesEvent.FixFocusLock -> Unit // handled by the Route, which opens the setup
            GatesEvent.ConfirmAbandon -> {
                form.update { it.copy(confirmAbandon = false) }
                viewModelScope.launch { safely("Abandoning a gate") { coordinator.abandon() } }
            }
        }
    }

    private suspend fun saveSubject() {
        val name = FocusRules.cleanSubjectName(form.value.subjectDraft) ?: return
        if (!FocusRules.canAddSubject(focusDao.activeCount())) return
        val existing = focusDao.byName(name)
        val id = when {
            existing == null -> UUID.randomUUID().toString().also { focusDao.insertSubject(FocusSubjectEntity(it, name, createdAt = clock.now())) }
            else -> existing.id.also { focusDao.setArchived(it, false) }
        }
        form.update { it.copy(addingSubject = false, subjectDraft = "") }
        updateSetup { it.copy(subjectId = id) }
    }

    private fun updateSetup(change: (GateSetup) -> GateSetup) = form.update { state -> state.copy(setup = state.setup?.let(change)) }

    /** The subject goes to whichever setup window is open: a custom or stopwatch session, or Pomodoro. */
    private fun setSubject(subjectId: String?) = form.update { state ->
        state.copy(setup = state.setup?.copy(subjectId = subjectId), pomodoro = state.pomodoro?.copy(subjectId = subjectId))
    }

    private fun setStat(stat: Stat) = form.update { state ->
        val allowed = stat.takeIf(GATE_STATS::contains)
        state.copy(
            setup = state.setup?.let { it.copy(stat = allowed ?: it.stat) },
            pomodoro = state.pomodoro?.let { it.copy(stat = allowed ?: it.stat) },
        )
    }

    private fun reportStart(result: PomodoroStartResult) {
        when (result) {
            PomodoroStartResult.TooLate -> completionCenter.message(UserMessage.GateTooLate)
            PomodoroStartResult.AlreadyRunning -> completionCenter.message(UserMessage.GateAlreadyRunning)
            PomodoroStartResult.NotReady -> completionCenter.message(UserMessage.HabitNotAllowed)
            is PomodoroStartResult.Started, PomodoroStartResult.NoHunter -> Unit
        }
    }

    /**
     * While a session is open: moves it on from what its round did (a claimed round starts the break, a long break that ran
     * out becomes the finish screen) and plays the soft chime once when a break ends in front of the hunter.
     */
    private suspend fun followPomodoro(state: GatesUiState) {
        val pomodoro = state.pomodoro
        if (pomodoro == null) {
            breakWasOver = false
            return
        }
        val roundEnded = pomodoro.phase == PomodoroPhase.FOCUS && state.active == null
        val longBreakRanOut = pomodoro.phase == PomodoroPhase.LONG_BREAK && pomodoro.breakOver
        if (roundEnded || longBreakRanOut) safely("Moving the session on") { pomodoroSessions.sync() }
        val isBreak = pomodoro.phase == PomodoroPhase.SHORT_BREAK || pomodoro.phase == PomodoroPhase.LONG_BREAK
        if (isBreak && pomodoro.breakOver && !breakWasOver) safely("Playing the break chime") { coordinator.playBreakChime() }
        breakWasOver = isBreak && pomodoro.breakOver
    }

    private fun PomodoroSessionEntity.toUi(now: Instant): PomodoroUi {
        val left = phaseEndsAt?.let { Duration.between(now, it) }?.takeIf { !it.isNegative } ?: Duration.ZERO
        return PomodoroUi(
            phase = phase,
            round = currentRound,
            rounds = rounds,
            breakLeft = left,
            breakOver = breakOver(now),
            config = config,
            stat = stat,
            subjectId = subjectId,
        )
    }

    private fun GateRunEntity.toActive(now: Instant, subject: String?): ActiveGate {
        val phase = GateRunMath.phase(timing, now)
        val paused = phase is GatePhase.Paused
        return ActiveGate(
            rank = gateRank,
            mode = mode,
            elapsed = GateRunMath.elapsed(timing, now),
            subject = subject,
            stat = stat,
            label = label,
            remaining = GateRunMath.remaining(timing, now),
            total = Duration.ofSeconds(plannedSec.toLong()),
            breachUsed = Duration.ofMillis(backgroundMs),
            paused = paused,
            pauseLeft = (phase as? GatePhase.Paused)?.pauseLeft ?: Duration.ZERO,
            canPause = pausedAt == null && phase is GatePhase.Running,
        )
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "%s failed", what)
            completionCenter.message(UserMessage.Error)
        }
    }

    private fun PomodoroSetupUi.adjusted(field: PomodoroField, direction: Int): PomodoroSetupUi = when (field) {
        PomodoroField.FOCUS -> copy(focusMinutes = PomodoroRules.clampFocus(focusMinutes + direction * PomodoroRules.FOCUS_STEP))
        PomodoroField.SHORT_BREAK -> copy(shortBreakMinutes = PomodoroRules.clampShortBreak(shortBreakMinutes + direction))
        PomodoroField.LONG_BREAK -> copy(longBreakMinutes = PomodoroRules.clampLongBreak(longBreakMinutes + direction * PomodoroRules.LONG_BREAK_STEP))
        PomodoroField.ROUNDS -> copy(rounds = PomodoroRules.clampRounds(rounds + direction))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 1_000L
        const val STOPWATCH_TICK_MS = 33L
    }
}
