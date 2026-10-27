package com.assismohanty.awaken.feature.campaign

import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.BarShape
import com.assismohanty.awaken.core.designsystem.charts.CalendarCell
import com.assismohanty.awaken.core.designsystem.components.HoldToConfirmRing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.RadarChart
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.StatChip
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.components.unitLabel
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.CampaignHabitPreview
import com.assismohanty.awaken.core.domain.CampaignModel
import com.assismohanty.awaken.core.domain.CampaignPreview
import com.assismohanty.awaken.core.domain.CampaignProblem
import com.assismohanty.awaken.core.domain.toDays
import com.assismohanty.awaken.engine.campaign.Archetype
import com.assismohanty.awaken.engine.campaign.CampaignAct
import com.assismohanty.awaken.engine.campaign.CampaignRules
import com.assismohanty.awaken.engine.campaign.CampaignScan
import com.assismohanty.awaken.engine.campaign.CampaignStatus
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CampaignRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: CampaignViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CampaignScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** The 45-day Campaign (DESIGN_SYSTEM §4.10): intro → scan → forge → result → oath, then progress. */
@Composable
fun CampaignScreen(state: CampaignUiState, onEvent: (CampaignEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    GateBackground(rank = Rank.E, modifier = modifier) {
        Column(
            modifier = Modifier
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.campaign_title),
                        style = AwakenTheme.type.metricL,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    QuietButton(text = stringResource(R.string.action_back), onClick = onBack)
                }
                if (state.loading) return@Column
                when (state.step) {
                    CampaignStep.INTRO -> IntroStep(onEvent)
                    CampaignStep.SCAN -> ScanStep(state, onEvent)
                    CampaignStep.FORGE -> ForgeStep(ready = state.preview != null, onFinished = { onEvent(CampaignEvent.ForgeFinished) })
                    CampaignStep.RESULT -> state.preview?.let { ResultStep(it, state.error, onEvent) }
                    CampaignStep.OATH -> state.preview?.let { OathStep(state.hunterName, it.endDate, state.sealing, onSealed = { onEvent(CampaignEvent.Seal) }) }
                    CampaignStep.PROGRESS -> state.campaign?.let { ProgressStep(it, onEvent) } ?: IntroStep(onEvent)
                }
            }
        }
    }
    if (state.confirmAbandon) {
        AlertDialog(
            onDismissRequest = { onEvent(CampaignEvent.DismissAbandon) },
            containerColor = colors.surfaceOverlay,
            title = { Text(stringResource(R.string.campaign_abandon_title), style = AwakenTheme.type.title, color = colors.textPrimary) },
            text = { Text(stringResource(R.string.campaign_abandon_body), style = AwakenTheme.type.body, color = colors.textSecondary) },
            confirmButton = { QuietButton(text = stringResource(R.string.campaign_abandon_confirm), onClick = { onEvent(CampaignEvent.ConfirmAbandon) }) },
            dismissButton = { QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(CampaignEvent.DismissAbandon) }) },
        )
    }
}

