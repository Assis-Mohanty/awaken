package com.assismohanty.awaken.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Phone + small-phone previews in both palettes (CLAUDE.md). `AwakenTheme`'s default follows the preview's night mode,
 * so every screen preview renders dark and light without passing a mode.
 */
@Preview(name = "Phone", device = "spec:width=411dp,height=891dp", showBackground = true, backgroundColor = 0xFF000000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Small phone", device = "spec:width=360dp,height=640dp", showBackground = true, backgroundColor = 0xFF000000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Phone · light", device = "spec:width=411dp,height=891dp", showBackground = true, backgroundColor = 0xFFF6F6F4, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Small phone · light", device = "spec:width=360dp,height=640dp", showBackground = true, backgroundColor = 0xFFF6F6F4, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class PhonePreviews

/** Single component on the void background, plus a 200 % font-scale variant (DESIGN_SYSTEM §6) and a light variant. */
@Preview(name = "Component", showBackground = true, backgroundColor = 0xFF000000, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Component · font 200%", showBackground = true, backgroundColor = 0xFF000000, widthDp = 380, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Component · light", showBackground = true, backgroundColor = 0xFFF6F6F4, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class ComponentPreview
