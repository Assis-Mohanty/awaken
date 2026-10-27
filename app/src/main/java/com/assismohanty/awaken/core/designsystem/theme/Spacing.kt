package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Spacing on an 8dp grid — DESIGN_SYSTEM.md §2.3 (4, 8, 12, 16, 24, 32, 48; screen margin 16; content max width 560). */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    /** Screen margin. */
    val gutter = 16.dp
    /** Inner padding of a card. */
    val card = 20.dp
    /** Between cards in a stack. */
    val cardGap = 12.dp
    /** Space between sections of a screen. */
    val section = 32.dp
    val contentMaxWidth = 560.dp
    /** Inside a card that holds list rows: rows bring their own vertical padding. */
    val listCard = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
}
