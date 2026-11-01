package com.assismohanty.awaken.feature.gate

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.components.SelectableTile
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.DifficultyChip
import com.assismohanty.awaken.core.designsystem.components.IconCircle
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.GateDial
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.StatChip
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.GATE_STATS
import com.assismohanty.awaken.engine.FocusRules
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GateRules
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.quest.shortClock
import java.time.Duration

@Composable
fun GatesEntry(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: GatesViewModel = hiltViewModel()) {
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
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Focus Gates (DESIGN_SYSTEM §4.4): the large title over the E/D/C/B gates as a 2 × 2 grid of cards, then the setup
 * card; while a gate is open, the dial is the one big thing on the screen.
 */
@Composable
fun GatesScreen(state: GatesUiState, onEvent: (GatesEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val active = state.active
    LargeTitleScaffold(
        title = when {
            active == null -> lexString(R.string.gates_title)
            active.mode == GateMode.STOPWATCH -> lexString(R.string.focus_stopwatch_title)
            else -> lexString(R.string.gate_name, active.rank.name)
        },
        onBack = if (active == null) onBack else null,
        modifier = modifier,
    ) {
        when {
            state.loading -> SkeletonCard()
            active != null -> GateRun(active, state.confirmAbandon, onEvent)
            else -> GatePicker(state, onEvent)
        }
    }
}

@Composable
private fun GatePicker(state: GatesUiState, onEvent: (GatesEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Text(
        lexString(R.string.gates_body, shortClock(GateRules.BREACH_BUDGET_MS)),
        style = AwakenTheme.type.body,
        color = colors.textSecondary,
    )
    FocusModeChips(state.setup?.mode ?: GateMode.PRESET, onEvent)
    val setup = state.setup
    if (setup != null && setup.mode != GateMode.PRESET) {
        FocusSetupWindow(setup, state, onEvent)
        return
    }
    state.tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusModeChips(selected: GateMode, onEvent: (GatesEvent) -> Unit) {
    val colors = AwakenTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            GateMode.PRESET to R.string.focus_mode_preset,
            GateMode.CUSTOM to R.string.focus_mode_custom,
            GateMode.STOPWATCH to R.string.focus_mode_stopwatch,
        ).forEach { (mode, label) ->
            ChoiceChip(
                text = lexString(label),
                selected = selected == mode,
                onClick = { onEvent(if (mode == GateMode.PRESET) GatesEvent.CloseSetup else GatesEvent.PickMode(mode)) },
                color = colors.accent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSetupWindow(setup: GateSetup, state: GatesUiState, onEvent: (GatesEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val custom = setup.mode == GateMode.CUSTOM
    SystemWindow(title = lexString(if (custom) R.string.focus_custom_title else R.string.focus_stopwatch_title), accent = colors.accent) {
        if (custom) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SecondaryButton(
                    text = lexString(R.string.focus_minus),
                    onClick = { onEvent(GatesEvent.MinutesChanged(setup.minutes - FocusRules.CUSTOM_STEP_MINUTES)) },
                    enabled = setup.minutes > FocusRules.CUSTOM_MIN_MINUTES,
                )
                Text(
                    lexString(R.string.focus_custom_minutes, setup.minutes),
                    style = AwakenTheme.type.metricL,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = lexString(R.string.focus_plus),
                    onClick = { onEvent(GatesEvent.MinutesChanged(setup.minutes + FocusRules.CUSTOM_STEP_MINUTES)) },
                    enabled = setup.minutes < FocusRules.CUSTOM_MAX_MINUTES,
                )
            }
        }
        Text(
            lexString(if (custom) R.string.focus_custom_rule else R.string.focus_stopwatch_rule),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
        )
        Text(lexString(R.string.gate_stat_label), style = AwakenTheme.type.label, color = colors.textSecondary)
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
        Text(lexString(R.string.focus_subject_label), style = AwakenTheme.type.label, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ChoiceChip(
                text = lexString(R.string.focus_subject_none),
                selected = setup.subjectId == null,
                onClick = { onEvent(GatesEvent.SubjectPicked(null)) },
                color = colors.accent,
            )
            state.subjects.forEach { subject ->
                ChoiceChip(
                    text = subject.name,
                    selected = setup.subjectId == subject.id,
                    onClick = { onEvent(GatesEvent.SubjectPicked(subject.id)) },
                    color = colors.accent,
                )
            }
            if (state.canAddSubject && !state.addingSubject) {
                ChoiceChip(text = lexString(R.string.focus_subject_add), selected = false, onClick = { onEvent(GatesEvent.StartAddSubject) }, color = colors.accent)
            }
        }
        if (!state.canAddSubject) {
            Text(lexString(R.string.focus_subject_limit), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        if (state.addingSubject) {
            OutlinedTextField(
                value = state.subjectDraft,
                onValueChange = { onEvent(GatesEvent.SubjectDraftChanged(it)) },
                label = { Text(lexString(R.string.focus_subject_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.divider,
                    focusedLabelColor = colors.accent,
                    cursorColor = colors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(text = lexString(R.string.focus_subject_save), onClick = { onEvent(GatesEvent.SaveSubject) }, enabled = state.subjectDraft.isNotBlank(), modifier = Modifier.fillMaxWidth())
            QuietButton(text = lexString(R.string.action_back), onClick = { onEvent(GatesEvent.CancelAddSubject) }, modifier = Modifier.fillMaxWidth())
        }
        Text(lexString(R.string.gate_rule, shortClock(GateRules.BREACH_BUDGET_MS)), style = AwakenTheme.type.caption, color = colors.textSecondary)
        PrimaryButton(text = lexString(R.string.action_enter_gate), onClick = { onEvent(GatesEvent.Enter) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GatePortal(tile: GateTile, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val gateColor = if (tile.available) colors.gate(tile.rank) else colors.textMuted
    val name = lexString(R.string.gate_name, tile.rank.name)
    val minutes = lexString(R.string.gate_minutes, tile.minutes)
    val description = if (tile.available) {
        lexString(R.string.cd_gate_tile, name, minutes)
    } else {
        lexString(R.string.cd_gate_tile_late, name, minutes)
    }
    SelectableTile(
        accent = gateColor,
        selected = selected,
        enabled = tile.available,
        description = description,
        onClick = onClick,
        modifier = modifier,
    ) {
        IconCircle(AwakenGlyphs.Portal, gateColor)
        Text(name, style = AwakenTheme.type.windowTitle, color = if (tile.available) colors.textPrimary else colors.textMuted)
        Text(minutes, style = AwakenTheme.type.metricL, color = if (tile.available) colors.textPrimary else colors.textMuted)
        if (tile.available) {
            DifficultyChip(tile.rank.difficulty)
        } else {
            Text(lexString(R.string.gate_tile_late), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GateSetupWindow(setup: GateSetup, onEvent: (GatesEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.gate_name, setup.rank.name), accent = colors.gate(setup.rank)) {
        Text(lexString(R.string.gate_stat_label), style = AwakenTheme.type.label, color = colors.textSecondary)
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
            label = { Text(lexString(R.string.gate_label_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.divider,
                focusedLabelColor = colors.accent,
                cursorColor = colors.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(lexString(R.string.gate_rule, shortClock(GateRules.BREACH_BUDGET_MS)), style = AwakenTheme.type.caption, color = colors.textSecondary)
        PrimaryButton(
            text = lexString(R.string.action_enter_gate),
            onClick = { onEvent(GatesEvent.Enter) },
            modifier = Modifier.fillMaxWidth(),
        )
        QuietButton(text = lexString(R.string.action_back), onClick = { onEvent(GatesEvent.CloseSetup) }, modifier = Modifier.fillMaxWidth())
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            StatChip(active.stat)
            active.label?.let { Text(it, style = AwakenTheme.type.label, color = colors.textPrimary) }
        }
        if (active.mode == GateMode.STOPWATCH) {
            Text(lexString(R.string.focus_elapsed_caption), style = AwakenTheme.type.label, color = colors.textSecondary)
            Text(shortClock(active.elapsed.toMillis()), style = AwakenTheme.type.metricL, color = colors.textPrimary)
            PrimaryButton(lexString(R.string.action_stop_stopwatch), onClick = { onEvent(GatesEvent.Stop) }, modifier = Modifier.fillMaxWidth())
        } else {
            GateDial(gate = active.rank, remaining = active.remaining, total = active.total, breachUsed = active.breachUsed)
        }
        active.subject?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary) }
        Text(
            lexString(R.string.gate_breach_line, shortClock(active.breachUsed.toMillis()), shortClock(GateRules.BREACH_BUDGET_MS)),
            style = AwakenTheme.type.label,
            color = if (active.breachUsed.isZero) colors.textSecondary else colors.critical,
        )
        when {
            active.paused -> {
                Text(lexString(R.string.gate_paused_left, shortClock(active.pauseLeft.toMillis())), style = AwakenTheme.type.label, color = colors.textPrimary)
                PrimaryButton(lexString(R.string.action_resume), onClick = { onEvent(GatesEvent.Resume) }, modifier = Modifier.fillMaxWidth())
            }
            active.canPause -> SecondaryButton(
                text = lexString(R.string.action_pause, shortClock(GateRules.MAX_PAUSE.toMillis())),
                onClick = { onEvent(GatesEvent.Pause) },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Text(lexString(R.string.gate_pause_used), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        if (confirmAbandon) {
            SystemWindow(title = null, accent = colors.critical, reveal = false) {
                Text(lexString(R.string.gate_abandon_confirm), style = AwakenTheme.type.body, color = colors.textPrimary)
                PrimaryButton(lexString(R.string.action_abandon_gate), onClick = { onEvent(GatesEvent.ConfirmAbandon) }, color = colors.critical, modifier = Modifier.fillMaxWidth())
                QuietButton(lexString(R.string.action_keep_going), onClick = { onEvent(GatesEvent.CancelAbandon) }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            QuietButton(lexString(R.string.action_abandon_gate), onClick = { onEvent(GatesEvent.Abandon) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val previewTiles = GateRank.entries.map { GateTile(it, it.durationMinutes, available = it != GateRank.B) }

@PhonePreviews
@Composable
private fun GatesPickerPreview() {
    AwakenTheme {
        GatesScreen(GatesUiState(loading = false, tiles = previewTiles, setup = GateSetup(GateRank.D, Stat.INT, "DSA – graphs")), onEvent = {}, onBack = {})
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
            onBack = {},
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
            onBack = {},
        )
    }
}

@PhonePreviews
@Composable
private fun GatesCustomPreview() {
    AwakenTheme {
        GatesScreen(
            GatesUiState(
                loading = false,
                tiles = previewTiles,
                setup = GateSetup(GateRank.D, Stat.INT, "", GateMode.CUSTOM, minutes = 45, subjectId = "s1"),
                subjects = listOf(SubjectUi("s1", "DSA"), SubjectUi("s2", "Reading")),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@PhonePreviews
@Composable
private fun GateStopwatchPreview() {
    AwakenTheme {
        GatesScreen(
            GatesUiState(
                loading = false,
                tiles = previewTiles,
                active = ActiveGate(
                    rank = GateRank.E, mode = GateMode.STOPWATCH, elapsed = Duration.ofMinutes(37).plusSeconds(5), subject = "DSA",
                    stat = Stat.INT, label = null, remaining = Duration.ofDays(1), total = Duration.ofDays(1),
                    breachUsed = Duration.ofSeconds(10), paused = false, pauseLeft = Duration.ZERO, canPause = true,
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
