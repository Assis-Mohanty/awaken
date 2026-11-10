package com.assismohanty.awaken.feature.comingsoon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/** What a switched-off feature is called on its Coming soon page. */
enum class ComingSoonFeature(val titleRes: Int, val lineRes: Int) {
    PATHS(R.string.coming_soon_paths_title, R.string.coming_soon_paths_line),
    REEL_COUNTER(R.string.coming_soon_reels_title, R.string.coming_soon_reels_line),
}

/** The page every switched-off feature opens (see `AiFeature`, `StrictFeature`). */
@Composable
fun ComingSoonEntry(feature: ComingSoonFeature, onBack: () -> Unit, modifier: Modifier = Modifier) =
    ComingSoonScreen(feature, onBack, modifier)

@Composable
fun ComingSoonScreen(feature: ComingSoonFeature, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LargeTitleScaffold(title = lexString(feature.titleRes), onBack = onBack, modifier = modifier) {
        EmptyState(
            line = lexString(feature.lineRes),
            actionLabel = lexString(R.string.coming_soon_back),
            onAction = onBack,
        )
    }
}

@PhonePreviews
@Composable
private fun ComingSoonPathsPreview() {
    AwakenTheme { ComingSoonScreen(ComingSoonFeature.PATHS, onBack = {}) }
}

@PhonePreviews
@Composable
private fun ComingSoonReelsPreview() {
    AwakenTheme { ComingSoonScreen(ComingSoonFeature.REEL_COUNTER, onBack = {}) }
}
