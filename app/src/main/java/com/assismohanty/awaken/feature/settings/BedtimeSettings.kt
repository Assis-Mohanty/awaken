package com.assismohanty.awaken.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.BedtimeReader
import com.assismohanty.awaken.core.domain.ObserveSettingsUseCase
import com.assismohanty.awaken.core.domain.SetBedtimeUseCase
import com.assismohanty.awaken.engine.BedtimeRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phone-down nights as Settings shows them (GD §16.12). Times are minutes since midnight. */
data class BedtimeSettingsUi(
    val enabled: Boolean = false,
    val bedtimeMinute: Int = BedtimeRules.DEFAULT_BEDTIME,
    val wakeMinute: Int = BedtimeRules.DEFAULT_WAKE,
    val hasAccess: Boolean = true,
    /** The chosen window is not allowed (length or wake time), so Save is off. */
    val invalid: Boolean = false,
    /** A change to this window is waiting to be saved. */
    val dirty: Boolean = false,
)

sealed interface BedtimeSettingsEvent {
    data class Toggle(val on: Boolean) : BedtimeSettingsEvent
    data class StepBedtime(val direction: Int) : BedtimeSettingsEvent
    data class StepWake(val direction: Int) : BedtimeSettingsEvent
    data object Save : BedtimeSettingsEvent
    data object Resumed : BedtimeSettingsEvent
}

private data class Draft(val bedtime: Int? = null, val wake: Int? = null)

