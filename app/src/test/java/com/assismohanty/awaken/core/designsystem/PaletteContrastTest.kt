package com.assismohanty.awaken.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.assismohanty.awaken.core.designsystem.theme.AwakenPalette
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * DESIGN_SYSTEM §2.1 contrast floors in both looks: ink ≥ 7:1 on cards and on the page, secondary ink ≥ 5:1 on cards and ≥ 4.5:1 (AA) on the page, button
 * text ≥ 4.5:1 on its fill. The stat accents are the brief's fixed hexes and are decorative: they mark icons and chips
 * that always carry text, so they have no floor here (several are under 3:1 on white; DESIGN_SYSTEM §2.1).
 */
class PaletteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, colors: AwakenPalette) {
        // Secondary ink is ≥ 5:1 on cards; on the page it meets WCAG AA (light: 4.98:1 with the brief's #6B6965 on #F5F4F1).
        listOf(Triple("card", colors.panel, 5.0), Triple("page", colors.void, 4.5)).forEach { (surface, bg, secondary) ->
            assertWithMessage("$name ink on $surface").that(contrast(colors.bone, bg)).isAtLeast(7.0)
            assertWithMessage("$name secondary on $surface").that(contrast(colors.mist, bg)).isAtLeast(secondary)
        }
        assertWithMessage("$name text on the primary button").that(contrast(colors.onAccent, colors.system)).isAtLeast(4.5)
    }

    @Test
    fun darkPaletteMeetsTheFloors() = check("dark", AwakenPalette.Dark)

    @Test
    fun lightPaletteMeetsTheFloors() = check("light", AwakenPalette.Light)
}
