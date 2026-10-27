package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The top-level screen frame (DESIGN_SYSTEM §3): a 34sp large title with a quiet [subtitle] above it and header
 * [actions] beside it, scrolling with the content; once the title has scrolled away a 17sp title fades in, pinned at the
 * top on the page colour with a hairline. Content sits in a 560dp column with 16dp margins and 12dp between cards.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    var headerHeight by remember { mutableIntStateOf(0) }
    val collapsed by remember { derivedStateOf { headerHeight > 0 && scrollState.value >= headerHeight } }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(motion.duration(motion.quickMs), easing = motion.standardEasing), label = "titleBar")
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = Spacing.md, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                GreetingHeader(
                    greeting = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    actions = actions,
                    modifier = Modifier
                        .padding(bottom = Spacing.sm)
                        .onSizeChanged { headerHeight = it.height },
                )
                content()
            }
        }
        if (barAlpha > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = barAlpha }
                    .background(colors.surface),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(AwakenTheme.dimens.topBarHeight), contentAlignment = Alignment.Center) {
                    // The large title is the accessible heading; the pinned copy is decoration.
                    Text(
                        title,
                        style = AwakenTheme.type.title,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Spacing.xxl + Spacing.md).clearAndSetSemantics { },
                    )
                    // Back stays reachable once the large header has scrolled away.
                    onBack?.let { BackButton(it, Modifier.align(Alignment.CenterStart).padding(start = Spacing.xs)) }
                }
                HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
            }
        }
    }
}

@PhonePreviews
@Composable
private fun LargeTitleScaffoldPreview() {
    AwakenTheme {
        GateBackground(tint = AwakenTheme.colors.surface) {
            LargeTitleScaffold(title = "Good morning, Assis", subtitle = "Tuesday, 27 October") {
                SkeletonCard()
                SkeletonCard(lines = 4)
            }
        }
    }
}
