package com.assismohanty.awaken.feature.protect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.IconCircle
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.RisingFill
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.PauseRules
import kotlinx.coroutines.delay

/**
 * What the pause screen shows (GD §19.1). [seconds] is how long the screen takes to fill, [line] the one line from the
 * bank, [opensToday] how many times this app was opened today (this one included). With [askIntention] the "What for?"
 * chips show; [chosen] is the one picked.
 */
data class PauseOverlayUiState(
    val appLabel: String,
    val appIcon: ImageBitmap?,
    val seconds: Int,
    val line: String?,
    val opensToday: Int,
    val requireTap: Boolean,
    val askIntention: Boolean,
    val chosen: PauseRules.Intention? = null,
)

/**
 * The full-screen pause: a colour rises from the bottom and covers the screen over [PauseOverlayUiState.seconds], and that
 * is the only clock (no number). When it is full the app opens by itself ([onContinue]), or a Continue button is enabled
 * if [PauseOverlayUiState.requireTap]. [Not now] works from the first moment. Under reduce-motion the colour rises in
 * whole-second steps instead of smoothly. The screen never pays or punishes anything.
 */
@Composable
fun PauseOverlayContent(
    state: PauseOverlayUiState,
    onNotNow: () -> Unit,
    onContinue: () -> Unit,
    onIntention: (PauseRules.Intention) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = AwakenTheme.motion.reduceMotion
    var progress by remember(state.seconds) { mutableFloatStateOf(0f) }
    var done by remember(state.seconds) { mutableStateOf(state.seconds <= 0) }
    LaunchedEffect(state.seconds, state.requireTap, reduceMotion) {
        if (state.seconds > 0) {
            if (reduceMotion) {
                for (second in 1..state.seconds) {
                    delay(ONE_SECOND_MS)
                    progress = second / state.seconds.toFloat()
                }
            } else {
                animate(0f, 1f, animationSpec = tween(state.seconds * ONE_SECOND_MS.toInt(), easing = LinearEasing)) { value, _ -> progress = value }
            }
        }
        done = true
        if (!state.requireTap) onContinue()
    }
    PauseOverlayLayout(state, progress, done, onNotNow, onContinue, onIntention, modifier)
}

/** The pause screen at a given [progress] (0 empty, 1 full); stateless so every moment of it can be previewed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PauseOverlayLayout(
    state: PauseOverlayUiState,
    progress: Float,
    done: Boolean,
    onNotNow: () -> Unit,
    onContinue: () -> Unit,
    onIntention: (PauseRules.Intention) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    ScreenBackground(modifier = modifier) {
        RisingFill(progress = progress, description = lexString(R.string.pause_overlay_seconds_left, state.seconds))
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg)
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.appIcon != null) {
                Image(bitmap = state.appIcon, contentDescription = null, modifier = Modifier.size(AwakenTheme.dimens.crestLarge))
            } else {
                IconCircle(AwakenGlyphs.Lock, colors.accent, size = AwakenTheme.dimens.crestLarge)
            }
            Text(
                text = lexString(R.string.pause_overlay_about, state.appLabel),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (state.line != null) {
                Text(text = state.line, style = type.body, color = colors.textPrimary, textAlign = TextAlign.Center)
            }
            Text(
                text = lexPlural(R.plurals.pause_overlay_opens, state.opensToday, state.opensToday),
                style = type.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (state.askIntention) {
                Text(text = lexString(R.string.pause_intention_title), style = type.label, color = colors.textPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PauseRules.Intention.entries.forEach { intention ->
                        ChoiceChip(
                            text = lexString(intention.label()),
                            selected = state.chosen == intention,
                            onClick = { onIntention(intention) },
                        )
                    }
                }
            }
            if (state.requireTap) {
                PrimaryButton(
                    text = lexString(R.string.pause_overlay_continue, state.appLabel),
                    onClick = onContinue,
                    enabled = done,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            QuietButton(text = lexString(R.string.pause_overlay_not_now), onClick = onNotNow, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun PauseRules.Intention.label(): Int = when (this) {
    PauseRules.Intention.REPLY -> R.string.pause_intention_reply
    PauseRules.Intention.LOOK_UP -> R.string.pause_intention_look_up
    PauseRules.Intention.POST -> R.string.pause_intention_post
    PauseRules.Intention.BREAK -> R.string.pause_intention_break
    PauseRules.Intention.NO_REASON -> R.string.pause_intention_none
}

private const val ONE_SECOND_MS = 1_000L

private const val SAMPLE_LINE = "What did you come here for?"

@PhonePreviews
@Composable
private fun PauseHalfwayPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseOverlayLayout(
            state = PauseOverlayUiState("Instagram", null, seconds = 5, line = SAMPLE_LINE, opensToday = 3, requireTap = false, askIntention = false),
            progress = 0.5f, done = false, onNotNow = {}, onContinue = {}, onIntention = {},
        )
    }
}

@PhonePreviews
@Composable
private fun PauseFullTapToContinueWithChipsPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseOverlayLayout(
            state = PauseOverlayUiState(
                "A very long app name that keeps going and going", null, seconds = 5, line = SAMPLE_LINE, opensToday = 1,
                requireTap = true, askIntention = true, chosen = PauseRules.Intention.LOOK_UP,
            ),
            progress = 1f, done = true, onNotNow = {}, onContinue = {}, onIntention = {},
        )
    }
}
