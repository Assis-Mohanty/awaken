package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.PomodoroPhase

/**
 * The daily focus goal ring (GD §16.4): a ring that fills with today's focus minutes, "80 / 120 min" inside and the focus
 * streak under it. A full ring at or over the goal. One TalkBack description for the whole control; display only, no XP.
 */
@Composable
fun FocusGoalRing(minutes: Int, goal: Int, streak: Int, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val met = minutes >= goal
    val description = lexPlural(R.plurals.focus_goal_cd, streak, minutes, goal, streak)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Chart(
            ChartSpec.Ring(
                progress = (minutes.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f),
                description = "",
                color = colors.accent,
                size = dimens.avatar * RING_SCALE,
                stroke = dimens.heroRingStroke - Spacing.xxs,
            ),
        ) {
            Text(
                text = formatCount(minutes),
                style = AwakenTheme.type.numeric,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = lexString(if (met) R.string.focus_goal_reached else R.string.focus_goal_of, formatCount(minutes), formatCount(goal)),
                style = AwakenTheme.type.windowTitle,
                color = colors.textPrimary,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = lexPlural(R.plurals.focus_goal_streak, streak, streak),
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

private const val RING_SCALE = 1.8f

/**
 * Where a Pomodoro session is (GD §16.3): a dot per round, filled for the rounds done, a ring for the one under way,
 * and "Round 2 of 4 · Break". [round] is the current round, 1-based; during a break it is the round just finished.
 */
@Composable
fun PomodoroTrack(round: Int, rounds: Int, phase: PomodoroPhase, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val label = lexString(
        when (phase) {
            PomodoroPhase.FOCUS -> R.string.pomodoro_track_focus
            PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_track_short_break
            PomodoroPhase.LONG_BREAK -> R.string.pomodoro_track_long_break
            PomodoroPhase.DONE -> R.string.pomodoro_track_done
        },
        round,
        rounds,
    )
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            for (index in 1..rounds) {
                val done = index < round || (index == round && phase != PomodoroPhase.FOCUS)
                val dot = Modifier.size(dimens.heroDot * 2).clearAndSetSemantics { }
                when {
                    done -> Column(dot.background(colors.accent, CircleShape)) {}
                    index == round -> Column(dot.border(dimens.accentWidth, colors.accent, CircleShape)) {}
                    else -> Column(dot.background(colors.divider, CircleShape)) {}
                }
            }
        }
        Text(text = label, style = AwakenTheme.type.label, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
    }
}

@ComponentPreview
@Composable
private fun FocusGoalRingPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FocusGoalRing(minutes = 0, goal = 120, streak = 0)
            FocusGoalRing(minutes = 80, goal = 120, streak = 4)
            FocusGoalRing(minutes = 120, goal = 120, streak = 1)
            FocusGoalRing(minutes = 190, goal = 120, streak = 12)
        }
    }
}

@ComponentPreview
@Composable
private fun PomodoroTrackPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PomodoroTrack(round = 1, rounds = 4, phase = PomodoroPhase.FOCUS)
            PomodoroTrack(round = 2, rounds = 4, phase = PomodoroPhase.SHORT_BREAK)
            PomodoroTrack(round = 3, rounds = 4, phase = PomodoroPhase.FOCUS)
            PomodoroTrack(round = 4, rounds = 4, phase = PomodoroPhase.LONG_BREAK)
            PomodoroTrack(round = 4, rounds = 4, phase = PomodoroPhase.DONE)
        }
    }
}
