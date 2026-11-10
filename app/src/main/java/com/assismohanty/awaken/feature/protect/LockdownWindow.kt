package com.assismohanty.awaken.feature.protect

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.LockdownController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LockdownWindowUiState(val running: Boolean = false, val waitMinutes: Int = 0)

@HiltViewModel
class LockdownWindowViewModel @Inject constructor(controller: LockdownController) : ViewModel() {
    val uiState: StateFlow<LockdownWindowUiState> = combine(controller.observeRunning(), controller.observeLatest()) { running, _ ->
        val left = controller.cooldownLeft()
        LockdownWindowUiState(running != null, if (left.isZero) 0 else (left.toMinutes() + 1).toInt())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockdownWindowUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Settings → Lockdown: whether one runs, the wait after a broken one, and the way into its setup. */
@Composable
fun LockdownWindowRoute(onOpen: () -> Unit, modifier: Modifier = Modifier, viewModel: LockdownWindowViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LockdownWindowContent(state, onOpen, modifier)
}

@Composable
fun LockdownWindowContent(state: LockdownWindowUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.lockdown_title), accent = colors.accent, reveal = false, modifier = modifier) {
        Text(lexString(R.string.lockdown_window_note), style = type.caption, color = colors.textSecondary)
        if (state.running) {
            Text(lexString(R.string.lockdown_window_running), style = type.label, color = colors.textPrimary)
        } else if (state.waitMinutes > 0) {
            Text(lexString(R.string.lockdown_window_cooldown, state.waitMinutes), style = type.label, color = colors.textPrimary)
        }
        SecondaryButton(lexString(R.string.lockdown_window_start), onOpen, Modifier.fillMaxWidth(), enabled = !state.running && state.waitMinutes == 0)
    }
}

@PhonePreviews
@Composable
private fun LockdownWindowPreview() {
    AwakenTheme(reduceMotion = true) { LockdownWindowContent(LockdownWindowUiState(), {}) }
}

@PhonePreviews
@Composable
private fun LockdownWindowWaitingPreview() {
    AwakenTheme(reduceMotion = true) { LockdownWindowContent(LockdownWindowUiState(waitMinutes = 38), {}) }
}
