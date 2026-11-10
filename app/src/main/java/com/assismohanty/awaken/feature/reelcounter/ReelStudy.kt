package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenSwitch
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.ReviewTextField
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.reelcounter.core.study.ChannelMatch
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReelStudyUiState(
    val consent: Boolean = false,
    val on: Boolean = false,
    val channels: List<String> = emptyList(),
)

sealed interface ReelStudyEvent {
    data object Agree : ReelStudyEvent
    data class SetOn(val on: Boolean) : ReelStudyEvent
    data class Add(val name: String) : ReelStudyEvent
    data class Remove(val name: String) : ReelStudyEvent
}

@HiltViewModel
class ReelStudyViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val uiState: StateFlow<ReelStudyUiState> =
        combine(settings.studyConsent, settings.studyOn, settings.studyChannels) { consent, on, channels ->
            ReelStudyUiState(consent, on, channels.sortedBy { it.lowercase() })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReelStudyUiState())

    fun onEvent(event: ReelStudyEvent) {
        viewModelScope.launch {
            val channels = uiState.value.channels.toSet()
            when (event) {
                ReelStudyEvent.Agree -> settings.setStudyConsent(true)
                is ReelStudyEvent.SetOn -> settings.setStudyOn(event.on)
                // The store ignores list edits while Study mode is on.
                is ReelStudyEvent.Add -> {
                    val name = event.name.trim()
                    val known = channels.any { ChannelMatch.normalize(it) == ChannelMatch.normalize(name) }
                    if (ChannelMatch.normalize(name).isNotEmpty() && !known && channels.size < MAX_CHANNELS) settings.setStudyChannels(channels + name)
                }
                is ReelStudyEvent.Remove -> settings.setStudyChannels(channels - event.name)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_CHANNELS = 100
    }
}

@Composable
fun ReelStudyRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReelStudyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReelStudyScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun ReelStudyScreen(state: ReelStudyUiState, onEvent: (ReelStudyEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    LargeTitleScaffold(
        title = lexString(R.string.reel_study_title),
        subtitle = lexString(R.string.reel_study_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (!state.consent) {
            // The disclosure names exactly what is read, and nothing below it shows until the hunter agrees.
            AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
                Text(lexString(R.string.reel_study_disclosure_title), style = type.windowTitle, color = colors.textPrimary)
                Text(lexString(R.string.reel_study_disclosure), style = type.body, color = colors.textSecondary)
                PrimaryButton(lexString(R.string.reel_study_agree), { onEvent(ReelStudyEvent.Agree) }, Modifier.fillMaxWidth())
            }
            return@LargeTitleScaffold
        }
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AwakenTheme.dimens.touchTarget + Spacing.xs)
                    .toggleable(value = state.on, role = Role.Switch, onValueChange = { onEvent(ReelStudyEvent.SetOn(it)) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(lexString(R.string.reel_study_switch), style = type.windowTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                AwakenSwitch(checked = state.on)
            }
        }
        if (state.on) Text(lexString(R.string.reel_study_locked), style = type.caption, color = colors.textSecondary)
        AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
            Text(lexString(R.string.reel_study_list), style = type.windowTitle, color = colors.textPrimary)
            if (state.channels.isEmpty()) Text(lexString(R.string.reel_study_empty), style = type.body, color = colors.textSecondary)
            state.channels.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(name, style = type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    QuietButton(lexString(R.string.reel_study_remove), { onEvent(ReelStudyEvent.Remove(name)) }, enabled = !state.on)
                }
            }
            if (!state.on) {
                var draft by remember { mutableStateOf("") }
                ReviewTextField(
                    prompt = lexString(R.string.reel_study_add_prompt),
                    value = draft,
                    onValueChange = { draft = it },
                    maxChars = NAME_LIMIT,
                    singleLine = true,
                )
                SecondaryButton(
                    lexString(R.string.reel_study_add),
                    { onEvent(ReelStudyEvent.Add(draft)); draft = "" },
                    Modifier.fillMaxWidth(),
                    enabled = draft.isNotBlank(),
                )
            }
        }
        Text(lexString(R.string.reel_study_note), style = type.caption, color = colors.textSecondary)
    }
}

private const val NAME_LIMIT = 60

@PhonePreviews
@Composable
private fun ReelStudyDisclosurePreview() {
    AwakenTheme(reduceMotion = true) { ReelStudyScreen(ReelStudyUiState(), {}, {}) }
}

@PhonePreviews
@Composable
private fun ReelStudyEditPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelStudyScreen(ReelStudyUiState(consent = true, channels = listOf("3Blue1Brown", "Veritasium")), {}, {})
    }
}

@PhonePreviews
@Composable
private fun ReelStudyOnPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelStudyScreen(ReelStudyUiState(consent = true, on = true, channels = listOf("3Blue1Brown", "Veritasium")), {}, {})
    }
}
