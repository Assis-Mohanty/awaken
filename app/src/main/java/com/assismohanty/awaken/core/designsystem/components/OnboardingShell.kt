package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * A thin bar of [segments] equal segments; segment i is full once [progress] (0..segments) passes i + 1. Moves forward over
 * 300ms ease-out (`revealMs`), and jumps under reduce motion. One TalkBack node described by [description] ("Step 3 of 5").
 */
@Composable
fun SegmentedProgressBar(segments: Int, progress: Float, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val motion = AwakenTheme.motion
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, segments.toFloat()),
        animationSpec = tween(motion.duration(motion.revealMs), easing = motion.revealEasing),
        label = "onboardingProgress",
    )
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(dimens.onbSegmentGap),
    ) {
        repeat(segments) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(dimens.onbSegmentHeight)
                    .clip(AwakenTheme.shapes.pill)
                    .background(colors.accentMuted),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((animated - index).coerceIn(0f, 1f))
                        .background(colors.accent),
                )
            }
        }
    }
}

/**
 * The one frame every onboarding step uses: back arrow, logo and a segmented progress bar on top, the step's [body] in the
 * middle, and a Continue button pinned to the bottom, disabled until the step is valid. [onBack] is null on the first step
 * and [onSkip] on steps that cannot be skipped (the name).
 *
 * [body] is called with the step key it is drawing, and must draw that step from the key alone (use [OnboardingStepBody]
 * for the heading, subtitle and content layout). While steps change, the outgoing step is still drawn by its own key, so it
 * slides out with its own words. Steps slide left and fade over 280ms when [forward] and slide back otherwise; with reduce
 * motion they only crossfade. The top and the button stay put. The body decides its own scrolling.
 */
@Composable
fun OnboardingShell(
    stepKey: Int,
    forward: Boolean,
    segments: Int,
    progress: Float,
    progressDescription: String,
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    skipText: String = lexString(R.string.action_skip),
    primaryEnabled: Boolean = true,
    /** False on the building screen, which has nothing to press. */
    showPrimary: Boolean = true,
    body: @Composable (step: Int) -> Unit,
) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.gutter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(AwakenTheme.dimens.topBarHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (onBack != null) {
                    BackButton(onBack)
                } else {
                    Box(Modifier.size(AwakenTheme.dimens.touchTarget))
                }
                SegmentedProgressBar(segments = segments, progress = progress, description = progressDescription, modifier = Modifier.weight(1f))
                if (onSkip != null) {
                    QuietButton(text = skipText, onClick = onSkip)
                } else {
                    Box(Modifier.size(AwakenTheme.dimens.touchTarget))
                }
            }
            AnimatedContent(
                targetState = stepKey,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    if (motion.reduceMotion) {
                        fadeIn(tween(motion.quickMs)) togetherWith fadeOut(tween(motion.quickMs))
                    } else {
                        val spec = tween<Float>(motion.stepMs, easing = motion.standardEasing)
                        val slide = tween<androidx.compose.ui.unit.IntOffset>(motion.stepMs, easing = motion.standardEasing)
                        val direction = if (forward) 1 else -1
                        (slideInHorizontally(slide) { it * direction / SLIDE_DIVISOR } + fadeIn(spec)) togetherWith
                            (slideOutHorizontally(slide) { -it * direction / SLIDE_DIVISOR } + fadeOut(spec))
                    }
                },
                label = "onboardingStep",
            ) { step -> body(step) }
            if (showPrimary) {
                PrimaryButton(
                    text = primaryText,
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.md),
                )
            }
        }
    }
}

/** One step's layout inside [OnboardingShell]: a big heading (two lines at most), an optional grey subtitle, then [content] (scrolling when [scrollable]). */
@Composable
fun OnboardingStepBody(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = title,
            style = AwakenTheme.type.headline,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.lg).semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = AwakenTheme.type.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = Spacing.lg)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            content = content,
        )
    }
}

/** A step slides about a third of the width, not all of it: it reads as movement without a long wipe. */
private const val SLIDE_DIVISOR = 3

@PhonePreviews
@Composable
private fun OnboardingShellPreview() {
    AwakenTheme(reduceMotion = true) {
        ScreenBackground {
            OnboardingShell(
                stepKey = 1,
                forward = true,
                segments = 5,
                progress = 1.4f,
                progressDescription = "Step 2 of 22",
                primaryText = "Continue",
                onPrimary = {},
                onBack = {},
                primaryEnabled = false,
            ) {
                OnboardingStepBody(title = "What should we call you?", subtitle = "Your name stays on this phone.") {
                    Text("Step content", style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
                }
            }
        }
    }
}

@PhonePreviews
@Composable
private fun OnboardingShellSkippablePreview() {
    AwakenTheme(reduceMotion = true) {
        ScreenBackground {
            OnboardingShell(
                stepKey = 4,
                forward = true,
                segments = 5,
                progress = 2.6f,
                progressDescription = "Step 8 of 22",
                primaryText = "Continue",
                onPrimary = {},
                onBack = {},
                onSkip = {},
            ) {
                OnboardingStepBody(title = "What do you want to improve?", subtitle = "This shapes your daily plan.") {}
            }
        }
    }
}