@Composable
private fun IntroStep(onEvent: (CampaignEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.campaign_window_intro), accent = colors.accent, modifier = Modifier.staggeredEntrance(0)) {
        Text(stringResource(R.string.campaign_intro_headline), style = type.title, color = colors.textPrimary)
        Text(stringResource(R.string.campaign_intro_body), style = type.body, color = colors.textSecondary)
        CampaignAct.entries.forEach { act ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(act.numeralRes), style = type.numeric, color = colors.accent)
                Column {
                    Text(stringResource(act.nameRes), style = type.label, color = colors.textPrimary)
                    Text(stringResource(act.daysRes), style = type.caption, color = colors.textSecondary)
                }
            }
        }
        Text(stringResource(R.string.campaign_intro_rule), style = type.caption, color = colors.reward)
        PrimaryButton(text = stringResource(R.string.campaign_begin_scan), onClick = { onEvent(CampaignEvent.Begin) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ScanStep(state: CampaignUiState, onEvent: (CampaignEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val (id, stat) = CampaignScan.QUESTIONS[state.question]
    SystemWindow(
        title = stringResource(R.string.campaign_window_scan),
        accent = colors.stat(stat),
        headerTrailing = {
            Text(stringResource(R.string.campaign_scan_progress, state.question + 1, state.questionCount), style = type.numericSmall, color = colors.textSecondary)
        },
    ) {
        StatChip(stat)
        Text(stringResource(questionRes(id)), style = type.title, color = colors.textPrimary)
        ANSWER_LABELS.forEachIndexed { value, label ->
            val chosen = state.answers[id] == value
            if (chosen) {
                PrimaryButton(text = stringResource(label), onClick = { onEvent(CampaignEvent.Answer(value)) }, modifier = Modifier.fillMaxWidth())
            } else {
                SecondaryButton(text = stringResource(label), onClick = { onEvent(CampaignEvent.Answer(value)) }, modifier = Modifier.fillMaxWidth())
            }
        }
        QuietButton(text = stringResource(R.string.action_back), onClick = { onEvent(CampaignEvent.BackQuestion) })
    }
}

/** "Forging your campaign": a counter and three checks, then RESULT once the plan is ready (DS §4.10). */
@Composable
private fun ForgeStep(ready: Boolean, onFinished: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val motion = AwakenTheme.motion
    val progress = remember { Animatable(if (motion.reduceMotion) 1f else 0f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(motion.duration(FORGE_MS), easing = LinearEasing))
    }
    LaunchedEffect(ready, progress.value >= 1f) {
        if (ready && progress.value >= 1f) {
            delay(motion.duration(motion.standardMs).toLong())
            finish()
        }
    }
    val percent = (progress.value * 100).toInt()
    SystemWindow(title = stringResource(R.string.campaign_window_forge), accent = colors.accent, reveal = false) {
        Text(stringResource(R.string.campaign_forge_headline), style = type.title, color = colors.textPrimary)
        Text(
            stringResource(R.string.campaign_forge_percent, percent),
            style = type.metricXL,
            color = colors.textPrimary,
        )
        Chart(ChartSpec.Progress(progress = progress.value, color = colors.accent, track = colors.divider, shape = BarShape.SQUARE))
        FORGE_STEPS.forEachIndexed { index, step ->
            val done = progress.value >= (index + 1f) / FORGE_STEPS.size
            Text(
                text = (if (done) "✓  " else "·  ") + stringResource(step),
                style = type.label,
                color = if (done) colors.textPrimary else colors.textMuted,
            )
        }
    }
}

@Composable
private fun ResultStep(preview: CampaignPreview, error: CampaignProblem?, onEvent: (CampaignEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val accent = colors.stat(preview.weakest)
    SystemWindow(title = stringResource(R.string.campaign_window_result), accent = accent, modifier = Modifier.staggeredEntrance(0)) {
        Text(stringResource(preview.archetype.nameRes), style = type.metricL, color = colors.textPrimary)
        Text(stringResource(preview.archetype.lineRes), style = type.body, color = colors.textSecondary)
        Chart(
            ChartSpec.Radar(
                values = preview.scores.mapValues { (_, score) -> score + 1f },
                maxValue = (CampaignScan.MAX_ANSWER * 2 + 1).toFloat(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatChip(preview.weakest)
            Text(stringResource(R.string.campaign_weakest, preview.weakest.label()), style = type.title, color = accent)
        }
    }
    SystemWindow(title = stringResource(R.string.campaign_window_plan), accent = colors.accent, modifier = Modifier.staggeredEntrance(1)) {
        Text(
            stringResource(R.string.campaign_plan_dates, formatDate(preview.startDate), formatDate(preview.endDate)),
            style = type.caption,
            color = colors.textSecondary,
        )
        preview.habits.forEach { PlannedHabitRow(it) }
        val problem = error ?: preview.problem
        if (problem != null) {
            Text(stringResource(problem.messageRes), style = type.body, color = colors.critical)
        } else {
            Text(stringResource(R.string.campaign_plan_note), style = type.caption, color = colors.textSecondary)
            PrimaryButton(text = stringResource(R.string.campaign_to_oath), onClick = { onEvent(CampaignEvent.ToOath) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlannedHabitRow(habit: CampaignHabitPreview) {
    val colors = AwakenTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatChip(habit.stat)
        Column(modifier = Modifier.weight(1f)) {
            Text(habit.title, style = AwakenTheme.type.label, color = colors.textPrimary)
            Text(
                "${habit.target} ${unitLabel(habit.unit, habit.target)} · ${daysText(habit.days)}",
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** The oath: name + end date, sealed by holding the seal for 1.5 s (a TalkBack tap also seals). */
@Composable
private fun OathStep(name: String, endDate: LocalDate, sealing: Boolean, onSealed: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.campaign_window_oath), accent = colors.reward) {
        Text(stringResource(R.string.campaign_oath_body, name.ifBlank { stringResource(R.string.campaign_oath_hunter) }), style = type.body, color = colors.textPrimary)
        Text(stringResource(R.string.campaign_oath_end, formatDate(endDate)), style = type.title, color = colors.reward)
        HoldToConfirmRing(
            label = stringResource(R.string.campaign_oath_hold),
            idleText = stringResource(R.string.campaign_oath_hold_short),
            doneText = stringResource(R.string.campaign_oath_sealed),
            enabled = !sealing,
            onConfirmed = onSealed,
        )
        Text(stringResource(R.string.campaign_oath_note), style = type.caption, color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProgressStep(model: CampaignModel, onEvent: (CampaignEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val campaign = model.campaign
    when (campaign.status) {
        CampaignStatus.ACTIVE -> {
            val day = model.dayIndex ?: 1
            SystemWindow(
                title = stringResource(R.string.campaign_window_progress),
                accent = colors.accent,
                modifier = Modifier.staggeredEntrance(0),
                headerTrailing = { Text(stringResource(model.act.numeralRes), style = type.numericSmall, color = colors.accent) },
            ) {
                Text(stringResource(R.string.campaign_day_of, day, CampaignRules.LENGTH_DAYS), style = type.metricXL, color = colors.textPrimary)
                Text(stringResource(model.act.nameRes), style = type.title, color = colors.textPrimary)
                Text(
                    pluralStringResource(R.plurals.campaign_cleared_of_needed, model.clearedDays, model.clearedDays, CampaignRules.COMPLETION_CLEARED_DAYS),
                    style = type.label,
                    color = colors.textSecondary,
                )
                Text(
                    stringResource(if (model.reachable) R.string.campaign_reachable else R.string.campaign_unreachable),
                    style = type.caption,
                    color = if (model.reachable) colors.positive else colors.reward,
                )
                CampaignCalendar(model)
                Text(stringResource(R.string.campaign_ends, formatDate(model.endDate)), style = type.caption, color = colors.textSecondary)
            }
        }
        CampaignStatus.COMPLETED -> FinishedWindow(stringResource(R.string.campaign_completed_title), stringResource(R.string.campaign_completed_body, campaign.clearedDays), colors.reward, onEvent)
        CampaignStatus.ENDED -> FinishedWindow(stringResource(R.string.campaign_ended_title), stringResource(R.string.campaign_ended_body, campaign.clearedDays), colors.accent, onEvent)
        CampaignStatus.ABANDONED -> FinishedWindow(stringResource(R.string.campaign_abandoned_title), stringResource(R.string.campaign_abandoned_body), colors.textSecondary, onEvent)
    }
    if (campaign.status == CampaignStatus.ACTIVE) {
        SystemWindow(title = stringResource(R.string.campaign_window_habits), accent = colors.accent, modifier = Modifier.staggeredEntrance(1)) {
            Text(stringResource(campaign.archetype.nameRes), style = type.label, color = colors.textSecondary)
            model.habits.filter { it.active }.forEach { habit ->
                PlannedHabitRow(CampaignHabitPreview(habit.templateId.orEmpty(), habit.stat, habit.title, habit.unit, habit.daysOfWeekMask.toDays(), habit.difficulty, habit.target))
            }
            Text(stringResource(R.string.campaign_habits_note), style = type.caption, color = colors.textSecondary)
            QuietButton(text = stringResource(R.string.campaign_abandon), onClick = { onEvent(CampaignEvent.AskAbandon) })
        }
    }
}

@Composable
private fun FinishedWindow(title: String, body: String, accent: androidx.compose.ui.graphics.Color, onEvent: (CampaignEvent) -> Unit) {
    SystemWindow(title = title, accent = accent, modifier = Modifier.staggeredEntrance(0)) {
        Text(body, style = AwakenTheme.type.body, color = AwakenTheme.colors.textPrimary)
        PrimaryButton(text = stringResource(R.string.campaign_start_new), onClick = { onEvent(CampaignEvent.StartAnew) }, modifier = Modifier.fillMaxWidth())
    }
}

/** 45 cells, nine to a row: cleared, today, missed and still to come. */
@Composable
private fun CampaignCalendar(model: CampaignModel) {
    val description = pluralStringResource(R.plurals.campaign_calendar_cd, model.clearedDays, model.clearedDays, model.dayIndex ?: 0)
    val cells = (0 until CampaignRules.LENGTH_DAYS).map { offset ->
        val date = model.campaign.startDate.plusDays(offset.toLong())
        when {
            date in model.clearedDates -> CalendarCell.FILLED
            date == model.today -> CalendarCell.CURRENT
            date.isBefore(model.today) -> CalendarCell.PAST
            else -> CalendarCell.UPCOMING
        }
    }
    Chart(ChartSpec.CalendarGrid(cells = cells, columns = CALENDAR_COLUMNS, description = description))
}

@Composable
private fun daysText(days: Set<DayOfWeek>): String =
    if (days.size == DayOfWeek.entries.size) {
        stringResource(R.string.campaign_every_day)
    } else {
        days.sorted().joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

private fun formatDate(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(date)

private val CampaignAct.nameRes: Int
    get() = when (this) {
        CampaignAct.FOUNDATION -> R.string.campaign_act_foundation
        CampaignAct.BUILD -> R.string.campaign_act_build
        CampaignAct.ASCEND -> R.string.campaign_act_ascend
    }

private val CampaignAct.numeralRes: Int
    get() = when (this) {
        CampaignAct.FOUNDATION -> R.string.campaign_act_i
        CampaignAct.BUILD -> R.string.campaign_act_ii
        CampaignAct.ASCEND -> R.string.campaign_act_iii
    }

private val CampaignAct.daysRes: Int
    get() = when (this) {
        CampaignAct.FOUNDATION -> R.string.campaign_act_foundation_days
        CampaignAct.BUILD -> R.string.campaign_act_build_days
        CampaignAct.ASCEND -> R.string.campaign_act_ascend_days
    }

internal val Archetype.nameRes: Int
    get() = when (this) {
        Archetype.REBUILDER -> R.string.archetype_rebuilder
        Archetype.SCHOLAR -> R.string.archetype_scholar
        Archetype.IRON_WILL -> R.string.archetype_iron_will
        Archetype.RESTORER -> R.string.archetype_restorer
        Archetype.ENVOY -> R.string.archetype_envoy
    }

private val Archetype.lineRes: Int
    get() = when (this) {
        Archetype.REBUILDER -> R.string.archetype_rebuilder_line
        Archetype.SCHOLAR -> R.string.archetype_scholar_line
        Archetype.IRON_WILL -> R.string.archetype_iron_will_line
        Archetype.RESTORER -> R.string.archetype_restorer_line
        Archetype.ENVOY -> R.string.archetype_envoy_line
    }

private val CampaignProblem.messageRes: Int
    get() = when (this) {
        CampaignProblem.ACTIVE -> R.string.campaign_problem_active
        CampaignProblem.NO_ROOM -> R.string.campaign_problem_no_room
        CampaignProblem.NO_HABITS -> R.string.campaign_problem_no_habits
    }

private fun questionRes(id: String): Int = when (id) {
    "str_move" -> R.string.scan_str_move
    "str_strength" -> R.string.scan_str_strength
    "int_learn" -> R.string.scan_int_learn
    "int_focus" -> R.string.scan_int_focus
    "wil_follow" -> R.string.scan_wil_follow
    "wil_phone" -> R.string.scan_wil_phone
    "vit_sleep" -> R.string.scan_vit_sleep
    "vit_fuel" -> R.string.scan_vit_fuel
    "cha_connect" -> R.string.scan_cha_connect
    else -> R.string.scan_cha_speak
}

private val ANSWER_LABELS = listOf(R.string.scan_answer_never, R.string.scan_answer_sometimes, R.string.scan_answer_often, R.string.scan_answer_always)
private val FORGE_STEPS = listOf(R.string.campaign_forge_step_scan, R.string.campaign_forge_step_quests, R.string.campaign_forge_step_schedule)
private const val FORGE_MS = 2_400
private const val CALENDAR_COLUMNS = 9

internal fun previewCampaignPreview(): CampaignPreview {
    val start = LocalDate.of(2026, 10, 25)
    return CampaignPreview(
        scores = mapOf(Stat.STR to 1, Stat.INT to 4, Stat.WIL to 3, Stat.VIT to 5, Stat.CHA to 6),
        weakest = Stat.STR,
        archetype = Archetype.REBUILDER,
        habits = listOf(
            CampaignHabitPreview("a", Stat.STR, "Do push-ups", "reps", DayOfWeek.entries.toSet(), Difficulty.EASY, 20),
            CampaignHabitPreview("b", Stat.STR, "Take an easy walk", "min", setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), Difficulty.EASY, 15),
            CampaignHabitPreview("c", Stat.WIL, "Still Mind", "min", setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY), Difficulty.EASY, 5),
        ),
        startDate = start,
        endDate = CampaignRules.endDate(start),
        problem = null,
    )
}

@PhonePreviews
@Composable
private fun CampaignResultPreview() {
    AwakenTheme(reduceMotion = true) {
        CampaignScreen(
            state = CampaignUiState(loading = false, step = CampaignStep.RESULT, preview = previewCampaignPreview()),
            onEvent = {},
            onBack = {},
        )
    }
}

@PhonePreviews
@Composable
private fun CampaignIntroPreview() {
    AwakenTheme(reduceMotion = true) {
        CampaignScreen(state = CampaignUiState(loading = false, step = CampaignStep.INTRO), onEvent = {}, onBack = {})
    }
}
