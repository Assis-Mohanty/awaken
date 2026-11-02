package com.assismohanty.awaken.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.reelcounter.core.lumi.LumiState
import com.assismohanty.awaken.reelcounter.core.lumi.lumiStage

/** The three sizes Lumi is shown at, so feature screens never write a literal size. */
object LumiSizes {
    /** Inside the floating counter bubble. */
    val Bubble = 36.dp

    /** Next to the number on the Today tile. */
    val Tile = 44.dp

    /** The hero on the Reel counter screen. */
    val Hero = 168.dp
}

/** Lumi's picture for [stage] 1..6 (1 radiant, 6 switched off). The widget uses this directly. */
@DrawableRes
fun lumiDrawable(stage: Int): Int = when (stage) {
    1 -> com.assismohanty.awaken.reelcounter.R.drawable.rc_lumi_stage_1
    2 -> com.assismohanty.awaken.reelcounter.R.drawable.rc_lumi_stage_2
    3 -> com.assismohanty.awaken.reelcounter.R.drawable.rc_lumi_stage_3
    4 -> com.assismohanty.awaken.reelcounter.R.drawable.rc_lumi_stage_4
    5 -> com.assismohanty.awaken.reelcounter.R.drawable.rc_lumi_stage_5
    else -> com.assismohanty.awaken.reelcounter.R.drawable.rc_lumi_stage_6
}

/** The short status line for [stage]: "Fully charged" … "Switched off". */
@StringRes
fun lumiStatusRes(stage: Int): Int = when (stage) {
    1 -> R.string.reel_lumi_status_1
    2 -> R.string.reel_lumi_status_2
    3 -> R.string.reel_lumi_status_3
    4 -> R.string.reel_lumi_status_4
    5 -> R.string.reel_lumi_status_5
    else -> R.string.reel_lumi_status_6
}

private object LumiMotion {
    const val CROSSFADE_MS = 400

    /** The "sigh" when the stage changes: 1.0 → 0.94 → 1.0 over the same 400ms as the crossfade. */
    const val SIGH_SCALE = 0.94f
    const val SIGH_HALF_MS = 200

    /** Within a stage Lumi dims by at most this much (10%) as the count climbs. */
    const val MAX_DIM = 0.10f

    /** Stage 1 only: a 2s loop (1s up, 1s down) of this height. */
    val BOB = 3.dp
    const val BOB_HALF_MS = 1_000
    const val SETTLE_MS = 200
}

/**
 * Lumi, the Reel counter mascot, at [state]. One composable for the bubble, the Today tile and the Reel counter
 * screen. [reelsToday] is only for the spoken description: "Lumi, Getting tired, 42 reels today".
 *
 * Stage changes crossfade over 400ms with a small "sigh" in scale. Inside a stage Lumi dims a little with
 * [LumiState.progressInStage]. Stage 1 bobs gently; stage 6 never moves. With reduced motion the stage swaps
 * instantly and nothing moves. The bob runs only while the host is resumed and the composable is on screen, so a
 * hidden overlay or a screen in the background costs no battery.
 */
@Composable
fun LumiView(state: LumiState, size: Dp, reelsToday: Int, modifier: Modifier = Modifier) {
    val motion = AwakenTheme.motion
    val reduce = motion.reduceMotion
    val description = lexPlural(R.plurals.reel_lumi_cd, reelsToday, lexString(lumiStatusRes(state.stage)), reelsToday)

    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val sigh = remember { Animatable(1f) }
    val bob = remember { Animatable(0f) }

    var shownStage by remember { mutableIntStateOf(state.stage) }
    LaunchedEffect(state.stage, reduce) {
        if (shownStage != state.stage) {
            shownStage = state.stage
            if (!reduce) {
                sigh.animateTo(LumiMotion.SIGH_SCALE, tween(LumiMotion.SIGH_HALF_MS))
                sigh.animateTo(1f, tween(LumiMotion.SIGH_HALF_MS))
            }
        }
        if (reduce) sigh.snapTo(1f)
    }

    val bobbing = state.stage == 1 && !reduce && resumed
    LaunchedEffect(bobbing) {
        if (bobbing) {
            while (true) {
                bob.animateTo(1f, tween(LumiMotion.BOB_HALF_MS, easing = FastOutSlowInEasing))
                bob.animateTo(0f, tween(LumiMotion.BOB_HALF_MS, easing = FastOutSlowInEasing))
            }
        } else if (bob.value != 0f) {
            if (reduce) bob.snapTo(0f) else bob.animateTo(0f, tween(LumiMotion.SETTLE_MS))
        }
    }

    val brightness = 1f - LumiMotion.MAX_DIM * state.progressInStage.coerceIn(0f, 1f)
    val dim = remember(brightness) { ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) }) }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = sigh.value
                scaleY = sigh.value
                translationY = -bob.value * LumiMotion.BOB.toPx()
            }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Crossfade(targetState = state.stage, animationSpec = tween(motion.duration(LumiMotion.CROSSFADE_MS)), label = "lumi") { stage ->
            Image(
                painter = painterResource(lumiDrawable(stage)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = dim,
                modifier = Modifier.size(size),
            )
        }
    }
}

@ComponentPreview
@Composable
private fun LumiStagesPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LumiRow(1..3)
            LumiRow(4..6)
        }
    }
}

@Composable
private fun LumiRow(stages: IntRange) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        stages.forEach { stage ->
            // Each stage at the start of its band, so the previews show the pictures themselves.
            LumiView(state = LumiState(stage, 0f), size = LumiSizes.Hero / 2, reelsToday = stage * 10)
        }
    }
}

@ComponentPreview
@Composable
private fun LumiDimmingPreview() {
    AwakenTheme(reduceMotion = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(0, 10, 25).forEach { reels ->
                LumiView(state = lumiStage(reels, null), size = LumiSizes.Tile, reelsToday = reels)
            }
            LumiView(state = LumiState(2, 0.99f), size = LumiSizes.Tile, reelsToday = 24)
        }
    }
}
