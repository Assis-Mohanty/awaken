package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** One destination of the [FloatingNavBar]: line icon at rest, solid icon when selected. */
@Immutable
data class NavBarItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * The bottom navigation (DESIGN_SYSTEM §3): a floating pill on the card colour, 16dp from the screen edges. The selected
 * destination sits in a lighter inner pill with its solid icon and label; the others show their line icon only, so the
 * bar stays calm. Every item is a 48dp tab; the label is always in the semantics.
 */
@Composable
fun FloatingNavBar(items: List<NavBarItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xs)
            .height(dimens.navBarHeight)
            .clip(AwakenTheme.shapes.pill)
            .background(colors.surfaceElevated)
            .padding(Spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavPill(item, selected, onClick = { onSelect(index) }, modifier = Modifier.weight(if (selected) SELECTED_WEIGHT else 1f))
        }
    }
}

@Composable
private fun NavPill(item: NavBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val fill by animateColorAsState(
        if (selected) colors.surfaceOverlay else Color.Transparent,
        tween(motion.duration(motion.quickMs), easing = motion.standardEasing),
        label = "navPill",
    )
    Box(
        modifier = modifier
            .height(AwakenTheme.dimens.navIndicatorHeight)
            .clip(AwakenTheme.shapes.pill)
            .background(fill)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(
                if (selected) item.selectedIcon else item.icon,
                contentDescription = if (selected) null else item.label,
                tint = if (selected) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.size(AwakenTheme.dimens.icon),
            )
            if (selected) {
                Text(item.label, style = AwakenTheme.type.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The selected pill is wider so its label fits. */
private const val SELECTED_WEIGHT = 1.9f

@ComponentPreview
@Composable
private fun FloatingNavBarPreview() {
    AwakenTheme {
        FloatingNavBar(
            items = listOf(
                NavBarItem("Today", AwakenGlyphs.Home, AwakenGlyphs.HomeSolid),
                NavBarItem("Gym", AwakenGlyphs.Dumbbell, AwakenGlyphs.DumbbellSolid),
                NavBarItem("Diet", AwakenGlyphs.Bowl, AwakenGlyphs.BowlSolid),
                NavBarItem("Progress", AwakenGlyphs.Bars, AwakenGlyphs.BarsSolid),
            ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
