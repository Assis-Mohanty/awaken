package com.assismohanty.awaken.feature.quit

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.components.DetailRows
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.ConfirmDialog
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.MilestoneLadder
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.QuitCounterCard
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.awakenTextFieldColors
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.QuitCounterState
import com.assismohanty.awaken.core.domain.RestartEntry
import com.assismohanty.awaken.core.data.db.entity.QuitCounterEntity
import com.assismohanty.awaken.engine.QuitKind
import com.assismohanty.awaken.engine.QuitRules
import com.assismohanty.awaken.engine.model.Rank
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

@Composable
fun QuitRoute(onBack: () -> Unit, onOpenUsageAccess: () -> Unit = {}, modifier: Modifier = Modifier, viewModel: QuitViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(QuitEvent.Resumed)
        onPauseOrDispose { }
    }
    BackHandler(enabled = state.selectedId != null) { viewModel.onEvent(QuitEvent.CloseDetail) }
    QuitScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { if (state.selectedId != null) viewModel.onEvent(QuitEvent.CloseDetail) else onBack() },
        onOpenUsageAccess = onOpenUsageAccess,
        modifier = modifier,
    )
}

/** Quit counters (DESIGN_SYSTEM §9.3): the list, or one counter's detail with its milestones and its Restarts log. */
@Composable
fun QuitScreen(
    state: QuitUiState,
    onEvent: (QuitEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUsageAccess: () -> Unit = {},
) {
    val colors = AwakenTheme.colors
    val selected = state.selected
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(title = selected?.counter?.name ?: lexString(R.string.quit_title), onBack = onBack) {
            when {
                state.loading -> Unit
                selected != null -> QuitDetail(selected, state, onEvent, onOpenUsageAccess)
                else -> {
                    if (state.counters.isEmpty()) EmptyState(line = lexString(R.string.quit_empty), actionLabel = lexString(R.string.quit_add), onAction = { onEvent(QuitEvent.OpenAdd) })
                    state.counters.forEach { counter ->
                        QuitCounterCard(
                            name = counter.counter.name,
                            run = runText(counter),
                            best = bestText(counter),
                            progress = counter.milestoneProgress,
                            extra = extraText(counter, state.currency),
                            note = counterNote(counter, state.hasAccess),
                            onClick = { onEvent(QuitEvent.Open(counter.counter.id)) },
                        )
                    }
                    if (state.atLimit) {
                        Text(lexString(R.string.quit_limit), style = AwakenTheme.type.caption, color = colors.textSecondary)
                    }
                    if (state.counters.isNotEmpty()) PrimaryButton(
                        text = lexString(R.string.quit_add),
                        onClick = { onEvent(QuitEvent.OpenAdd) },
                        enabled = !state.atLimit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(lexString(R.string.quit_privacy), style = AwakenTheme.type.caption, color = colors.textMuted)
                    Text(lexString(R.string.quit_footer), style = AwakenTheme.type.caption, color = colors.textMuted)
                }
            }
        }
    }
    state.add?.let { AwakenBottomSheet(onDismiss = { onEvent(QuitEvent.CloseAdd) }, title = lexString(R.string.quit_add_title)) { AddContent(it, state, onEvent, onOpenUsageAccess) } }
    state.restart?.let { RestartDialog(it, onEvent) }
    if (state.confirmArchive) {
        ConfirmDialog(
            title = lexString(R.string.quit_archive_title),
            body = lexString(R.string.quit_archive_body),
            confirm = lexString(R.string.quit_archive),
            dismiss = lexString(R.string.action_cancel),
            onConfirm = { onEvent(QuitEvent.ConfirmArchive) },
            onDismiss = { onEvent(QuitEvent.DismissArchive) },
        )
    }
}

@Composable
private fun QuitDetail(counter: QuitCounterState, state: QuitUiState, onEvent: (QuitEvent) -> Unit, onOpenUsageAccess: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Text(runText(counter), style = type.metricXL, color = colors.textPrimary)
    Chart(ChartSpec.Progress(progress = counter.milestoneProgress, color = colors.accent))
    Text(nextText(counter), style = type.caption, color = colors.textSecondary)
    MilestoneLadder(QuitRules.MILESTONES, counter.currentDays)
    DetailRows(
        listOfNotNull(
            lexString(R.string.quit_row_best) to if (counter.best.toHours() > 0) durationText(counter.best) else lexString(R.string.quit_row_best_none),
            moneyText(counter, state.currency)?.let { lexString(R.string.quit_row_saved) to it },
            timeText(counter)?.let { lexString(R.string.quit_row_time) to it },
        ),
    )
    counterNote(counter, state.hasAccess)?.let { Text(it, style = type.caption, color = colors.textSecondary) }
    if (counter.counter.kind == QuitKind.DOOMSCROLLING) {
        Text(lexString(R.string.quit_doom_how), style = type.caption, color = colors.textSecondary)
        if (!state.hasAccess) SecondaryButton(text = lexString(R.string.quit_allow_access), onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth())
    }
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth(), title = lexString(R.string.quit_restarts_title)) {
        if (counter.restarts.isEmpty()) {
            Text(lexString(R.string.quit_restarts_empty), style = type.body, color = colors.textSecondary)
        }
        counter.restarts.forEach { RestartRow(it) }
    }
    if (counter.counter.kind.isTimeBased) {
        SecondaryButton(text = lexString(R.string.quit_restart_action), onClick = { onEvent(QuitEvent.OpenRestart) }, modifier = Modifier.fillMaxWidth())
    }
    QuietButton(text = lexString(R.string.quit_archive), onClick = { onEvent(QuitEvent.AskArchive) })
    Text(lexString(R.string.quit_footer), style = type.caption, color = colors.textMuted)
}

