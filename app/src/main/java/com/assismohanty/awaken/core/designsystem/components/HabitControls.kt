package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

/**
 * What a habit's row needs beyond a plain quest row (GD §16.1-16.2). [progress] is a count habit's tally;
 * [weekDone]/[weekTarget] are set for a times-per-week habit; [keptWindowOpen] is true from the Evening block on.
 */
@Immutable
data class HabitRowModel(
    val type: HabitType,
    val progress: Int = 0,
    val weekDone: Int? = null,
    val weekTarget: Int? = null,
    val skipped: Boolean = false,
    val slipped: Boolean = false,
    val keptWindowOpen: Boolean = false,
) {
    /** Only a Check habit completes with a swipe or the check circle; the others have their own control. */
    val completesByHand: Boolean get() = type == HabitType.CHECK
}

/** The taps a habit row can make. Unused ones stay empty. */
@Immutable
data class HabitRowActions(
    val onPlus: () -> Unit = {},
    val onMinus: () -> Unit = {},
    val onStartTimed: () -> Unit = {},
    val onKept: () -> Unit = {},
    val onSlip: () -> Unit = {},
    val onUndoSlip: () -> Unit = {},
    val onUndoSkip: () -> Unit = {},
)

/** The small chip on a habit row: "Holding", "2 of 3 this week", "Skipped". Nothing for a plain Check, Count or Timed habit (the row already says the minutes). */
@Composable
fun HabitTypeChip(habit: HabitRowModel, target: Int, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    when {
        habit.skipped -> Pill(lexString(R.string.habit_chip_skipped), colors.textMuted, modifier, background = colors.surfaceOverlay)
        habit.type == HabitType.NEGATIVE ->
            if (habit.slipped) Pill(lexString(R.string.habit_chip_slipped), colors.textMuted, modifier, background = colors.surfaceOverlay)
            else Pill(lexString(R.string.habit_chip_holding), colors.accent, modifier)
        habit.weekTarget != null -> Pill(lexString(R.string.habit_chip_week, habit.weekDone ?: 0, habit.weekTarget), colors.accent, modifier)
        else -> Unit
    }
}

/** True when [HabitTypeChip] would draw something, so a row knows to show its chip line. */
fun HabitRowModel.hasChip(): Boolean =
    skipped || type == HabitType.NEGATIVE || weekTarget != null

/**
 * A count habit's counter (GD §16.1): − and + round buttons around "5 / 8 glasses" over a thin progress bar. − is off at 0;
 * both are off when the habit is [enabled] = false (skipped or missed). One TalkBack description for the whole control.
 */
@Composable
fun QuantityStepper(
    title: String,
    progress: Int,
    target: Int,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val description = lexString(R.string.cd_habit_progress, title, progress, target)
    Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = false) { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StepButton(false, lexString(R.string.cd_habit_minus, title), onMinus, enabled && progress > 0)
            Text(
                text = lexString(R.string.habit_count_progress, formatCount(progress), formatCount(target), unitLabel(unit, target)),
                style = AwakenTheme.type.numericSmall,
                color = if (enabled) colors.textPrimary else colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            StepButton(true, lexString(R.string.cd_habit_plus, title), onPlus, enabled && progress < target)
        }
        Chart(
            ChartSpec.Progress(progress = progress.toFloat() / target.coerceAtLeast(1), color = colors.accent, track = colors.divider, height = dimens.meterHeight),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepButton(plus: Boolean, description: String, onClick: () -> Unit, enabled: Boolean) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(dimens.touchTarget).semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (plus) AwakenGlyphs.Plus else AwakenGlyphs.Minus,
            contentDescription = null,
            tint = if (enabled) colors.textPrimary else colors.textMuted,
            modifier = Modifier
                .size(dimens.sigil - Spacing.xxs)
                .border(dimens.accentWidth, if (enabled) colors.textMuted else colors.divider, AwakenTheme.shapes.pill)
                .padding(Spacing.xxs),
        )
    }
}

/**
 * An avoid habit's controls (GD §16.1). Before the evening only "I slipped" is offered; from the evening "Kept it today"
 * confirms. A slip reads as one calm line with an undo, never as a failure.
 */
@Composable
fun NegativeHabitRow(habit: HabitRowModel, actions: HabitRowActions, modifier: Modifier = Modifier, open: Boolean = true) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        when {
            habit.slipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lexString(R.string.habit_slipped_line), style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
                QuietButton(lexString(R.string.habit_undo_slip), actions.onUndoSlip)
            }
            open -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(lexString(R.string.habit_kept), actions.onKept, enabled = habit.keptWindowOpen)
                QuietButton(lexString(R.string.habit_slip), actions.onSlip)
            }
            else -> Unit
        }
        if (open && !habit.slipped && !habit.keptWindowOpen) {
            Text(lexString(R.string.habit_kept_hint), style = AwakenTheme.type.caption, color = colors.textMuted)
        }
    }
}

