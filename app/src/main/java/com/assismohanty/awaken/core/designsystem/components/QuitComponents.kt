package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * One quit counter in the list (DESIGN_SYSTEM §9.1): its name, the current run large, the best run, a bar toward the next
 * milestone and, when set, what it has saved. Calm colours only: nothing here is red, and a restart never shows an alarm.
 * [paused] is Doomscrolling waiting for usage data. The callers pass every word, so this component knows no counter rules.
 */
@Composable
fun QuitCounterCard(
    name: String,
    run: String,
    best: String,
    progress: Float,
    next: String,
    modifier: Modifier = Modifier,
    extra: String? = null,
    note: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = AwakenTheme.colors
    val description = lexString(R.string.quit_card_cd, name, run, best)
    AwakenCard(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }, reveal = false, onClick = onClick) {
        Text(name, style = AwakenTheme.type.label, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
        Text(run, style = AwakenTheme.type.metricL, color = colors.textPrimary, modifier = Modifier.clearAndSetSemantics { })
        Text(best, style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
        Chart(ChartSpec.Progress(progress = progress, color = colors.accent), modifier = Modifier.clearAndSetSemantics { })
        Text(next, style = AwakenTheme.type.caption, color = colors.textMuted, modifier = Modifier.clearAndSetSemantics { })
        extra?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { }) }
        note?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { }) }
    }
}

/**
 * The milestones as a row (GD §16.13 N25): each reached one filled, the rest outlined. [reachedDays] is the current run in
 * whole days. Each milestone has its own TalkBack label ("7 days, reached").
 */
@Composable
fun MilestoneLadder(milestones: List<Int>, reachedDays: Int, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        milestones.forEach { days ->
            val reached = reachedDays >= days
            val description = lexString(if (reached) R.string.quit_milestone_reached_cd else R.string.quit_milestone_ahead_cd, days.toString())
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = AwakenTheme.dimens.iconSmall + Spacing.xs, minHeight = AwakenTheme.dimens.iconSmall + Spacing.xs)
                    .then(
                        if (reached) {
                            Modifier.background(colors.accent, CircleShape)
                        } else {
                            Modifier.border(AwakenTheme.dimens.hairline, colors.divider, CircleShape)
                        },
                    )
                    .padding(horizontal = Spacing.xs)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    days.toString(),
                    style = AwakenTheme.type.caption,
                    color = if (reached) colors.surface else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

@ComponentPreview
@Composable
private fun QuitCounterCardPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            QuitCounterCard(
                name = "Smoking",
                run = "12 days",
                best = "Best run: 30 days. It's still yours.",
                progress = 0.7f,
                next = "Next: 14 days",
                extra = "₹1,800 saved · 6 h back",
            )
            QuitCounterCard(name = "Alcohol", run = "7 h", best = "Best run: none yet", progress = 0f, next = "Next: 1 day")
            QuitCounterCard(
                name = "Doomscrolling",
                run = "3 days",
                best = "Best run: 5 days. It's still yours.",
                progress = 0.5f,
                next = "Next: 7 days",
                note = "Waiting for usage data. The count is paused, not reset.",
            )
        }
    }
}

@ComponentPreview
@Composable
private fun MilestoneLadderPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            MilestoneLadder(listOf(1, 3, 7, 14, 30, 90, 180, 365), reachedDays = 0)
            MilestoneLadder(listOf(1, 3, 7, 14, 30, 90, 180, 365), reachedDays = 16)
            MilestoneLadder(listOf(1, 3, 7, 14, 30, 90, 180, 365), reachedDays = 400)
        }
    }
}
