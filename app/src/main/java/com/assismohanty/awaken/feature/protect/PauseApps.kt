package com.assismohanty.awaken.feature.protect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.OutlinedTextField
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
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.designsystem.components.AwakenSwitch
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.lock.AlwaysAllowed
import com.assismohanty.awaken.engine.FocusLockRules
import com.assismohanty.awaken.engine.PauseRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One app in the picker. [locked] = it is never covered (phone, messages, payments, launcher...) and so can't be picked. */
data class PauseAppRow(val packageName: String, val label: String, val chosen: Boolean, val locked: Boolean)

data class PauseAppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val suggested: List<PauseAppRow> = emptyList(),
    val others: List<PauseAppRow> = emptyList(),
    val chosenCount: Int = 0,
) {
    val full: Boolean get() = chosenCount >= PauseRules.MAX_TARGETS
}

sealed interface PauseAppsEvent {
    data class Query(val text: String) : PauseAppsEvent
    data class Toggle(val packageName: String) : PauseAppsEvent
}

@HiltViewModel
class PauseAppsViewModel @Inject constructor(
    private val store: PauseStore,
    private val usage: UsageGateway,
    private val alwaysAllowed: AlwaysAllowed,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<PauseAppsUiState> = combine(apps, store.settings, query) { installed, settings, q ->
        if (installed == null) return@combine PauseAppsUiState(query = q)
        val fixed = alwaysAllowed.current()
        val chosen = settings.config.targets
        val rows = installed
            .filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }
            .map { PauseAppRow(it.packageName, it.label, it.packageName in chosen, it.packageName !in chosen && FocusLockRules.neverBlocked(it.packageName, fixed)) }
        val (suggested, others) = rows.partition { it.packageName in SUGGESTED && !it.locked }
        PauseAppsUiState(false, q, suggested.sortedBy { SUGGESTED.indexOf(it.packageName) }, others, chosen.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PauseAppsUiState())

    init {
        viewModelScope.launch { apps.value = usage.launcherApps() }
    }

    fun onEvent(event: PauseAppsEvent) {
        when (event) {
            is PauseAppsEvent.Query -> query.update { event.text }
            is PauseAppsEvent.Toggle -> viewModelScope.launch {
                val current = store.current().config.targets
                if (event.packageName in current) {
                    store.setTargets(current - event.packageName)
                } else if (PauseRules.canTarget(event.packageName, current, alwaysAllowed.current())) {
                    store.setTargets(current + event.packageName)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** The apps most often wanted here, in the order they are offered (PLAN §2.1). TikTok has two package names. */
        val SUGGESTED = listOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.twitter.android",
            "com.reddit.frontpage",
        )
    }
}

@Composable
fun PauseAppsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PauseAppsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PauseAppsScreen(state, viewModel::onEvent, onBack, modifier)
}

/** Choose the apps Pause covers (GD §19.1 PZ6): up to 12, suggested ones first. */
@Composable
fun PauseAppsScreen(state: PauseAppsUiState, onEvent: (PauseAppsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.pause_apps_title), subtitle = lexString(R.string.pause_apps_subtitle), onBack = onBack) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(PauseAppsEvent.Query(it)) },
                singleLine = true,
                label = { Text(lexString(R.string.pause_apps_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.full) Text(lexString(R.string.pause_apps_full), style = type.caption, color = colors.textSecondary)
            if (!state.loading && state.suggested.isEmpty() && state.others.isEmpty()) {
                Text(lexString(R.string.pause_apps_empty), style = type.caption, color = colors.textSecondary)
            }
            if (state.suggested.isNotEmpty()) {
                SystemWindow(title = lexString(R.string.pause_apps_suggested), accent = colors.accent, reveal = false) {
                    state.suggested.forEach { AppRow(it, state.full, onEvent) }
                }
            }
            if (state.others.isNotEmpty()) {
                SystemWindow(title = lexString(R.string.pause_apps_all), accent = colors.accent, reveal = false) {
                    state.others.forEach { AppRow(it, state.full, onEvent) }
                }
            }
        }
    }
}

@Composable
private fun AppRow(row: PauseAppRow, full: Boolean, onEvent: (PauseAppsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val enabled = !row.locked && (row.chosen || !full)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AwakenTheme.dimens.touchTarget)
            .toggleable(value = row.chosen, enabled = enabled, role = Role.Switch, onValueChange = { onEvent(PauseAppsEvent.Toggle(row.packageName)) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, style = AwakenTheme.type.label, color = colors.textPrimary)
            if (row.locked) Text(lexString(R.string.pause_apps_locked), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        AwakenSwitch(checked = row.chosen, enabled = enabled)
    }
}

@PhonePreviews
@Composable
private fun PauseAppsPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseAppsScreen(
            PauseAppsUiState(
                loading = false,
                suggested = listOf(PauseAppRow("a", "Instagram", chosen = true, locked = false), PauseAppRow("b", "YouTube", chosen = false, locked = false)),
                others = listOf(PauseAppRow("c", "Phone", chosen = false, locked = true), PauseAppRow("d", "Notes", chosen = false, locked = false)),
                chosenCount = 1,
            ),
            {}, {},
        )
    }
}

@PhonePreviews
@Composable
private fun PauseAppsFullPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseAppsScreen(
            PauseAppsUiState(
                loading = false,
                others = listOf(PauseAppRow("a", "Instagram", chosen = true, locked = false), PauseAppRow("b", "Notes", chosen = false, locked = false)),
                chosenCount = PauseRules.MAX_TARGETS,
            ),
            {}, {},
        )
    }
}
