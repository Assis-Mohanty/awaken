package com.assismohanty.awaken.feature.gate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.DifficultyChip
import com.assismohanty.awaken.core.designsystem.components.GateDial
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.StatChip
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.GATE_STATS
import com.assismohanty.awaken.engine.GateRules
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.quest.shortClock
import java.time.Duration

@Composable
fun GatesEntry(modifier: Modifier = Modifier, viewModel: GatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The gate runs either way; the permission only adds the countdown outside the app (ARCH §3.3).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    GatesScreen(
        state = state,
        onEvent = { event ->
            if (event == GatesEvent.Enter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.onEvent(event)
        },
        modifier = modifier,
    )
}

/** Focus Gates (DESIGN_SYSTEM §4.4): the E/D/C/B portals, then the full-screen dial while a gate is open. */
@Composable
fun GatesScreen(state: GatesUiState, onEvent: (GatesEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            val active = state.active
            when {
                state.loading -> Unit
                active != null -> GateRun(active, state.confirmAbandon, onEvent)
                else -> GatePicker(state, onEvent)
            }
        }
    }
}

@Composable
private fun GatePicker(state: GatesUiState, onEvent: (GatesEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Text(stringResource(R.string.gates_title), style = AwakenTheme.type.windowTitle, color = colors.bone)
    Text(
        stringResource(R.string.gates_body, shortClock(GateRules.BREACH_BUDGET_MS)),
        style = AwakenTheme.type.body,
        color = colors.mist,
    )
    state.tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            row.forEach { tile ->
                GatePortal(
                    tile = tile,
                    selected = state.setup?.rank == tile.rank,
                    onClick = { onEvent(GatesEvent.Pick(tile.rank)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    state.setup?.let { GateSetupWindow(it, onEvent) }
}

@Composable
private fun GatePortal(tile: GateTile, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val shape = AwakenTheme.shapes.window
    val gateColor = if (tile.available) colors.gate(tile.rank) else colors.fog
    val name = stringResource(R.string.gate_name, tile.rank.name)
    val minutes = stringResource(R.string.gate_minutes, tile.minutes)
    val description = if (tile.available) {
        stringResource(R.string.cd_gate_tile, name, minutes)
    } else {
        stringResource(R.string.cd_gate_tile_late, name, minutes)
    }
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.gateTileHeight)
            .background(if (selected) gateColor.copy(alpha = AwakenAlpha.TINT) else colors.panel, shape)
            .border(if (selected) dimens.accentWidth else dimens.hairline, if (selected) gateColor else colors.edge, shape)
            .clickable(enabled = tile.available, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(name, style = AwakenTheme.type.windowTitle, color = gateColor)
        Text(minutes, style = AwakenTheme.type.numeric, color = if (tile.available) colors.bone else colors.fog)
        if (tile.available) {
            DifficultyChip(tile.rank.difficulty)
        } else {
            Text(stringResource(R.string.gate_tile_late), style = AwakenTheme.type.caption, color = colors.mist)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GateSetupWindow(setup: GateSetup, onEvent: (GatesEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.gate_name, setup.rank.name), accent = colors.gate(setup.rank)) {
        Text(stringResource(R.string.gate_stat_label), style = AwakenTheme.type.label, color = colors.mist)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            GATE_STATS.forEach { stat ->
                ChoiceChip(
                    text = stat.label(),
                    selected = setup.stat == stat,
                    onClick = { onEvent(GatesEvent.StatChanged(stat)) },
                    color = colors.stat(stat),
                )
            }
        }
        OutlinedTextField(
            value = setup.label,
            onValueChange = { onEvent(GatesEvent.LabelChanged(it)) },
            label = { Text(stringResource(R.string.gate_label_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.system,
                unfocusedBorderColor = colors.edge,
                focusedLabelColor = colors.system,
                cursorColor = colors.system,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.gate_rule, shortClock(GateRules.BREACH_BUDGET_MS)), style = AwakenTheme.type.caption, color = colors.mist)
        PrimaryButton(
            text = stringResource(R.string.action_enter_gate),
            onClick = { onEvent(GatesEvent.Enter) },
            color = colors.gate(setup.rank),
            modifier = Modifier.fillMaxWidth(),
        )
        QuietButton(text = stringResource(R.string.action_back), onClick = { onEvent(GatesEvent.CloseSetup) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GateRun(active: ActiveGate, confirmAbandon: Boolean, onEvent: (GatesEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.gate_name, active.rank.name), style = AwakenTheme.type.windowTitle, color = colors.gate(active.rank))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            StatChip(active.stat)
            active.label?.let { Text(it, style = AwakenTheme.type.label, color = colors.bone) }
        }
        GateDial(gate = active.rank, remaining = active.remaining, total = active.total, breachUsed = active.breachUsed)
        Text(
            stringResource(R.string.gate_breach_line, shortClock(active.breachUsed.toMillis()), shortClock(GateRules.BREACH_BUDGET_MS)),
            style = AwakenTheme.type.label,
            color = if (active.breachUsed.isZero) colors.mist else colors.ember,
        )
        when {
            active.paused -> {
                Text(stringResource(R.string.gate_paused_left, shortClock(active.pauseLeft.toMillis())), style = AwakenTheme.type.label, color = colors.bone)
                PrimaryButton(stringResource(R.string.action_resume), onClick = { onEvent(GatesEvent.Resume) }, modifier = Modifier.fillMaxWidth())
            }
            active.canPause -> SecondaryButton(
                text = stringResource(R.string.action_pause, shortClock(GateRules.MAX_PAUSE.toMillis())),
                onClick = { onEvent(GatesEvent.Pause) },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Text(stringResource(R.string.gate_pause_used), style = AwakenTheme.type.caption, color = colors.mist)
        }
        if (confirmAbandon) {
            SystemWindow(title = null, accent = colors.ember, reveal = false) {
                Text(stringResource(R.string.gate_abandon_confirm), style = AwakenTheme.type.body, color = colors.bone)
                PrimaryButton(stringResource(R.string.action_abandon_gate), onClick = { onEvent(GatesEvent.ConfirmAbandon) }, color = colors.ember, modifier = Modifier.fillMaxWidth())
                QuietButton(stringResource(R.string.action_keep_going), onClick = { onEvent(GatesEvent.CancelAbandon) }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            QuietButton(stringResource(R.string.action_abandon_gate), onClick = { onEvent(GatesEvent.Abandon) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val previewTiles = GateRank.entries.map { GateTile(it, it.durationMinutes, available = it != GateRank.B) }

@PhonePreviews
@Composable
private fun GatesPickerPreview() {
    AwakenTheme {
        GatesScreen(GatesUiState(loading = false, tiles = previewTiles, setup = GateSetup(GateRank.D, Stat.INT, "DSA – graphs")), onEvent = {})
    }
}

@PhonePreviews
@Composable
private fun GateRunPreview() {
    AwakenTheme {
        GatesScreen(
            GatesUiState(
                loading = false,
                tiles = previewTiles,
                active = ActiveGate(
                    rank = GateRank.C, stat = Stat.INT, label = "DSA – graphs", remaining = Duration.ofMinutes(31).plusSeconds(12),
                    total = Duration.ofMinutes(50), breachUsed = Duration.ofSeconds(25), paused = false, pauseLeft = Duration.ZERO, canPause = true,
                ),
            ),
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun GatePausedPreview() {
    AwakenTheme {
        GatesScreen(
            GatesUiState(
                loading = false,
                tiles = previewTiles,
                active = ActiveGate(
                    rank = GateRank.E, stat = Stat.WIL, label = null, remaining = Duration.ofMinutes(9), total = Duration.ofMinutes(15),
                    breachUsed = Duration.ZERO, paused = true, pauseLeft = Duration.ofMinutes(3).plusSeconds(40), canPause = false,
                ),
                confirmAbandon = true,
            ),
            onEvent = {},
        )
    }
}