/** Everything below a habit row's title: the stepper, the Start button, the avoid buttons or the skipped line. */
@Composable
fun HabitRowControls(row: QuestRowModel, habit: HabitRowModel, actions: HabitRowActions, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val open = row.state == QuestRowState.OPEN
    Column(modifier = modifier.fillMaxWidth().padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        when {
            habit.skipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lexString(R.string.habit_skipped_line), style = AwakenTheme.type.caption, color = colors.textMuted, modifier = Modifier.weight(1f))
                QuietButton(lexString(R.string.habit_undo_skip), actions.onUndoSkip)
            }
            habit.type == HabitType.QUANTITY && row.state != QuestRowState.CLOSED ->
                QuantityStepper(row.title, habit.progress, row.target, row.unit, actions.onMinus, actions.onPlus)
            habit.type == HabitType.TIMED && open ->
                SecondaryButton(lexString(R.string.habit_start_timed, row.target), actions.onStartTimed)
            habit.type == HabitType.NEGATIVE && row.state != QuestRowState.DONE -> NegativeHabitRow(habit, actions, open = open || habit.slipped)
            else -> Unit
        }
    }
}

private val previewRow = QuestRowModel(
    "h", "Drink water", Stat.VIT, 8, "glasses", Difficulty.EASY, VerificationSource.SELF, isMain = false, state = QuestRowState.OPEN,
)

/** Whole habit rows in a plan card, one per type and state (GD §16.1-16.2): how Today draws them. */
@ComponentPreview
@Composable
private fun HabitQuestRowsPreview() {
    val rows = listOf(
        previewRow.copy(id = "1", habit = HabitRowModel(HabitType.QUANTITY, progress = 5)),
        previewRow.copy(id = "2", title = "Read", unit = "min", target = 30, verification = VerificationSource.TIMER, habit = HabitRowModel(HabitType.TIMED)),
        previewRow.copy(id = "3", title = "No sugar", unit = "", target = 1, habit = HabitRowModel(HabitType.NEGATIVE, keptWindowOpen = true)),
        previewRow.copy(id = "4", title = "Gym", unit = "", target = 1, habit = HabitRowModel(HabitType.CHECK, weekDone = 2, weekTarget = 3)),
        previewRow.copy(id = "5", title = "Stretch", unit = "", target = 1, state = QuestRowState.CLOSED, habit = HabitRowModel(HabitType.CHECK, skipped = true)),
        previewRow.copy(id = "6", title = "No scrolling in bed", unit = "", target = 1, state = QuestRowState.CLOSED, habit = HabitRowModel(HabitType.NEGATIVE, slipped = true)),
    )
    AwakenTheme(reduceMotion = true) {
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero) {
            Column {
                rows.forEachIndexed { index, row ->
                    QuestRow(model = row, onComplete = {}, onOpen = {}, showDivider = index > 0)
                }
            }
        }
    }
}

@ComponentPreview
@Composable
private fun QuantityStepperPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            QuantityStepper("Drink water", 0, 8, "glasses", {}, {})
            QuantityStepper("Drink water", 5, 8, "glasses", {}, {})
            QuantityStepper("Drink water", 8, 8, "glasses", {}, {})
            QuantityStepper("Drink water", 3, 8, "glasses", {}, {}, enabled = false)
        }
    }
}

@ComponentPreview
@Composable
private fun NegativeHabitPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NegativeHabitRow(HabitRowModel(HabitType.NEGATIVE, keptWindowOpen = false), HabitRowActions())
            NegativeHabitRow(HabitRowModel(HabitType.NEGATIVE, keptWindowOpen = true), HabitRowActions())
            NegativeHabitRow(HabitRowModel(HabitType.NEGATIVE, slipped = true), HabitRowActions())
        }
    }
}

@ComponentPreview
@Composable
private fun HabitChipsPreview() {
    AwakenTheme(reduceMotion = true) {
        Row(modifier = Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            HabitTypeChip(HabitRowModel(HabitType.NEGATIVE), 1)
            HabitTypeChip(HabitRowModel(HabitType.CHECK, weekDone = 2, weekTarget = 3), 1)
            HabitTypeChip(HabitRowModel(HabitType.CHECK, skipped = true), 1)
        }
    }
}

@ComponentPreview
@Composable
private fun HabitRowControlsPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HabitRowControls(previewRow, HabitRowModel(HabitType.QUANTITY, progress = 3), HabitRowActions())
            HabitRowControls(previewRow.copy(title = "Read", unit = "min", target = 30, verification = VerificationSource.TIMER), HabitRowModel(HabitType.TIMED), HabitRowActions())
            HabitRowControls(previewRow.copy(title = "No sugar", target = 1, unit = ""), HabitRowModel(HabitType.NEGATIVE, keptWindowOpen = true), HabitRowActions())
            HabitRowControls(previewRow, HabitRowModel(HabitType.CHECK, skipped = true), HabitRowActions())
        }
    }
}
