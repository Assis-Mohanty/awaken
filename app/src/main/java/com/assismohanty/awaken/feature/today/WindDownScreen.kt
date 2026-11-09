package com.assismohanty.awaken.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.FieldPrimaryButton
import com.assismohanty.awaken.core.designsystem.components.FieldQuietButton
import com.assismohanty.awaken.core.designsystem.components.SessionDial
import com.assismohanty.awaken.core.designsystem.components.SessionScreen
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
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
    val phase = if (started) BedtimeRules.breathPhase(elapsedSeconds.toDouble()) else BreathPhase.IN
    val finished = started && phase == BreathPhase.DONE
    val phaseSeconds = if (phase == BreathPhase.OUT) BedtimeRules.EXHALE_SECONDS else BedtimeRules.INHALE_SECONDS
    // The arc drains over the length of each breath; the engine owns the timings, this only draws them.
    val inCycle = elapsedSeconds % (BedtimeRules.INHALE_SECONDS + BedtimeRules.EXHALE_SECONDS)
    val intoPhase = if (phase == BreathPhase.OUT) inCycle - BedtimeRules.INHALE_SECONDS else inCycle
    val numeral = lexString(
        when {
            !started -> R.string.bedtime_wind_ready
            finished -> R.string.bedtime_wind_done
            phase == BreathPhase.OUT -> R.string.bedtime_wind_out
            else -> R.string.bedtime_wind_in
        },
    )
    val caption = when {
        !started -> lexString(R.string.bedtime_wind_ready_caption, BedtimeRules.INHALE_SECONDS, BedtimeRules.EXHALE_SECONDS)
        finished -> lexString(R.string.bedtime_wind_finished)
        else -> lexString(R.string.bedtime_wind_for_seconds, phaseSeconds)
    }
    SessionScreen(
        stat = BedtimeRules.BEDTIME_STAT,
        title = lexString(R.string.bedtime_wind_title),
        subtitle = lexString(R.string.bedtime_wind_sub),
        modifier = modifier,
        body = { diameter ->
            SessionDial(
                diameter = diameter,
                fraction = if (started && !finished) 1f - intoPhase.toFloat() / phaseSeconds else 1f,
                numeral = numeral,
                caption = caption,
                description = lexString(R.string.bedtime_wind_cd, numeral, (BedtimeRules.BREATH_TOTAL_SECONDS - elapsedSeconds).coerceAtLeast(0)),
            )
        },
        actions = {
            when {
                !started -> FieldPrimaryButton(text = lexString(R.string.bedtime_wind_start), onClick = onStart)
                finished -> FieldPrimaryButton(text = lexString(R.string.bedtime_wind_done), onClick = onDone)
                else -> FieldQuietButton(text = lexString(R.string.bedtime_wind_finish), onClick = onDone)
            }
        },
    )
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
