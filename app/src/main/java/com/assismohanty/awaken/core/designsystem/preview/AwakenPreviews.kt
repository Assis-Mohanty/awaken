package com.assismohanty.awaken.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Phone + small-phone previews in both palettes, plus the phone at 200 % font scale (CLAUDE.md, DESIGN_SYSTEM §3
 * quality bar). `AwakenTheme`'s default follows the preview's night mode, so every screen preview renders dark and light
 * without passing a mode.
 */
@Preview(name = "Phone", device = "spec:width=411dp,height=891dp", showBackground = true, backgroundColor = 0xFF131417, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Small phone", device = "spec:width=360dp,height=640dp", showBackground = true, backgroundColor = 0xFF131417, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Phone · light", device = "spec:width=411dp,height=891dp", showBackground = true, backgroundColor = 0xFFF2F2ED, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Small phone · light", device = "spec:width=360dp,height=640dp", showBackground = true, backgroundColor = 0xFFF2F2ED, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Phone · light · font 200%", device = "spec:width=411dp,height=891dp", showBackground = true, backgroundColor = 0xFFF2F2ED, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class PhonePreviews

/** Single component on the page colour, plus a 200 % font-scale variant (DESIGN_SYSTEM §6) and a light variant. */
@Preview(name = "Component", showBackground = true, backgroundColor = 0xFF131417, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Component · font 200%", showBackground = true, backgroundColor = 0xFF131417, widthDp = 380, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Component · light", showBackground = true, backgroundColor = 0xFFF2F2ED, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class ComponentPreview

/** A screen on its side: a large and a small landscape phone in the dark palette, and the small one in the light palette. */
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp", showBackground = true, backgroundColor = 0xFF131417, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Landscape · small", device = "spec:width=640dp,height=360dp", showBackground = true, backgroundColor = 0xFF131417, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Landscape · small · light", device = "spec:width=640dp,height=360dp", showBackground = true, backgroundColor = 0xFFF2F2ED, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class LandscapePreviews
