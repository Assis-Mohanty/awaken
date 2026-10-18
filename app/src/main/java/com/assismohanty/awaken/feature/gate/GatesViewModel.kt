package com.assismohanty.awaken.feature.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.GATE_LABEL_MAX
import com.assismohanty.awaken.core.domain.GATE_STATS
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.core.work.GateCoordinator
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GatePhase
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
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
    val stat: Stat,
    val label: String?,
    val remaining: Duration,
    val total: Duration,
    val breachUsed: Duration,
    val paused: Boolean,
    val pauseLeft: Duration,
    val canPause: Boolean,
)

/** Picking a gate: the chosen rank, stat and optional label (GD §8). */
data class GateSetup(val rank: GateRank, val stat: Stat = Stat.INT, val label: String = "")

data class GatesUiState(
    val loading: Boolean = true,
    val tiles: List<GateTile> = emptyList(),
    val setup: GateSetup? = null,
    val active: ActiveGate? = null,
    val confirmAbandon: Boolean = false,
)

sealed interface GatesEvent {
    data class Pick(val rank: GateRank) : GatesEvent
    data object CloseSetup : GatesEvent
    data class StatChanged(val stat: Stat) : GatesEvent
    data class LabelChanged(val label: String) : GatesEvent
    data object Enter : GatesEvent
    data object Pause : GatesEvent
    data object Resume : GatesEvent
    data object Abandon : GatesEvent
    data object ConfirmAbandon : GatesEvent
    data object CancelAbandon : GatesEvent
}

private data class FormState(val setup: GateSetup? = null, val confirmAbandon: Boolean = false)

@HiltViewModel
class GatesViewModel @Inject constructor(
    hunterDao: HunterDao,
    gateRunDao: GateRunDao,
    private val coordinator: GateCoordinator,
    private val completionCenter: CompletionCenter,
    private val clock: Clock,
) : ViewModel() {

    private val form = MutableStateFlow(FormState())

    // Collected only while the screen is visible; it drives the dial and the claim at the planned end.
    private val ticks = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<GatesUiState> =
        combine(hunterDao.observeProfile(), gateRunDao.observeRunning(), form, ticks) { profile, run, form, now ->
            val dayStartHour = profile?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR
            val nextDayStart = GameDay.endOf(clock.gameDate(dayStartHour), clock.zone(), dayStartHour)
            GatesUiState(
                loading = false,
                tiles = GateRank.entries.map { GateTile(it, it.durationMinutes, GateRunMath.canStart(it, now, nextDayStart)) },
                setup = form.setup.takeIf { run == null },
                active = run?.toActive(now),
                confirmAbandon = form.confirmAbandon && run != null,
            )
        }.onEach { state ->
            if (state.active?.remaining?.isZero == true) safely("Claiming a gate") { coordinator.tick() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GatesUiState())

    fun onEvent(event: GatesEvent) {
        when (event) {
            is GatesEvent.Pick -> form.update { it.copy(setup = GateSetup(event.rank)) }
            GatesEvent.CloseSetup -> form.update { it.copy(setup = null) }
            is GatesEvent.StatChanged -> updateSetup { it.copy(stat = event.stat.takeIf(GATE_STATS::contains) ?: it.stat) }
            is GatesEvent.LabelChanged -> updateSetup { it.copy(label = event.label.take(GATE_LABEL_MAX)) }
            GatesEvent.Enter -> {
                val setup = form.value.setup ?: return
                viewModelScope.launch {
                    safely("Opening a gate") {
                        coordinator.start(setup.rank, setup.stat, setup.label)
                        form.update { it.copy(setup = null) }
                    }
                }
            }
            GatesEvent.Pause -> viewModelScope.launch { safely("Pausing a gate") { coordinator.pause() } }
            GatesEvent.Resume -> viewModelScope.launch { safely("Resuming a gate") { coordinator.resume() } }
            GatesEvent.Abandon -> form.update { it.copy(confirmAbandon = true) }
            GatesEvent.CancelAbandon -> form.update { it.copy(confirmAbandon = false) }
            GatesEvent.ConfirmAbandon -> {
                form.update { it.copy(confirmAbandon = false) }
                viewModelScope.launch { safely("Abandoning a gate") { coordinator.abandon() } }
            }
        }
    }

    private fun updateSetup(change: (GateSetup) -> GateSetup) = form.update { state -> state.copy(setup = state.setup?.let(change)) }

    private fun GateRunEntity.toActive(now: Instant): ActiveGate {
        val phase = GateRunMath.phase(timing, now)
        val paused = phase is GatePhase.Paused
        return ActiveGate(
            rank = gateRank,
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
    }
}
