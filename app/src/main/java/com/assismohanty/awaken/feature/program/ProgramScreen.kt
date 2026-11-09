package com.assismohanty.awaken.feature.program

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.designsystem.components.SectionTitle
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.assismohanty.awaken.core.data.db.entity.ProgramEntity
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.ConfirmDialog
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.ProgramCalendar
import com.assismohanty.awaken.core.designsystem.components.ProgramDayMark
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.AbandonProgramUseCase
import com.assismohanty.awaken.core.domain.ChooseProgramBreakUseCase
import com.assismohanty.awaken.core.domain.ObserveProgramUseCase
import com.assismohanty.awaken.core.domain.ProgramAvailabilityUseCase
import com.assismohanty.awaken.core.domain.ProgramMark
import com.assismohanty.awaken.core.domain.ProgramView
import com.assismohanty.awaken.core.domain.StartProgramResult
import com.assismohanty.awaken.core.domain.StartProgramUseCase
import com.assismohanty.awaken.engine.ProgramBreakChoice
import com.assismohanty.awaken.engine.ProgramRuleKey
import com.assismohanty.awaken.engine.ProgramRules
import com.assismohanty.awaken.engine.ProgramSpec
import com.assismohanty.awaken.engine.ProgramStart
import com.assismohanty.awaken.engine.ProgramStatus
import com.assismohanty.awaken.engine.model.Rank
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
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

data class ProgramsUiState(
    val loading: Boolean = true,
    val view: ProgramView? = null,
    /** Per program key: can it start now, and if not, why. */
    val availability: Map<String, ProgramStart> = emptyMap(),
    /** The program whose detail is open; null shows the list. */
    val selectedKey: String? = null,
    val confirmStart: Boolean = false,
    val confirmAbandon: Boolean = false,
) {
    val running: Boolean get() = view?.program?.status == ProgramStatus.ACTIVE
    val selected: ProgramSpec? get() = selectedKey?.let(ProgramRules::spec)
}

sealed interface ProgramsEvent {
    data class Open(val key: String) : ProgramsEvent
    data object CloseDetail : ProgramsEvent
    data object AskStart : ProgramsEvent
    data object DismissStart : ProgramsEvent
    data object ConfirmStart : ProgramsEvent
    data object AskAbandon : ProgramsEvent
    data object DismissAbandon : ProgramsEvent
    data object ConfirmAbandon : ProgramsEvent
}

@HiltViewModel
class ProgramsViewModel @Inject constructor(
    observe: ObserveProgramUseCase,
    private val availability: ProgramAvailabilityUseCase,
    private val start: StartProgramUseCase,
    private val abandon: AbandonProgramUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(ProgramsUiState())
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<ProgramsUiState> = combine(observe(ticks), local) { view, local ->
        local.copy(loading = false, view = view, availability = availability())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProgramsUiState())

    fun onEvent(event: ProgramsEvent) {
        when (event) {
            is ProgramsEvent.Open -> local.update { it.copy(selectedKey = event.key) }
            ProgramsEvent.CloseDetail -> local.update { it.copy(selectedKey = null) }
            ProgramsEvent.AskStart -> local.update { it.copy(confirmStart = true) }
            ProgramsEvent.DismissStart -> local.update { it.copy(confirmStart = false) }
            ProgramsEvent.ConfirmStart -> viewModelScope.launch {
                val key = local.value.selectedKey ?: return@launch
                val result = start(key)
                local.update { it.copy(confirmStart = false, selectedKey = if (result is StartProgramResult.Started) null else it.selectedKey) }
            }
            ProgramsEvent.AskAbandon -> local.update { it.copy(confirmAbandon = true) }
            ProgramsEvent.DismissAbandon -> local.update { it.copy(confirmAbandon = false) }
            ProgramsEvent.ConfirmAbandon -> viewModelScope.launch {
                abandon()
                local.update { it.copy(confirmAbandon = false) }
            }
        }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ProgramsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ProgramsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { if (state.selectedKey != null) viewModel.onEvent(ProgramsEvent.CloseDetail) else onBack() },
        modifier = modifier,
    )
}

