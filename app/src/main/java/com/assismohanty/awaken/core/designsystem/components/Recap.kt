package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * Story-style pages for a recap (DESIGN_SYSTEM §8.1 RecapCard): swipe, or tap the left/right third, to move. Never
 * auto-advances. With reduce motion the pages crossfade instantly instead of sliding. Page dots below.
 */
@Composable
fun RecapPager(
    pageCount: Int,
    page: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(page: Int) -> Unit,
) {
    val motion = AwakenTheme.motion
    val change by rememberUpdatedState(onPageChange)
    val current by rememberUpdatedState(page)
    fun go(to: Int) {
        if (to in 0 until pageCount && to != current) change(to)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(pageCount) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, amount -> total += amount },
                        onDragEnd = {
                            if (total < -SWIPE_PX) go(current + 1) else if (total > SWIPE_PX) go(current - 1)
                        },
                    )
                }
                .pointerInput(pageCount) {
                    detectTapGestures { offset ->
                        when {
                            offset.x < size.width / EDGE_PARTS -> go(current - 1)
                            offset.x > size.width * (EDGE_PARTS - 1) / EDGE_PARTS -> go(current + 1)
                        }
                    }
                },
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val ms = motion.duration(motion.standardMs)
                    if (motion.reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(ms)) { if (forward) it / SLIDE_PARTS else -it / SLIDE_PARTS } + fadeIn(tween(ms))) togetherWith
                            (slideOutHorizontally(tween(ms)) { if (forward) -it / SLIDE_PARTS else it / SLIDE_PARTS } + fadeOut(tween(ms)))
                    }
                },
                label = "recapPage",
            ) { shown ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) { content(shown) }
            }
        }
        PageDots(pageCount, page, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** One recap page: a kicker, one hero number, 1–3 supporting rows, then any extra [footer] (e.g. Share). */
@Composable
fun RecapCard(
    kicker: String,
    hero: String,
    heroLabel: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier.fillMaxWidth().heightIn(min = AwakenTheme.dimens.recapCardMinHeight), reveal = false) {
        Text(kicker, style = AwakenTheme.type.overline, color = colors.textSecondary)
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(hero, style = AwakenTheme.type.metricXL, color = colors.textPrimary)
            Text(heroLabel, style = AwakenTheme.type.body, color = colors.textSecondary)
        }
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = AwakenTheme.type.body, color = colors.textSecondary)
                Text(value, style = AwakenTheme.type.numericSmall, color = colors.textPrimary)
            }
        }
        footer()
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val description = lexString(R.string.recap_page_cd, current + 1, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description; stateDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(DOT)
                    .clip(AwakenTheme.shapes.pill)
                    .background(if (index == current) colors.textPrimary else colors.divider),
            )
        }
    }
}

private val DOT = 7.dp
private const val SWIPE_PX = 48f
private const val EDGE_PARTS = 3
private const val SLIDE_PARTS = 5

@ComponentPreview
@Composable
private fun RecapCardPreview() {
    AwakenTheme {
        RecapPager(pageCount = 4, page = 1, onPageChange = {}, modifier = Modifier.padding(Spacing.gutter)) {
            RecapCard(
                kicker = "OCTOBER 2026",
                hero = "41",
                heroLabel = "quests completed",
                rows = listOf("Levels gained" to "3", "Focus hours" to "12.5"),
            )
        }
    }
}
