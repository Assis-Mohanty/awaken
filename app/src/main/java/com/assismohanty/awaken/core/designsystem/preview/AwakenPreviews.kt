package com.assismohanty.awaken.core.designsystem.preview

import androidx.compose.ui.tooling.preview.Preview

/** Phone + small-phone previews (CLAUDE.md: every screen previews at both sizes; app is dark-only). */
@Preview(name = "Phone", device = "spec:width=411dp,height=891dp", showBackground = true, backgroundColor = 0xFF05080F)
@Preview(name = "Small phone", device = "spec:width=360dp,height=640dp", showBackground = true, backgroundColor = 0xFF05080F)
annotation class PhonePreviews
