package com.assismohanty.awaken.feature.protect

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BreathingRing
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.IconCircle
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.PauseRules
import kotlinx.coroutines.delay

/**
 * What the pause screen shows (GD §19.1). [seconds] is the countdown, [line] the one line from the bank, [opensToday] how
 * many times this app was opened today (this one included). With [askIntention] the "What for?" chips show; [chosen] is
 * the one picked.
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
 * The full-screen pause: a breathing ring that counts down, today's open count, one line, and [Not now] at any time. When
 * the countdown ends the app opens by itself ([onContinue]), or a Continue button appears if [PauseOverlayUiState.requireTap].
 * The screen never pays or punishes anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseOverlayContent(
    state: PauseOverlayUiState,
    onNotNow: () -> Unit,
    onContinue: () -> Unit,
    onIntention: (PauseRules.Intention) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    var remaining by remember(state.seconds) { mutableIntStateOf(state.seconds) }
    LaunchedEffect(state.seconds, state.requireTap) {
        while (remaining > 0) {
            delay(ONE_SECOND_MS)
            remaining -= 1
        }
        if (!state.requireTap) onContinue()
    }
    ScreenBackground(modifier = modifier) {
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
                Image(bitmap = state.appIcon, contentDescription = null, modifier = Modifier.size(AwakenTheme.dimens.crestLarge / 2))
            } else {
                IconCircle(AwakenGlyphs.Lock, colors.accent, size = AwakenTheme.dimens.crestLarge / 2)
            }
            Text(
                text = lexString(R.string.pause_overlay_about, state.appLabel),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            BreathingRing(
                description = if (remaining > 0) lexString(R.string.pause_overlay_seconds_left, remaining) else lexString(R.string.pause_overlay_ready),
            ) {
                if (remaining > 0) {
                    Text(text = remaining.toString(), style = type.metricXL, color = colors.textPrimary)
                } else {
                    Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(AwakenTheme.dimens.icon))
                }
            }
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
                    enabled = remaining == 0,
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

private val sampleLine = "What did you come here for?"

@PhonePreviews
@Composable
private fun PauseCountingPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseOverlayContent(
            state = PauseOverlayUiState("Instagram", null, seconds = 5, line = sampleLine, opensToday = 3, requireTap = false, askIntention = false),
            onNotNow = {}, onContinue = {}, onIntention = {},
        )
    }
}

@PhonePreviews
@Composable
private fun PauseTapToContinueWithChipsPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseOverlayContent(
            state = PauseOverlayUiState(
                "A very long app name that keeps going and going", null, seconds = 0, line = sampleLine, opensToday = 1,
                requireTap = true, askIntention = true, chosen = PauseRules.Intention.LOOK_UP,
            ),
            onNotNow = {}, onContinue = {}, onIntention = {},
        )
    }
}
