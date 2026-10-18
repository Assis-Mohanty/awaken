package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.GateRules
import com.assismohanty.awaken.engine.model.GateRank
import java.time.Duration

/**
 * Focus Gate countdown (DESIGN_SYSTEM §3): thick ring in the gate's rank colour for remaining time, `displayXL` timer,
 * and a thin inner ring for the breach budget that shifts from `mist` to `ember` as it fills.
 */
@Composable
fun GateDial(
    gate: GateRank,
    remaining: Duration,
    total: Duration,
    breachUsed: Duration,
    modifier: Modifier = Modifier,
    breachBudget: Duration = Duration.ofMillis(GateRules.BREACH_BUDGET_MS),
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val gateColor = colors.gate(gate)
    val remainingFraction = if (total.isZero) 0f else (remaining.toMillis().toFloat() / total.toMillis()).coerceIn(0f, 1f)
    val breachFraction = (breachUsed.toMillis().toFloat() / breachBudget.toMillis()).coerceIn(0f, 1f)
    val remainingText = formatClock(remaining)
    val description = stringResource(R.string.cd_gate_dial, remainingText, formatClock(breachUsed), formatClock(breachBudget))

    Box(
        modifier = modifier
            .size(dimens.gateDialSize)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ring = dimens.gateDialRing.toPx()
            val inner = dimens.gateDialInnerRing.toPx()
            val outerInset = ring / 2f
            val outerSize = Size(size.width - ring, size.height - ring)
            drawArc(colors.edge, 0f, FULL, false, Offset(outerInset, outerInset), outerSize, style = Stroke(ring))
            drawArc(gateColor, START, FULL * remainingFraction, false, Offset(outerInset, outerInset), outerSize, style = Stroke(ring, cap = StrokeCap.Butt))

            val innerInset = ring + Spacing.sm.toPx() + inner / 2f
            val innerSize = Size(size.width - innerInset * 2, size.height - innerInset * 2)
            drawArc(colors.edge, 0f, FULL, false, Offset(innerInset, innerInset), innerSize, style = Stroke(inner))
            if (breachFraction > 0f) {
                drawArc(lerp(colors.mist, colors.ember, breachFraction), START, FULL * breachFraction, false, Offset(innerInset, innerInset), innerSize, style = Stroke(inner))
            }
        }
        Text(text = remainingText, style = AwakenTheme.type.displayXL, color = colors.bone)
    }
}

/** "mm:ss", or "h:mm:ss" from one hour. */
fun formatClock(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

private const val FULL = 360f
private const val START = -90f
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L

@ComponentPreview
@Composable
private fun GateDialPreview() {
    AwakenTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GateDial(GateRank.D, remaining = Duration.ofMinutes(17).plusSeconds(42), total = Duration.ofMinutes(25), breachUsed = Duration.ofSeconds(20))
        }
    }
}

@ComponentPreview
@Composable
private fun GateDialBreachPreview() {
    AwakenTheme {
        GateDial(GateRank.B, remaining = Duration.ofMinutes(61), total = Duration.ofMinutes(90), breachUsed = Duration.ofSeconds(80))
    }
}
