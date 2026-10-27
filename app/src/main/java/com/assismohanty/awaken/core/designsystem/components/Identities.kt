package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat

/** One identity as the UI shows it: [phrase] is the words after "Someone who" (GD §14.7). */
@Immutable
data class IdentityOption(val id: String, val stat: Stat, val phrase: String)

/**
 * "Someone who reads" pills with the stat's glyph, up to three selected (DESIGN_SYSTEM §8.1). The caller enforces the
 * limit and sets [showLimit] after a 4th tap, which reads "Pick up to 3" (announced politely).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityChips(
    options: List<IdentityOption>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    showLimit: Boolean = false,
) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            options.forEach { option ->
                ChoiceChip(
                    text = lexString(R.string.identity_statement, option.phrase),
                    selected = option.id in selected,
                    onClick = { onToggle(option.id) },
                    color = colors.stat(option.stat),
                    icon = AwakenGlyphs.stat(option.stat),
                    multiSelect = true,
                )
            }
        }
        if (showLimit) {
            Text(
                text = lexString(R.string.identity_limit),
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Read-only "Someone who trains · reads" line (Status header, Settings row); null when no identity is chosen. */
@Composable
fun identityLine(phrases: List<String>): String? =
    if (phrases.isEmpty()) null else lexString(R.string.identity_statement, phrases.joinToString(lexString(R.string.identity_separator)))

@ComponentPreview
@Composable
private fun IdentityChipsPreview() {
    AwakenTheme {
        IdentityChips(
            options = listOf(
                IdentityOption("trains", Stat.STR, "trains"),
                IdentityOption("reads", Stat.INT, "reads"),
                IdentityOption("focuses", Stat.WIL, "focuses deeply"),
                IdentityOption("sleeps_well", Stat.VIT, "sleeps well"),
                IdentityOption("speaks_up", Stat.CHA, "speaks up"),
            ),
            selected = listOf("reads", "trains", "focuses"),
            onToggle = {},
            showLimit = true,
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
