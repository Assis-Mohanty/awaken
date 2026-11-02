package com.assismohanty.awaken.feature.quit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.domain.AddQuitCounterUseCase
import com.assismohanty.awaken.core.domain.AddQuitResult
import com.assismohanty.awaken.core.domain.ArchiveQuitCounterUseCase
import com.assismohanty.awaken.core.domain.LogQuitRestartUseCase
import com.assismohanty.awaken.core.domain.ObserveQuitCountersUseCase
import com.assismohanty.awaken.core.domain.QuitCounterState
import com.assismohanty.awaken.engine.QuitKind
import com.assismohanty.awaken.engine.QuitRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "new counter" sheet. */
data class QuitAddForm(
    val kind: QuitKind = QuitKind.SMOKING,
    val customName: String = "",
    val earlier: Boolean = false,
    val daysAgo: Int = 1,
    val cost: Double = 0.0,
    val minutes: Double = 0.0,
    val error: QuitAddError? = null,
)

enum class QuitAddError { NAME, LIMIT, EXISTS, START }

/** The "log a restart" dialog. */
data class QuitRestartForm(val trigger: String? = null, val note: String = "")

data class QuitUiState(
    val loading: Boolean = true,
    /** Active counters; archived ones are hidden. */
    val counters: List<QuitCounterState> = emptyList(),
    val selectedId: String? = null,
    val add: QuitAddForm? = null,
    val restart: QuitRestartForm? = null,
    val confirmArchive: Boolean = false,
    /** Usage access, for the Doomscrolling counter. */
    val hasAccess: Boolean = true,
    /** ISO currency code for the cost estimate, from the phone's region. */
    val currency: String = "",
) {
    val selected: QuitCounterState? get() = counters.firstOrNull { it.counter.id == selectedId }
    val atLimit: Boolean get() = counters.size >= QuitRules.MAX_COUNTERS
}

sealed interface QuitEvent {
    data class Open(val id: String) : QuitEvent
    data object CloseDetail : QuitEvent
    data object OpenAdd : QuitEvent
    data object CloseAdd : QuitEvent
    data class PickKind(val kind: QuitKind) : QuitEvent
    data class CustomName(val name: String) : QuitEvent
    data class SetEarlier(val earlier: Boolean) : QuitEvent
    data class StepDays(val delta: Int) : QuitEvent
    data class Cost(val value: Double) : QuitEvent
    data class Minutes(val value: Double) : QuitEvent

    /** [name] is the preset's word (resolved by the screen) or the custom name. */
    data class SubmitAdd(val name: String) : QuitEvent
    data object OpenRestart : QuitEvent
    data object CloseRestart : QuitEvent
    data class PickTrigger(val trigger: String?) : QuitEvent
    data class Note(val text: String) : QuitEvent
    data object ConfirmRestart : QuitEvent
    data object AskArchive : QuitEvent
    data object DismissArchive : QuitEvent
    data object ConfirmArchive : QuitEvent
    data object Resumed : QuitEvent
}

