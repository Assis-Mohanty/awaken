package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Spacing on a 4dp grid: DESIGN_SYSTEM.md §2.3 (4, 8, 12, 16, 24, 32, 48; screen margin 20; content max width 560). */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    /** Screen margin. */
    val zero = 0.dp
    val gutter = 20.dp
    /** Inner padding of a card. */
    val card = 16.dp
    /** Between cards in a stack. */
    val cardGap = 8.dp
    /** Space between sections of a screen. */
    val section = 28.dp
    val contentMaxWidth = 560.dp
    /** Inside a card that holds list rows: rows bring their own vertical padding. */
    val listCard = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
}
