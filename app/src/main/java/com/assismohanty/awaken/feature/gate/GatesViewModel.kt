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
import com.assismohanty.awaken.core.domain.CompletionCenter
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
)

sealed interface GatesEvent {
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

private data class FormState(
    val setup: GateSetup? = null,
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

    val uiState: StateFlow<GatesUiState> =
        combine(hunterDao.observeProfile(), gateRunDao.observeRunning(), combine(form, lockMissing) { f, l -> f to l }, focusDao.observeAll(), ticks) { profile, run, formAndLock, allSubjects, now ->
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
            )
        }.onEach { state ->
            stopwatchRunning.value = state.active?.mode == GateMode.STOPWATCH && !state.active.paused
            if (state.active != null && state.active.mode != GateMode.STOPWATCH && state.active.remaining.isZero) safely("Claiming a gate") { coordinator.tick() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GatesUiState())

    fun onEvent(event: GatesEvent) {
        when (event) {
            is GatesEvent.Pick -> form.update { it.copy(setup = GateSetup(event.rank)) }
            is GatesEvent.PickMode -> form.update { it.copy(setup = GateSetup(GateRank.E, mode = event.mode)) }
            is GatesEvent.MinutesChanged -> updateSetup { it.copy(minutes = FocusRules.clampCustomMinutes(event.minutes)) }
            is GatesEvent.SubjectPicked -> updateSetup { it.copy(subjectId = event.subjectId) }
            GatesEvent.StartAddSubject -> form.update { it.copy(addingSubject = true, subjectDraft = "") }
            is GatesEvent.SubjectDraftChanged -> form.update { it.copy(subjectDraft = event.text.take(FocusRules.SUBJECT_NAME_MAX)) }
            GatesEvent.CancelAddSubject -> form.update { it.copy(addingSubject = false, subjectDraft = "") }
            GatesEvent.SaveSubject -> viewModelScope.launch { safely("Adding a subject") { saveSubject() } }
            is GatesEvent.ArchiveSubject -> viewModelScope.launch {
                safely("Archiving a subject") {
                    focusDao.setArchived(event.subjectId, true)
                    updateSetup { if (it.subjectId == event.subjectId) it.copy(subjectId = null) else it }
                }
            }
            GatesEvent.Stop -> viewModelScope.launch { safely("Stopping the stopwatch") { coordinator.stopStopwatch() } }
            GatesEvent.CloseSetup -> form.update { it.copy(setup = null) }
            is GatesEvent.StatChanged -> updateSetup { it.copy(stat = event.stat.takeIf(GATE_STATS::contains) ?: it.stat) }
            is GatesEvent.LabelChanged -> updateSetup { it.copy(label = event.label.take(GATE_LABEL_MAX)) }
            GatesEvent.Enter -> {
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 1_000L
        const val STOPWATCH_TICK_MS = 33L
    }
}
