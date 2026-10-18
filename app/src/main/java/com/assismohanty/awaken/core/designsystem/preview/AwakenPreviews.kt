package com.assismohanty.awaken.core.designsystem.preview

import androidx.compose.ui.tooling.preview.Preview

/** Phone + small-phone previews (CLAUDE.md: every screen previews at both sizes; app is dark-only). */
@Preview(name = "Phone", device = "spec:width=411dp,height=891dp", showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Small phone", device = "spec:width=360dp,height=640dp", showBackground = true, backgroundColor = 0xFF000000)
annotation class PhonePreviews

/** Single component on the void background, plus a 200 % font-scale variant (DESIGN_SYSTEM §6). */
@Preview(name = "Component", showBackground = true, backgroundColor = 0xFF000000, widthDp = 380)
@Preview(name = "Component · font 200%", showBackground = true, backgroundColor = 0xFF000000, widthDp = 380, fontScale = 2f)
annotation class ComponentPreview