@HiltViewModel
class BedtimeSettingsViewModel @Inject constructor(
    private val reader: BedtimeReader,
    private val setBedtime: SetBedtimeUseCase,
    private val usage: UsageGateway,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {
    private val draft = MutableStateFlow(Draft())
    private val access = MutableStateFlow(usage.hasAccess())

    val uiState: StateFlow<BedtimeSettingsUi> = combine(
        reader.observeSettings(),
        observeSettings().filterNotNull(),
        draft,
        access,
    ) { versions, settings, draft, hasAccess ->
        val latest = versions.maxByOrNull { it.effectiveFrom }
        val bedtime = draft.bedtime ?: latest?.bedtimeMinute ?: BedtimeRules.DEFAULT_BEDTIME
        val wake = draft.wake ?: latest?.wakeMinute ?: BedtimeRules.DEFAULT_WAKE
        BedtimeSettingsUi(
            enabled = latest?.enabled == true,
            bedtimeMinute = bedtime,
            wakeMinute = wake,
            hasAccess = hasAccess,
            invalid = !BedtimeRules.isValidWindow(bedtime, wake, settings.dayStartHour),
            dirty = draft.bedtime != null || draft.wake != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BedtimeSettingsUi())

    fun onEvent(event: BedtimeSettingsEvent) {
        val state = uiState.value
        when (event) {
            is BedtimeSettingsEvent.Toggle -> viewModelScope.launch {
                if (event.on) setBedtime.enableOrEdit(state.bedtimeMinute, state.wakeMinute) else setBedtime.disable()
                draft.value = Draft()
            }
            is BedtimeSettingsEvent.StepBedtime -> draft.update { it.copy(bedtime = step(BEDTIME_OPTIONS, state.bedtimeMinute, event.direction), wake = it.wake ?: state.wakeMinute) }
            is BedtimeSettingsEvent.StepWake -> draft.update { it.copy(wake = step(WAKE_OPTIONS, state.wakeMinute, event.direction), bedtime = it.bedtime ?: state.bedtimeMinute) }
            BedtimeSettingsEvent.Save -> viewModelScope.launch {
                if (!state.invalid && setBedtime.enableOrEdit(state.bedtimeMinute, state.wakeMinute) is com.assismohanty.awaken.core.domain.SetBedtimeResult.Saved) {
                    draft.value = Draft()
                }
            }
            BedtimeSettingsEvent.Resumed -> access.value = usage.hasAccess()
        }
    }

    private fun step(options: List<Int>, current: Int, direction: Int): Int {
        val index = options.indexOf(current).takeIf { it >= 0 } ?: 0
        return options[(index + direction).coerceIn(0, options.lastIndex)]
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** 20:00 through 23:45, then 00:00 through 02:00, in order of the night (GD §16.12, N21). */
        val BEDTIME_OPTIONS = (BedtimeRules.BEDTIME_EARLIEST until 24 * 60 step BedtimeRules.STEP_MINUTES).toList() +
            (0..BedtimeRules.BEDTIME_LATEST step BedtimeRules.STEP_MINUTES).toList()
        val WAKE_OPTIONS = BedtimeRules.WAKE_RANGE.step(BedtimeRules.STEP_MINUTES).toList()
    }
}

@Composable
fun BedtimeSettingsRoute(
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BedtimeSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(BedtimeSettingsEvent.Resumed)
        onPauseOrDispose { }
    }
    BedtimeSettingsWindow(state, viewModel::onEvent, onGrantAccess, modifier)
}

/** Settings → Focus → Phone-down nights (DESIGN_SYSTEM §9.3). */
@Composable
fun BedtimeSettingsWindow(
    state: BedtimeSettingsUi,
    onEvent: (BedtimeSettingsEvent) -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.bedtime_settings_title), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(lexString(R.string.bedtime_settings_explain), style = type.body, color = colors.textSecondary)
        SwitchRow(
            title = lexString(R.string.bedtime_settings_on),
            note = if (state.enabled) {
                lexString(R.string.bedtime_settings_window, clockText(state.bedtimeMinute), clockText(state.wakeMinute))
            } else {
                null
            },
            checked = state.enabled,
            onChange = { onEvent(BedtimeSettingsEvent.Toggle(it)) },
        )
        if (!state.hasAccess) {
            Text(lexString(R.string.bedtime_settings_needs_access), style = type.caption, color = colors.textSecondary)
            SecondaryButton(text = lexString(R.string.bedtime_grant_access), onClick = onGrantAccess, modifier = Modifier.fillMaxWidth())
        }
        TimeStepper(
            label = lexString(R.string.bedtime_settings_bedtime),
            value = clockText(state.bedtimeMinute),
            lessDescription = lexString(R.string.bedtime_bedtime_earlier),
            moreDescription = lexString(R.string.bedtime_bedtime_later),
            onLess = { onEvent(BedtimeSettingsEvent.StepBedtime(-1)) },
            onMore = { onEvent(BedtimeSettingsEvent.StepBedtime(1)) },
        )
        TimeStepper(
            label = lexString(R.string.bedtime_settings_wake),
            value = clockText(state.wakeMinute),
            lessDescription = lexString(R.string.bedtime_wake_earlier),
            moreDescription = lexString(R.string.bedtime_wake_later),
            onLess = { onEvent(BedtimeSettingsEvent.StepWake(-1)) },
            onMore = { onEvent(BedtimeSettingsEvent.StepWake(1)) },
        )
        if (state.invalid) {
            Text(lexString(R.string.bedtime_settings_invalid), style = type.caption, color = colors.critical)
        } else {
            Text(
                lexString(if (state.enabled) R.string.bedtime_settings_apply_edit else R.string.bedtime_settings_apply_start),
                style = type.caption,
                color = colors.textSecondary,
            )
        }
        if (state.enabled) {
            SecondaryButton(
                text = lexString(R.string.bedtime_settings_save),
                onClick = { onEvent(BedtimeSettingsEvent.Save) },
                enabled = state.dirty && !state.invalid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(lexString(R.string.bedtime_settings_allowed), style = type.caption, color = colors.textSecondary)
    }
}

@Composable
private fun TimeStepper(
    label: String,
    value: String,
    lessDescription: String,
    moreDescription: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = AwakenTheme.colors
    Text(label, style = AwakenTheme.type.label, color = colors.textPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SecondaryButton(
            text = lexString(R.string.pomodoro_minus),
            onClick = onLess,
            modifier = Modifier.semantics { contentDescription = lessDescription },
        )
        Text(value, style = AwakenTheme.type.metricL, color = colors.textPrimary, modifier = Modifier.weight(1f))
        SecondaryButton(
            text = lexString(R.string.pomodoro_plus),
            onClick = onMore,
            modifier = Modifier.semantics { contentDescription = moreDescription },
        )
    }
}

private fun clockText(minute: Int): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(LocalTime.of(minute / 60, minute % 60))

@PhonePreviews
@Composable
private fun BedtimeSettingsOffPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeSettingsWindow(BedtimeSettingsUi(), {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeSettingsOnPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeSettingsWindow(BedtimeSettingsUi(enabled = true, dirty = true, bedtimeMinute = 22 * 60 + 30), {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeSettingsNoAccessPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeSettingsWindow(BedtimeSettingsUi(enabled = true, hasAccess = false), {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeSettingsInvalidPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeSettingsWindow(BedtimeSettingsUi(enabled = true, dirty = true, bedtimeMinute = 2 * 60, wakeMinute = 5 * 60, invalid = true), {}, {})
}
