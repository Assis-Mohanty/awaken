package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank

/**
 * Today's first row (DECLUTTER_PLAN §4): "Level 12 · Rank D", a thin XP bar with its numbers, and the streak. One tap
 * target that opens Progress. The caller passes plain numbers; nothing here knows a rule.
 */
@Composable
fun TodayHeaderRow(
    level: Int,
    rank: Rank,
    xpIntoLevel: Long,
    xpForLevel: Long,
    streak: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val description = lexString(R.string.today_header_cd, level, rank.name, formatCount(xpIntoLevel), formatCount(xpForLevel), streak)
    AwakenCard(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        reveal = false,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                lexString(R.string.today_header_level, level, rank.name),
                style = AwakenTheme.type.title,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            Text(
                lexPlural(R.plurals.today_header_streak, streak, streak),
                style = AwakenTheme.type.label,
                color = colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        XpBar(level = level, xpIntoLevel = xpIntoLevel, xpForLevel = xpForLevel, showLabel = false, modifier = Modifier.clearAndSetSemantics { })
        Text(
            lexString(R.string.xp_progress, formatCount(xpIntoLevel), formatCount(xpForLevel)),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * A section heading with up to two quiet actions on the right ("Today's plan · 3 of 7 done"  Plan day  Habits). The title is
 * the accessible heading.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (isLargeText()) {
        // Big text: the title gets the full width and its actions sit on a row of their own.
        Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.md)) {
            Text(title, style = AwakenTheme.type.windowTitle, color = AwakenTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) { actions() }
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            title,
            style = AwakenTheme.type.windowTitle,
            color = AwakenTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

@ComponentPreview
@Composable
private fun TodayHeaderRowPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            TodayHeaderRow(level = 12, rank = Rank.D, xpIntoLevel = 1_240, xpForLevel = 1_800, streak = 14, onClick = {})
            SectionHeader(title = "Today's plan · 3 of 7 done") {
                QuietButton(text = "Plan day", onClick = {})
                QuietButton(text = "Habits", onClick = {})
            }
        }
    }
}