/** Programs (DESIGN_SYSTEM §9.3): the three programs, one program's detail, or the run in progress with its calendar. */
@Composable
fun ProgramsScreen(state: ProgramsUiState, onEvent: (ProgramsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val view = state.view
    val selected = state.selected
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(
            title = (if (state.running) view?.spec else selected)?.let { lexString(programNameRes(it.key)) } ?: lexString(R.string.programs_title),
            subtitle = if (selected == null && !state.running) lexString(R.string.programs_intro) else null,
            onBack = onBack,
        ) {
            when {
                state.loading -> Unit
                state.running && view != null -> RunningWindow(view, onEvent)
                selected != null -> DetailWindow(selected, state.availability[selected.key] ?: ProgramStart.OK, onEvent)
                else -> {
                    view?.takeIf { it.program.status != ProgramStatus.ACTIVE }?.let { ResultWindow(it) }
                    AwakenCard(reveal = false, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
                        ProgramRules.CATALOG.forEach { spec -> ProgramRow(spec, onClick = { onEvent(ProgramsEvent.Open(spec.key)) }) }
                    }
                    Text(lexString(R.string.programs_note), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
                }
            }
        }
    }
    if (state.confirmStart && selected != null) {
        ConfirmDialog(
            title = lexString(R.string.program_start_title, lexString(programNameRes(selected.key))),
            body = lexString(R.string.program_start_body),
            confirm = lexString(R.string.program_start),
            dismiss = lexString(R.string.action_cancel),
            onConfirm = { onEvent(ProgramsEvent.ConfirmStart) },
            onDismiss = { onEvent(ProgramsEvent.DismissStart) },
        )
    }
    if (state.confirmAbandon) {
        ConfirmDialog(
            title = lexString(R.string.program_abandon_title),
            body = lexString(R.string.program_abandon_body),
            confirm = lexString(R.string.program_abandon),
            dismiss = lexString(R.string.action_cancel),
            onConfirm = { onEvent(ProgramsEvent.ConfirmAbandon) },
            onDismiss = { onEvent(ProgramsEvent.DismissAbandon) },
        )
    }
}

@Composable
private fun ProgramRow(spec: ProgramSpec, onClick: () -> Unit) {
    val colors = AwakenTheme.colors
    ListRow(
        title = lexString(programNameRes(spec.key)),
        secondary = lexString(R.string.program_card_note, spec.rules.size),
        icon = AwakenGlyphs.Repeat,
        onClick = onClick,
    ) {
        Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.icon))
    }
}

@Composable
private fun DetailWindow(spec: ProgramSpec, availability: ProgramStart, onEvent: (ProgramsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.program_rules_title), accent = colors.accent, reveal = false) {
        Text(lexString(R.string.program_days, spec.days), style = AwakenTheme.type.label, color = colors.textSecondary)
        spec.rules.forEach { rule -> Text(lexString(ruleTitleRes(rule.key)), style = AwakenTheme.type.body, color = colors.textPrimary) }
        if (spec.needsPhoneDownNight) Text(lexString(R.string.program_night_rule), style = AwakenTheme.type.body, color = colors.textPrimary)
        refusalRes(availability)?.let { Text(lexString(it), style = AwakenTheme.type.caption, color = colors.textSecondary) }
        PrimaryButton(
            text = lexString(R.string.program_start),
            onClick = { onEvent(ProgramsEvent.AskStart) },
            enabled = availability == ProgramStart.OK,
            modifier = Modifier.fillMaxWidth(),
        )
        QuietButton(text = lexString(R.string.action_back), onClick = { onEvent(ProgramsEvent.CloseDetail) })
    }
}

@Composable
private fun RunningWindow(view: ProgramView, onEvent: (ProgramsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val day = view.dayToday ?: view.spec.days
    BigValue(value = day.toString(), unit = lexString(R.string.program_of_days, view.spec.days), large = true)
    ProgramCalendar(view.marks.map { it.toDayMark() }, description = calendarDescription(view, day))
    SectionTitle(lexString(R.string.program_today_rules))
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
        view.ruleTitles.forEach { Text(it, style = AwakenTheme.type.body, color = colors.textPrimary) }
        if (view.spec.needsPhoneDownNight) Text(lexString(R.string.program_night_rule), style = AwakenTheme.type.body, color = colors.textPrimary)
    }
    QuietButton(text = lexString(R.string.program_abandon), onClick = { onEvent(ProgramsEvent.AskAbandon) })
}

