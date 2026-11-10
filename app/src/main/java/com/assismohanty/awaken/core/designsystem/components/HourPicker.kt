package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * Picks one of [options] (hours, in order): the chosen one big in a well with the one before and the one after above and below
 * it. Tapping a neighbour moves to it. [label] words a value ("04:00").
 */
@Composable
fun HourPicker(options: List<Int>, selected: Int, onSelect: (Int) -> Unit, label: @Composable (Int) -> String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val index = options.indexOf(selected).coerceAtLeast(0)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Neighbour(options.getOrNull(index - 1), onSelect, label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AwakenTheme.shapes.inner)
                .background(colors.surfaceSunken)
                .padding(vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(label(options.getOrElse(index) { selected }), style = AwakenTheme.type.metricXL, color = colors.textPrimary)
        }
        Neighbour(options.getOrNull(index + 1), onSelect, label)
    }
}

@Composable
private fun Neighbour(hour: Int?, onSelect: (Int) -> Unit, label: @Composable (Int) -> String) {
    // An empty slot keeps the well from moving when you reach the first or last option.
    Box(modifier = Modifier.heightIn(min = AwakenTheme.dimens.touchTarget), contentAlignment = Alignment.Center) {
        if (hour != null) {
            Text(
                label(hour),
                style = AwakenTheme.type.title,
                color = AwakenTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(AwakenTheme.shapes.inner)
                    .clickable(role = Role.Button) { onSelect(hour) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .semantics { role = Role.Button },
            )
        }
    }
}

@ComponentPreview
@Composable
private fun HourPickerPreview() {
    AwakenTheme {
        Column(Modifier.padding(Spacing.gutter)) {
            HourPicker(options = (0..6).toList(), selected = 4, onSelect = {}, label = { "%02d:00".format(it) })
        }
    }
}
