package com.assismohanty.awaken.feature.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.RankCrest
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.TrialSummary

/** The Rank Trial slot on Today (GD §5): "Begin trial" when available, else progress and today's stage quests. */
@Composable
fun TrialWindow(
    trial: TrialSummary,
    stages: List<QuestRowModel>,
    onBegin: () -> Unit,
    onOpenTrial: () -> Unit,
    onComplete: (String) -> Unit,
    onOpenQuest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val rank = when (trial) {
        is TrialSummary.Available -> trial.rank
        is TrialSummary.Active -> trial.rank
    }
    SystemWindow(
        title = stringResource(R.string.trial_available, rank.name),
        accent = colors.rank(rank),
        modifier = modifier,
        headerTrailing = { RankCrest(rank = rank, size = AwakenTheme.dimens.icon) },
    ) {
        when (trial) {
            is TrialSummary.Available -> {
                Text(stringResource(R.string.trial_available_body, rank.name), style = type.body, color = colors.bone)
                Text(stringResource(R.string.trial_rule), style = type.caption, color = colors.mist)
                PrimaryButton(
                    text = stringResource(R.string.action_begin_trial),
                    onClick = onBegin,
                    color = colors.rank(rank),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
            is TrialSummary.Active -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = pluralStringResource(R.plurals.trial_progress, trial.stages, trial.cleared, trial.stages),
                        style = type.label,
                        color = colors.bone,
                        modifier = Modifier.weight(1f),
                    )
                    Text(pluralStringResource(R.plurals.trial_days_left, trial.daysLeft, trial.daysLeft), style = type.label, color = colors.mist)
                }
                stages.forEachIndexed { index, row ->
                    QuestRow(
                        model = row,
                        onComplete = { onComplete(row.id) },
                        onOpen = { onOpenQuest(row.id) },
                        showDivider = index > 0,
                    )
                }
                QuietButton(text = stringResource(R.string.action_view_trial), onClick = onOpenTrial)
            }
        }
    }
}

/**
 * DESIGN_SYSTEM §4.2: when the day flips to cleared, the directive edge pulses once and settles on `crit`. A day that is
 * already cleared when the screen opens doesn't pulse. Reduce-motion: no pulse.
 */
@Composable
fun clearedPulseAccent(cleared: Boolean, base: Color): Color {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val pulse = remember { Animatable(0f) }
    var wasCleared by remember { mutableStateOf(cleared) }
    LaunchedEffect(cleared) {
        if (cleared && !wasCleared && !motion.reduceMotion) {
            pulse.animateTo(1f, tween(motion.quickMs, easing = motion.quickEasing))
            pulse.animateTo(0f, tween(motion.revealMs, easing = motion.revealEasing))
        }
        wasCleared = cleared
    }
    return if (cleared) lerp(colors.crit, colors.bone, pulse.value) else base
}