@Composable
private fun ResultWindow(view: ProgramView) {
    val colors = AwakenTheme.colors
    val (title, body) = when (view.program.status) {
        ProgramStatus.COMPLETED -> lexString(R.string.program_completed_title) to lexString(R.string.program_completed_body)
        ProgramStatus.FINISHED -> lexString(R.string.program_finished_title) to lexString(R.string.program_finished_body, view.brokenDays)
        else -> lexString(R.string.program_abandoned_title) to lexString(R.string.program_abandoned_body, view.marks.count { it != ProgramMark.UPCOMING && it != ProgramMark.TODAY })
    }
    SystemWindow(title = title, accent = if (view.program.status == ProgramStatus.COMPLETED) colors.reward else colors.accent, reveal = false) {
        Text(lexString(programNameRes(view.spec.key)), style = AwakenTheme.type.label, color = colors.textSecondary)
        Text(body, style = AwakenTheme.type.body, color = colors.textSecondary)
        if (view.program.status != ProgramStatus.ABANDONED) {
            ProgramCalendar(view.marks.map { it.toDayMark() }, description = calendarDescription(view, view.spec.days))
        }
    }
}

@Composable
private fun calendarDescription(view: ProgramView, day: Int): String {
    val kept = view.marks.count { it == ProgramMark.KEPT }
    val rest = view.marks.count { it == ProgramMark.REST }
    return if (view.dayToday != null) {
        lexString(R.string.program_calendar_cd, day, view.spec.days, kept, rest, view.brokenDays)
    } else {
        lexString(R.string.program_calendar_done_cd, view.spec.days, kept, rest, view.brokenDays)
    }
}

private fun ProgramMark.toDayMark() = when (this) {
    ProgramMark.KEPT -> ProgramDayMark.KEPT
    ProgramMark.REST -> ProgramDayMark.REST
    ProgramMark.BROKEN -> ProgramDayMark.BROKEN
    ProgramMark.TODAY -> ProgramDayMark.TODAY
    ProgramMark.UPCOMING -> ProgramDayMark.UPCOMING
}

@StringRes
private fun programNameRes(key: String): Int = when (key) {
    "steady_30" -> R.string.program_name_steady_30
    "groundwork_66" -> R.string.program_name_groundwork_66
    else -> R.string.program_name_ironclad_75
}

@StringRes
private fun ruleTitleRes(key: ProgramRuleKey): Int = when (key) {
    ProgramRuleKey.WALK -> R.string.program_rule_walk
    ProgramRuleKey.READ -> R.string.program_rule_read
    ProgramRuleKey.FOCUS_15 -> R.string.program_rule_focus_15
    ProgramRuleKey.PLAN -> R.string.program_rule_plan
    ProgramRuleKey.WORKOUT -> R.string.program_rule_workout
    ProgramRuleKey.FOCUS_25 -> R.string.program_rule_focus_25
    ProgramRuleKey.STEPS_10K -> R.string.program_rule_steps_10k
}

@StringRes
private fun refusalRes(start: ProgramStart): Int? = when (start) {
    ProgramStart.OK -> null
    ProgramStart.NOT_ENOUGH_SLOTS -> R.string.program_refused_slots
    ProgramStart.PROGRAM_ACTIVE -> R.string.program_refused_program
}

/**
 * The question after a broken day (DESIGN_SYSTEM §9.3), asked from Today on the next open. Restart and Continue carry equal
 * weight, and not answering means Continue at the next rollover. Nothing is ever taken either way.
 */