@HiltViewModel
class QuitViewModel @Inject constructor(
    observeCounters: ObserveQuitCountersUseCase,
    private val addCounter: AddQuitCounterUseCase,
    private val logRestart: LogQuitRestartUseCase,
    private val archiveCounter: ArchiveQuitCounterUseCase,
    private val usage: UsageGateway,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(Local())
    private val access = MutableStateFlow(usage.hasAccess())

    /** Re-derives the runs once a minute while the screen is on; days and hours move slowly. */
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<QuitUiState> = combine(observeCounters(ticks), local, access) { counters, local, hasAccess ->
        QuitUiState(
            loading = false,
            counters = counters.filter { !it.archived },
            selectedId = local.selectedId,
            add = local.add,
            restart = local.restart,
            confirmArchive = local.confirmArchive,
            hasAccess = hasAccess,
            currency = currencyCode(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QuitUiState())

    private data class Local(
        val selectedId: String? = null,
        val add: QuitAddForm? = null,
        val restart: QuitRestartForm? = null,
        val confirmArchive: Boolean = false,
    )

    fun onEvent(event: QuitEvent) {
        when (event) {
            is QuitEvent.Open -> local.update { it.copy(selectedId = event.id) }
            QuitEvent.CloseDetail -> local.update { it.copy(selectedId = null, restart = null, confirmArchive = false) }
            QuitEvent.OpenAdd -> local.update { it.copy(add = QuitAddForm()) }
            QuitEvent.CloseAdd -> local.update { it.copy(add = null) }
            is QuitEvent.PickKind -> editAdd { it.copy(kind = event.kind, error = null) }
            is QuitEvent.CustomName -> editAdd { it.copy(customName = event.name.take(QuitRules.NAME_MAX), error = null) }
            is QuitEvent.SetEarlier -> editAdd { it.copy(earlier = event.earlier, error = null) }
            is QuitEvent.StepDays -> editAdd { it.copy(daysAgo = (it.daysAgo + event.delta).coerceIn(1, MAX_DAYS_AGO), error = null) }
            is QuitEvent.Cost -> editAdd { it.copy(cost = event.value) }
            is QuitEvent.Minutes -> editAdd { it.copy(minutes = event.value) }
            is QuitEvent.SubmitAdd -> submitAdd(event.name)
            QuitEvent.OpenRestart -> local.update { it.copy(restart = QuitRestartForm()) }
            QuitEvent.CloseRestart -> local.update { it.copy(restart = null) }
            is QuitEvent.PickTrigger -> local.update { l -> l.copy(restart = l.restart?.copy(trigger = event.trigger)) }
            is QuitEvent.Note -> local.update { l -> l.copy(restart = l.restart?.copy(note = event.text.take(QuitRules.NOTE_MAX))) }
            QuitEvent.ConfirmRestart -> confirmRestart()
            QuitEvent.AskArchive -> local.update { it.copy(confirmArchive = true) }
            QuitEvent.DismissArchive -> local.update { it.copy(confirmArchive = false) }
            QuitEvent.ConfirmArchive -> {
                val id = local.value.selectedId ?: return
                viewModelScope.launch {
                    archiveCounter(id)
                    local.update { it.copy(selectedId = null, confirmArchive = false) }
                }
            }
            QuitEvent.Resumed -> access.value = usage.hasAccess()
        }
    }

    private fun editAdd(change: (QuitAddForm) -> QuitAddForm) = local.update { l -> l.copy(add = l.add?.let(change)) }

    private fun submitAdd(name: String) {
        val form = local.value.add ?: return
        viewModelScope.launch {
            val code = currencyCode()
            val costMinor = form.cost.takeIf { it > 0.0 && code.isNotEmpty() }?.let { (it * 10.0.pow(fractionDigits(code))).roundToLong() }
            val start = if (form.kind.isTimeBased && form.earlier) clock.now().minus(Duration.ofDays(form.daysAgo.toLong())) else null
            when (addCounter(form.kind, name, start, costMinor, code.takeIf { costMinor != null }, form.minutes.toInt().takeIf { it > 0 })) {
                is AddQuitResult.Added -> local.update { it.copy(add = null) }
                AddQuitResult.InvalidName -> editAdd { it.copy(error = QuitAddError.NAME) }
                AddQuitResult.LimitReached -> editAdd { it.copy(error = QuitAddError.LIMIT) }
                AddQuitResult.AlreadyExists -> editAdd { it.copy(error = QuitAddError.EXISTS) }
                AddQuitResult.FutureStart -> editAdd { it.copy(error = QuitAddError.START) }
            }
        }
    }

    private fun confirmRestart() {
        val id = local.value.selectedId ?: return
        val form = local.value.restart ?: return
        viewModelScope.launch {
            logRestart(id, trigger = form.trigger, note = form.note)
            local.update { it.copy(restart = null) }
        }
    }

    private fun currencyCode(): String = try {
        Currency.getInstance(Locale.getDefault()).currencyCode
    } catch (e: IllegalArgumentException) {
        ""
    }

    private fun fractionDigits(code: String): Int = Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0)

    private companion object {
        const val TICK_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_DAYS_AGO = 3_650
    }
}
