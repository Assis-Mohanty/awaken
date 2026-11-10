package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.data.BlockMode
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReelBlockingUiState(
    val consent: Boolean = false,
    val mode: BlockMode = BlockMode.OFF,
    val apps: Set<ShortVideoApp> = emptySet(),
)

sealed interface ReelBlockingEvent {
    data object Agree : ReelBlockingEvent
    data class SetMode(val mode: BlockMode) : ReelBlockingEvent
    data class ToggleApp(val app: ShortVideoApp) : ReelBlockingEvent
}

@HiltViewModel
class ReelBlockingViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val uiState: StateFlow<ReelBlockingUiState> =
        combine(settings.blockConsent, settings.blockMode, settings.blockApps, ::ReelBlockingUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReelBlockingUiState())

    fun onEvent(event: ReelBlockingEvent) {
        viewModelScope.launch {
            when (event) {
                ReelBlockingEvent.Agree -> settings.setBlockConsent(true)
                is ReelBlockingEvent.SetMode -> settings.setBlockMode(event.mode)
                is ReelBlockingEvent.ToggleApp -> {
                    val now = uiState.value.apps
                    settings.setBlockApps(if (event.app in now) now - event.app else now + event.app)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ReelBlockingRoute(
    onBack: () -> Unit,
    onOpenStudy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReelBlockingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReelBlockingScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onOpenStudy = onOpenStudy, modifier = modifier)
}

@Composable
fun ReelBlockingScreen(
    state: ReelBlockingUiState,
    onEvent: (ReelBlockingEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStudy: (() -> Unit)? = null,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    LargeTitleScaffold(
        title = lexString(R.string.reel_block_title),
        subtitle = lexString(R.string.reel_block_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (!state.consent) {
            // The disclosure comes first and nothing below it is shown until the hunter agrees.
            AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
                Text(lexString(R.string.reel_block_disclosure_title), style = type.windowTitle, color = colors.textPrimary)
                Text(lexString(R.string.reel_block_disclosure), style = type.body, color = colors.textSecondary)
                PrimaryButton(lexString(R.string.reel_block_agree), { onEvent(ReelBlockingEvent.Agree) }, Modifier.fillMaxWidth())
            }
            return@LargeTitleScaffold
        }
        AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
            Text(lexString(R.string.reel_block_when), style = type.windowTitle, color = colors.textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                BlockMode.entries.forEach { mode ->
                    ChoiceChip(
                        text = lexString(mode.labelRes()),
                        selected = state.mode == mode,
                        onClick = { onEvent(ReelBlockingEvent.SetMode(mode)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
            Text(lexString(R.string.reel_block_apps), style = type.windowTitle, color = colors.textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ShortVideoApp.entries.forEach { app ->
                    ChoiceChip(
                        text = lexString(app.labelRes()),
                        selected = app in state.apps,
                        onClick = { onEvent(ReelBlockingEvent.ToggleApp(app)) },
                        modifier = Modifier.fillMaxWidth(),
                        multiSelect = true,
                    )
                }
            }
        }
        Text(lexString(R.string.reel_block_note), style = type.caption, color = colors.textSecondary)
        if (onOpenStudy != null) SecondaryButton(lexString(R.string.reel_study_open), onOpenStudy, Modifier.fillMaxWidth())
    }
}

private fun BlockMode.labelRes(): Int = when (this) {
    BlockMode.OFF -> R.string.reel_block_mode_off
    BlockMode.SESSIONS -> R.string.reel_block_mode_sessions
    BlockMode.ALWAYS -> R.string.reel_block_mode_always
}

@PhonePreviews
@Composable
private fun ReelBlockingDisclosurePreview() {
    AwakenTheme(reduceMotion = true) { ReelBlockingScreen(ReelBlockingUiState(), {}, {}) }
}

@PhonePreviews
@Composable
private fun ReelBlockingOnPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelBlockingScreen(
            ReelBlockingUiState(consent = true, mode = BlockMode.SESSIONS, apps = setOf(ShortVideoApp.INSTAGRAM, ShortVideoApp.YOUTUBE)),
            {}, {},
        )
    }
}
