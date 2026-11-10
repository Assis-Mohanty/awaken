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
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PauseWindowUiState(val enabled: Boolean = false, val appCount: Int = 0)

@HiltViewModel
class PauseWindowViewModel @Inject constructor(store: PauseStore) : ViewModel() {
    val uiState: StateFlow<PauseWindowUiState> = store.settings
        .map { PauseWindowUiState(it.config.enabled, it.config.targets.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PauseWindowUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Settings → Pause: whether it is on, how many apps, and the way into its own screen. */
@Composable
fun PauseWindowRoute(onOpen: () -> Unit, modifier: Modifier = Modifier, viewModel: PauseWindowViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PauseWindowContent(state, onOpen, modifier)
}

@Composable
fun PauseWindowContent(state: PauseWindowUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.pause_title), accent = colors.accent, reveal = false, modifier = modifier) {
        Text(lexString(R.string.pause_window_note), style = type.caption, color = colors.textSecondary)
        Text(
            text = when {
                !state.enabled -> lexString(R.string.pause_window_off)
                state.appCount == 0 -> lexString(R.string.pause_apps_none)
                else -> lexPlural(R.plurals.pause_apps_count, state.appCount, state.appCount)
            },
            style = type.label,
            color = colors.textPrimary,
        )
        SecondaryButton(lexString(R.string.pause_window_open), onOpen, Modifier.fillMaxWidth())
    }
}

@PhonePreviews
@Composable
private fun PauseWindowPreview() {
    AwakenTheme(reduceMotion = true) { PauseWindowContent(PauseWindowUiState(enabled = true, appCount = 4), {}) }
}

@PhonePreviews
@Composable
private fun PauseWindowOffPreview() {
    AwakenTheme(reduceMotion = true) { PauseWindowContent(PauseWindowUiState(), {}) }
}
