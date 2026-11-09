package com.assismohanty.awaken.feature.focuslock

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenSwitch
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.LandscapePreviews
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank

@Composable
fun AllowedAppsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AllowedAppsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AllowedAppsScreen(state, viewModel::onEvent, onBack, modifier)
}

/** Allowed apps (GD 8.2): tick the apps Focus Lock leaves uncovered. Always-allowed apps are ticked and locked. */
@Composable
fun AllowedAppsScreen(state: AllowedAppsUiState, onEvent: (AllowedAppsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(
            title = lexString(R.string.focus_lock_allowed_title),
            subtitle = lexString(R.string.focus_lock_allowed_subtitle),
            onBack = onBack,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(AllowedAppsEvent.Query(it)) },
                singleLine = true,
                label = { Text(lexString(R.string.focus_lock_allowed_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SystemWindow(title = null, accent = colors.accent, reveal = false) {
                if (!state.loading && state.rows.isEmpty()) {
                    Text(lexString(R.string.focus_lock_allowed_empty), style = type.caption, color = colors.textSecondary)
                }
                state.rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AwakenTheme.dimens.touchTarget)
                            .toggleable(
                                value = row.allowed,
                                enabled = !row.locked,
                                role = Role.Switch,
                                onValueChange = { onEvent(AllowedAppsEvent.Toggle(row.packageName)) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.label, style = type.label, color = colors.textPrimary)
                            if (row.locked) Text(lexString(R.string.focus_lock_allowed_always), style = type.caption, color = colors.textSecondary)
                        }
                        AwakenSwitch(checked = row.allowed, enabled = !row.locked)
                    }
                }
            }
        }
    }
}

@PhonePreviews
@LandscapePreviews
@Composable
private fun AllowedAppsPreview() {
    AwakenTheme(reduceMotion = true) {
        AllowedAppsScreen(
            AllowedAppsUiState(
                loading = false,
                rows = listOf(
                    AllowedAppRow("a", "Maps", allowed = true, locked = false),
                    AllowedAppRow("b", "Messages", allowed = true, locked = true),
                    AllowedAppRow("c", "Instagram", allowed = false, locked = false),
                ),
            ),
            {}, {},
        )
    }
}
