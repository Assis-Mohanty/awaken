package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.limit.LimitPolicy
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One app's daily limit. A null [limit] means no limit. */
data class ReelLimitRow(val app: ShortVideoApp, val limit: Int?)

data class ReelLimitsUiState(val rows: List<ReelLimitRow> = ShortVideoApp.entries.map { ReelLimitRow(it, null) })

sealed interface ReelLimitsEvent {
    data class Toggle(val app: ShortVideoApp, val on: Boolean) : ReelLimitsEvent
    data class Step(val app: ShortVideoApp, val up: Boolean) : ReelLimitsEvent
}

@HiltViewModel
class ReelLimitsViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val uiState: StateFlow<ReelLimitsUiState> = settings.limits
        .map { limits -> ReelLimitsUiState(ShortVideoApp.entries.map { ReelLimitRow(it, limits[it]) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReelLimitsUiState())

    fun onEvent(event: ReelLimitsEvent) {
        viewModelScope.launch {
            when (event) {
                is ReelLimitsEvent.Toggle -> settings.setLimit(event.app, if (event.on) LimitPolicy.DEFAULT_LIMIT else null)
                is ReelLimitsEvent.Step -> {
                    val current = uiState.value.rows.first { it.app == event.app }.limit ?: return@launch
                    val next = current + if (event.up) LimitPolicy.STEP else -LimitPolicy.STEP
                    settings.setLimit(event.app, LimitPolicy.clamp(next))
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ReelLimitsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReelLimitsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReelLimitsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun ReelLimitsScreen(state: ReelLimitsUiState, onEvent: (ReelLimitsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    LargeTitleScaffold(title = lexString(R.string.reel_limits_title), onBack = onBack, modifier = modifier) {
        Text(lexString(R.string.reel_limits_note), style = type.body, color = colors.textSecondary)
        state.rows.forEach { row ->
            SystemWindow(title = lexString(row.app.labelRes()), accent = colors.accent, reveal = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AwakenTheme.dimens.touchTarget)
                        .toggleable(value = row.limit != null, role = Role.Switch, onValueChange = { onEvent(ReelLimitsEvent.Toggle(row.app, it)) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lexString(R.string.reel_limit_toggle, lexString(row.app.labelRes())), style = type.label, color = colors.textPrimary)
                        Text(
                            row.limit?.let { lexString(R.string.reel_limit_value, it) } ?: lexString(R.string.reel_limit_off),
                            style = type.caption,
                            color = colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = row.limit != null,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.surface),
                    )
                }
                if (row.limit != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SecondaryButton(
                            lexString(R.string.reel_limit_lower),
                            { onEvent(ReelLimitsEvent.Step(row.app, up = false)) },
                            Modifier.weight(1f),
                            enabled = row.limit > LimitPolicy.MIN_LIMIT,
                        )
                        SecondaryButton(
                            lexString(R.string.reel_limit_raise),
                            { onEvent(ReelLimitsEvent.Step(row.app, up = true)) },
                            Modifier.weight(1f),
                            enabled = row.limit < LimitPolicy.MAX_LIMIT,
                        )
                    }
                }
            }
        }
    }
}

@PhonePreviews
@Composable
private fun ReelLimitsAllOffPreview() {
    AwakenTheme(reduceMotion = true) { ReelLimitsScreen(ReelLimitsUiState(), {}, {}) }
}

@PhonePreviews
@Composable
private fun ReelLimitsSomeOnPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelLimitsScreen(
            ReelLimitsUiState(
                ShortVideoApp.entries.map {
                    ReelLimitRow(it, when (it) {
                        ShortVideoApp.INSTAGRAM -> 50
                        ShortVideoApp.YOUTUBE -> LimitPolicy.MIN_LIMIT
                        else -> null
                    })
                },
            ),
            {}, {},
        )
    }
}
