package com.assismohanty.awaken.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.ConsistencyMeter
import com.assismohanty.awaken.core.designsystem.components.InsightCard
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.MetricTile
import com.assismohanty.awaken.core.designsystem.components.ReviewButtons
import com.assismohanty.awaken.core.designsystem.components.ReviewStepIndicator
import com.assismohanty.awaken.core.designsystem.components.ReviewTextField
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.InsightsModel
import com.assismohanty.awaken.core.domain.WeeklyReviewModel
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.GameWeek
import com.assismohanty.awaken.engine.StatusMath
import com.assismohanty.awaken.engine.WeeklyReviewRules
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.status.insightLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ReviewRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    ReviewScreen(state, viewModel::onEvent, onClose, modifier)
}

/** Weekly Review (DESIGN_SYSTEM §8.2): five steps in one window, no XP anywhere (GD §14.4). */
@Composable
fun ReviewScreen(state: ReviewUiState, onEvent: (ReviewEvent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(enabled = state.step != ReviewStep.NUMBERS) { onEvent(ReviewEvent.Back) }
    LargeTitleScaffold(title = lexString(R.string.review_title), onBack = onClose, modifier = modifier) {
        val model = state.model
        when {
            state.loading -> SkeletonCard()
            model == null -> InsightCard(text = lexString(R.string.review_closed))
            else -> {
                ReviewStepIndicator(step = state.step.ordinal + 1, total = ReviewStep.entries.size)
                when (state.step) {
                    ReviewStep.NUMBERS -> NumbersStep(model, onEvent)
                    ReviewStep.WENT_WELL -> {
                        ReviewTextField(
                            prompt = lexString(R.string.review_went_well),
                            value = state.wentWell,
                            onValueChange = { onEvent(ReviewEvent.WentWellChanged(it)) },
                            maxChars = WeeklyReviewRules.TEXT_MAX,
                        )
                        ReviewButtons(lexString(R.string.action_continue), { onEvent(ReviewEvent.Next) }, onSkip = { onEvent(ReviewEvent.Skip) })
                    }
                    ReviewStep.SLIPPED -> {
                        ReviewTextField(
                            prompt = lexString(R.string.review_slipped),
                            value = state.slipped,
                            onValueChange = { onEvent(ReviewEvent.SlippedChanged(it)) },
                            maxChars = WeeklyReviewRules.TEXT_MAX,
                        )
                        ReviewButtons(lexString(R.string.action_continue), { onEvent(ReviewEvent.Next) }, onSkip = { onEvent(ReviewEvent.Skip) })
                    }
                    ReviewStep.FOCUS -> FocusStep(state, onEvent)
                    ReviewStep.INTENTION -> {
                        ReviewTextField(
                            prompt = lexString(R.string.review_intention),
                            value = state.intention,
                            onValueChange = { onEvent(ReviewEvent.IntentionChanged(it)) },
                            maxChars = WeeklyReviewRules.INTENTION_MAX,
                            singleLine = true,
                        )
                        ReviewButtons(
                            nextLabel = lexString(R.string.review_finish),
                            onNext = { onEvent(ReviewEvent.Save) },
                            onSkip = { onEvent(ReviewEvent.Skip) },
                            nextEnabled = state.canSave,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumbersStep(model: WeeklyReviewModel, onEvent: (ReviewEvent) -> Unit) {
    val report = model.report
    AwakenCard(reveal = false, title = lexString(if (model.soFar) R.string.review_numbers_so_far else R.string.review_numbers_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            MetricTile(lexString(R.string.review_quests), formatCount(report.questsCompleted), Modifier.weight(1f))
            MetricTile(lexString(R.string.review_xp), formatCount(report.totalXp), Modifier.weight(1f))
        }
        report.bestDay?.let { day ->
            Text(
                lexString(R.string.review_best_day) + " · " + day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale())),
                style = AwakenTheme.type.caption,
                color = AwakenTheme.colors.textSecondary,
            )
        }
        ConsistencyMeter(model.consistency)
    }
    InsightCard(text = insightLine(model.insights), icon = AwakenGlyphs.Sparkle)
    ReviewButtons(lexString(R.string.action_continue), { onEvent(ReviewEvent.Next) })
}

@Composable
private fun FocusStep(state: ReviewUiState, onEvent: (ReviewEvent) -> Unit) {
    Text(lexString(R.string.review_focus_title), style = AwakenTheme.type.section, color = AwakenTheme.colors.textPrimary)
    Text(lexString(R.string.review_focus_body), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Stat.entries.forEach { stat ->
            ChoiceChip(
                text = stat.label(),
                selected = stat in state.focus,
                onClick = { onEvent(ReviewEvent.ToggleFocus(stat)) },
                color = AwakenTheme.colors.stat(stat),
                icon = AwakenGlyphs.stat(stat),
                multiSelect = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    ReviewButtons(lexString(R.string.action_continue), { onEvent(ReviewEvent.Next) }, nextEnabled = state.focus.size == ReviewUiState.FOCUS_COUNT)
}

private val previewWeek = GameWeek(LocalDate.of(2026, 10, 26))

private val previewModel = WeeklyReviewModel(
    isoWeek = "2026-W44",
    report = StatusMath.weeklyReport(
        previewWeek,
        mapOf(Stat.STR to 240L, Stat.INT to 180L, Stat.WIL to 60L, Stat.VIT to 90L, Stat.CHA to 0L),
        mapOf(previewWeek.monday.plusDays(2) to 210L),
        questsCompleted = 23,
    ),
    consistency = ConsistencyScore(cleared = 22, eligible = 28),
    insights = InsightsModel(daysNeeded = 3),
    focus = listOf(Stat.STR, Stat.INT),
)

@PhonePreviews
@Composable
private fun ReviewNumbersPreview() {
    AwakenTheme { ReviewScreen(ReviewUiState(loading = false, model = previewModel, focus = previewModel.focus), {}, {}) }
}

@PhonePreviews
@Composable
private fun ReviewWentWellPreview() {
    AwakenTheme {
        ReviewScreen(
            ReviewUiState(loading = false, model = previewModel, step = ReviewStep.WENT_WELL, wentWell = "Three runs, all before work."),
            {},
            {},
        )
    }
}

@PhonePreviews
@Composable
private fun ReviewFocusPreview() {
    AwakenTheme {
        ReviewScreen(ReviewUiState(loading = false, model = previewModel, step = ReviewStep.FOCUS, focus = listOf(Stat.VIT, Stat.WIL)), {}, {})
    }
}

@PhonePreviews
@Composable
private fun ReviewClosedPreview() {
    AwakenTheme { ReviewScreen(ReviewUiState(loading = false, model = null), {}, {}) }
}
