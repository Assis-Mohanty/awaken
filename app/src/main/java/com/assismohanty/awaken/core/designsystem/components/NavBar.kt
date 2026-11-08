package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** One destination of the [NavBar]: line icon at rest, solid icon when selected. */
@Immutable
data class NavBarItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * The bottom navigation (design/components.md): five tabs on the page colour with a hairline above. Every tab shows its
 * icon and label; the selected one fills a pill indicator in ink behind its solid icon. Each tab is a 48dp target and the
 * label is always in the semantics. Labels stop growing at 130% so five of them always fit on a small phone.
 */
@Composable
fun NavBar(items: List<NavBarItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(MAX_FONT_SCALE))) {
        Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
            HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.xxs, vertical = Spacing.xs)
                    .selectableGroup(),
                verticalAlignment = Alignment.Top,
            ) {
                items.forEachIndexed { index, item ->
                    NavTab(item, index == selectedIndex, onClick = { onSelect(index) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavTab(item: NavBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val dimens = AwakenTheme.dimens
    val fill by animateColorAsState(
        if (selected) colors.accent else Color.Transparent,
        tween(motion.duration(motion.quickMs), easing = motion.standardEasing),
        label = "navPill",
    )
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.touchTarget)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs - 1.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = dimens.navIndicatorWidth, height = dimens.navIndicatorHeight)
                .clip(AwakenTheme.shapes.pill)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = if (selected) colors.onAccent else colors.textSecondary,
                modifier = Modifier.size(dimens.icon - Spacing.xxs / 2),
            )
        }
        Text(
            item.label,
            style = AwakenTheme.type.micro,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_FONT_SCALE = 1.3f

@ComponentPreview
@Composable
private fun NavBarPreview() {
    AwakenTheme {
        NavBar(
            items = listOf(
                NavBarItem("Today", AwakenGlyphs.Home, AwakenGlyphs.HomeSolid),
                NavBarItem("Focus", AwakenGlyphs.Focus, AwakenGlyphs.FocusSolid),
                NavBarItem("Gym", AwakenGlyphs.Dumbbell, AwakenGlyphs.DumbbellSolid),
                NavBarItem("Diet", AwakenGlyphs.Bowl, AwakenGlyphs.BowlSolid),
                NavBarItem("Progress", AwakenGlyphs.Bars, AwakenGlyphs.BarsSolid),
            ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
