package com.assismohanty.awaken.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.BreathingGuide
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.BreathPhase
import kotlinx.coroutines.delay

/**
 * The optional wind-down (GD §16.12): two minutes of paced breathing, 4 seconds in and 6 out. It pays nothing, sends
 * nothing and is not checked; the timer only counts seconds on screen.
 */
@Composable
fun WindDownRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var started by rememberSaveable { mutableStateOf(false) }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(started) {
        while (started && elapsed < BedtimeRules.BREATH_TOTAL_SECONDS) {
            delay(MILLIS_PER_SECOND)
            elapsed++
        }
    }
    WindDownScreen(started = started, elapsedSeconds = elapsed, onStart = { started = true }, onDone = onBack, modifier = modifier)
}

@Composable
fun WindDownScreen(started: Boolean, elapsedSeconds: Int, onStart: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val phase = if (started) BedtimeRules.breathPhase(elapsedSeconds.toDouble()) else BreathPhase.IN
    val finished = started && phase == BreathPhase.DONE
    LargeTitleScaffold(title = lexString(R.string.bedtime_wind_title), onBack = onDone, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(lexString(R.string.bedtime_wind_intro), style = AwakenTheme.type.body, color = colors.textSecondary)
            if (started) {
                BreathingGuide(phase, secondsLeft = (BedtimeRules.BREATH_TOTAL_SECONDS - elapsedSeconds).coerceAtLeast(0))
            }
            when {
                finished -> PrimaryButton(text = lexString(R.string.bedtime_wind_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
                !started -> PrimaryButton(text = lexString(R.string.bedtime_wind_start), onClick = onStart, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private const val MILLIS_PER_SECOND = 1_000L

@PhonePreviews
@Composable
private fun WindDownIntroPreview() = AwakenTheme(reduceMotion = true) {
    WindDownScreen(started = false, elapsedSeconds = 0, onStart = {}, onDone = {})
}

@PhonePreviews
@Composable
private fun WindDownBreathingPreview() = AwakenTheme(reduceMotion = true) {
    WindDownScreen(started = true, elapsedSeconds = 6, onStart = {}, onDone = {})
}

@PhonePreviews
@Composable
private fun WindDownFinishedPreview() = AwakenTheme(reduceMotion = true) {
    WindDownScreen(started = true, elapsedSeconds = BedtimeRules.BREATH_TOTAL_SECONDS, onStart = {}, onDone = {})
}