@HiltViewModel
class ProgramBreakViewModel @Inject constructor(
    observe: ObserveProgramUseCase,
    private val choose: ChooseProgramBreakUseCase,
) : ViewModel() {
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    /** True while a broken day waits for an answer. */
    val pending: StateFlow<Boolean> = combine(observe(ticks), MutableStateFlow(Unit)) { view, _ -> view?.pendingBreak != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun answer(choice: ProgramBreakChoice) {
        viewModelScope.launch { choose(choice) }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ProgramBreakHost(viewModel: ProgramBreakViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    // Swiping it away is allowed: an unanswered break becomes Continue at the next rollover (GD §16.15).
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (pending && !dismissed) BreakSheet(onChoose = viewModel::answer, onDismiss = { dismissed = true })
}

@Composable
internal fun BreakSheet(onChoose: (ProgramBreakChoice) -> Unit, onDismiss: () -> Unit) {
    AwakenBottomSheet(onDismiss = onDismiss, title = lexString(R.string.program_break_title)) {
        BreakContent(onChoose)
    }
}

@Composable
internal fun BreakContent(onChoose: (ProgramBreakChoice) -> Unit) {
    Text(lexString(R.string.program_break_body), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
    SecondaryButton(text = lexString(R.string.program_break_restart), onClick = { onChoose(ProgramBreakChoice.RESTART) }, modifier = Modifier.fillMaxWidth())
    SecondaryButton(text = lexString(R.string.program_break_continue), onClick = { onChoose(ProgramBreakChoice.CONTINUE) }, modifier = Modifier.fillMaxWidth())
}

// Previews

private fun view(key: String, status: ProgramStatus, marks: List<ProgramMark>, day: Int?, broken: Int = marks.count { it == ProgramMark.BROKEN }) =
    ProgramView(
        program = ProgramEntity("p1", key, LocalDate.of(2026, 10, 20), status, Instant.parse("2026-10-20T08:00:00Z")),
        spec = checkNotNull(ProgramRules.spec(key)),
        dayToday = day,
        marks = marks,
        brokenDays = broken,
        pendingBreak = null,
        ruleTitles = listOf("Walk 7,000 steps", "Read 10 pages", "15-minute focus session"),
    )

private fun state(view: ProgramView? = null, selectedKey: String? = null, availability: ProgramStart = ProgramStart.OK) = ProgramsUiState(
    loading = false, view = view, selectedKey = selectedKey, availability = ProgramRules.CATALOG.associate { it.key to availability },
)

private val runningMarks = List(11) { ProgramMark.KEPT }.toMutableList().also {
    it[4] = ProgramMark.BROKEN
    it[7] = ProgramMark.REST
} + ProgramMark.TODAY + List(18) { ProgramMark.UPCOMING }

@PhonePreviews
@Composable
private fun ProgramsListPreview() = AwakenTheme(reduceMotion = true) { ProgramsScreen(state(), {}, {}) }

@PhonePreviews
@Composable
private fun ProgramDetailPreview() = AwakenTheme(reduceMotion = true) { ProgramsScreen(state(selectedKey = "ironclad_75"), {}, {}) }

@PhonePreviews
@Composable
private fun ProgramDetailRefusedPreview() = AwakenTheme(reduceMotion = true) {
    ProgramsScreen(state(selectedKey = "groundwork_66", availability = ProgramStart.NOT_ENOUGH_SLOTS), {}, {})
}

@PhonePreviews
@Composable
private fun ProgramRunningPreview() = AwakenTheme(reduceMotion = true) {
    ProgramsScreen(state(view("steady_30", ProgramStatus.ACTIVE, runningMarks, day = 12)), {}, {})
}

@PhonePreviews
@Composable
private fun ProgramCompletedPreview() = AwakenTheme(reduceMotion = true) {
    ProgramsScreen(state(view("steady_30", ProgramStatus.COMPLETED, List(30) { ProgramMark.KEPT }, day = null)), {}, {})
}

@PhonePreviews
@Composable
private fun ProgramFinishedPreview() = AwakenTheme(reduceMotion = true) {
    val marks = List(30) { if (it == 6 || it == 19) ProgramMark.BROKEN else ProgramMark.KEPT }
    ProgramsScreen(state(view("steady_30", ProgramStatus.FINISHED, marks, day = null)), {}, {})
}

@PhonePreviews
@Composable
private fun ProgramAbandonedPreview() = AwakenTheme(reduceMotion = true) {
    ProgramsScreen(state(view("steady_30", ProgramStatus.ABANDONED, List(8) { ProgramMark.KEPT } + List(22) { ProgramMark.UPCOMING }, day = null)), {}, {})
}

@PhonePreviews
@Composable
private fun ProgramBreakPreview() = AwakenTheme(reduceMotion = true) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.androidxPadding(),
        verticalArrangement = Arrangement.spacedBy(com.assismohanty.awaken.core.designsystem.theme.Spacing.sm),
    ) { BreakContent {} }
}

private fun Modifier.androidxPadding(): Modifier = this.then(Modifier.padding(com.assismohanty.awaken.core.designsystem.theme.Spacing.md))
