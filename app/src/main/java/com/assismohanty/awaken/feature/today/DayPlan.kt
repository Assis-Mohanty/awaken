package com.assismohanty.awaken.feature.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.DayPlan
import com.assismohanty.awaken.core.domain.DayPlanResult
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveDayPlanUseCase
import com.assismohanty.awaken.core.domain.SetRecoveryModeUseCase
import com.assismohanty.awaken.core.domain.SetRestDayUseCase
import com.assismohanty.awaken.engine.StreakRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class DayPlanUiState(
    val open: Boolean = false,
    val plan: DayPlan? = null,
    @StringRes val error: Int? = null,
    val busy: Boolean = false,
)

sealed interface DayPlanEvent {
    data object Open : DayPlanEvent
    data object Dismiss : DayPlanEvent
    data class ToggleRest(val date: LocalDate) : DayPlanEvent
    data class SetRecovery(val on: Boolean) : DayPlanEvent
}

/** Rest Day and Recovery Mode (GD §7.2). Settings proper arrives in Phase 10; this sheet is the entry point until then. */
@HiltViewModel
class DayPlanViewModel @Inject constructor(
    observeDayPlan: ObserveDayPlanUseCase,
    dayRefresher: DayRefresher,
    private val setRestDay: SetRestDayUseCase,
    private val setRecoveryMode: SetRecoveryModeUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(DayPlanUiState())

    val uiState: StateFlow<DayPlanUiState> = combine(observeDayPlan(dayRefresher.ticks), local) { plan, state ->
        state.copy(plan = plan)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DayPlanUiState())

    fun onEvent(event: DayPlanEvent) {
        when (event) {
            DayPlanEvent.Open -> local.update { it.copy(open = true, error = null) }
            DayPlanEvent.Dismiss -> local.update { it.copy(open = false, error = null) }
            is DayPlanEvent.ToggleRest -> run {
                val plan = uiState.value.plan ?: return@run DayPlanResult.NotAllowed
                setRestDay(event.date, rest = event.date !in plan.restDays)
            }
            is DayPlanEvent.SetRecovery -> run { setRecoveryMode(event.on) }
        }
    }

    private fun run(action: suspend () -> DayPlanResult) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = try {
                when (action()) {
                    DayPlanResult.Saved -> null
                    DayPlanResult.NotAllowed -> R.string.msg_rest_not_allowed
                    DayPlanResult.NoRecoveryLeft -> R.string.msg_recovery_none
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Day plan change failed")
                R.string.msg_error
            }
            local.update { it.copy(busy = false, error = error) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPlanSheet(state: DayPlanUiState, onEvent: (DayPlanEvent) -> Unit) {
    val plan = state.plan
    if (!state.open || plan == null) return
    ModalBottomSheet(
        onDismissRequest = { onEvent(DayPlanEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.panelRaised,
        shape = AwakenTheme.shapes.window,
    ) {
        DayPlanContent(
            state = state,
            plan = plan,
            onEvent = onEvent,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayPlanContent(state: DayPlanUiState, plan: DayPlan, onEvent: (DayPlanEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.day_plan_title), style = type.windowTitle, color = colors.system, modifier = Modifier.semantics { heading() })

        Text(stringResource(R.string.rest_day_label), style = type.label, color = colors.bone, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.rest_day_body), style = type.caption, color = colors.mist)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            plan.upcoming.forEach { date ->
                ChoiceChip(
                    text = stringResource(R.string.rest_day_chip, date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), date.dayOfMonth),
                    selected = date in plan.restDays,
                    onClick = { onEvent(DayPlanEvent.ToggleRest(date)) },
                    multiSelect = true,
                    enabled = !state.busy && plan.canToggleRest(date),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .toggleable(
                    value = plan.recoveryToday,
                    enabled = !state.busy,
                    role = Role.Switch,
                    onValueChange = { onEvent(DayPlanEvent.SetRecovery(it)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.recovery_label), style = type.label, color = colors.bone)
                Text(stringResource(R.string.recovery_body), style = type.caption, color = colors.mist)
                Text(
                    text = pluralStringResource(
                        R.plurals.recovery_remaining, plan.recoveryRemaining, plan.recoveryRemaining, StreakRules.RECOVERY_DAYS_PER_MONTH,
                    ),
                    style = type.caption,
                    color = colors.system,
                )
            }
            // The row is the toggle target; the switch only shows state.
            Switch(
                checked = plan.recoveryToday,
                onCheckedChange = null,
                enabled = !state.busy,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.system, checkedThumbColor = colors.void),
            )
        }

        state.error?.let { Text(stringResource(it), style = type.label, color = colors.ember) }
    }
}

@PhonePreviews
@Composable
private fun DayPlanPreview() {
    val today = LocalDate.of(2026, 10, 19)
    val plan = DayPlan(
        today = today,
        upcoming = (1..DayPlan.REST_PLAN_DAYS).map { today.plusDays(it) },
        restDays = setOf(today.plusDays(5)),
        recoveryToday = false,
        recoveryRemaining = 7,
    )
    AwakenTheme { DayPlanContent(DayPlanUiState(open = true, plan = plan), plan, onEvent = {}) }
}
