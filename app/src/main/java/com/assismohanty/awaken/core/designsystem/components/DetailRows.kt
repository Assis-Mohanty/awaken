package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** One card of label / value rows split by hairlines: "Best run · 30 days". The callers pass every word. */
@Composable
fun DetailRows(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier.fillMaxWidth(), reveal = false, spacing = Spacing.zero) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(label, style = AwakenTheme.type.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Text(value, style = AwakenTheme.type.numericSmall, color = colors.textPrimary)
            }
        }
    }
}

@ComponentPreview
@Composable
private fun DetailRowsPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter)) {
            DetailRows(listOf("Best run" to "30 days", "Saved" to "\u20B918", "Time back" to "4 h"))
        }
    }
}
