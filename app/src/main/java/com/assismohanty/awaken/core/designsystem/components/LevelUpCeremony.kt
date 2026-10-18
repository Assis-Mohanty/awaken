package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The level-up ceremony (DESIGN_SYSTEM §2.5), ~2.2 s: dim to 90 % void → gate ring expands → the level number rolls from
 * [oldLevel] to [newLevel] → [title] types on in [accent] (`crit` gold by default) → stat gains list in → heavy haptic when the number lands. Tap to skip
 * after 600 ms (jumps to the end), tap again to dismiss. Reduce-motion: a static window with the final state.
 *
 * Reused for rank promotion (Phase 7) by passing a different [title] and a [crest].
 */
@Composable
fun LevelUpCeremony(
    oldLevel: Int,
    newLevel: Int,
    statGains: Map<Stat, Int>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.system_title_level_up),
    accent: Color = AwakenTheme.colors.crit,
    crest: (@Composable () -> Unit)? = null,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val motion = AwakenTheme.motion
    val haptics = AwakenTheme.haptics
    val animate = !motion.reduceMotion
    val scope = rememberCoroutineScope()

    val ring = remember { Animatable(if (animate) 0f else 1f) }
    val number = remember { Animatable(if (animate) oldLevel.toFloat() else newLevel.toFloat()) }
    var titleVisible by remember { mutableStateOf(!animate) }
    var gainsVisible by remember { mutableStateOf(!animate) }
    var skippable by remember { mutableStateOf(!animate) }
    var finished by remember { mutableStateOf(!animate) }

    LaunchedEffect(oldLevel, newLevel) {
        if (!animate) {
            haptics.levelUp()
            return@LaunchedEffect
        }
        launch {
            delay(motion.ceremonySkippableAfterMs)
            skippable = true
        }
        val ringJob = async { ring.animateTo(1f, tween(RING_MS, easing = motion.revealEasing)) }
        delay(NUMBER_DELAY_MS)
        number.animateTo(newLevel.toFloat(), tween(NUMBER_MS, easing = LinearEasing))
        haptics.levelUp()
        titleVisible = true
        delay(GAINS_DELAY_MS)
        gainsVisible = true
        ringJob.await()
        finished = true
    }

    fun onTap() {
        when {
            finished -> onDismiss()
            skippable -> scope.launch {
                ring.snapTo(1f)
                number.snapTo(newLevel.toFloat())
                titleVisible = true
                gainsVisible = true
                finished = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.void.copy(alpha = AwakenAlpha.DIM_SCRIM))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::onTap),
        contentAlignment = Alignment.Center,
    ) {
        val ringStroke = AwakenTheme.dimens.ceremonyRing
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = ring.value
            val alpha = when {
                !animate -> RING_STATIC_ALPHA // reduce-motion: a still gate behind the window
                progress in 0f..1f -> (1f - progress) * RING_ALPHA // expands and fades out
                else -> 0f
            }
            if (alpha > 0f) {
                drawCircle(
                    color = accent.copy(alpha = alpha),
                    radius = size.minDimension / 2f * progress,
                    style = Stroke(width = ringStroke.toPx()),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .padding(Spacing.gutter)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            crest?.invoke()
            Text(
                text = stringResource(R.string.level_short, number.value.toInt()),
                style = type.displayXL,
                color = colors.bone,
            )
            if (titleVisible) {
                TypeOnText(text = title, style = type.windowTitle, color = accent, animate = animate)
            }
            AnimatedVisibility(visible = gainsVisible, enter = fadeIn(tween(motion.duration(motion.standardMs)))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    statGains.forEach { (stat, gain) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(AwakenGlyphs.stat(stat), contentDescription = null, tint = colors.stat(stat), modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
                            Text(stringResource(R.string.stat_gain, stat.label(), formatCount(gain)), style = type.label, color = colors.bone)
                        }
                    }
                }
            }
            if (finished) {
                Text(stringResource(R.string.level_up_continue), style = type.caption, color = colors.mist)
            }
        }
    }
}

private const val RING_MS = 1_400
private const val NUMBER_DELAY_MS = 300L
private const val NUMBER_MS = 900
private const val GAINS_DELAY_MS = 450L
private const val RING_ALPHA = 0.8f
private const val RING_STATIC_ALPHA = 0.25f

@PhonePreviews
@Composable
private fun LevelUpCeremonyPreview() {
    AwakenTheme(reduceMotion = true) {
        GateBackground(tint = AwakenTheme.colors.rankC) {
            LevelUpCeremony(oldLevel = 9, newLevel = 10, statGains = mapOf(Stat.STR to 94, Stat.WIL to 28), onDismiss = {}, crest = {
                RankCrest(Rank.D, AwakenTheme.dimens.crestLarge)
            })
        }
    }
}

/**
 * The ceremony in its own full-screen window: blocks touch and TalkBack focus on the screen underneath, and Back
 * dismisses it. Screens should use this rather than placing [LevelUpCeremony] in their own layout.
 */
@Composable
fun LevelUpDialog(
    oldLevel: Int,
    newLevel: Int,
    statGains: Map<Stat, Int>,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.system_title_level_up),
    accent: Color = AwakenTheme.colors.crit,
    crest: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        LevelUpCeremony(
            oldLevel = oldLevel,
            newLevel = newLevel,
            statGains = statGains,
            onDismiss = onDismiss,
            title = title,
            accent = accent,
            crest = crest,
        )
    }
}