@Composable
private fun RestartRow(entry: RestartEntry) {
    val colors = AwakenTheme.colors
    val detail = when {
        entry.auto && entry.minutes != null -> lexString(R.string.quit_restart_auto, entry.minutes)
        else -> listOfNotNull(entry.trigger?.let { triggerName(it) }, entry.note).joinToString(" · ")
    }
    Text(formatDate(entry.at), style = AwakenTheme.type.label, color = colors.textPrimary)
    if (detail.isNotEmpty()) Text(detail, style = AwakenTheme.type.caption, color = colors.textSecondary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddContent(form: QuitAddForm, state: QuitUiState, onEvent: (QuitEvent) -> Unit, onOpenUsageAccess: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        QuitKind.entries.forEach { kind ->
            ChoiceChip(text = lexString(kindNameRes(kind)), selected = kind == form.kind, onClick = { onEvent(QuitEvent.PickKind(kind)) })
        }
    }
    if (form.kind == QuitKind.CUSTOM) {
        OutlinedTextField(
            value = form.customName,
            onValueChange = { onEvent(QuitEvent.CustomName(it)) },
            label = { Text(lexString(R.string.quit_name_label)) },
            singleLine = true,
            isError = form.error == QuitAddError.NAME,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = awakenTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (form.kind.isTimeBased) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ChoiceChip(text = lexString(R.string.quit_start_today), selected = !form.earlier, onClick = { onEvent(QuitEvent.SetEarlier(false)) })
            ChoiceChip(text = lexString(R.string.quit_start_earlier), selected = form.earlier, onClick = { onEvent(QuitEvent.SetEarlier(true)) })
        }
        if (form.earlier) {
            val lessCd = lexString(R.string.quit_days_less)
            val moreCd = lexString(R.string.quit_days_more)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = lexString(R.string.pomodoro_minus),
                    onClick = { onEvent(QuitEvent.StepDays(-1)) },
                    enabled = form.daysAgo > 1,
                    modifier = Modifier.semantics { contentDescription = lessCd },
                )
                Text(lexString(R.string.quit_start_days_ago, form.daysAgo), style = type.metricL, color = colors.textPrimary, modifier = Modifier.weight(1f))
                SecondaryButton(
                    text = lexString(R.string.pomodoro_plus),
                    onClick = { onEvent(QuitEvent.StepDays(1)) },
                    modifier = Modifier.semantics { contentDescription = moreCd },
                )
            }
            Text(lexString(R.string.quit_back_dated_note), style = type.caption, color = colors.textSecondary)
        }
        Text(lexString(R.string.quit_cost_label), style = type.label, color = colors.textPrimary)
        NumberField(value = form.cost, onValueChange = { onEvent(QuitEvent.Cost(it)) }, description = lexString(R.string.quit_cost_label), suffix = state.currency.ifEmpty { null })
        Text(lexString(R.string.quit_minutes_label), style = type.label, color = colors.textPrimary)
        NumberField(value = form.minutes, onValueChange = { onEvent(QuitEvent.Minutes(it)) }, description = lexString(R.string.quit_minutes_label), decimals = false)
        Text(lexString(R.string.quit_estimates_note), style = type.caption, color = colors.textSecondary)
    } else {
        Text(lexString(R.string.quit_doom_how), style = type.caption, color = colors.textSecondary)
        if (!state.hasAccess) {
            Text(lexString(R.string.quit_needs_access), style = type.caption, color = colors.textSecondary)
            SecondaryButton(text = lexString(R.string.quit_allow_access), onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth())
        }
    }
    form.error?.let {
        val message = when (it) {
            QuitAddError.NAME -> R.string.quit_name_label
            QuitAddError.LIMIT -> R.string.quit_limit
            QuitAddError.EXISTS -> R.string.quit_exists
            QuitAddError.START -> R.string.quit_start_earlier
        }
        Text(lexString(message), style = type.caption, color = colors.textSecondary)
    }
    val name = if (form.kind == QuitKind.CUSTOM) form.customName else lexString(kindNameRes(form.kind))
    PrimaryButton(
        text = lexString(R.string.quit_add_action),
        onClick = { onEvent(QuitEvent.SubmitAdd(name)) },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestartDialog(form: QuitRestartForm, onEvent: (QuitEvent) -> Unit) {
    val colors = AwakenTheme.colors
    AlertDialog(
        onDismissRequest = { onEvent(QuitEvent.CloseRestart) },
        containerColor = colors.surfaceOverlay,
        title = { Text(lexString(R.string.quit_restart_title), style = AwakenTheme.type.title, color = colors.textPrimary) },
        text = { RestartContent(form, onEvent) },
        confirmButton = { QuietButton(text = lexString(R.string.quit_restart_confirm), onClick = { onEvent(QuitEvent.ConfirmRestart) }) },
        dismissButton = { QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(QuitEvent.CloseRestart) }) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RestartContent(form: QuitRestartForm, onEvent: (QuitEvent) -> Unit) {
    val colors = AwakenTheme.colors
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(lexString(R.string.quit_restart_body), style = AwakenTheme.type.body, color = colors.textSecondary)
        Text(lexString(R.string.quit_trigger_label), style = AwakenTheme.type.label, color = colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TRIGGERS.forEach { key ->
                ChoiceChip(
                    text = triggerName(key),
                    selected = form.trigger == key,
                    onClick = { onEvent(QuitEvent.PickTrigger(if (form.trigger == key) null else key)) },
                )
            }
        }
        OutlinedTextField(
            value = form.note,
            onValueChange = { onEvent(QuitEvent.Note(it)) },
            label = { Text(lexString(R.string.quit_note_label)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = awakenTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Words

private val TRIGGERS = listOf("STRESS", "BOREDOM", "SOCIAL", "TIRED", "OTHER")

@Composable
private fun triggerName(key: String): String = lexString(
    when (key) {
        "STRESS" -> R.string.quit_trigger_stress
        "BOREDOM" -> R.string.quit_trigger_boredom
        "SOCIAL" -> R.string.quit_trigger_social
        "TIRED" -> R.string.quit_trigger_tired
        else -> R.string.quit_trigger_other
    },
)

@StringRes
internal fun kindNameRes(kind: QuitKind): Int = when (kind) {
    QuitKind.SMOKING -> R.string.quit_kind_smoking
    QuitKind.PORN -> R.string.quit_kind_porn
    QuitKind.JUNK_FOOD -> R.string.quit_kind_junk_food
    QuitKind.DOOMSCROLLING -> R.string.quit_kind_doomscrolling
    QuitKind.ALCOHOL -> R.string.quit_kind_alcohol
    QuitKind.CUSTOM -> R.string.quit_kind_custom
}

/** "12 days" or, under a day, "7 h" ("Just started" before the first hour). */
@Composable
private fun durationText(duration: Duration): String = when {
    duration.toDays() > 0 -> lexPlural(R.plurals.quit_days, duration.toDays().toInt(), duration.toDays().toInt())
    duration.toHours() > 0 -> lexString(R.string.quit_hours, duration.toHours().toInt())
    else -> lexString(R.string.quit_just_started)
}

@Composable
private fun runText(counter: QuitCounterState): String = durationText(counter.current)

@Composable
private fun bestText(counter: QuitCounterState): String =
    if (counter.best.toHours() > 0) lexString(R.string.quit_best, durationText(counter.best)) else lexString(R.string.quit_best_none)

@Composable
private fun nextText(counter: QuitCounterState): String =
    counter.nextMilestone?.let { lexString(R.string.quit_next, lexPlural(R.plurals.quit_days, it, it)) } ?: lexString(R.string.quit_all_milestones)

@Composable
private fun moneyText(counter: QuitCounterState, fallbackCurrency: String): String? =
    counter.moneySavedMinor?.takeIf { counter.counter.costPerDayMinor != null }?.let { formatMoney(it, counter.counter.currency ?: fallbackCurrency) }

@Composable
private fun timeText(counter: QuitCounterState): String? = counter.minutesReclaimed?.let { minutes ->
    if (minutes >= MINUTES_PER_HOUR) lexString(R.string.quit_time_h, (minutes / MINUTES_PER_HOUR).toInt()) else lexString(R.string.quit_time_min, minutes.toInt())
}

@Composable
private fun extraText(counter: QuitCounterState, fallbackCurrency: String): String? {
    val money = moneyText(counter, fallbackCurrency)
    val time = timeText(counter)
    return when {
        money != null && time != null -> lexString(R.string.quit_saved_and_time, money, time)
        money != null -> lexString(R.string.quit_saved_only, money)
        time != null -> lexString(R.string.quit_time_only, time)
        else -> null
    }
}

@Composable
private fun counterNote(counter: QuitCounterState, hasAccess: Boolean): String? = when {
    counter.counter.kind != QuitKind.DOOMSCROLLING -> null
    !hasAccess -> lexString(R.string.quit_needs_access)
    counter.paused -> lexString(R.string.quit_paused)
    else -> null
}

private fun formatMoney(minor: Long, code: String): String = try {
    val currency = Currency.getInstance(code)
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { this.currency = currency }.format(minor / Math.pow(10.0, digits.toDouble()))
} catch (e: IllegalArgumentException) {
    minor.toString()
}

private fun formatDate(at: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(at)

private const val MINUTES_PER_HOUR = 60L

// Previews

private fun counter(
    kind: QuitKind,
    name: String,
    currentDays: Long,
    bestDays: Long,
    restarts: List<RestartEntry> = emptyList(),
    cost: Long? = null,
    minutes: Int? = null,
    paused: Boolean = false,
    currentHours: Long = 0,
): QuitCounterState {
    val days = currentDays.toInt()
    return QuitCounterState(
        counter = QuitCounterEntity("c-$name", kind, name, Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T08:00:00Z"), cost, "INR", minutes),
        current = Duration.ofDays(currentDays).plusHours(currentHours),
        best = Duration.ofDays(bestDays),
        reachedMilestone = QuitRules.reachedMilestone(days),
        nextMilestone = QuitRules.nextMilestone(days),
        milestoneProgress = QuitRules.milestoneProgress(days),
        moneySavedMinor = cost?.let { it * currentDays },
        minutesReclaimed = minutes?.let { it * currentDays },
        restarts = restarts,
        paused = paused,
    )
}

private val previewRestarts = listOf(
    RestartEntry(Instant.parse("2026-10-20T20:00:00Z"), "STRESS", "Long week", auto = false, minutes = null),
    RestartEntry(Instant.parse("2026-10-08T18:00:00Z"), null, null, auto = false, minutes = null),
)

private fun previewState(vararg counters: QuitCounterState) = QuitUiState(loading = false, counters = counters.toList(), currency = "INR")

private val previewList = previewState(
    counter(QuitKind.SMOKING, "Smoking", 12, 30, previewRestarts, cost = 150, minutes = 20),
    counter(QuitKind.ALCOHOL, "Alcohol", 0, 0, currentHours = 7),
    counter(QuitKind.DOOMSCROLLING, "Doomscrolling", 3, 5, paused = true),
)

@PhonePreviews
@Composable
private fun QuitListPreview() = AwakenTheme(reduceMotion = true) { QuitScreen(previewList, {}, {}) }

@PhonePreviews
@Composable
private fun QuitEmptyPreview() = AwakenTheme(reduceMotion = true) { QuitScreen(previewState(), {}, {}) }

@PhonePreviews
@Composable
private fun QuitDetailPreview() = AwakenTheme(reduceMotion = true) {
    QuitScreen(previewList.copy(selectedId = "c-Smoking"), {}, {})
}

@PhonePreviews
@Composable
private fun QuitDetailNoRestartsPreview() = AwakenTheme(reduceMotion = true) {
    QuitScreen(previewList.copy(selectedId = "c-Alcohol"), {}, {})
}

@PhonePreviews
@Composable
private fun QuitDoomscrollingPausedPreview() = AwakenTheme(reduceMotion = true) {
    QuitScreen(previewList.copy(selectedId = "c-Doomscrolling"), {}, {})
}

@PhonePreviews
@Composable
private fun QuitDoomscrollingNoAccessPreview() = AwakenTheme(reduceMotion = true) {
    QuitScreen(previewList.copy(selectedId = "c-Doomscrolling", hasAccess = false), {}, {})
}

@PhonePreviews
@Composable
private fun QuitAddPreview() = AwakenTheme(reduceMotion = true) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.androidxPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { AddContent(QuitAddForm(earlier = true, daysAgo = 12), previewList, {}, {}) }
}

@PhonePreviews
@Composable
private fun QuitRestartPreview() = AwakenTheme(reduceMotion = true) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.androidxPadding()) {
        RestartContent(QuitRestartForm(trigger = "STRESS", note = "Long week"), {})
    }
}

private fun Modifier.androidxPadding(): Modifier = this.then(Modifier.padding(Spacing.md))
