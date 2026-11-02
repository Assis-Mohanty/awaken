package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.BreathPhase
import com.assismohanty.awaken.engine.StepGoalRules

/**
 * Today's steps against the daily goal (GD §16.11): a ring that fills to the goal, "6,200 / 8,000" beside it, and "Goal
 * reached" with a check once the goal is met. Display only: the ring pays nothing. One TalkBack description.
 */
@Composable
fun StepGoalRing(steps: Long, goal: Int, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val reached = StepGoalRules.reached(steps, goal)
    val description = lexString(if (reached) R.string.step_ring_cd_reached else R.string.step_ring_cd, formatCount(steps), formatCount(goal))
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Chart(
            ChartSpec.Ring(
                progress = StepGoalRules.progress(steps, goal),
                description = "",
                color = if (reached) colors.positive else colors.accent,
                size = AwakenTheme.dimens.avatar,
                stroke = Spacing.xs,
            ),
        ) {
            if (reached) Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.positive, modifier = Modifier.size(Spacing.md))
        }
        Column {
            Text(
                lexString(R.string.step_ring_of, formatCount(steps), formatCount(goal)),
                style = AwakenTheme.type.numericSmall,
                color = colors.textPrimary,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                lexString(if (reached) R.string.step_goal_reached else R.string.health_metric_steps),
                style = AwakenTheme.type.caption,
                color = if (reached) colors.positive else colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * The wind-down circle (GD §16.12): it grows over the 4-second inhale and shrinks over the 6-second exhale. Under
 * reduce-motion the circle stays put and only the words change. [secondsLeft] is for TalkBack.
 */
@Composable
fun BreathingGuide(phase: BreathPhase, secondsLeft: Int, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val word = lexString(
        when (phase) {
            BreathPhase.IN -> R.string.bedtime_breathe_in
            BreathPhase.OUT -> R.string.bedtime_breathe_out
            BreathPhase.DONE -> R.string.bedtime_wind_finished
        },
    )
    val description = lexString(R.string.bedtime_wind_cd, word, secondsLeft)
    val scale = remember { Animatable(CIRCLE_MIN) }
    LaunchedEffect(phase, motion.reduceMotion) {
        if (motion.reduceMotion) {
            scale.snapTo(CIRCLE_MAX)
            return@LaunchedEffect
        }
        when (phase) {
            BreathPhase.IN -> scale.animateTo(CIRCLE_MAX, tween(BedtimeRules.INHALE_SECONDS * MILLIS, easing = LinearEasing))
            BreathPhase.OUT -> scale.animateTo(CIRCLE_MIN, tween(BedtimeRules.EXHALE_SECONDS * MILLIS, easing = LinearEasing))
            BreathPhase.DONE -> scale.snapTo(CIRCLE_MIN)
        }
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(modifier = Modifier.size(CIRCLE_BOX), contentAlignment = Alignment.Center) {
            Box(Modifier.size(CIRCLE_BOX).scale(scale.value).background(colors.accent.copy(alpha = CIRCLE_ALPHA), CircleShape))
        }
        Text(word, style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.clearAndSetSemantics { })
    }
}

private val CIRCLE_BOX = 200.dp
private const val CIRCLE_MIN = 0.4f
private const val CIRCLE_MAX = 1f
private const val CIRCLE_ALPHA = 0.35f
private const val MILLIS = 1_000

@ComponentPreview
@Composable
private fun StepGoalRingPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StepGoalRing(steps = 0, goal = 8_000)
            StepGoalRing(steps = 6_200, goal = 8_000)
            StepGoalRing(steps = 9_100, goal = 8_000)
        }
    }
}

@ComponentPreview
@Composable
private fun BreathingGuidePreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BreathingGuide(BreathPhase.IN, secondsLeft = 116)
            BreathingGuide(BreathPhase.DONE, secondsLeft = 0)
        }
    }
}
